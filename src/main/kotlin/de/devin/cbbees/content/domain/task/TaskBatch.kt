package de.devin.cbbees.content.domain.task

import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.domain.job.BeeJob
import net.minecraft.core.BlockPos
import java.util.UUID

class TaskBatch(
    val tasks: List<BeeTask>,
    val job: BeeJob,
    val targetPosition: BlockPos,
    /** Execution phase — all batches in phase N must complete before phase N+1 is dispatched. */
    val phase: Int = 0,
    /** Which bee type should handle this batch. Hive uses this to consume the right item. */
    val beeType: BeeType = BeeType.CONSTRUCTION,
) {
    companion object {
        const val MAX_RETRIES = 5
        /** Minimum ticks before a released batch can be re-dispatched (3 seconds). */
        const val RETRY_COOLDOWN_TICKS = 60L
    }

    var status: TaskStatus = TaskStatus.PENDING
    var assignedNetworkId: UUID? = null
    /** UUID of the bee currently working on this batch. */
    var assignedBeeId: UUID? = null

    val priority: Int get() = tasks.maxOfOrNull { it.priority } ?: 0

    /** How many times this batch has been released after a failure. */
    var retryCount: Int = 0
        private set

    /** Game tick when this batch was last released. Used for cooldown. */
    var lastReleasedTick: Long = 0L
        private set

    /** Game tick when this batch was picked up or started. Used for stale detection. */
    var startedAtTick: Long = 0L
        private set

    private var currentIndex = 0

    val primaryTask: BeeTask? get() = tasks.firstOrNull()

    fun getCurrentTask(): BeeTask? = if (currentIndex < tasks.size) tasks[currentIndex] else null

    fun advance(): Boolean {
        currentIndex++
        if (currentIndex >= tasks.size) {
            status = TaskStatus.COMPLETED
            return false
        }
        return true
    }

    fun getRemainingTasks(): List<BeeTask> = tasks.subList(currentIndex, tasks.size)

    fun isComplete(): Boolean = currentIndex >= tasks.size

    /**
     * True once the primary action (first task) has finished, even if follow-up
     * tasks like [DropOffItemsAction] are still running. Used by phase gating
     * so the next phase can start as soon as blocks are physically removed.
     */
    fun isPrimaryActionDone(): Boolean {
        return status == TaskStatus.COMPLETED
                || status == TaskStatus.CANCELLED
                || status == TaskStatus.FAILED
                || (tasks.firstOrNull()?.status == TaskStatus.COMPLETED)
    }

    /** Whether this batch can be retried (hasn't exceeded max retries). */
    fun canRetry(): Boolean = retryCount < MAX_RETRIES

    /** Whether the cooldown period has elapsed since last release. */
    fun isCooldownElapsed(currentTick: Long): Boolean =
        retryCount == 0 || currentTick - lastReleasedTick >= RETRY_COOLDOWN_TICKS

    fun release(resetNetwork: Boolean = true, gameTick: Long = 0L) {
        currentIndex = 0
        assignedBeeId = null
        tasks.forEach { it.release() }
        retryCount++
        lastReleasedTick = gameTick
        if (retryCount >= MAX_RETRIES) {
            status = TaskStatus.FAILED
        } else {
            status = TaskStatus.PENDING
        }
        if (resetNetwork) {
            assignedNetworkId = null
        }
    }

    /**
     * Returns the batch to PENDING without incrementing the retry counter.
     * Used when a flight plan fails due to transient conditions (missing materials)
     * that the player can resolve — the batch should retry indefinitely.
     */
    fun releaseWithoutRetry() {
        currentIndex = 0
        assignedBeeId = null
        tasks.forEach { it.release() }
        status = TaskStatus.PENDING
        // Keep assignedNetworkId so the stall resolver can find the network
    }

    fun assignToBee(beeId: UUID, gameTime: Long) {
        status = TaskStatus.IN_PROGRESS
        assignedBeeId = beeId
        startedAtTick = gameTime
        tasks.forEach { it.assignToBee(beeId) }
    }
}
