package de.devin.cbbees.content.domain.job

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.BatchDispatcher
import de.devin.cbbees.content.domain.built.BuiltSchematics
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
import de.devin.cbbees.content.schematics.SchematicJobKey
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import java.util.TreeSet
import java.util.UUID

/**
 * Represents a job that can be worked on by bees from multiple sources.
 *
 * A BeeJob aggregates tasks and tracks contributions from multiple BeeSource instances.
 * Jobs require a minimum number of bees to start, and multiple sources can pool their
 * bees together to meet this requirement.
 *
 * @property jobId Unique identifier for this job.
 * @property centerPos The center position of this job (used for range calculations).
 * @property requiredBeeCount Minimum number of bees needed to start this job.
 */
data class BeeJob(
    val jobId: UUID,
    var centerPos: BlockPos,
    val level: Level,
    val jobType: JobType,
    var ownerId: UUID? = null,
    var uniquenessKey: Any? = null
) {
    /** Dimension key for serialization — resolved from [level] at creation time. */
    val dimensionKey: ResourceKey<Level> = level.dimension()

    /** Schematic placement metadata for client-side ghost block rendering. */
    var schematicPlacement: SchematicPlacement? = null

    private val _batches = mutableListOf<TaskBatch>()
    private val pending = TreeSet(buildOrder)
    private val working = LinkedHashSet<TaskBatch>()

    /** Pending batches no network can reach. They wait here without holding up later phases. */
    private val parked = LinkedHashSet<TaskBatch>()

    /** Working batches whose primary action may still be running; finished ones are dropped when found. */
    private val unfinished = TreeSet(buildOrder)

    /** The batches of this job, in build order. */
    val batches: List<TaskBatch> get() = _batches

    /** Batches waiting for a bee, earliest phase first, then build order. */
    val pendingBatches: Collection<TaskBatch> get() = pending

    /** Batches a bee is currently working on. */
    val workingBatches: Collection<TaskBatch> get() = working

    /** Pending batches set aside because no network can reach them; see [park]. */
    val parkedBatches: Collection<TaskBatch> get() = parked

    val tasks: List<BeeTask> get() = batches.flatMap { it.tasks }

    /** Why dispatch last found nothing to do for this job, or null when it is just waiting. */
    var stall: Stall? = null

    var status: JobStatus = JobStatus.IN_PROGRESS
        internal set

    /**
     * Returns true if all batches in phases lower than [phase] are finished
     * (completed, cancelled, or permanently failed). This gates dispatch of
     * later phases so, e.g., dependent blocks are fully removed before
     * support blocks during deconstruction.
     */
    fun isPhaseReady(phase: Int): Boolean = phase <= lowestOpenPhase()

    /** The earliest phase that still has a batch whose primary action isn't done. */
    fun lowestOpenPhase(): Int {
        // A done primary action only reopens through a release, which moves the batch back to pending
        while (unfinished.firstOrNull()?.isPrimaryActionDone() == true) unfinished.pollFirst()
        return minOf(pending.firstOrNull()?.phase ?: Int.MAX_VALUE, unfinished.firstOrNull()?.phase ?: Int.MAX_VALUE)
    }

    /** The open phase when last checked, so [checkPhaseOpened] can tell when a later one opens. */
    private var openPhase = Int.MIN_VALUE

    /**
     * Wakes the dispatcher when a later phase opens, so idle bees start on it right away
     * instead of on the next dispatch cycle. Called whenever a main action completes or a
     * batch leaves the pending or working set.
     */
    internal fun checkPhaseOpened() {
        val phase = lowestOpenPhase()
        if (phase > openPhase && openPhase != Int.MIN_VALUE) BatchDispatcher.wake()
        openPhase = phase
    }

    /** Batches before this index were finished when last checked; lets [isComplete] skip them. */
    private var firstOpenBatch = 0

    /**
     * Adds a task to this job.
     */
    fun addTask(task: BeeTask) {
        addBatches(listOf(TaskBatch(listOf(task), this, task.targetPos)))
    }

    /**
     * Adds multiple batches to this job.
     */
    fun addBatches(newBatches: List<TaskBatch>) {
        newBatches.forEach { batch ->
            batch.order = _batches.size
            _batches.add(batch)
            track(batch)
        }
    }

    internal fun onStatusChanged(batch: TaskBatch, old: TaskStatus) {
        when (old) {
            TaskStatus.PENDING -> {
                pending.remove(batch)
                parked.remove(batch)
            }
            TaskStatus.PICKED, TaskStatus.IN_PROGRESS -> {
                working.remove(batch)
                unfinished.remove(batch)
            }
            else -> {}
        }
        track(batch)
        checkPhaseOpened()
    }

    /** Sets a pending batch aside until a network can reach it; the job still can't finish without it. */
    internal fun park(batch: TaskBatch) {
        if (pending.remove(batch)) parked.add(batch)
        checkPhaseOpened()
    }

    /** Puts a parked batch back in line for dispatch. */
    internal fun unpark(batch: TaskBatch) {
        if (parked.remove(batch)) pending.add(batch)
    }

    private fun track(batch: TaskBatch) {
        when (batch.status) {
            TaskStatus.PENDING -> pending.add(batch)
            TaskStatus.PICKED, TaskStatus.IN_PROGRESS -> {
                working.add(batch)
                unfinished.add(batch)
                stall = null
            }
            else -> {}
        }
    }

    /**
     * Checks if all tasks are completed.
     */
    fun isComplete(): Boolean {
        while (firstOpenBatch < batches.size && batches[firstOpenBatch].isFinished()) firstOpenBatch++
        if (firstOpenBatch < batches.size) return false
        // A released batch can reopen tasks behind the cursor, so confirm before finishing
        firstOpenBatch = batches.indexOfFirst { !it.isFinished() }.takeIf { it >= 0 } ?: batches.size
        return firstOpenBatch == batches.size
    }

    private fun TaskBatch.isFinished(): Boolean =
        tasks.all { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.CANCELLED }

    fun displayName(): Component = jobDisplayName(schematicPlacement, jobType)

    /**
     * Marks this job as completed.
     */
    fun complete() {
        status = JobStatus.COMPLETED
    }

    /**
     * Checks if this job should be completed.
     */
    fun checkCompletion() {
        if (status == JobStatus.IN_PROGRESS && isComplete()) {
            complete()
            BuiltSchematics.record(this)
            JobNotifications.finished(this)
        }
    }

    /**
     * Cancels this job and all its tasks.
     */
    fun cancel() {
        status = JobStatus.CANCELLED
        tasks.forEach {
            if (it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS) {
                it.cancel()
            }
        }
    }

    fun save(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        tag.putUUID("JobId", jobId)
        tag.putInt("CenterX", centerPos.x)
        tag.putInt("CenterY", centerPos.y)
        tag.putInt("CenterZ", centerPos.z)
        tag.putString("Dimension", dimensionKey.location().toString())
        tag.putString("JobType", jobType.id)
        tag.putString("Status", status.name)
        if (ownerId != null) tag.putUUID("OwnerId", ownerId!!)

        val key = uniquenessKey
        if (key is SchematicJobKey) {
            val keyTag = key.save()
            keyTag.putString("Type", "schematic_job_key")
            tag.put("UniquenessKey", keyTag)
        }

        schematicPlacement?.let { tag.put("SchematicPlacement", it.save()) }

        val batchList = ListTag()
        batches.forEach { batch -> batchList.add(batch.save(registries)) }
        tag.put("Batches", batchList)
        return tag
    }

    companion object {
        private val buildOrder = compareBy<TaskBatch>({ it.phase }, { it.order })

        fun load(tag: CompoundTag, registries: HolderLookup.Provider, server: MinecraftServer): BeeJob? {
            val dimStr = tag.getString("Dimension")
            val dimKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimStr))
            val level = server.getLevel(dimKey)
            if (level == null) {
                CreateBuzzyBeez.LOGGER.warn("[JobPool] Dropping job in unknown dimension: $dimStr")
                return null
            }

            val jobId = tag.getUUID("JobId")
            val centerPos = BlockPos(tag.getInt("CenterX"), tag.getInt("CenterY"), tag.getInt("CenterZ"))
            val jobTypeId = tag.getString("JobType")
            val jobType = JobType.entries.firstOrNull { it.id == jobTypeId }
            if (jobType == null) {
                CreateBuzzyBeez.LOGGER.warn("[JobPool] Dropping job with unknown type: $jobTypeId")
                return null
            }

            val ownerId = if (tag.hasUUID("OwnerId")) tag.getUUID("OwnerId") else null

            var uniquenessKey: Any? = null
            if (tag.contains("UniquenessKey")) {
                val keyTag = tag.getCompound("UniquenessKey")
                if (keyTag.getString("Type") == "schematic_job_key") {
                    uniquenessKey = SchematicJobKey.load(keyTag)
                }
            }

            val job = BeeJob(jobId, centerPos, level, jobType, ownerId, uniquenessKey)
            job.status = JobStatus.valueOf(tag.getString("Status"))

            if (tag.contains("SchematicPlacement")) {
                job.schematicPlacement = SchematicPlacement.load(tag.getCompound("SchematicPlacement"))
            }

            val batchList = tag.getList("Batches", Tag.TAG_COMPOUND.toInt())
            job.addBatches((0 until batchList.size).mapNotNull { TaskBatch.load(batchList.getCompound(it), registries, job) })

            return job
        }
    }
}