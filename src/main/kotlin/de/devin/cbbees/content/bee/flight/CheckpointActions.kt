package de.devin.cbbees.content.bee.flight

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TransportTask
import de.devin.cbbees.items.AllItems as CBeesItems
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Fly-through waypoint — always advances immediately.
 * Used for spawn points or intermediate routing waypoints.
 *
 * @see CheckpointAction
 */
object FlyThrough : CheckpointAction {
    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long) = true
}

/**
 * Pick up items from a specific logistics port. The [providerId] identifies the port
 * selected during flight planning, ensuring the bee gathers from the intended source.
 * Falls back to a network-wide scan if the original port is no longer available.
 *
 * @param items the items this bee needs to pick up
 * @param providerId the UUID of the logistics port selected by the flight planner
 * @see CheckpointAction
 */
class GatherFromPort(val items: List<ItemStack>, val providerId: UUID) : CheckpointAction {

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        val log = CreateBuzzyBeez.LOGGER
        val network = bee.network()
        if (network == null) {
            log.debug("[GatherFromPort] Bee ${bee.id.toString().substring(0, 6)}: no network"); return false
        }

        val targetPort = network.ports.firstOrNull { it.id == providerId }
        log.debug(
            "[GatherFromPort] Bee ${
                bee.id.toString().substring(0, 6)
            } arrived, gathering ${items.size} item type(s) from ${targetPort?.javaClass?.simpleName ?: "fallback"}"
        )
        var allGathered = true
        items.forEach { item ->
            if (bee.isInventoryFull()) {
                log.debug("[GatherFromPort] Bee inventory full"); return@forEach
            }
            // Use the planned provider if it still has the item, otherwise fall back to network scan
            val provider = if (targetPort != null && targetPort.hasItemStack(item)) {
                targetPort
            } else {
                network.findAvailableProvider(item.copyWithCount(1), bee.id)
            }

            if (provider == null) {
                log.debug("[GatherFromPort] No reachable provider for ${item.hoverName.string}")
                allGathered = false
                return@forEach
            }

            if (provider.hasItemStack(item) && provider.removeItemStack(item)) {
                val remainder = bee.addToInventory(item.copy())
                if (!remainder.isEmpty) provider.addItemStack(remainder)
                bee.consumeSpring(CBBeesConfig.springDrainPickup.get())
                log.debug("[GatherFromPort] Gathered ${item.hoverName.string} x${item.count} from ${provider.javaClass.simpleName}")
            } else {
                log.debug("[GatherFromPort] Provider ${provider.javaClass.simpleName} hasItemStack=false for ${item.hoverName.string}")
                allGathered = false
            }
        }

        // Release the reservation regardless of outcome — items are now in the bee's inventory or gone
        network.releaseReservations(bee.id)

        if (allGathered) {
            log.debug("[GatherFromPort] All items gathered successfully")
            return true
        }

        // Items unavailable — try to replan from current position with a different provider.
        val newPlan = FlightPlanComputer.replanFrom(bee, bee.currentTask, bee.network(), level)
        if (newPlan != null) {
            log.debug("[GatherFromPort] Replanning flight for bee ${bee.id.toString().substring(0, 6)}")
            // Reserve items at the new gather port
            val newGather = newPlan.checkpoints.map { it.action }.filterIsInstance<GatherFromPort>().firstOrNull()
            if (newGather != null) {
                val port = network.ports.firstOrNull { it.id == newGather.providerId }
                port?.reserve(bee.id, newGather.items, gameTime)
            }
            bee.flightPlan = newPlan
            bee.planStartTick = gameTime
            bee.currentCheckpointIndex = 0
            ServerBeeManager.broadcastFlightPlan(bee, newPlan, clientStartIndex = 0)
            return true // advance past this checkpoint — the new plan starts fresh
        }

        log.debug("[GatherFromPort] Gather FAILED, no alternative provider — releasing batch")
        bee.currentTask?.releaseWithoutRetry()
        bee.springTension = -9999f
        return false
    }
}

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

        ActionThrottle.record()
        val done = beeAction.execute(level, bee, bee.getBeeContext())

        if (done) {
            failTicks = 0
            val drain = if (beeAction is RemoveBlockAction) CBBeesConfig.springDrainBreak.get()
            else CBBeesConfig.springDrainPlace.get()
            bee.consumeSpring(drain)

            task.complete()
            bee.currentTask?.advance()
        } else {
            failTicks++
            // If the action fails repeatedly (e.g., missing materials at placement),
            // release the batch and return home rather than looping forever.
            if (failTicks >= MAX_ACTION_FAIL_TICKS) {
                bee.currentTask?.releaseWithoutRetry()
                bee.springTension = -9999f
            }
        }
        return done
    }

    companion object {
        private const val MAX_ACTION_FAIL_TICKS = 20 // 1 second
    }
}

/** Global per-tick throttle for block operations across all bees. */
object ActionThrottle {
    private var opsThisTick = 0
    private var lastTick = -1L

    fun canExecute(gameTime: Long): Boolean {
        if (gameTime != lastTick) {
            lastTick = gameTime; opsThisTick = 0
        }
        return opsThisTick < CBBeesConfig.maxBlockOperationsPerTick.get()
    }

    fun record() {
        opsThisTick++
    }
}

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
                    bee.flightPlan = plan
                    bee.planStartTick = level.gameTime
                    bee.currentCheckpointIndex = 0
                    if (plan.checkpoints.size > 1) {
                        val travel = FlightPlan.travelTicks(
                            plan.checkpoints[0].pos, plan.checkpoints[1].pos, plan.speed
                        )
                        bee.currentCheckpointIndex = 1
                        bee.nextCheckpointArrivalTick = level.gameTime + travel
                    }
                    ServerBeeManager.broadcastFlightPlan(bee, plan, clientStartIndex = 0)
                }
                return true
            }
        }

        return true
    }
}

/**
 * Return the bee to its hive. Charges return fuel, adds the bee item back
 * to the hive inventory, and removes the bee from [ServerBeeManager].
 *
 * Placed after [CheckForNextWork] in the flight plan — only reached when
 * there's no more work available.
 *
 * @see CheckForNextWork
 * @see CheckpointAction
 */
class EnterHive : CheckpointAction {

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        val log = CreateBuzzyBeez.LOGGER
        val hive = bee.hiveInstance ?: run {
            log.debug(
                "[EnterHive] Bee ${
                    bee.id.toString().substring(0, 6)
                }: no hiveInstance"
            ); return false
        }

        val deficit = 1.0f - bee.springTension
        hive.chargeReturnFuel(deficit, bee.getBeeContext())

        val beeItem = ItemStack(
            if (bee.type == BeeType.CONSTRUCTION)
                CBeesItems.MECHANICAL_BEE.get()
            else
                CBeesItems.MECHANICAL_BUMBLE_BEE.get()
        )

        if (hive.returnBee(beeItem)) {
            hive.onBeeRemovedById(bee.id)
            log.debug(
                "[EnterHive] Bee ${
                    bee.id.toString().substring(0, 6)
                } returned to ${hive.javaClass.simpleName}, activeBees=${hive.getActiveBeeCount()}"
            )
            ServerBeeManager.removeBee(bee.id)
            return true
        }

        bee.dropInventory()
        level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, beeItem))
        hive.onBeeRemovedById(bee.id)
        log.debug(
            "[EnterHive] Bee ${
                bee.id.toString().substring(0, 6)
            } dropped as item (hive full), activeBees=${hive.getActiveBeeCount()}"
        )
        ServerBeeManager.removeBee(bee.id)
        return true
    }
}

/**
 * Recharge the bee's spring tension at the hive.
 * Pauses for a calculated duration based on the hive's RPM and bee context.
 *
 * @see CheckpointAction
 */
class RechargeSpring : CheckpointAction {
    private var finishTick: Long = -1

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        if (finishTick < 0) {
            val hive = bee.hiveInstance ?: return true // skip if no hive
            val ctx = bee.getBeeContext()
            finishTick = gameTime + hive.rechargeSpring(ctx)
        }
        if (gameTime >= finishTick) {
            bee.springTension = 1.0f
            finishTick = -1
            return true
        }
        return false
    }
}

/**
 * Bumble bee: pick up items from the source transport port.
 *
 * @param task the transport task defining source position and items
 * @see CheckpointAction
 */
class PickupTransport(private val task: TransportTask) : CheckpointAction {

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        val network = bee.network() ?: return false
        val port = network.transportPortsByPos[task.sourcePos]?.takeIf { it.isValidProvider() } ?: return false

        port.releaseReservation(bee.id)

        var pickedUp = false
        task.items.forEach { item ->
            if (bee.isInventoryFull()) return@forEach
            if (port.hasItemStack(item) && port.removeItemStack(item)) {
                val rem = bee.addToInventory(item.copy())
                if (!rem.isEmpty) port.addItemStack(rem)
                pickedUp = true
                bee.consumeSpring(CBBeesConfig.springDrainPickup.get())
            }
        }
        return pickedUp
    }
}

/**
 * Bumble bee: deposit items at the target transport port.
 *
 * @param task the transport task defining target position
 * @see CheckpointAction
 */
class DepositTransport(private val task: TransportTask) : CheckpointAction {

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        val network = bee.network()
        val port = network?.transportPortsByPos?.get(task.targetPos)?.takeIf { it.isValidRequester() }

        bee.getInventoryContents().forEach { item ->
            if (port != null) {
                val remainder = port.addItemStack(item.copy())
                bee.removeFromInventory(item, item.count)
                bee.consumeSpring(CBBeesConfig.springDrainDeposit.get())
                if (!remainder.isEmpty) {
                    level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, remainder))
                }
            } else {
                bee.removeFromInventory(item, item.count)
                level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, item.copy()))
            }
        }
        return true
    }
}

/**
 * Drop off excess items at a logistics port or on the ground.
 *
 * @see CheckpointAction
 */
class DropOffItems : CheckpointAction {

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        val contents = bee.getInventoryContents()
        if (contents.isEmpty()) return true

        val port = bee.network()?.findDropOff(contents.first(), bee.hiveId)
        contents.forEach { item ->
            if (port != null) {
                val remainder = port.addItemStack(item.copy())
                bee.removeFromInventory(item, item.count)
                if (!remainder.isEmpty) {
                    level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, remainder))
                }
            } else {
                bee.removeFromInventory(item, item.count)
                level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, item.copy()))
            }
        }
        return true
    }
}
