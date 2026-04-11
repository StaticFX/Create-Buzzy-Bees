package de.devin.cbbees.content.domain.job

import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.core.BlockPos
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
    var ownerId: UUID? = null,
    var uniquenessKey: Any? = null
) {
    /** Schematic placement metadata for client-side ghost block rendering. */
    var schematicPlacement: SchematicPlacement? = null

    /**
     * The batches associated with this job.
     */
    val batches: MutableList<TaskBatch> = mutableListOf()

    val tasks: List<BeeTask> get() = batches.flatMap { it.tasks }

    /**
     * Current number of bees contributed to this job.
     */
    var contributedBees: Int = 0
        private set

    /**
     * Map of source IDs to the number of bees they've contributed.
     */
    private val contributions: MutableMap<BeeHive, Int> = mutableMapOf()

    var status: JobStatus = JobStatus.WAITING_FOR_BEES
        private set

    /**
     * Checks if this job has enough bees to start.
     */
    fun canStart(): Boolean = contributedBees >= batches.size

    /**
     * Adds a contribution of bees from a source.
     *
     * @param beeHive the contributing source.
     * @param beeCount The number of bees being contributed.
     */
    @Synchronized
    fun addContribution(beeHive: BeeHive, beeCount: Int) {
        val currentContribution = contributions.getOrDefault(beeHive, 0)
        contributions[beeHive] = currentContribution + beeCount
        contributedBees = contributions.values.sum()

        if (canStart() && status == JobStatus.WAITING_FOR_BEES) {
            status = JobStatus.IN_PROGRESS
        }
    }

    @Synchronized
    fun removeContribution(beeHive: BeeHive): Int {
        val removed = contributions.remove(beeHive) ?: 0
        contributedBees = contributions.values.sum()
        return removed
    }

    /**
     * Gets the number of bees contributed by a specific source.
     */
    fun getContribution(beeHive: BeeHive): Int = contributions.getOrDefault(beeHive, 0)

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
     * Gets the next pending task and assigns it to a robot.
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
        if ((status == JobStatus.IN_PROGRESS || status == JobStatus.WAITING_FOR_BEES) && isComplete()) {
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
}