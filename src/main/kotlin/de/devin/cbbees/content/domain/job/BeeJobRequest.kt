package de.devin.cbbees.content.domain.job

import de.devin.cbbees.content.domain.history.HistoryEntry
import de.devin.cbbees.content.domain.history.JobHistory
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Base class for everything that creates and dispatches bee jobs.
 *
 * Subclasses provide type-specific validation, job configuration, and task generation
 * via the hook methods below. The shared [submit] template handles the full lifecycle:
 * validate → create job → generate tasks → dispatch.
 *
 * To add a new planner type:
 * 1. Add a [JobType] entry
 * 2. Create a class extending [BeeJobRequest], or BeeJobPacket when triggered by a packet
 * 3. Override the abstract hooks
 * 4. Register packets in AllPackets
 */
abstract class BeeJobRequest {

    /**
     * Template method — must run on the server thread.
     * Orchestrates the full job creation lifecycle.
     */
    fun submit(player: ServerPlayer) {
        if (!validate(player)) return

        val jobId = UUID.randomUUID()
        val job = BeeJob(jobId, BlockPos.ZERO, player.level(), jobType()).apply {
            ownerId = player.uuid
            uniquenessKey = createUniquenessKey(player)
        }
        configureJob(job, player)
        startHistory(job, player)

        val server = player.server ?: return
        beforeGenerate(job, player)

        generateTasks(player, job, server) { batches, centerPos ->
            JobDispatcher.completeJobSetup(job, batches, centerPos, player, completionKey())
        }
    }

    // ── Subclass hooks ──

    protected abstract fun jobType(): JobType

    /** Translation key for the message shown once the job starts (e.g. "cbbees.deconstruction.started"). */
    protected abstract fun completionKey(): String

    /** Create the uniqueness key to prevent duplicate jobs. */
    protected abstract fun createUniquenessKey(player: ServerPlayer): Any

    /** Type-specific validation before job creation. Return false to abort. */
    protected open fun validate(player: ServerPlayer): Boolean = true

    /** Configure the job after creation (e.g. set schematicPlacement). */
    protected open fun configureJob(job: BeeJob, player: ServerPlayer) {}

    protected open fun startHistory(job: BeeJob, player: ServerPlayer) {
        if (!job.jobType.changesBlocks) return
        JobHistory.of(player).pushUndo(HistoryEntry(job.jobId, job.jobType, job.dimensionKey), clearRedo = true)
    }

    /** Called after job creation but before task generation (e.g. clear schematic from item). */
    protected open fun beforeGenerate(job: BeeJob, player: ServerPlayer) {}

    /**
     * Generate tasks for the job. Call [onComplete] with the resulting batches and center position.
     * May be synchronous or asynchronous — the callback handles both.
     */
    protected abstract fun generateTasks(
        player: ServerPlayer,
        job: BeeJob,
        server: MinecraftServer,
        onComplete: (batches: List<TaskBatch>, centerPos: BlockPos) -> Unit
    )
}
