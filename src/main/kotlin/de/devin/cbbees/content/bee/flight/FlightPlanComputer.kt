package de.devin.cbbees.content.bee.flight

import de.devin.cbbees.compat.sable.SableCompat
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.flight.FlightPlanComputer.computeAsync
import de.devin.cbbees.content.bee.flight.FlightPlanComputer.computeTransportAsync
import de.devin.cbbees.content.bee.flight.FlightPlanComputer.forConstruction
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.action.impl.DropOffItemsAction
import de.devin.cbbees.content.domain.action.impl.PlaceFluidAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.logistics.DockPort
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TransportTask
import de.devin.cbbees.util.ItemStackKey
import de.devin.cbbees.util.ServerTickScheduler
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.LiquidBlock
import net.neoforged.neoforge.fluids.FluidStack
import kotlin.math.abs
import kotlin.math.max

/**
 * Computes [FlightPlan]s for bees based on their assigned tasks.
 *
 * The plan is a sequence of [Checkpoint]s the bee must visit. Each checkpoint carries
 * a composable [CheckpointAction] that executes server-side on arrival. The client
 * receives only the positions and pause durations for smooth autonomous interpolation.
 *
 * Plans are computed synchronously on the server thread (fast — just building a list).
 * If needed, could be moved to a background thread since it only reads immutable task data.
 *
 * @see FlightPlan
 * @see CheckpointAction
 */
object FlightPlanComputer {

    private const val DEFAULT_SPEED = 0.35f
    private const val GATHER_PAUSE_TICKS = 5
    private const val PICKUP_PAUSE_TICKS = 5
    private const val DEPOSIT_PAUSE_TICKS = 5
    private const val DROP_OFF_PAUSE_TICKS = 5

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "BeeFlightPlanner").apply { isDaemon = true }
    }

    /**
     * Computes a construction flight plan on a background thread.
     *
     * Block state reads for obstacle avoidance are snapshotted on the server thread
     * before dispatching to the worker. The [onComplete] callback is invoked on the
     * server thread via [ServerLevel.getServer].
     *
     * @see forConstruction
     */
    fun computeAsync(
        bee: ServerBeeData,
        batch: TaskBatch,
        network: BeeNetwork,
        level: ServerLevel,
        onComplete: (FlightPlan?) -> Unit,
    ) {
        val rawCheckpoints = buildRawConstructionCheckpoints(bee, batch, network)
        if (rawCheckpoints == null) {
            level.server.execute { onComplete(null) }
            return
        }

        val crossings = spaceCrossings(rawCheckpoints, level)
        val collisionSnapshot = snapshotCollisions(rawCheckpoints.map { it.pos }, crossings, level)

        executor.submit {
            val finalCheckpoints = insertObstacleWaypointsFromSnapshot(rawCheckpoints, collisionSnapshot, crossings)
            level.server.execute { onComplete(FlightPlan.of(bee.id, bee.type, DEFAULT_SPEED, finalCheckpoints, level)) }
        }
    }

    /**
     * Computes a transport flight plan on a background thread.
     */
    fun computeTransportAsync(
        bee: ServerBeeData,
        task: TransportTask,
        level: ServerLevel,
        onComplete: (FlightPlan) -> Unit,
    ) {
        val rawCheckpoints = buildRawTransportCheckpoints(bee, task)
        val crossings = spaceCrossings(rawCheckpoints, level)
        val collisionSnapshot = snapshotCollisions(rawCheckpoints.map { it.pos }, crossings, level)

        executor.submit {
            val finalCheckpoints = insertObstacleWaypointsFromSnapshot(rawCheckpoints, collisionSnapshot, crossings)
            level.server.execute { onComplete(FlightPlan.of(bee.id, BeeType.TRANSPORT, DEFAULT_SPEED, finalCheckpoints, level)) }
        }
    }

    /**
     * Snapshots block collision state along all straight-line segments between positions.
     * Called on the server thread. Returns an immutable map safe to read from any thread.
     */
    private fun snapshotCollisions(positions: List<BlockPos>, crossings: Set<Int>, level: ServerLevel): Map<BlockPos, Boolean> {
        val snapshot = mutableMapOf<BlockPos, Boolean>()

        positions.zipWithNext().forEachIndexed { segment, (from, to) ->
            if (segment in crossings) return@forEachIndexed
            val dx = to.x - from.x
            val dy = to.y - from.y
            val dz = to.z - from.z
            val steps = max(max(abs(dx), abs(dy)), abs(dz)).coerceAtLeast(1)

            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val pos = BlockPos(
                    from.x + (dx * t).toInt(),
                    from.y + (dy * t).toInt(),
                    from.z + (dz * t).toInt(),
                )
                if (pos !in snapshot && level.isLoaded(pos)) {
                    snapshot[pos] = !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty
                }
            }

            snapshot.filter { it.value }.keys.toList().forEach { blocked ->
                for (dy2 in 1..MAX_FLY_OVER_HEIGHT) {
                    val above = blocked.above(dy2)
                    if (above !in snapshot && level.isLoaded(above)) {
                        snapshot[above] = !level.getBlockState(above).getCollisionShape(level, above).isEmpty
                    }
                }
            }
        }

        return snapshot
    }

    /**
     * Builds a construction flight plan synchronously (with optional obstacle avoidance).
     * Prefer [computeAsync] for production use.
     */
    fun forConstruction(
        bee: ServerBeeData,
        batch: TaskBatch,
        network: BeeNetwork,
        level: ServerLevel? = null
    ): FlightPlan? {
        val raw = buildRawConstructionCheckpoints(bee, batch, network) ?: return null
        val checkpoints = if (level != null) insertObstacleWaypoints(raw, level) else raw
        return FlightPlan.of(bee.id, bee.type, DEFAULT_SPEED, checkpoints, level)
    }

    /**
     * Builds a transport flight plan synchronously (with optional obstacle avoidance).
     * Prefer [computeTransportAsync] for production use.
     */
    fun forTransport(bee: ServerBeeData, task: TransportTask, level: ServerLevel? = null): FlightPlan {
        val raw = buildRawTransportCheckpoints(bee, task)
        val checkpoints = if (level != null) insertObstacleWaypoints(raw, level) else raw
        return FlightPlan.of(bee.id, bee.type, DEFAULT_SPEED, checkpoints, level)
    }

    /**
     * Builds a flight plan that takes the bee straight from its current position back into its hive.
     * Used when a bee has nothing left to do on its current plan (e.g., transport source ran dry).
     */
    fun forReturnHome(bee: ServerBeeData, level: ServerLevel? = null): FlightPlan {
        val hiveApproach = bee.hiveInstance?.approachPos() ?: (bee.hivePos ?: bee.blockPosition()).above()
        val raw = listOf(
            Checkpoint(bee.blockPosition(), FlyThrough),
            Checkpoint(hiveApproach, EnterHive()),
        )
        val checkpoints = if (level != null) insertObstacleWaypoints(raw, level) else raw
        return FlightPlan.of(bee.id, bee.type, DEFAULT_SPEED, checkpoints, level)
    }

    private fun buildRawConstructionCheckpoints(bee: ServerBeeData, batch: TaskBatch, network: BeeNetwork): List<Checkpoint>? = buildList {
        add(Checkpoint(bee.blockPosition(), FlyThrough))
        val missing = computeMissingItems(bee, batch)
        if (missing.isNotEmpty()) {
            val provider = findBestProvider(network, missing, bee.id) ?: return null
            add(Checkpoint(provider.gatherPos(), GatherFromPort(missing, provider.id), clientPauseTicks = GATHER_PAUSE_TICKS))
        }
        val missingFluid = computeMissingFluid(bee, batch)
        if (!missingFluid.isEmpty) {
            val dock = network.findFluidProvider(missingFluid) ?: return null
            add(Checkpoint(dock.gatherPos(), GatherFluidFromDock(missingFluid, dock.id), clientPauseTicks = GATHER_PAUSE_TICKS))
        }
        val remainingTasks = batch.getRemainingTasks()
        var lastCheckpointPos = bee.blockPosition()
        remainingTasks.forEach { task ->
            val action = task.action
            when (action) {
                is DropOffItemsAction -> {
                    val dropPos = findFluidDropOff(batch, network)?.gatherPos()
                        ?: network.findDropOff(ItemStack.EMPTY, bee.hiveId)?.pos?.above()
                        ?: task.targetPos
                    add(Checkpoint(dropPos, ExecuteBeeAction(action, task), clientPauseTicks = DROP_OFF_PAUSE_TICKS))
                    lastCheckpointPos = dropPos
                }

                is RemoveBlockAction -> {
                    add(
                        Checkpoint(
                            task.targetPos,
                            ExecuteBeeAction(action, task),
                            clientPauseTicks = action.getWorkTicks(bee.getBeeContext())
                        )
                    )
                    lastCheckpointPos = task.targetPos
                }

                else -> {
                    add(Checkpoint(task.targetPos, ExecuteBeeAction(action, task)))
                    lastCheckpointPos = task.targetPos
                }
            }
        }
        add(Checkpoint(lastCheckpointPos, CheckForNextWork()))
        val hiveApproach = bee.hiveInstance?.approachPos() ?: (bee.hivePos ?: bee.blockPosition()).above()
        add(Checkpoint(hiveApproach, EnterHive()))
    }

    private fun buildRawTransportCheckpoints(bee: ServerBeeData, task: TransportTask) = buildList {
        add(Checkpoint(bee.blockPosition(), FlyThrough))
        add(Checkpoint(task.sourcePos.above(), PickupTransport(task), clientPauseTicks = PICKUP_PAUSE_TICKS))
        add(Checkpoint(task.targetPos.above(), DepositTransport(task), clientPauseTicks = DEPOSIT_PAUSE_TICKS))
        val hiveApproach = bee.hiveInstance?.approachPos() ?: (bee.hivePos ?: bee.blockPosition()).above()
        add(Checkpoint(hiveApproach, EnterHive()))
    }

    /**
     * Recomputes a flight plan from the bee's current position.
     * Used when a checkpoint fails validation (e.g., port destroyed).
     */
    fun replanFrom(
        bee: ServerBeeData,
        batch: TaskBatch?,
        network: BeeNetwork?,
        level: ServerLevel? = null
    ): FlightPlan? {
        if (network == null) return null
        return when (bee.type) {
            BeeType.CONSTRUCTION -> batch?.let { forConstruction(bee, it, network, level) }
            BeeType.TRANSPORT -> bee.transportTask?.let { forTransport(bee, it, level) }
        }
    }

    /**
     * Inserts waypoints using a pre-snapshotted collision map. Safe to call from any thread.
     */
    private fun insertObstacleWaypointsFromSnapshot(
        checkpoints: List<Checkpoint>,
        collisionSnapshot: Map<BlockPos, Boolean>,
        crossings: Set<Int>,
    ): List<Checkpoint> = buildList {
        checkpoints.forEachIndexed { index, checkpoint ->
            add(checkpoint)
            val next = checkpoints.getOrNull(index + 1) ?: return@forEachIndexed
            if (index in crossings) return@forEachIndexed
            findObstacleWaypointFromSnapshot(checkpoint.pos, next.pos, collisionSnapshot)?.let {
                add(Checkpoint(it, FlyThrough))
            }
        }
    }

    private fun findObstacleWaypointFromSnapshot(
        from: BlockPos,
        to: BlockPos,
        snapshot: Map<BlockPos, Boolean>
    ): BlockPos? {
        val dx = to.x - from.x;
        val dy = to.y - from.y;
        val dz = to.z - from.z
        val steps = max(max(abs(dx), abs(dy)), abs(dz)).coerceAtLeast(1)
        var highestObstruction: BlockPos? = null

        for (i in 1 until steps) {
            val t = i.toFloat() / steps
            val pos = BlockPos(from.x + (dx * t).toInt(), from.y + (dy * t).toInt(), from.z + (dz * t).toInt())
            if (snapshot[pos] == true) {
                if (highestObstruction == null || pos.y > highestObstruction.y) highestObstruction = pos
            }
        }

        if (highestObstruction == null) return null

        for (dy2 in 1..MAX_FLY_OVER_HEIGHT) {
            val above = highestObstruction.above(dy2)
            if (snapshot[above] != true) {
                return BlockPos((from.x + to.x) / 2, above.y, (from.z + to.z) / 2)
            }
        }
        return BlockPos((from.x + to.x) / 2, highestObstruction.y + MAX_FLY_OVER_HEIGHT, (from.z + to.z) / 2)
    }

    private const val MAX_FLY_OVER_HEIGHT = 10

    private fun spaceCrossings(checkpoints: List<Checkpoint>, level: ServerLevel): Set<Int> =
        SableCompat.flight.crossings(level, checkpoints.map { it.pos })

    /**
     * Inserts [FlyThrough] waypoints between checkpoints to route around solid blocks.
     *
     * For each pair of consecutive checkpoints, does a simple block-by-block raycast.
     * If any block along the line is solid, inserts a waypoint above the obstruction.
     * Cheap: at most ~50 block checks per segment (typical flight distance).
     */
    private fun insertObstacleWaypoints(checkpoints: List<Checkpoint>, level: ServerLevel): List<Checkpoint> =
        buildList {
            val crossings = spaceCrossings(checkpoints, level)
            checkpoints.forEachIndexed { index, checkpoint ->
                add(checkpoint)
                val next = checkpoints.getOrNull(index + 1) ?: return@forEachIndexed
                if (index in crossings) return@forEachIndexed

                val waypoint = findObstacleWaypoint(checkpoint.pos, next.pos, level)
                if (waypoint != null) {
                    add(Checkpoint(waypoint, FlyThrough))
                }
            }
        }

    /**
     * Raycasts from [from] to [to] checking for solid blocks.
     * If an obstruction is found, returns a waypoint above it. Otherwise null.
     */
    private fun findObstacleWaypoint(from: BlockPos, to: BlockPos, level: ServerLevel): BlockPos? {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val steps = max(max(abs(dx), abs(dy)), abs(dz)).coerceAtLeast(1)

        var highestObstruction: BlockPos? = null

        for (i in 1 until steps) {
            val t = i.toFloat() / steps
            val checkPos = BlockPos(
                from.x + (dx * t).toInt(),
                from.y + (dy * t).toInt(),
                from.z + (dz * t).toInt(),
            )

            if (!level.isLoaded(checkPos)) continue
            if (!level.getBlockState(checkPos).getCollisionShape(level, checkPos).isEmpty) {
                if (highestObstruction == null || checkPos.y > highestObstruction.y) {
                    highestObstruction = checkPos
                }
            }
        }

        if (highestObstruction == null) return null

        for (dy2 in 1..MAX_FLY_OVER_HEIGHT) {
            val above = highestObstruction.above(dy2)
            if (level.isLoaded(above) && level.getBlockState(above).getCollisionShape(level, above).isEmpty) {
                val midX = (from.x + to.x) / 2
                val midZ = (from.z + to.z) / 2
                return BlockPos(midX, above.y, midZ)
            }
        }

        return BlockPos((from.x + to.x) / 2, highestObstruction.y + MAX_FLY_OVER_HEIGHT, (from.z + to.z) / 2)
    }

    private fun computeMissingItems(bee: ServerBeeData, batch: TaskBatch): List<ItemStack> {
        val totalRequired = mutableMapOf<ItemStackKey, Int>()
        batch.getRemainingTasks().forEach { task ->
            (task.action as? ItemConsumingAction)?.requiredItems?.forEach { req ->
                val key = ItemStackKey(req)
                totalRequired[key] = (totalRequired[key] ?: 0) + req.count
            }
        }
        bee.getInventoryContents().forEach { carried ->
            val key = ItemStackKey(carried)
            totalRequired[key]?.let { needed ->
                val remaining = needed - carried.count
                if (remaining <= 0) totalRequired.remove(key)
                else totalRequired[key] = remaining
            }
        }
        return totalRequired.map { (key, count) -> key.stack.copy().also { it.count = count } }
    }

    private fun computeMissingFluid(bee: ServerBeeData, batch: TaskBatch): FluidStack {
        val required = batch.getRemainingTasks().firstNotNullOfOrNull { it.action as? PlaceFluidAction }?.fluid
            ?: return FluidStack.EMPTY
        val carried = bee.carriedFluid
        val carriedAmount = if (FluidStack.isSameFluidSameComponents(carried, required)) carried.amount else 0
        return if (carriedAmount >= required.amount) FluidStack.EMPTY
        else required.copyWithAmount(required.amount - carriedAmount)
    }

    /** Dock Port for the fluid a removal batch will pick up, or null if it isn't removing a fluid source. */
    private fun findFluidDropOff(batch: TaskBatch, network: BeeNetwork): DockPort? {
        val level = network.level ?: return null
        val removal = batch.tasks.firstNotNullOfOrNull { it.action as? RemoveBlockAction } ?: return null
        val state = level.getBlockState(removal.pos)
        if (state.block !is LiquidBlock || !state.fluidState.isSource) return null
        return network.findFluidDropOff(FluidStack(state.fluidState.type, ServerBeeData.FLUID_CAPACITY))
    }

    private fun findBestProvider(
        network: BeeNetwork,
        missing: List<ItemStack>,
        beeId: java.util.UUID,
    ): de.devin.cbbees.content.domain.logistics.LogisticsPort? {
        val log = CreateBuzzyBeez.LOGGER
        val first = missing.firstOrNull() ?: return null
        log.debug("[FlightPlan] Finding provider for ${first.hoverName.string} x${first.count}")
        val provider = network.findAvailableProvider(first.copyWithCount(1), beeId)
        if (provider == null) {
            log.debug("[FlightPlan] No available provider found for ${first.hoverName.string}")
        } else {
            log.debug("[FlightPlan] Selected provider: ${provider.javaClass.simpleName} at ${provider.pos}")
        }
        return provider
    }
}
