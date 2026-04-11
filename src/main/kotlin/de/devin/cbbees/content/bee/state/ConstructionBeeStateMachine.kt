package de.devin.cbbees.content.bee.state

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.BeeSeparation
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.action.impl.DropOffItemsAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.items.AllItems as CBeesItems
import de.devin.cbbees.util.ItemStackKey
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * O(1) state machine for construction bees, replacing the Brain/Behavior system.
 *
 * Each tick: run cross-cutting checks (flight drain, orphan, stuck, spring),
 * then dispatch to the current state handler. No behavior precondition evaluation.
 */
object ConstructionBeeStateMachine {

    /** Global throttle for block operations per tick (shared with bumble bees). */
    private var operationsThisTick = 0
    private var lastThrottleTick = -1L

    fun canExecuteAction(gameTime: Long): Boolean {
        if (gameTime != lastThrottleTick) {
            lastThrottleTick = gameTime
            operationsThisTick = 0
        }
        return operationsThisTick < CBBeesConfig.maxBlockOperationsPerTick.get()
    }

    fun recordAction() { operationsThisTick++ }

    /**
     * Tick for non-entity [ServerBeeData]. Movement and stuck detection are handled
     * by [ServerBeeManager], so this only does state transitions and actions.
     */
    fun tickData(bee: ServerBeeData, level: ServerLevel, gameTime: Long) {
        // Flight drain
        if (bee.velocity.lengthSqr() > 0.001) bee.consumeSpring(CBBeesConfig.springDrainFlight.get())

        // Orphan check
        if (bee.hiveInstance == null) {
            val hive = ServerBeeNetworkManager.findHive(bee.hiveId ?: UUID.randomUUID())
            if (hive != null) bee.hiveInstance = hive
            else {
                bee.orphanedTicks++
                if (bee.orphanedTicks >= 200) {
                    // Drop as item
                    val itemStack = ItemStack(CBeesItems.MECHANICAL_BEE.get())
                    level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, itemStack))
                    bee.springTension = -9999f // signal removal
                }
                return
            }
        }

        // Portable beehive tracking
        (bee.hiveInstance as? PortableBeeHive)?.let { portable ->
            if (gameTime % 20 == 0L) {
                bee.hivePos = portable.player.blockPosition().above(2)
                if (!portable.isValid()) {
                    bee.currentTask?.release(gameTick = gameTime)
                    bee.currentTask = null
                    bee.hiveInstance = null
                    bee.constructionState = ConstructionBeeState.FLYING_HOME
                    return
                }
            }
        }

        // Spring empty → recharge
        if (bee.constructionState != ConstructionBeeState.RECHARGING
            && bee.constructionState != ConstructionBeeState.ENTERING_HIVE
            && bee.constructionState != ConstructionBeeState.ORPHANED
            && bee.springTension <= 0f
        ) {
            bee.constructionState = ConstructionBeeState.RECHARGING
            bee.walkTarget = bee.hivePos
        }

        // State dispatch — uses the same logic as the entity version but with ServerBeeData fields
        when (bee.constructionState) {
            ConstructionBeeState.GATHERING -> tickGatheringData(bee, level, gameTime)
            ConstructionBeeState.FLYING_TO_TASK -> tickFlyingToTaskData(bee)
            ConstructionBeeState.EXECUTING -> tickExecutingData(bee, level, gameTime)
            ConstructionBeeState.FLYING_HOME -> tickFlyingHomeData(bee)
            ConstructionBeeState.ENTERING_HIVE -> tickEnteringHiveData(bee, level, gameTime)
            ConstructionBeeState.RECHARGING -> tickRechargingData(bee, gameTime)
            ConstructionBeeState.DROPPING_ITEMS -> tickDroppingItemsData(bee, level)
            ConstructionBeeState.ORPHANED -> { /* handled above */ }
            ConstructionBeeState.RETURNING_TO_OWNER -> { /* TODO */ }
        }
    }

    // ── ServerBeeData state handlers (reuse helper methods via BeeWorker) ──

    private fun tickGatheringData(bee: ServerBeeData, level: ServerLevel, gameTime: Long) {
        val batch = bee.currentTask ?: run { bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos; return }
        val currentTask = batch.getCurrentTask()
        if (currentTask != null) {
            val action = currentTask.action
            if (action is ItemConsumingAction && action.hasItems(bee)) {
                bee.constructionState = ConstructionBeeState.FLYING_TO_TASK
                bee.walkTarget = currentTask.targetPos
                return
            }
            if (action !is ItemConsumingAction) {
                bee.constructionState = ConstructionBeeState.FLYING_TO_TASK
                bee.walkTarget = currentTask.targetPos
                return
            }
        }

        val missing = computeMissingItems(bee, batch)
        if (missing.isEmpty()) {
            bee.constructionState = ConstructionBeeState.FLYING_TO_TASK
            bee.walkTarget = batch.getCurrentTask()?.targetPos
            return
        }

        val network = bee.network() ?: run {
            batch.release(gameTick = gameTime); bee.currentTask = null
            bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos; return
        }

        val gatherPlan = buildGatherPlan(network, missing, bee.id)
        if (gatherPlan.isNotEmpty()) {
            val (targetPort, itemsAtPort) = gatherPlan.maxByOrNull { it.value.size }!!
            network.releaseReservations(bee.id)
            targetPort.reserve(bee.id, itemsAtPort, gameTime)

            if (bee.blockPosition().closerThan(targetPort.pos, 2.5)) {
                itemsAtPort.forEach { item ->
                    if (bee.isInventoryFull()) return@forEach
                    if (targetPort.hasItemStack(item) && targetPort.removeItemStack(item)) {
                        val remainder = bee.addToInventory(item.copy())
                        if (!remainder.isEmpty) targetPort.addItemStack(remainder)
                        bee.consumeSpring(CBBeesConfig.springDrainPickup.get())
                    }
                }
                targetPort.releaseReservation(bee.id)
            } else {
                bee.walkTarget = targetPort.pos
            }
            return
        }

        // No providers
        network.releaseReservations(bee.id)
        batch.release(gameTick = gameTime)
        bee.currentTask = null
        bee.walkTarget = null
        bee.constructionState = ConstructionBeeState.FLYING_HOME
        bee.walkTarget = bee.hivePos
    }

    private fun tickFlyingToTaskData(bee: ServerBeeData) {
        val batch = bee.currentTask ?: run { bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos; return }
        val task = batch.getCurrentTask() ?: run { bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos; return }
        val action = task.action
        if (action is ItemConsumingAction && !action.hasItems(bee)) {
            bee.constructionState = ConstructionBeeState.GATHERING; bee.walkTarget = null; return
        }
        if (bee.blockPosition().closerThan(task.targetPos, 2.5)) {
            bee.constructionState = ConstructionBeeState.EXECUTING; bee.walkTarget = null
        } else {
            bee.walkTarget = task.targetPos
        }
    }

    private fun tickExecutingData(bee: ServerBeeData, level: ServerLevel, gameTime: Long) {
        val batch = bee.currentTask ?: run { bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos; return }
        val task = batch.getCurrentTask() ?: run { bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos; return }
        val hive = bee.hiveInstance ?: run { bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos; return }
        if (!bee.blockPosition().closerThan(task.targetPos, 2.5)) {
            bee.constructionState = ConstructionBeeState.FLYING_TO_TASK; bee.walkTarget = task.targetPos; return
        }
        val action = task.action
        if (action is ItemConsumingAction && !action.hasItems(bee)) {
            bee.constructionState = ConstructionBeeState.GATHERING; bee.walkTarget = null; return
        }
        if (!canExecuteAction(gameTime)) return
        if (action !is DropOffItemsAction) {
            bee.network()?.let { if (!it.isInRange(task.targetPos)) return }
        }
        recordAction()
        val done = action.execute(level, bee, bee.getBeeContext())
        if (done) {
            val drain = if (action is RemoveBlockAction) CBBeesConfig.springDrainBreak.get() else CBBeesConfig.springDrainPlace.get()
            bee.consumeSpring(drain)
            task.complete()
            if (!batch.advance()) {
                val nextBatch = hive.notifyTaskCompleted(task, bee.id)
                if (nextBatch != null) {
                    bee.currentTask = nextBatch
                    nextBatch.assignToBee(bee.id, gameTime)
                    bee.constructionState = ConstructionBeeState.GATHERING
                } else {
                    bee.currentTask = null
                    bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos
                }
            } else {
                val nextTask = batch.getCurrentTask()
                if (nextTask?.action is DropOffItemsAction && bee.isInventoryEmpty()) {
                    nextTask.complete()
                    if (!batch.advance()) {
                        val nextBatch = hive.notifyTaskCompleted(nextTask, bee.id)
                        if (nextBatch != null) {
                            bee.currentTask = nextBatch
                            nextBatch.assignToBee(bee.id, gameTime)
                            bee.constructionState = ConstructionBeeState.GATHERING
                        } else {
                            bee.currentTask = null; bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos
                        }
                    }
                } else {
                    nextTask?.action?.onActivate(bee)
                    bee.constructionState = ConstructionBeeState.GATHERING
                }
            }
            bee.walkTarget = null
        } else {
            bee.network()?.releaseReservations(bee.id)
            batch.release(gameTick = gameTime); bee.currentTask = null
            bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos
        }
    }

    private fun tickFlyingHomeData(bee: ServerBeeData) {
        if (bee.getInventoryContents().isNotEmpty() && bee.currentTask == null) {
            bee.constructionState = ConstructionBeeState.DROPPING_ITEMS; bee.walkTarget = null; return
        }
        val hive = bee.hiveInstance ?: return
        if (bee.blockPosition().closerThan(hive.pos, 4.0)) {
            bee.constructionState = ConstructionBeeState.ENTERING_HIVE; bee.walkTarget = null
        } else {
            bee.walkTarget = hive.pos
        }
    }

    private fun tickEnteringHiveData(bee: ServerBeeData, level: ServerLevel, gameTime: Long) {
        val hive = bee.hiveInstance ?: run { bee.orphanedTicks = 0; bee.constructionState = ConstructionBeeState.ORPHANED; return }
        if (!bee.blockPosition().closerThan(hive.pos, 4.0)) {
            bee.constructionState = ConstructionBeeState.FLYING_HOME; return
        }
        val ctx = bee.getBeeContext()
        hive.chargeReturnFuel(1.0f - bee.springTension, ctx)
        val beeItem = ItemStack(CBeesItems.MECHANICAL_BEE.get())
        if (hive.returnBee(beeItem)) {
            // Update hive tracking before removing the bee
            (hive as? MechanicalBeehiveBlockEntity)?.onBeeRemovedById(bee.id)
            ServerBeeManager.removeBee(bee.id)
        } else {
            bee.hiveEntryRetries++
            if (bee.hiveEntryRetries >= 3) {
                level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, beeItem))
                bee.dropInventory()
                (hive as? MechanicalBeehiveBlockEntity)?.onBeeRemovedById(bee.id)
                ServerBeeManager.removeBee(bee.id)
            }
        }
    }

    private fun tickRechargingData(bee: ServerBeeData, gameTime: Long) {
        val hive = bee.hiveInstance ?: run { bee.constructionState = ConstructionBeeState.ORPHANED; bee.orphanedTicks = 0; return }
        if (bee.rechargeFinishTick >= 0) {
            if (gameTime >= bee.rechargeFinishTick) {
                bee.springTension = 1.0f; bee.rechargeFinishTick = -1
                bee.constructionState = if (bee.currentTask != null) ConstructionBeeState.GATHERING else ConstructionBeeState.FLYING_HOME
                bee.walkTarget = if (bee.currentTask != null) null else bee.hivePos
            }
            return
        }
        if (bee.blockPosition().closerThan(hive.pos, 4.0)) {
            val ctx = bee.getBeeContext()
            bee.rechargeFinishTick = gameTime + hive.rechargeSpring(ctx)
        } else {
            bee.walkTarget = hive.pos
        }
    }

    private fun tickDroppingItemsData(bee: ServerBeeData, level: ServerLevel) {
        val excess = getExcessItems(bee, bee.currentTask)
        if (excess.isEmpty()) { bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos; return }
        val port = bee.network()?.findDropOff(excess.first())
        if (port == null) {
            excess.forEach { item ->
                bee.removeFromInventory(item, item.count)
                level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, item.copy()))
            }
            bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos
            return
        }
        if (bee.blockPosition().closerThan(port.pos, 2.5)) {
            excess.forEach { item ->
                val remainder = port.addItemStack(item.copy())
                if (!remainder.isEmpty) level.addFreshEntity(ItemEntity(level, port.pos.x + 0.5, port.pos.y + 0.5, port.pos.z + 0.5, remainder))
                bee.removeFromInventory(item, item.count)
            }
            bee.constructionState = ConstructionBeeState.FLYING_HOME; bee.walkTarget = bee.hivePos
        } else {
            bee.walkTarget = port.pos
        }
    }

    // ── Entity-based tick (legacy, used while entities still exist) ──

    fun tick(bee: MechanicalBeeEntity, level: ServerLevel, gameTime: Long) {
        // ── Cross-cutting checks (replaces CORE behaviors) ──
        tickFlightDrain(bee)
        if (bee.rechargeFinishTick < 0) BeeSeparation.applyFlightOffset(bee)
        if (tickPortableBeehiveTracking(bee, level, gameTime)) return
        if (tickReturnToOwner(bee, level)) return
        if (tickOrphanedCheck(bee, gameTime)) return
        tickStuckCheck(bee, level, gameTime)
        tickNavigation(bee)

        // Spring empty → recharge (highest priority in WORK)
        if (bee.beeState != ConstructionBeeState.RECHARGING
            && bee.beeState != ConstructionBeeState.ENTERING_HIVE
            && bee.beeState != ConstructionBeeState.ORPHANED
            && bee.springTension <= 0f
        ) {
            bee.beeState = ConstructionBeeState.RECHARGING
            bee.walkTargetPos = null
        }

        // ── State dispatch ──
        when (bee.beeState) {
            ConstructionBeeState.GATHERING -> tickGathering(bee, level, gameTime)
            ConstructionBeeState.FLYING_TO_TASK -> tickFlyingToTask(bee)
            ConstructionBeeState.EXECUTING -> tickExecuting(bee, level, gameTime)
            ConstructionBeeState.FLYING_HOME -> tickFlyingHome(bee)
            ConstructionBeeState.ENTERING_HIVE -> tickEnteringHive(bee, level, gameTime)
            ConstructionBeeState.RECHARGING -> tickRecharging(bee, gameTime)
            ConstructionBeeState.DROPPING_ITEMS -> tickDroppingItems(bee, level)
            ConstructionBeeState.ORPHANED -> { /* handled in tickOrphanedCheck */ }
            ConstructionBeeState.RETURNING_TO_OWNER -> { /* handled in tickReturnToOwner */ }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Cross-cutting checks
    // ════════════════════════════════════════════════════════════════════

    private fun tickFlightDrain(bee: MechanicalBeeEntity) {
        if (bee.deltaMovement.lengthSqr() > 0.001) {
            bee.consumeSpring(CBBeesConfig.springDrainFlight.get())
        }
    }

    private fun tickNavigation(bee: MechanicalBeeEntity) {
        val target = bee.walkTargetPos ?: return
        if (bee.navigation.isDone) {
            bee.navigation.moveTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5, 1.0)
        }
    }

    private fun tickOrphanedCheck(bee: MechanicalBeeEntity, gameTime: Long): Boolean {
        if (bee.beeState == ConstructionBeeState.ORPHANED) {
            bee.orphanedTicks++
            if (bee.orphanedTicks % 40 == 1) {
                val adopted = bee.tryAdoptHive()
                if (adopted != null) {
                    bee.orphanedTicks = 0
                    bee.beeState = ConstructionBeeState.FLYING_HOME
                    return false
                }
            }
            if (bee.orphanedTicks >= 200) {
                bee.dropBeeItemAndDiscard("orphaned for 200 ticks")
            }
            return true
        }

        if (bee.hiveInstance == null) {
            // Try to look up hive
            val hive = bee.beehive()
            if (hive == null) {
                bee.beeState = ConstructionBeeState.ORPHANED
                bee.orphanedTicks = 0
                bee.walkTargetPos = null
                return true
            }
            bee.hiveInstance = hive
        }
        return false
    }

    private fun tickStuckCheck(bee: MechanicalBeeEntity, level: ServerLevel, gameTime: Long) {
        val target = bee.walkTargetPos ?: run { bee.stuckData.reset(); return }
        val data = bee.stuckData
        val targetVec = Vec3.atCenterOf(target)

        // Reset if target changed
        val dx = targetVec.x - data.lastTargetX
        val dz = targetVec.z - data.lastTargetZ
        if (dx * dx + dz * dz > 1.0) {
            data.lastTargetX = targetVec.x
            data.lastTargetY = targetVec.y
            data.lastTargetZ = targetVec.z
            data.lastDistanceToTarget = bee.position().distanceTo(targetVec)
            data.ticksSinceCheck = 0
            data.failedChecks = 0
            return
        }

        data.ticksSinceCheck++
        if (data.ticksSinceCheck < 20) return
        data.ticksSinceCheck = 0

        val currentDist = bee.position().distanceTo(targetVec)
        val progress = data.lastDistanceToTarget - currentDist

        if (progress < 1.5) {
            data.failedChecks++
            if (data.failedChecks < 3) {
                bee.navigation.recomputePath()
            }
        } else {
            data.failedChecks = 0
        }

        data.lastDistanceToTarget = currentDist

        if (data.failedChecks >= 3) {
            // Teleport to target
            val safeY = findSafeY(level, target)
            bee.teleportTo(target.x + 0.5, safeY, target.z + 0.5)
            data.reset()
            bee.walkTargetPos = null
            BeeDebug.log(bee, "Stuck! Teleported to target")
        }
    }

    private fun tickReturnToOwner(bee: MechanicalBeeEntity, level: ServerLevel): Boolean {
        val player = bee.returningToOwner ?: return false
        if (!player.isAlive) {
            bee.dropBeeItemAndDiscard("owner disconnected or dead")
            return true
        }
        if (bee.blockPosition().closerThan(player.blockPosition(), 3.0)) {
            bee.dropBeeItemAndDiscard("reached owner — portable beehive removed")
            return true
        }
        bee.walkTargetPos = player.blockPosition().above(2)
        return true
    }

    private fun tickPortableBeehiveTracking(bee: MechanicalBeeEntity, level: ServerLevel, gameTime: Long): Boolean {
        if (bee.tickCount % 20 != 0) return false
        val hive = bee.hiveInstance as? PortableBeeHive ?: return false

        bee.hivePos = hive.player.blockPosition().above(2)

        if (!hive.isValid() && bee.returningToOwner == null) {
            val task = bee.currentTask
            if (task != null) {
                task.release(gameTick = gameTime)
                bee.currentTask = null
            }
            bee.hiveInstance = null
            bee.hivePos = null
            bee.walkTargetPos = null
            bee.returningToOwner = hive.player
            bee.springTension = 1.0f
            bee.beeState = ConstructionBeeState.RETURNING_TO_OWNER
            return true
        }
        return false
    }

    // ════════════════════════════════════════════════════════════════════
    //  State handlers
    // ════════════════════════════════════════════════════════════════════

    private fun tickGathering(bee: MechanicalBeeEntity, level: ServerLevel, gameTime: Long) {
        val batch = bee.currentTask ?: run { transitionToHome(bee); return }

        val currentTask = batch.getCurrentTask()
        if (currentTask != null) {
            val action = currentTask.action
            if (action is ItemConsumingAction && action.hasItems(bee)) {
                // Current task already has items — go execute
                bee.beeState = ConstructionBeeState.FLYING_TO_TASK
                bee.walkTargetPos = currentTask.targetPos
                return
            }
            if (action !is ItemConsumingAction) {
                // Task doesn't need items
                bee.beeState = ConstructionBeeState.FLYING_TO_TASK
                bee.walkTargetPos = currentTask.targetPos
                return
            }
        }

        val missing = computeMissingItems(bee, batch)
        if (missing.isEmpty()) {
            bee.beeState = ConstructionBeeState.FLYING_TO_TASK
            bee.walkTargetPos = batch.getCurrentTask()?.targetPos
            return
        }

        val network = bee.network() ?: run {
            releaseBatch(bee, batch, gameTime)
            transitionToHome(bee)
            return
        }

        val hive = bee.hiveInstance
        val isPortable = hive is PortableBeeHive

        // Try network logistics ports
        val gatherPlan = buildGatherPlan(network, missing, bee.uuid)
        if (gatherPlan.isNotEmpty()) {
            val (targetPort, itemsAtPort) = gatherPlan.maxByOrNull { it.value.size }!!
            network.releaseReservations(bee.uuid)
            targetPort.reserve(bee.uuid, itemsAtPort, gameTime)

            if (bee.blockPosition().closerThan(targetPort.pos, bee.workRange)) {
                for (item in itemsAtPort) {
                    if (bee.isInventoryFull()) break
                    if (targetPort.hasItemStack(item) && targetPort.removeItemStack(item)) {
                        val remainder = bee.addToInventory(item.copy())
                        if (!remainder.isEmpty) targetPort.addItemStack(remainder)
                        bee.consumeSpring(CBBeesConfig.springDrainPickup.get())
                    }
                }
                targetPort.releaseReservation(bee.uuid)
                // Re-evaluate next tick (might need more items or be ready to fly)
            } else {
                bee.walkTargetPos = targetPort.pos
            }
            return
        }

        // Portable beehive fallback: player inventory
        if (isPortable) {
            val player = (hive as PortableBeeHive).player
            val playerItems = missing.filter { playerHasItem(player, it) }
            if (playerItems.isNotEmpty()) {
                if (bee.blockPosition().closerThan(player.blockPosition(), bee.workRange)) {
                    for (item in playerItems) {
                        if (bee.isInventoryFull()) break
                        val extracted = extractFromPlayer(player, item)
                        if (!extracted.isEmpty) {
                            val remainder = bee.addToInventory(extracted)
                            if (!remainder.isEmpty) player.inventory.add(remainder)
                            bee.consumeSpring(CBBeesConfig.springDrainPickup.get())
                        }
                    }
                    return
                } else {
                    bee.walkTargetPos = player.blockPosition()
                    return
                }
            }
        }

        // No providers — release batch
        BeeDebug.log(bee, "No providers for ${missing.size} missing items — releasing")
        network.releaseReservations(bee.uuid)
        releaseBatch(bee, batch, gameTime)
        bee.walkTargetPos = null
        transitionToHome(bee)
    }

    private fun tickFlyingToTask(bee: MechanicalBeeEntity) {
        val batch = bee.currentTask ?: run { transitionToHome(bee); return }
        val task = batch.getCurrentTask() ?: run { transitionToHome(bee); return }

        // Check if items are needed but missing
        val action = task.action
        if (action is ItemConsumingAction && !action.hasItems(bee)) {
            bee.beeState = ConstructionBeeState.GATHERING
            bee.walkTargetPos = null
            return
        }

        if (bee.blockPosition().closerThan(task.targetPos, bee.workRange)) {
            bee.beeState = ConstructionBeeState.EXECUTING
            bee.walkTargetPos = null
        } else {
            bee.walkTargetPos = task.targetPos
        }
    }

    private fun tickExecuting(bee: MechanicalBeeEntity, level: ServerLevel, gameTime: Long) {
        val batch = bee.currentTask ?: run { transitionToHome(bee); return }
        val task = batch.getCurrentTask() ?: run { transitionToHome(bee); return }
        val hive = bee.hiveInstance ?: run { transitionToHome(bee); return }

        // Check proximity
        if (!bee.blockPosition().closerThan(task.targetPos, bee.workRange)) {
            bee.beeState = ConstructionBeeState.FLYING_TO_TASK
            bee.walkTargetPos = task.targetPos
            return
        }

        // Check items
        val action = task.action
        if (action is ItemConsumingAction && !action.hasItems(bee)) {
            bee.beeState = ConstructionBeeState.GATHERING
            bee.walkTargetPos = null
            return
        }

        // Global throttle
        if (!canExecuteAction(gameTime)) return

        // Check network range (skip for DropOffItemsAction)
        if (action !is DropOffItemsAction) {
            val network = bee.network()
            if (network != null && !network.isInRange(task.targetPos)) return
        }

        BeeDebug.log(bee, "Executing: ${task.action.getDescription()}")
        recordAction()
        val done = task.action.execute(level, bee, bee.getBeeContext())

        if (done) {
            val drain = if (action is RemoveBlockAction) CBBeesConfig.springDrainBreak.get() else CBBeesConfig.springDrainPlace.get()
            bee.consumeSpring(drain)
            task.complete()

            if (!batch.advance()) {
                // Batch complete
                val nextBatch = hive.notifyTaskCompleted(task, bee.uuid)
                if (nextBatch != null) {
                    bee.currentTask = nextBatch
                    nextBatch.assignToBee(bee.uuid, gameTime)
                    bee.beeState = ConstructionBeeState.GATHERING
                } else {
                    bee.currentTask = null
                    transitionToHome(bee)
                }
            } else {
                // More tasks in batch
                val nextTask = batch.getCurrentTask()
                if (nextTask?.action is DropOffItemsAction && bee.getInventoryContents().isEmpty()) {
                    nextTask.complete()
                    if (!batch.advance()) {
                        val nextBatch = hive.notifyTaskCompleted(nextTask, bee.uuid)
                        if (nextBatch != null) {
                            bee.currentTask = nextBatch
                            nextBatch.assignToBee(bee.uuid, gameTime)
                            bee.beeState = ConstructionBeeState.GATHERING
                        } else {
                            bee.currentTask = null
                            transitionToHome(bee)
                        }
                    }
                } else {
                    nextTask?.action?.onActivate(bee)
                    bee.beeState = ConstructionBeeState.GATHERING // re-evaluate items
                }
            }
            bee.walkTargetPos = null
        } else {
            // Task failed
            BeeDebug.log(bee, "Task failed — releasing batch")
            bee.network()?.releaseReservations(bee.uuid)
            releaseBatch(bee, batch, gameTime)
            bee.walkTargetPos = null
            transitionToHome(bee)
        }
    }

    private fun tickFlyingHome(bee: MechanicalBeeEntity) {
        // Drop excess items first
        if (bee.getInventoryContents().isNotEmpty() && bee.currentTask == null) {
            bee.beeState = ConstructionBeeState.DROPPING_ITEMS
            bee.walkTargetPos = null
            return
        }

        val hive = bee.hiveInstance ?: return
        if (bee.blockPosition().closerThan(hive.pos, 4.0)) {
            bee.beeState = ConstructionBeeState.ENTERING_HIVE
            bee.walkTargetPos = null
        } else {
            bee.walkTargetPos = hive.pos
        }
    }

    private fun tickEnteringHive(bee: MechanicalBeeEntity, level: ServerLevel, gameTime: Long) {
        val hive = bee.hiveInstance ?: run {
            bee.beeState = ConstructionBeeState.ORPHANED
            bee.orphanedTicks = 0
            return
        }

        if (!bee.blockPosition().closerThan(hive.pos, 4.0)) {
            bee.beeState = ConstructionBeeState.FLYING_HOME
            return
        }

        val deficit = 1.0f - bee.springTension
        val ctx = bee.getBeeContextForRecharge()
        hive.chargeReturnFuel(deficit, ctx)

        val success = hive.returnBee(bee.beeItemStack())
        if (success) {
            bee.discard()
        } else {
            bee.hiveEntryRetries++
            if (bee.hiveEntryRetries >= 3) {
                bee.dropBeeItemAndDiscard("hive full — max retries")
                return
            }
            val adopted = bee.tryAdoptHive(exclude = hive)
            if (adopted == null) {
                bee.dropBeeItemAndDiscard("hive full — no other hive")
            } else {
                bee.hiveInstance = adopted
                bee.hivePos = adopted.pos
                bee.beeState = ConstructionBeeState.FLYING_HOME
            }
        }
    }

    private fun tickRecharging(bee: MechanicalBeeEntity, gameTime: Long) {
        val hive = bee.hiveInstance ?: run {
            bee.beeState = ConstructionBeeState.ORPHANED
            bee.orphanedTicks = 0
            return
        }

        if (bee.rechargeFinishTick >= 0) {
            if (gameTime >= bee.rechargeFinishTick) {
                bee.springTension = 1.0f
                bee.rechargeFinishTick = -1
                // Resume work or go home
                if (bee.currentTask != null) {
                    bee.beeState = ConstructionBeeState.GATHERING
                } else {
                    transitionToHome(bee)
                }
            }
            return
        }

        if (bee.blockPosition().closerThan(hive.pos, 4.0)) {
            val ctx = bee.getBeeContextForRecharge()
            val rechargeTicks = hive.rechargeSpring(ctx)
            bee.rechargeFinishTick = gameTime + rechargeTicks
        } else {
            bee.walkTargetPos = hive.pos
        }
    }

    private fun tickDroppingItems(bee: MechanicalBeeEntity, level: ServerLevel) {
        val excess = getExcessItems(bee, bee.currentTask)
        if (excess.isEmpty()) {
            transitionToHome(bee)
            return
        }

        val network = bee.network()
        val dropOffPort = network?.findDropOff(excess.first())

        if (dropOffPort == null) {
            val owner = bee.getOwnerPlayer()
            if (owner != null) {
                for (item in excess) {
                    bee.removeFromInventory(item, item.count)
                    if (!owner.inventory.add(item.copy())) {
                        level.addFreshEntity(ItemEntity(level, owner.x, owner.y, owner.z, item.copy()))
                    }
                }
            } else {
                for (item in excess) {
                    bee.removeFromInventory(item, item.count)
                    level.addFreshEntity(ItemEntity(level, bee.x, bee.y, bee.z, item.copy()))
                }
            }
            transitionToHome(bee)
            return
        }

        if (bee.blockPosition().closerThan(dropOffPort.pos, bee.workRange)) {
            for (item in excess) {
                val remainder = dropOffPort.addItemStack(item.copy())
                if (!remainder.isEmpty) {
                    level.addFreshEntity(ItemEntity(level, dropOffPort.pos.x + 0.5, dropOffPort.pos.y + 0.5, dropOffPort.pos.z + 0.5, remainder))
                }
                bee.removeFromInventory(item, item.count)
            }
            transitionToHome(bee)
        } else {
            bee.walkTargetPos = dropOffPort.pos
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════

    private fun transitionToHome(bee: MechanicalBeeEntity) {
        bee.beeState = ConstructionBeeState.FLYING_HOME
        bee.walkTargetPos = bee.hiveInstance?.pos
    }

    private fun releaseBatch(bee: MechanicalBeeEntity, batch: TaskBatch, gameTime: Long) {
        batch.release(gameTick = gameTime)
        bee.currentTask = null
    }

    fun computeMissingItems(bee: de.devin.cbbees.content.bee.server.BeeWorker, batch: TaskBatch): List<ItemStack> {
        val totalRequired = mutableMapOf<ItemStackKey, Int>()
        for (task in batch.getRemainingTasks()) {
            val action = task.action
            if (action is ItemConsumingAction) {
                for (req in action.requiredItems) {
                    val key = ItemStackKey(req)
                    totalRequired[key] = (totalRequired[key] ?: 0) + req.count
                }
            }
        }
        for (carried in bee.getInventoryContents()) {
            val key = ItemStackKey(carried)
            val needed = totalRequired[key] ?: continue
            val remaining = needed - carried.count
            if (remaining <= 0) totalRequired.remove(key) else totalRequired[key] = remaining
        }
        return totalRequired.map { (key, count) -> key.stack.copy().also { it.count = count } }
    }

    private fun buildGatherPlan(network: BeeNetwork, missing: List<ItemStack>, beeId: java.util.UUID): Map<LogisticsPort, List<ItemStack>> {
        val plan = mutableMapOf<LogisticsPort, MutableList<ItemStack>>()
        for (item in missing) {
            val searchStack = item.copyWithCount(1)
            val provider = network.findAvailableProvider(searchStack, beeId) ?: continue
            if (provider is PortableBeeHive) continue
            plan.getOrPut(provider) { mutableListOf() }.add(item)
        }
        return plan
    }

    private fun getExcessItems(bee: de.devin.cbbees.content.bee.server.BeeWorker, currentTask: TaskBatch? = null): List<ItemStack> {
        val contents = bee.getInventoryContents()
        if (contents.isEmpty()) return emptyList()
        val batch = currentTask ?: return contents.map { it.copy() }

        val needed = mutableMapOf<ItemStackKey, Int>()
        for (task in batch.getRemainingTasks()) {
            val action = task.action
            if (action is ItemConsumingAction) {
                for (req in action.requiredItems) {
                    val key = ItemStackKey(req)
                    needed[key] = (needed[key] ?: 0) + req.count
                }
            }
        }

        val excess = mutableListOf<ItemStack>()
        for (carried in contents) {
            val key = ItemStackKey(carried)
            val neededCount = needed[key] ?: 0
            if (neededCount <= 0) {
                excess.add(carried.copy())
            } else {
                val surplus = carried.count - neededCount
                needed[key] = maxOf(0, neededCount - carried.count)
                if (surplus > 0) excess.add(carried.copyWithCount(surplus))
            }
        }
        return excess
    }

    private fun playerHasItem(player: net.minecraft.world.entity.player.Player, stack: ItemStack): Boolean {
        if (player.isCreative) return true
        for (i in 0 until player.inventory.containerSize) {
            val slot = player.inventory.getItem(i)
            if (!slot.isEmpty && ItemStack.isSameItemSameComponents(slot, stack)) return true
        }
        return false
    }

    private fun extractFromPlayer(player: net.minecraft.world.entity.player.Player, needed: ItemStack): ItemStack {
        if (player.isCreative) return needed.copy()
        var remaining = needed.count
        val result = needed.copy().also { it.count = 0 }
        for (i in 0 until player.inventory.containerSize) {
            val slot = player.inventory.getItem(i)
            if (!slot.isEmpty && ItemStack.isSameItemSameComponents(slot, needed)) {
                val take = minOf(remaining, slot.count)
                slot.shrink(take)
                if (slot.isEmpty) player.inventory.setItem(i, ItemStack.EMPTY)
                result.grow(take)
                remaining -= take
                if (remaining <= 0) break
            }
        }
        return if (result.count > 0) result else ItemStack.EMPTY
    }

    private fun findSafeY(level: ServerLevel, targetPos: BlockPos): Double {
        for (dy in 1..4) {
            val checkPos = targetPos.above(dy)
            if (!level.getBlockState(checkPos).isSuffocating(level, checkPos)) return checkPos.y.toDouble()
        }
        return targetPos.y + 1.0
    }
}
