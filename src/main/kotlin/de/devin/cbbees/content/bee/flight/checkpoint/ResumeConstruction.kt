package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.content.bee.flight.CheckpointAction
import de.devin.cbbees.content.bee.flight.FlightPlanComputer
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.server.level.ServerLevel

/**
 * Recharge the bee's spring tension at the hive.
 * Pauses for a calculated duration based on the hive's RPM and bee context.
 *
 * @see CheckpointAction
 */
class ResumeConstruction(private val batch: TaskBatch) : CheckpointAction {
    private var requested = false
    private var failed = false

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        if (failed) return true
        if (requested) return false
        requested = true
        val network = bee.network() ?: return true
        FlightPlanComputer.computeAsync(bee, batch, network, level) { plan ->
            if (plan == null) {
                batch.releaseWithoutRetry()
                bee.currentTask = null
                failed = true
            } else {
                ServerBeeManager.startPlan(bee, plan, level.gameTime, network)
            }
        }
        return false
    }
}
