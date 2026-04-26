package de.devin.cbbees.network

import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobCalculationProgress
import de.devin.cbbees.content.domain.job.JobDispatcher
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Base class for all packets that create and dispatch bee jobs.
 *
 * Subclasses provide type-specific validation, job configuration, and task generation
 * via the hook methods below. The shared [handleJob] template handles the full lifecycle:
 * validate → create job → create progress tracker → generate tasks → dispatch.
 *
 * To add a new planner type:
 * 1. Add a [JobType] entry
 * 2. Create a packet class extending [BeeJobPacket]
 * 3. Override the abstract hooks
 * 4. Register in [AllPackets]
 */
abstract class BeeJobPacket : CustomPacketPayload {

    /**
     * Template method — called inside `context.enqueueWork`.
     * Orchestrates the full job creation lifecycle.
     */
    protected fun handleJob(player: ServerPlayer) {
        if (!validate(player)) return

        val jobId = UUID.randomUUID()
        val job = BeeJob(jobId, BlockPos.ZERO, player.level(), jobType()).apply {
            ownerId = player.uuid
            uniquenessKey = createUniquenessKey(player)
        }
        configureJob(job, player)

        val server = player.server ?: return
        val tracker = JobCalculationProgress.newTracker(
            jobId, player.uuid, progressKey(), estimateWork(player), server
        )
        tracker.start()

        beforeGenerate(job, player)

        generateTasks(player, job, server, tracker) { batches, centerPos ->
            JobDispatcher.completeJobSetup(job, batches, centerPos, player, tracker, completionKey())
        }
    }

    // ── Subclass hooks ──

    protected abstract fun jobType(): JobType

    /** Translation key for the progress toast (e.g. "cbbees.progress.processing_area"). */
    protected abstract fun progressKey(): String

    /** Translation key for the completion toast (e.g. "cbbees.deconstruction.started"). */
    protected abstract fun completionKey(): String

    /** Create the uniqueness key to prevent duplicate jobs. */
    protected abstract fun createUniquenessKey(player: ServerPlayer): Any

    /** Estimate the amount of work for progress tracking (e.g. volume in blocks). */
    protected abstract fun estimateWork(player: ServerPlayer): Int

    /** Type-specific validation before job creation. Return false to abort. */
    protected open fun validate(player: ServerPlayer): Boolean = true

    /** Configure the job after creation (e.g. set schematicPlacement). */
    protected open fun configureJob(job: BeeJob, player: ServerPlayer) {}

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
        tracker: JobCalculationProgress.Tracker,
        onComplete: (batches: List<TaskBatch>, centerPos: BlockPos) -> Unit
    )

    companion object {
        /** Standard handle pattern — use in each packet's companion `handle` function. */
        fun <T : BeeJobPacket> handlePacket(payload: T, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                payload.handleJob(player)
            }
        }
    }
}
