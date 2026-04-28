package de.devin.cbbees.content.bee.state

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.BeeSeparation
import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.TransportTask
import de.devin.cbbees.items.AllItems as CBeesItems
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.items.ItemHandlerHelper
import java.util.UUID

/**
 * O(1) state machine for transport (bumble) bees.
 */
object TransportBeeStateMachine {

    fun tickData(bee: ServerBeeData, level: ServerLevel, gameTime: Long) {
        // Flight drain
        if (bee.velocity.lengthSqr() > 0.001) bee.consumeSpring(CBBeesConfig.springDrainFlight.get())

        // Orphan check
        if (bee.hiveInstance == null) {
            val hive = ServerBeeNetworkManager.findHive(
                bee.hiveId ?: UUID.randomUUID()
            )
            if (hive != null) bee.hiveInstance = hive
            else {
                bee.orphanedTicks++
                if (bee.orphanedTicks >= 200) {
                    level.addFreshEntity(ItemEntity(
                        level, bee.pos.x, bee.pos.y, bee.pos.z,
                        ItemStack(CBeesItems.MECHANICAL_BUMBLE_BEE.get())
                    ))
                    bee.springTension = -9999f
                }
                return
            }
        }

        // Spring empty → recharge
        if (bee.transportState != TransportBeeState.RECHARGING
            && bee.transportState != TransportBeeState.ENTERING_HIVE
            && bee.transportState != TransportBeeState.ORPHANED
            && bee.springTension <= 0f
        ) {
            bee.transportState = TransportBeeState.RECHARGING
            bee.walkTarget = bee.hivePos
        }

        when (bee.transportState) {
            TransportBeeState.FLYING_TO_SOURCE -> {
                val task = bee.transportTask ?: run { goHome(bee); return }
                if (bee.blockPosition().closerThan(task.sourcePos, 2.5)) {
                    bee.transportState = TransportBeeState.PICKING_UP; bee.walkTarget = null
                } else bee.walkTarget = task.sourcePos
            }
            TransportBeeState.PICKING_UP -> {
                if (!bee.isInventoryEmpty()) {
                    val task = bee.transportTask ?: run { goHome(bee); return }
                    bee.transportState = TransportBeeState.FLYING_TO_TARGET; bee.walkTarget = task.targetPos; return
                }
                val task = bee.transportTask ?: run { goHome(bee); return }
                if (!bee.blockPosition().closerThan(task.sourcePos, 2.5)) {
                    bee.transportState = TransportBeeState.FLYING_TO_SOURCE; bee.walkTarget = task.sourcePos; return
                }
                val network = bee.network() ?: run { bee.transportTask = null; goHome(bee); return }
                val port = network.transportPortsByPos[task.sourcePos]?.takeIf { it.isValidProvider() }
                    ?: run { bee.transportTask = null; goHome(bee); return }
                port.releaseReservation(bee.id)
                val targetPort = network.transportPortsByPos[task.targetPos]?.takeIf { it.isValidRequester() }
                val targetHandler = targetPort?.getItemHandler(targetPort.world)
                var pickedUp = false
                task.items.forEach { item ->
                    if (bee.isInventoryFull()) return@forEach
                    var toExtract = item
                    targetHandler?.let { th ->
                        val sim = ItemHandlerHelper.insertItemStacked(th, item.copy(), true)
                        val canAccept = item.count - sim.count
                        if (canAccept <= 0) return@forEach
                        if (canAccept < item.count) toExtract = item.copyWithCount(canAccept)
                    }
                    if (port.hasItemStack(toExtract) && port.removeItemStack(toExtract)) {
                        val rem = bee.addToInventory(toExtract.copy())
                        if (!rem.isEmpty) port.addItemStack(rem)
                        pickedUp = true
                        bee.consumeSpring(CBBeesConfig.springDrainPickup.get())
                    }
                }
                if (pickedUp) { bee.transportState = TransportBeeState.FLYING_TO_TARGET; bee.walkTarget = task.targetPos }
                else { bee.transportTask = null; goHome(bee) }
            }
            TransportBeeState.FLYING_TO_TARGET -> {
                val task = bee.transportTask ?: run { goHome(bee); return }
                if (bee.blockPosition().closerThan(task.targetPos, 2.5)) {
                    bee.transportState = TransportBeeState.DEPOSITING; bee.walkTarget = null
                } else bee.walkTarget = task.targetPos
            }
            TransportBeeState.DEPOSITING -> {
                val task = bee.transportTask ?: run { goHome(bee); return }
                if (bee.isInventoryEmpty()) { bee.transportTask = null; goHome(bee); return }
                if (!bee.blockPosition().closerThan(task.targetPos, 2.5)) {
                    bee.transportState = TransportBeeState.FLYING_TO_TARGET; bee.walkTarget = task.targetPos; return
                }
                val network = bee.network()
                val port = if (task.returningOverflow) network?.transportPortsByPos?.get(task.targetPos)?.takeIf { it.isValidProvider() }
                           else network?.transportPortsByPos?.get(task.targetPos)?.takeIf { it.isValidRequester() }
                val items = bee.getInventoryContents().map { it.copy() }
                val overflow = mutableListOf<ItemStack>()
                if (port != null) {
                    items.forEach { item -> val rem = port.addItemStack(item); bee.removeFromInventory(item, item.count); bee.consumeSpring(CBBeesConfig.springDrainDeposit.get()); if (!rem.isEmpty) overflow.add(rem) }
                } else items.forEach { overflow.add(it); bee.removeFromInventory(it, it.count) }
                if (overflow.isNotEmpty() && !task.returningOverflow) {
                    overflow.forEach { item -> val rem = bee.addToInventory(item); if (!rem.isEmpty) level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, rem)) }
                    bee.transportTask = TransportTask(task.targetPos, task.sourcePos, overflow, returningOverflow = true)
                    bee.transportState = TransportBeeState.FLYING_TO_TARGET; bee.walkTarget = task.sourcePos
                } else {
                    overflow.forEach { level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, it)) }
                    bee.transportTask = null; goHome(bee)
                }
            }
            TransportBeeState.FLYING_HOME -> {
                val hive = bee.hiveInstance ?: return
                if (bee.blockPosition().closerThan(hive.pos, 4.0)) {
                    bee.transportState = TransportBeeState.ENTERING_HIVE; bee.walkTarget = null
                } else bee.walkTarget = hive.pos
            }
            TransportBeeState.ENTERING_HIVE -> {
                val hive = bee.hiveInstance ?: run { bee.transportState = TransportBeeState.ORPHANED; bee.orphanedTicks = 0; return }
                if (!bee.blockPosition().closerThan(hive.pos, 4.0)) { bee.transportState = TransportBeeState.FLYING_HOME; return }
                hive.chargeReturnFuel(1.0f - bee.springTension, bee.getBeeContext())
                val beeItem = ItemStack(CBeesItems.MECHANICAL_BUMBLE_BEE.get())
                if (hive.returnBee(beeItem)) {
                    hive.onBeeRemovedById(bee.id)
                    ServerBeeManager.removeBee(bee.id)
                } else {
                    bee.hiveEntryRetries++
                    if (bee.hiveEntryRetries >= 3) {
                        level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, beeItem))
                        bee.dropInventory()
                        hive.onBeeRemovedById(bee.id)
                        ServerBeeManager.removeBee(bee.id)
                    }
                }
            }
            TransportBeeState.RECHARGING -> {
                val hive = bee.hiveInstance ?: run { bee.transportState = TransportBeeState.ORPHANED; bee.orphanedTicks = 0; return }
                if (bee.rechargeFinishTick >= 0) {
                    if (gameTime >= bee.rechargeFinishTick) {
                        bee.springTension = 1.0f; bee.rechargeFinishTick = -1
                        if (bee.transportTask != null) { bee.transportState = TransportBeeState.FLYING_TO_SOURCE; bee.walkTarget = bee.transportTask?.sourcePos }
                        else goHome(bee)
                    }
                    return
                }
                if (bee.blockPosition().closerThan(hive.pos, 4.0)) {
                    bee.rechargeFinishTick = gameTime + hive.rechargeSpring(bee.getBeeContext())
                } else bee.walkTarget = hive.pos
            }
            TransportBeeState.ORPHANED -> { /* handled above */ }
        }
    }

    private fun goHome(bee: ServerBeeData) {
        bee.transportState = TransportBeeState.FLYING_HOME
        bee.walkTarget = bee.hivePos
    }

    fun tick(bee: MechanicalBumbleBeeEntity, level: ServerLevel, gameTime: Long) {
        // ── Cross-cutting ──
        tickFlightDrain(bee)
        if (bee.rechargeFinishTick < 0) BeeSeparation.applyFlightOffset(bee)
        if (tickOrphanedCheck(bee, gameTime)) return
        tickStuckCheck(bee, level, gameTime)
        tickNavigation(bee)

        // Spring empty → recharge
        if (bee.beeState != TransportBeeState.RECHARGING
            && bee.beeState != TransportBeeState.ENTERING_HIVE
            && bee.beeState != TransportBeeState.ORPHANED
            && bee.springTension <= 0f
        ) {
            bee.beeState = TransportBeeState.RECHARGING
            bee.walkTargetPos = null
        }

        when (bee.beeState) {
            TransportBeeState.FLYING_TO_SOURCE -> tickFlyingToSource(bee)
            TransportBeeState.PICKING_UP -> tickPickingUp(bee, level, gameTime)
            TransportBeeState.FLYING_TO_TARGET -> tickFlyingToTarget(bee)
            TransportBeeState.DEPOSITING -> tickDepositing(bee, level, gameTime)
            TransportBeeState.FLYING_HOME -> tickFlyingHome(bee)
            TransportBeeState.ENTERING_HIVE -> tickEnteringHive(bee, level)
            TransportBeeState.RECHARGING -> tickRecharging(bee, gameTime)
            TransportBeeState.ORPHANED -> { /* handled in tickOrphanedCheck */ }
        }
    }

    // ── Cross-cutting ──

    private fun tickFlightDrain(bee: MechanicalBumbleBeeEntity) {
        if (bee.deltaMovement.lengthSqr() > 0.001) {
            bee.consumeSpring(CBBeesConfig.springDrainFlight.get())
        }
    }

    private fun tickNavigation(bee: MechanicalBumbleBeeEntity) {
        val target = bee.walkTargetPos ?: return
        if (bee.navigation.isDone) {
            bee.navigation.moveTo(target.x + 0.5, target.y.toDouble(), target.z + 0.5, 1.0)
        }
    }

    private fun tickOrphanedCheck(bee: MechanicalBumbleBeeEntity, gameTime: Long): Boolean {
        if (bee.beeState == TransportBeeState.ORPHANED) {
            bee.orphanedTicks++
            if (bee.orphanedTicks % 40 == 1) {
                val adopted = bee.tryAdoptHive()
                if (adopted != null) {
                    bee.orphanedTicks = 0
                    bee.beeState = TransportBeeState.FLYING_HOME
                    return false
                }
            }
            if (bee.orphanedTicks >= 200) {
                bee.dropBeeItemAndDiscard("orphaned for 200 ticks")
            }
            return true
        }

        if (bee.hiveInstance == null) {
            val hive = bee.beehive()
            if (hive == null) {
                bee.beeState = TransportBeeState.ORPHANED
                bee.orphanedTicks = 0
                bee.walkTargetPos = null
                return true
            }
            bee.hiveInstance = hive
        }
        return false
    }

    private fun tickStuckCheck(bee: MechanicalBumbleBeeEntity, level: ServerLevel, gameTime: Long) {
        val target = bee.walkTargetPos ?: run { bee.stuckData.reset(); return }
        val data = bee.stuckData
        val targetVec = Vec3.atCenterOf(target)

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
        if (data.lastDistanceToTarget - currentDist < 1.5) {
            data.failedChecks++
            if (data.failedChecks < 3) bee.navigation.recomputePath()
        } else {
            data.failedChecks = 0
        }
        data.lastDistanceToTarget = currentDist

        if (data.failedChecks >= 3) {
            for (dy in 1..4) {
                val cp = target.above(dy)
                if (!level.getBlockState(cp).isSuffocating(level, cp)) {
                    bee.teleportTo(target.x + 0.5, cp.y.toDouble(), target.z + 0.5)
                    break
                }
            }
            data.reset()
            bee.walkTargetPos = null
        }
    }

    // ── State handlers ──

    private fun tickFlyingToSource(bee: MechanicalBumbleBeeEntity) {
        val task = bee.transportTask ?: run { transitionToHome(bee); return }
        if (bee.blockPosition().closerThan(task.sourcePos, bee.workRange)) {
            bee.beeState = TransportBeeState.PICKING_UP
            bee.walkTargetPos = null
        } else {
            bee.walkTargetPos = task.sourcePos
        }
    }

    private fun tickPickingUp(bee: MechanicalBumbleBeeEntity, level: ServerLevel, gameTime: Long) {
        if (!bee.isInventoryEmpty()) {
            // Already have items, go deliver
            val task = bee.transportTask ?: run { transitionToHome(bee); return }
            bee.beeState = TransportBeeState.FLYING_TO_TARGET
            bee.walkTargetPos = task.targetPos
            return
        }

        val task = bee.transportTask ?: run { transitionToHome(bee); return }
        if (!bee.blockPosition().closerThan(task.sourcePos, bee.workRange)) {
            bee.beeState = TransportBeeState.FLYING_TO_SOURCE
            bee.walkTargetPos = task.sourcePos
            return
        }

        val network = bee.network() ?: run {
            BeeDebug.logForEntity(bee, "Bumble", "No network — clearing task")
            bee.transportTask = null
            transitionToHome(bee)
            return
        }

        val port = network.transportPortsByPos[task.sourcePos]?.takeIf { it.isValidProvider() }
        if (port == null) {
            BeeDebug.logForEntity(bee, "Bumble", "Source port gone — clearing task")
            bee.transportTask = null
            transitionToHome(bee)
            return
        }

        port.releaseReservation(bee.uuid)

        val targetPort = network.transportPortsByPos[task.targetPos]?.takeIf { it.isValidRequester() }
        val targetHandler = targetPort?.getItemHandler(targetPort.world)

        var pickedUp = false
        for (item in task.items) {
            if (bee.isInventoryFull()) break
            var toExtract = item
            if (targetHandler != null) {
                val simulated = ItemHandlerHelper.insertItemStacked(targetHandler, item.copy(), true)
                val canAccept = item.count - simulated.count
                if (canAccept <= 0) continue
                if (canAccept < item.count) toExtract = item.copyWithCount(canAccept)
            }
            if (port.hasItemStack(toExtract) && port.removeItemStack(toExtract)) {
                val remainder = bee.addToInventory(toExtract.copy())
                if (!remainder.isEmpty) port.addItemStack(remainder)
                pickedUp = true
                bee.consumeSpring(CBBeesConfig.springDrainPickup.get())
            }
        }

        if (pickedUp) {
            bee.beeState = TransportBeeState.FLYING_TO_TARGET
            bee.walkTargetPos = task.targetPos
        } else {
            BeeDebug.logForEntity(bee, "Bumble", "No items at source — clearing task")
            bee.transportTask = null
            transitionToHome(bee)
        }
    }

    private fun tickFlyingToTarget(bee: MechanicalBumbleBeeEntity) {
        val task = bee.transportTask ?: run { transitionToHome(bee); return }
        if (bee.blockPosition().closerThan(task.targetPos, bee.workRange)) {
            bee.beeState = TransportBeeState.DEPOSITING
            bee.walkTargetPos = null
        } else {
            bee.walkTargetPos = task.targetPos
        }
    }

    private fun tickDepositing(bee: MechanicalBumbleBeeEntity, level: ServerLevel, gameTime: Long) {
        val task = bee.transportTask ?: run { transitionToHome(bee); return }
        if (bee.isInventoryEmpty()) {
            bee.transportTask = null
            transitionToHome(bee)
            return
        }
        if (!bee.blockPosition().closerThan(task.targetPos, bee.workRange)) {
            bee.beeState = TransportBeeState.FLYING_TO_TARGET
            bee.walkTargetPos = task.targetPos
            return
        }

        val isReturning = task.returningOverflow
        val network = bee.network()
        val port = if (isReturning) {
            network?.transportPortsByPos?.get(task.targetPos)?.takeIf { it.isValidProvider() }
        } else {
            network?.transportPortsByPos?.get(task.targetPos)?.takeIf { it.isValidRequester() }
        }

        val items = bee.getInventoryContents().map { it.copy() }
        val overflow = mutableListOf<ItemStack>()

        if (port != null) {
            for (item in items) {
                val remainder = port.addItemStack(item)
                bee.removeFromInventory(item, item.count)
                bee.consumeSpring(CBBeesConfig.springDrainDeposit.get())
                if (!remainder.isEmpty) overflow.add(remainder)
            }
        } else {
            for (item in items) {
                overflow.add(item)
                bee.removeFromInventory(item, item.count)
            }
        }

        if (overflow.isNotEmpty() && !isReturning) {
            for (item in overflow) {
                val rem = bee.addToInventory(item)
                if (!rem.isEmpty) level.addFreshEntity(ItemEntity(level, bee.x, bee.y, bee.z, rem))
            }
            bee.transportTask = TransportTask(
                sourcePos = task.targetPos,
                targetPos = task.sourcePos,
                items = overflow,
                returningOverflow = true
            )
            bee.beeState = TransportBeeState.FLYING_TO_TARGET
            bee.walkTargetPos = task.sourcePos
        } else {
            if (overflow.isNotEmpty()) {
                for (item in overflow) level.addFreshEntity(ItemEntity(level, bee.x, bee.y, bee.z, item))
            }
            bee.transportTask = null
            transitionToHome(bee)
        }
    }

    private fun tickFlyingHome(bee: MechanicalBumbleBeeEntity) {
        val hive = bee.hiveInstance ?: return
        if (bee.blockPosition().closerThan(hive.pos, 4.0)) {
            bee.beeState = TransportBeeState.ENTERING_HIVE
            bee.walkTargetPos = null
        } else {
            bee.walkTargetPos = hive.pos
        }
    }

    private fun tickEnteringHive(bee: MechanicalBumbleBeeEntity, level: ServerLevel) {
        val hive = bee.hiveInstance ?: run {
            bee.beeState = TransportBeeState.ORPHANED
            bee.orphanedTicks = 0
            return
        }

        if (!bee.blockPosition().closerThan(hive.pos, 4.0)) {
            bee.beeState = TransportBeeState.FLYING_HOME
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
                bee.beeState = TransportBeeState.FLYING_HOME
            }
        }
    }

    private fun tickRecharging(bee: MechanicalBumbleBeeEntity, gameTime: Long) {
        val hive = bee.hiveInstance ?: run {
            bee.beeState = TransportBeeState.ORPHANED
            bee.orphanedTicks = 0
            return
        }

        if (bee.rechargeFinishTick >= 0) {
            if (gameTime >= bee.rechargeFinishTick) {
                bee.springTension = 1.0f
                bee.rechargeFinishTick = -1
                if (bee.transportTask != null) {
                    bee.beeState = TransportBeeState.FLYING_TO_SOURCE
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

    private fun transitionToHome(bee: MechanicalBumbleBeeEntity) {
        bee.beeState = TransportBeeState.FLYING_HOME
        bee.walkTargetPos = bee.hiveInstance?.pos
    }
}
