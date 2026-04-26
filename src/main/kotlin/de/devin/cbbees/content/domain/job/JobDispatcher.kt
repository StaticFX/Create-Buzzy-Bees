package de.devin.cbbees.content.domain.job

import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.network.HiveJobsSyncPacket
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer

/**
 * Shared dispatch logic for bee jobs.
 * Handles the common tail after task generation: setting center position,
 * adding batches, reconnecting portable hive, dispatching, syncing, and completing the tracker.
 */
object JobDispatcher {

    fun completeJobSetup(
        job: BeeJob,
        batches: List<TaskBatch>,
        centerPos: BlockPos,
        player: ServerPlayer?,
        tracker: JobCalculationProgress.Tracker?,
        completionKey: String,
    ) {
        if (batches.isEmpty()) {
            tracker?.fail()
            return
        }
        job.centerPos = centerPos
        job.addBatches(batches)

        player?.let { p ->
            ServerBeeNetworkManager.findPortableHive(p.uuid)?.let {
                ServerBeeNetworkManager.reconnectPortableHive(it)
            }
        }
        GlobalJobPool.dispatchNewJob(job)
        player?.let { HiveJobsSyncPacket.sendPlayerSnapshotTo(it) }
        tracker?.complete(completionKey, batches.size)
    }
}
