package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.content.bee.flight.CheckpointAction
import de.devin.cbbees.content.bee.flight.FlightPlanComputer
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.server.level.ServerLevel

/**
 * At the hive: check for more work before entering.
 *
 * If a new [TaskBatch] is available from [GlobalJobPool.workBacklog], assigns it and
 * computes a new flight plan — the bee continues working without entering the hive.
 * If no work is available, the bee proceeds to enter the hive.
 *
 * This is what keeps bees busy: they only return home when there's truly nothing left to do.
 *
 * @see EnterHive
 * @see CheckpointAction
 */
class CheckForNextWork : CheckpointAction {

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        if (bee.springTension <= 0f) return true
        val hive = bee.hiveInstance ?: return true

        val nextBatch = GlobalJobPool.workBacklog(hive)
        if (nextBatch != null) {
            nextBatch.assignToBee(bee.id, gameTime)
            bee.currentTask = nextBatch

            val network = bee.network()
            if (network != null) {
                FlightPlanComputer.computeAsync(
                    bee, nextBatch, network, level
                ) { plan ->
                    if (plan == null) {
                        nextBatch.releaseWithoutRetry()
                        bee.currentTask = null
                        return@computeAsync
                    }
                    ServerBeeManager.startPlan(bee, plan, level.gameTime, network)
                }
                return true
            }
        }

        return true
    }
}
