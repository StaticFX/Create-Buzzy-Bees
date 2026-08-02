package de.devin.cbbees.content.domain.job

import de.devin.cbbees.CreateBuzzyBeez
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
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
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

    /**
     * Logical position that started this job (for example, the deployer block).
     * Pickup ItemEntity coordinates can be global/world-space even when the
     * triggering deployer is inside Sable, so hive routing must not infer the
     * preferred coordinate space from the item position alone.
     */
    var dispatchOrigin: BlockPos? = null

    /**
     * The batches associated with this job.
     */
    val batches: MutableList<TaskBatch> = mutableListOf()

    val tasks: List<BeeTask> get() = batches.flatMap { it.tasks }

    var status: JobStatus = JobStatus.IN_PROGRESS
        internal set

    /**
     * Returns true if all batches in phases lower than [phase] are finished
     * (completed, cancelled, or permanently failed). This gates dispatch of
     * later phases so, e.g., dependent blocks are fully removed before
     * support blocks during deconstruction.
     */
    fun isPhaseReady(phase: Int): Boolean {
        return batches.none { it.phase < phase && !it.isPrimaryActionDone() }
    }

    /**
     * Adds a task to this job.
     */
    fun addTask(task: BeeTask) {
        batches.add(TaskBatch(listOf(task), this, task.targetPos))
    }

    /**
     * Adds multiple tasks to this job.
     */
    fun addTasks(newTasks: List<BeeTask>) {
        newTasks.forEach { addTask(it) }
    }

    /**
     * Adds multiple batches to this job.
     */
    fun addBatches(newBatches: List<TaskBatch>) {
        batches.addAll(newBatches)
    }

    /**
     * Gets the next pending task and assigns it to a bee.
     */
    @Synchronized
    fun claimNextTaskBatch(beeId: UUID, gameTime: Long): TaskBatch? {
        val batch = batches.firstOrNull { it.status == TaskStatus.PENDING }
        batch?.assignToBee(beeId, gameTime)
        return batch
    }

    /**
     * Gets the next pending task, if any.
     */
    fun getNextTask(): BeeTask? {
        return tasks.firstOrNull { it.status == TaskStatus.PENDING }
    }

    /**
     * Checks if all tasks are completed.
     */
    fun isComplete(): Boolean {
        return tasks.all { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.CANCELLED }
    }

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

    /**
     * Gets the progress of this job as a percentage (0.0 to 1.0).
     */
    fun getProgress(): Float {
        if (tasks.isEmpty()) return 1.0f
        val completed = tasks.count { it.status == TaskStatus.COMPLETED }
        return completed.toFloat() / tasks.size.toFloat()
    }

    /**
     * Gets the number of remaining tasks.
     */
    fun getRemainingTaskCount(): Int {
        return tasks.count { it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS }
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
        dispatchOrigin?.let { origin ->
            tag.putInt("DispatchOriginX", origin.x)
            tag.putInt("DispatchOriginY", origin.y)
            tag.putInt("DispatchOriginZ", origin.z)
        }

        val batchList = ListTag()
        batches.forEach { batch -> batchList.add(batch.save(registries)) }
        tag.put("Batches", batchList)
        return tag
    }

    companion object {
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
            if (tag.contains("DispatchOriginX")) {
                job.dispatchOrigin = BlockPos(
                    tag.getInt("DispatchOriginX"),
                    tag.getInt("DispatchOriginY"),
                    tag.getInt("DispatchOriginZ")
                )
            }

            val batchList = tag.getList("Batches", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until batchList.size) {
                val batch = TaskBatch.load(batchList.getCompound(i), registries, job) ?: continue
                job.batches.add(batch)
            }

            return job
        }
    }
}
