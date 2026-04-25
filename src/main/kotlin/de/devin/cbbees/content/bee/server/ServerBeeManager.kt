package de.devin.cbbees.content.bee.server

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.flight.ClientCheckpoint
import de.devin.cbbees.content.bee.flight.ExecuteBeeAction
import de.devin.cbbees.content.bee.flight.FlightPlan
import de.devin.cbbees.content.bee.flight.FlightPlanComputer
import de.devin.cbbees.content.bee.state.*
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.items.AllItems
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TransportTask
import de.devin.cbbees.content.upgrades.BeeContext
import de.devin.cbbees.network.BeeCheckpointConfirmPacket
import de.devin.cbbees.network.BeeFlightPlanPacket
import de.devin.cbbees.network.BeeRemovePacket
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import java.util.*

/**
 * Server-side singleton managing all mechanical bees as lightweight data objects.
 *
 * Replaces the vanilla Entity tick chain with direct iteration over [ServerBeeData].
 * No PathfinderMob, no Brain, no SynchedEntityData — just state machines and Vec3 math.
 *
 * Registered as [SavedData] for world persistence.
 */
object ServerBeeManager {

    private val bees = mutableMapOf<UUID, ServerBeeData>()
    private var level: ServerLevel? = null

    val activeBees: Collection<ServerBeeData> get() = bees.values

    fun init(serverLevel: ServerLevel) {
        level = serverLevel
    }

    fun clear() {
        val currentLevel = level
        for (bee in bees.values) {
            returnBeeToHive(bee, currentLevel)
        }
        bees.clear()
        level = null
    }

    /**
     * Returns a bee to its hive on shutdown/disconnect. The bee item goes back
     * into the hive inventory, carried items are dropped at the hive, and the
     * task batch is released so it can be re-dispatched on next load.
     */
    private fun returnBeeToHive(bee: ServerBeeData, currentLevel: ServerLevel?) {
        val beeItem = ItemStack(
            if (bee.type == BeeType.CONSTRUCTION) AllItems.MECHANICAL_BEE.get()
            else AllItems.MECHANICAL_BUMBLE_BEE.get()
        )

        // Try cached hive first, then look up fresh from world by hiveId
        var hive = bee.hiveInstance
        if (hive == null && currentLevel != null && bee.hiveId != null) {
            hive = ServerBeeNetworkManager.findHive(bee.hiveId!!)
        }
        // Also try looking up the block entity directly from hivePos
        if (hive == null && currentLevel != null && bee.hivePos != null) {
            hive = currentLevel.getBlockEntity(bee.hivePos!!) as? BeeHive
        }

        if (hive != null && hive.returnBee(beeItem)) {
            if (currentLevel != null) {
                bee.getInventoryContents().forEach { item ->
                    bee.removeFromInventory(item, item.count)
                    currentLevel.addFreshEntity(
                        ItemEntity(currentLevel, hive.pos.x + 0.5, hive.pos.y + 1.0, hive.pos.z + 0.5, item.copy())
                    )
                }
            }
            (hive as? MechanicalBeehiveBlockEntity)?.onBeeRemovedById(bee.id)
        } else if (currentLevel != null) {
            currentLevel.addFreshEntity(
                ItemEntity(currentLevel, bee.pos.x, bee.pos.y, bee.pos.z, beeItem)
            )
            try { bee.dropInventory() } catch (_: UninitializedPropertyAccessException) {}
        }

        bee.currentTask?.release()
    }

    private val pendingRemovals = mutableSetOf<UUID>()
    private val pendingReturns = mutableSetOf<UUID>()
    private var isTicking = false

    private val maxCheckpointsPerTick: Int get() = CBBeesConfig.maxCheckpointsPerTick.get()

    fun tickAll(serverLevel: ServerLevel, gameTime: Long) {
        isTicking = true
        pendingRemovals.clear()
        pendingReturns.clear()
        val profiler = serverLevel.profiler

        val snapshot = bees.values.toTypedArray()
        var checkpointsThisTick = 0
        val confirmBatch = mutableListOf<BeeCheckpointConfirmPacket.Entry>()

        profiler.push("checkpoints")
        for (bee in snapshot) {
            if (bee.id in pendingRemovals) continue
            bee._level = serverLevel

            val plan = bee.flightPlan
            if (plan == null || bee.currentCheckpointIndex >= plan.checkpoints.size) continue

            if (gameTime < bee.nextCheckpointArrivalTick) continue
            if (checkpointsThisTick >= maxCheckpointsPerTick) continue

            profiler.push("arrival")
            checkpointsThisTick++
            val checkpoint = plan.checkpoints[bee.currentCheckpointIndex]
            bee.pos = Vec3.atCenterOf(checkpoint.pos)

            val completed = checkpoint.action.onArrival(bee, serverLevel, gameTime)
            if (completed) {
                if (checkpoint.action is ExecuteBeeAction) {
                    confirmBatch.add(BeeCheckpointConfirmPacket.Entry(bee.id, bee.currentCheckpointIndex))
                }
                advanceCheckpoint(bee, gameTime)
            }
            profiler.pop()

            if (bee.springTension < -999f) {
                pendingRemovals.add(bee.id)
                pendingReturns.add(bee.id)
            }
        }
        profiler.pop()

        if (confirmBatch.isNotEmpty()) {
            profiler.push("broadcastConfirm")
            broadcastCheckpointConfirmBatch(confirmBatch)
            profiler.pop()
        }

        isTicking = false
        pendingRemovals.forEach { id ->
            val bee = bees.remove(id)
            // Only return bees that exited abnormally (spring signal), not those
            // already returned by their checkpoint action (e.g., EnterHive).
            if (bee != null && id in pendingReturns) {
                returnBeeToHive(bee, level)
            }
        }
        pendingReturns.clear()
    }

    private fun advanceCheckpoint(bee: ServerBeeData, gameTime: Long) {
        val plan = bee.flightPlan ?: return
        val prevIndex = bee.currentCheckpointIndex
        bee.currentCheckpointIndex++

        if (bee.currentCheckpointIndex >= plan.checkpoints.size) return

        val from = plan.checkpoints[prevIndex]
        val to = plan.checkpoints[bee.currentCheckpointIndex]

        val travel = FlightPlan.travelTicks(from.pos, to.pos, plan.speed)
        bee.nextCheckpointArrivalTick = gameTime + travel + from.clientPauseTicks
    }

    private fun tickConstructionBee(bee: ServerBeeData, level: ServerLevel, gameTime: Long) {
        ConstructionBeeStateMachine.tickData(bee, level, gameTime)
    }

    private fun tickTransportBee(bee: ServerBeeData, level: ServerLevel, gameTime: Long) {
        TransportBeeStateMachine.tickData(bee, level, gameTime)
    }

    /**
     * Spawns a construction bee from a hive with the given task batch.
     */
    fun spawnConstructionBee(
        hive: BeeHive,
        batch: TaskBatch,
        networkId: UUID,
        spawnPos: Vec3,
        context: BeeContext,
        ownerId: UUID? = null,
        beeType: BeeType = BeeType.CONSTRUCTION,
    ): ServerBeeData {
        val bee = ServerBeeData(
            id = UUID.randomUUID(),
            type = beeType,
            networkId = networkId,
            hiveId = hive.id,
            ownerId = ownerId,
        ).apply {
            pos = spawnPos
            springTension = 1.0f
            hiveInstance = hive
            hivePos = hive.pos
            currentTask = batch
            constructionState = ConstructionBeeState.GATHERING
            cachedBeeContext = context
        }

        batch.assignToBee(bee.id, (level?.gameTime ?: 0L))
        bees[bee.id] = bee
        hive.onBeeSpawned(bee.id)

        val network = ServerBeeNetworkManager.getNetwork(networkId, level!!)
        if (network != null) {
            FlightPlanComputer.computeAsync(bee, batch, network, level!!) { plan ->
                if (plan == null) {
                    // Flight plan failed (e.g., no provider for required materials).
                    // Return the bee to its hive and put the batch back to PENDING.
                    // Don't count as a retry — material unavailability is transient.
                    batch.releaseWithoutRetry()
                    removeBee(bee.id)
                    returnBeeToHive(bee, level)
                    return@computeAsync
                }
                bee.flightPlan = plan
                bee.planStartTick = level!!.gameTime
                bee.currentCheckpointIndex = 0
                if (plan.checkpoints.size > 1) {
                    val travel = FlightPlan.travelTicks(
                        plan.checkpoints[0].pos, plan.checkpoints[1].pos, plan.speed
                    )
                    bee.currentCheckpointIndex = 1
                    bee.nextCheckpointArrivalTick = level!!.gameTime + travel
                }
                broadcastFlightPlan(bee, plan, clientStartIndex = 0)
            }
        }

        return bee
    }

    /**
     * Spawns a transport (bumble) bee with a transport task.
     */
    fun spawnTransportBee(
        hive: BeeHive,
        task: TransportTask,
        networkId: UUID,
        spawnPos: Vec3,
    ): ServerBeeData {
        val bee = ServerBeeData(
            id = UUID.randomUUID(),
            type = BeeType.TRANSPORT,
            networkId = networkId,
            hiveId = hive.id,
        ).apply {
            pos = spawnPos
            springTension = 1.0f
            hiveInstance = hive
            hivePos = hive.pos
            transportTask = task
            transportState = TransportBeeState.FLYING_TO_SOURCE
        }

        if (level != null) {
            FlightPlanComputer.computeTransportAsync(bee, task, level!!) { plan ->
                bee.flightPlan = plan
                bee.planStartTick = level!!.gameTime
                bee.currentCheckpointIndex = 0
                if (plan.checkpoints.size > 1) {
                    val travel = FlightPlan.travelTicks(
                        plan.checkpoints[0].pos, plan.checkpoints[1].pos, plan.speed
                    )
                    bee.currentCheckpointIndex = 1
                    bee.nextCheckpointArrivalTick = level!!.gameTime + travel
                }
                broadcastFlightPlan(bee, plan, clientStartIndex = 0)
            }
        }

        bees[bee.id] = bee
        return bee
    }

    /**
     * Sends a flight plan to all connected players.
     *
     * @param clientStartIndex override the checkpoint the client begins at.
     *   Defaults to `bee.currentCheckpointIndex` (correct for late-joiner resync).
     *   For initial spawn broadcasts, pass `0` so the client renders the full
     *   hive-to-target flight instead of appearing at the first work checkpoint.
     */
    fun broadcastFlightPlan(bee: ServerBeeData, plan: FlightPlan, clientStartIndex: Int? = null) {
        val gameTime = level?.gameTime ?: 0L
        val planStartTick = bee.planStartTick
        val elapsedTicks = if (planStartTick > 0) (gameTime - planStartTick) else 0L

        val packet = BeeFlightPlanPacket(
            beeId = plan.beeId,
            type = plan.type,
            speed = plan.speed,
            checkpoints = plan.checkpoints.map {
                ClientCheckpoint(it.pos, it.clientPauseTicks, awaitConfirm = it.action is ExecuteBeeAction)
            },
            startIndex = clientStartIndex ?: bee.currentCheckpointIndex,
            elapsedTicks = elapsedTicks,
        )
        val server = level?.server ?: return
        server.playerList.players.forEach { player ->
            PacketDistributor.sendToPlayer(player, packet)
        }
    }

    private fun broadcastCheckpointConfirmBatch(entries: List<BeeCheckpointConfirmPacket.Entry>) {
        val server = level?.server ?: return
        val packet = BeeCheckpointConfirmPacket(entries)
        server.playerList.players.forEach { player ->
            PacketDistributor.sendToPlayer(player, packet)
        }
    }

    fun broadcastRemoval(beeId: UUID) {
        val server = level?.server ?: return
        server.playerList.players.forEach { player ->
            PacketDistributor.sendToPlayer(player, BeeRemovePacket(beeId))
        }
    }

    /**
     * Removes a bee from the manager (bee entered hive, dropped as item, etc.).
     * If called during [tickAll], defers removal until iteration completes.
     */
    fun removeBee(id: UUID) {
        if (isTicking) {
            pendingRemovals.add(id)
        } else {
            bees.remove(id)
        }
        broadcastRemoval(id)
    }

    fun getBee(id: UUID): ServerBeeData? = bees[id]

    // Bees are ephemeral — not persisted to NBT. On shutdown, items are
    // dropped and tasks released. Hives respawn bees when they find pending work.
}

private fun BeeHive.onBeeSpawned(beeId: UUID) {
}
