package de.devin.cbbees.content.bee.flight

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TransportTask
import de.devin.cbbees.items.AllItems as CBeesItems
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack

// ════════════════════════════════════════════════════════════════════════════════
//  Composable CheckpointAction implementations
//  Each is a standalone class — add new ones freely without modifying any enum.
// ════════════════════════════════════════════════════════════════════════════════

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
 * Pick up items from a logistics port. Validates the port still exists and has stock.
 * Returns `false` if the port was destroyed (triggers flight plan recomputation).
 *
 * @param items the items this bee needs to pick up
 * @see CheckpointAction
 */
class GatherFromPort(private val items: List<ItemStack>) : CheckpointAction {

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        val network = bee.network() ?: return false

        var allGathered = true
        items.forEach { item ->
            if (bee.isInventoryFull()) return@forEach
            val searchStack = item.copyWithCount(1)
            val provider = network.findAvailableProvider(searchStack, bee.id) ?: run {
                allGathered = false
                return@forEach
            }
            if (provider is PortableBeeHive) { allGathered = false; return@forEach }

            if (provider.hasItemStack(item) && provider.removeItemStack(item)) {
                val remainder = bee.addToInventory(item.copy())
                if (!remainder.isEmpty) provider.addItemStack(remainder)
                bee.consumeSpring(CBBeesConfig.springDrainPickup.get())
            } else {
                allGathered = false
            }
        }

        if (allGathered) return true

        // Items not available — another bee took them or provider was emptied.
        // Return home immediately and release the batch so the stall system
        // can report it and the batch gets redispatched when items are restocked.
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
        if (gameTime != lastTick) { lastTick = gameTime; opsThisTick = 0 }
        return opsThisTick < CBBeesConfig.maxBlockOperationsPerTick.get()
    }

    fun record() { opsThisTick++ }
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
        val hive = bee.hiveInstance ?: return true // no hive → proceed to EnterHive

        // Ask the job pool for more work
        val nextBatch = GlobalJobPool.workBacklog(hive)
        if (nextBatch != null) {
            // Assign new batch and recompute flight plan
            nextBatch.assignToBee(bee.id, gameTime)
            bee.currentTask = nextBatch

            val network = bee.network()
            if (network != null) {
                // Compute a new plan starting from the hive
                FlightPlanComputer.computeAsync(
                    bee, nextBatch, network, level
                ) { plan ->
                    if (plan == null) {
                        // Can't build plan (missing materials) — release without retry
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
                return true // advance past this checkpoint (plan will be replaced async)
            }
        }

        return true // no work found → proceed to EnterHive (next checkpoint)
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
        val hive = bee.hiveInstance ?: return false

        val deficit = 1.0f - bee.springTension
        hive.chargeReturnFuel(deficit, bee.getBeeContext())

        val beeItem = ItemStack(
            if (bee.type == BeeType.CONSTRUCTION)
                CBeesItems.MECHANICAL_BEE.get()
            else
                CBeesItems.MECHANICAL_BUMBLE_BEE.get()
        )

        if (hive.returnBee(beeItem)) {
            (hive as? MechanicalBeehiveBlockEntity)?.onBeeRemovedById(bee.id)
            ServerBeeManager.removeBee(bee.id)
            return true
        }

        // Hive full — drop as item
        bee.dropInventory()
        level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, beeItem))
        (hive as? MechanicalBeehiveBlockEntity)?.onBeeRemovedById(bee.id)
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
