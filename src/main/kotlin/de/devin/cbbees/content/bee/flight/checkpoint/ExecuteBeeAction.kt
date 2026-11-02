package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.flight.CheckpointAction
import de.devin.cbbees.content.bee.flight.FlightPlanComputer
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.history.JobHistoryRecorder
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.server.level.ServerLevel

/**
 * Execute a [BeeAction] (place block, break block, etc.) and advance the [task] in the batch.
 * Respects the global block-operations-per-tick throttle.
 *
 * After the action completes, marks the task as completed and advances the batch.
 * This drives job progress tracking — without it, jobs never finish.
 *
 * @param beeAction the action to execute
 * @param task the task in the batch (for completion tracking)
 * @see CheckpointAction
 * @see de.devin.cbbees.content.domain.task.TaskBatch
 */
class ExecuteBeeAction(
    private val beeAction: BeeAction,
    private val task: BeeTask,
) : CheckpointAction {
    private var activated = false
    private var failTicks = 0

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        if (!activated) {
            beeAction.onActivate(bee)
            activated = true
        }

        if (!ActionThrottle.canExecute(gameTime)) return false

        val done = ActionThrottle.measure {
            JobHistoryRecorder.execute(level, task.job, beeAction) {
                beeAction.execute(level, bee, bee.getBeeContext())
            }
        }

        if (done) {
            failTicks = 0
            val drain = if (beeAction is RemoveBlockAction) CBBeesConfig.springDrainBreak.get()
            else CBBeesConfig.springDrainPlace.get()
            bee.consumeSpring(drain)

            task.complete()
            bee.currentTask?.advance()
            rewindIfExhausted(bee, level, gameTime)
        } else {
            failTicks++
            // If the action fails repeatedly (e.g., missing materials at placement),
            // release the batch and return home rather than looping forever.
            if (failTicks >= MAX_ACTION_FAIL_TICKS) bee.abandonTask()
        }
        return done
    }

    private fun rewindIfExhausted(bee: ServerBeeData, level: ServerLevel, gameTime: Long) {
        val batch = bee.currentTask ?: return
        if (bee.springTension > 0f || batch.getRemainingTasks().isEmpty()) return
        ServerBeeManager.redirect(bee, FlightPlanComputer.forRewind(bee, batch, level), gameTime)
    }

    companion object {
        private const val MAX_ACTION_FAIL_TICKS = 20 // 1 second
    }
}

/** Global per-tick time budget for block operations across all bees. */
