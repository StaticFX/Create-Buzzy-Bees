package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.action.impl.DropOffItemsAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.task.TaskBatch
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus

class ExecuteTaskBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        BeeMemoryModules.CURRENT_TASK.get() to MemoryStatus.VALUE_PRESENT,
        BeeMemoryModules.HIVE_INSTANCE.get() to MemoryStatus.VALUE_PRESENT
    ),
    1
) {

    companion object {
        /** Global throttle: limits block operations across ALL bees per tick to prevent TPS drops. */
        private var operationsThisTick = 0
        private var lastThrottleTick = -1L

        fun canExecuteAction(gameTime: Long): Boolean {
            if (gameTime != lastThrottleTick) {
                lastThrottleTick = gameTime
                operationsThisTick = 0
            }
            return operationsThisTick < de.devin.cbbees.config.CBBeesConfig.maxBlockOperationsPerTick.get()
        }

        fun recordAction() {
            operationsThisTick++
        }
    }

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        // Global throttle: too many block ops this tick → wait
        if (!canExecuteAction(level.gameTime)) return false

        val isDropOff = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).orElse(null)
            ?.getCurrentTask()?.action is DropOffItemsAction

        if (owner.springTension <= 0f) {
            if (isDropOff) BeeDebug.log(owner, "Execute(DropOff): BLOCKED by spring=0")
            else BeeDebug.log(owner, "Execute: spring depleted — returning to hive")
            return false
        }

        val batch = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val task = batch.getCurrentTask()
        if (task == null) {
            if (isDropOff) BeeDebug.log(owner, "Execute(DropOff): BLOCKED — no current task")
            else BeeDebug.log(owner, "Execute: no current task in batch")
            return false
        }

        if (isDropOff) {
            BeeDebug.log(owner, "Execute(DropOff): checking — beePos=${owner.blockPosition()}, targetPos=${task.targetPos}, spring=${owner.springTension}")
        }

        // Check if task is within the bee's current network range
        // Skip for DropOffItemsAction — it's a cleanup action that must always execute
        if (task.action !is DropOffItemsAction) {
            val network = owner.network()
            if (network != null && !network.isInRange(task.targetPos)) {
                BeeDebug.log(owner, "Execute: task at ${task.targetPos} out of network range")
                return false
            }
        }

        val workRange = owner.workRange
        val inRange = owner.blockPosition().closerThan(task.targetPos, workRange)

        if (!inRange) {
            if (isDropOff) {
                val dist = owner.blockPosition().distSqr(task.targetPos)
                BeeDebug.log(owner, "Execute(DropOff): BLOCKED by proximity — dist²=$dist, workRange=$workRange")
            }
            return false
        }

        // Check if the task needs items the bee doesn't have
        val action = task.action
        if (action is ItemConsumingAction && !action.hasItems(owner)) {
            BeeDebug.log(owner, "Execute: missing items for task")
            return false
        }

        if (isDropOff) BeeDebug.log(owner, "Execute(DropOff): PASSED all checks")
        else BeeDebug.log(owner, "Execute: ready to run ${task.action.getDescription()}")
        return true
    }

    override fun start(level: ServerLevel, owner: MechanicalBeeEntity, gameTime: Long) {
        val batch = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).get()
        val task = batch.getCurrentTask() ?: return
        val hive = owner.brain.getMemory(BeeMemoryModules.HIVE_INSTANCE.get()).get()

        BeeDebug.log(owner, "Executing: ${task.action.getDescription()}")

        recordAction()
        val done = task.action.execute(level, owner, owner.getBeeContext())

        if (done) {
            // Drain spring based on action type
            val drain = if (task.action is RemoveBlockAction)
                CBBeesConfig.springDrainBreak.get()
            else
                CBBeesConfig.springDrainPlace.get()
            owner.consumeSpring(drain)

            task.complete()
            if (!batch.advance()) {
                val nextBatch = hive.notifyTaskCompleted(task, owner.uuid)

                if (nextBatch != null) {
                    BeeDebug.log(owner, "Batch done — received next batch")
                    owner.brain.setMemory(BeeMemoryModules.CURRENT_TASK.get(), nextBatch)
                    owner.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
                } else {
                    BeeDebug.log(owner, "Batch done — no more work, returning to hive")
                    owner.brain.eraseMemory(BeeMemoryModules.CURRENT_TASK.get())
                }
            } else {
                val nextTask = batch.getCurrentTask()
                // Skip DropOffItemsAction when inventory is empty (e.g. drop items upgrade)
                if (nextTask?.action is DropOffItemsAction && owner.getInventoryContents().isEmpty()) {
                    BeeDebug.log(owner, "Skipping drop-off (inventory empty)")
                    nextTask.complete()
                    if (!batch.advance()) {
                        val nextBatch = hive.notifyTaskCompleted(nextTask, owner.uuid)
                        if (nextBatch != null) {
                            owner.brain.setMemory(BeeMemoryModules.CURRENT_TASK.get(), nextBatch)
                        } else {
                            owner.brain.eraseMemory(BeeMemoryModules.CURRENT_TASK.get())
                        }
                    }
                } else {
                    BeeDebug.log(owner, "Advancing to next sub-task in batch")
                    nextTask?.action?.onActivate(owner)
                }
                owner.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            }
        } else {
            BeeDebug.log(
                owner,
                "Task failed — releasing batch (retry ${batch.retryCount + 1}/${TaskBatch.MAX_RETRIES})"
            )
            owner.network()?.releaseReservations(owner.uuid)
            batch.release(gameTick = gameTime)
            owner.brain.eraseMemory(BeeMemoryModules.CURRENT_TASK.get())
            owner.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        }
    }
}
