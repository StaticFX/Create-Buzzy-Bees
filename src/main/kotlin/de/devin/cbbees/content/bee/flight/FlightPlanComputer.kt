package de.devin.cbbees.content.bee.flight

import de.devin.cbbees.content.bee.flight.FlightPlanComputer.computeAsync
import de.devin.cbbees.content.bee.flight.FlightPlanComputer.computeTransportAsync
import de.devin.cbbees.content.bee.flight.FlightPlanComputer.forConstruction
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.action.impl.DropOffItemsAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TransportTask
import de.devin.cbbees.util.ItemStackKey
import de.devin.cbbees.util.ServerTickScheduler
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
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
        onComplete: (FlightPlan) -> Unit,
    ) {
        // Snapshot: build raw checkpoints on the server thread (reads network/task data)
        val rawCheckpoints = buildRawConstructionCheckpoints(bee, batch, network)

        // Snapshot: collect all block positions that need collision checks
        val collisionSnapshot = snapshotCollisions(rawCheckpoints.map { it.pos }, level)

        executor.submit {
            // Off-thread: insert obstacle waypoints using the snapshot (no Level access needed)
            val finalCheckpoints = insertObstacleWaypointsFromSnapshot(rawCheckpoints, collisionSnapshot, level)

            val plan = FlightPlan(bee.id, BeeType.CONSTRUCTION, DEFAULT_SPEED, finalCheckpoints)

            // Deliver result back on the server thread. server.execute drains
            // during runAllTasks() at the start of the next tick — before our
            // ServerTickScheduler and before ServerBeeManager.tickAll(), so the
            // flight plan is set before the bee advances any checkpoints.
            level.server.execute { onComplete(plan) }
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
        val collisionSnapshot = snapshotCollisions(rawCheckpoints.map { it.pos }, level)

        executor.submit {
            val finalCheckpoints = insertObstacleWaypointsFromSnapshot(rawCheckpoints, collisionSnapshot, level)
            val plan = FlightPlan(bee.id, BeeType.TRANSPORT, DEFAULT_SPEED, finalCheckpoints)
            level.server.execute { onComplete(plan) }
        }
    }

    /**
     * Snapshots block collision state along all straight-line segments between positions.
     * Called on the server thread. Returns an immutable map safe to read from any thread.
     */
    private fun snapshotCollisions(positions: List<BlockPos>, level: ServerLevel): Map<BlockPos, Boolean> {
        val snapshot = mutableMapOf<BlockPos, Boolean>()

        positions.zipWithNext().forEach { (from, to) ->
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

            // Also snapshot blocks above potential obstructions for routing
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
    ): FlightPlan {
        val raw = buildRawConstructionCheckpoints(bee, batch, network)
        val checkpoints = if (level != null) insertObstacleWaypoints(raw, level) else raw
        return FlightPlan(bee.id, BeeType.CONSTRUCTION, DEFAULT_SPEED, checkpoints)
    }

    /**
     * Builds a transport flight plan synchronously (with optional obstacle avoidance).
     * Prefer [computeTransportAsync] for production use.
     */
    fun forTransport(bee: ServerBeeData, task: TransportTask, level: ServerLevel? = null): FlightPlan {
        val raw = buildRawTransportCheckpoints(bee, task)
        val checkpoints = if (level != null) insertObstacleWaypoints(raw, level) else raw
        return FlightPlan(bee.id, BeeType.TRANSPORT, DEFAULT_SPEED, checkpoints)
    }

    private fun buildRawConstructionCheckpoints(bee: ServerBeeData, batch: TaskBatch, network: BeeNetwork) = buildList {
        add(Checkpoint(bee.blockPosition(), FlyThrough))
        val missing = computeMissingItems(bee, batch)
        if (missing.isNotEmpty()) {
            findBestProvider(network, missing, bee.id)?.let {
                add(Checkpoint(it.pos.above(), GatherFromPort(missing), clientPauseTicks = GATHER_PAUSE_TICKS))
            }
        }
        val remainingTasks = batch.getRemainingTasks()
        remainingTasks.forEach { task ->
            val action = task.action
            when (action) {
                is DropOffItemsAction -> {
                    val dropPort = network.findDropOff(ItemStack.EMPTY)
                    val dropPos = dropPort?.pos?.above() ?: task.targetPos
                    add(Checkpoint(dropPos, ExecuteBeeAction(action, task), clientPauseTicks = DROP_OFF_PAUSE_TICKS))
                }

                is RemoveBlockAction -> {
                    add(
                        Checkpoint(
                            task.targetPos,
                            ExecuteBeeAction(action, task),
                            clientPauseTicks = action.getWorkTicks(bee.getBeeContext())
                        )
                    )
                }

                else -> {
                    add(Checkpoint(task.targetPos, ExecuteBeeAction(action, task)))
                }
            }
        }
        // Check for next batch RIGHT AFTER the last task — not at the hive.
        // If work exists, the bee replans from here (the construction site) without flying home.
        val lastTaskPos = remainingTasks.lastOrNull()?.targetPos ?: bee.blockPosition()
        add(Checkpoint(lastTaskPos, CheckForNextWork()))
        // Only reached if no more work — fly home and enter
        val hiveApproach = (bee.hivePos ?: bee.blockPosition()).above()
        add(Checkpoint(hiveApproach, EnterHive()))
    }

    private fun buildRawTransportCheckpoints(bee: ServerBeeData, task: TransportTask) = buildList {
        add(Checkpoint(bee.blockPosition(), FlyThrough))
        add(Checkpoint(task.sourcePos.above(), PickupTransport(task), clientPauseTicks = PICKUP_PAUSE_TICKS))
        add(Checkpoint(task.targetPos.above(), DepositTransport(task), clientPauseTicks = DEPOSIT_PAUSE_TICKS))
        val hiveApproach = (bee.hivePos ?: bee.blockPosition()).above()
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

    // ── Obstacle Avoidance (snapshot-based, thread-safe) ──

    /**
     * Inserts waypoints using a pre-snapshotted collision map. Safe to call from any thread.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun insertObstacleWaypointsFromSnapshot(
        checkpoints: List<Checkpoint>,
        collisionSnapshot: Map<BlockPos, Boolean>,
        level: ServerLevel, // unused but kept for signature compat
    ): List<Checkpoint> = buildList {
        checkpoints.forEachIndexed { index, checkpoint ->
            add(checkpoint)
            val next = checkpoints.getOrNull(index + 1) ?: return@forEachIndexed
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

    // ── Obstacle Avoidance (synchronous, uses Level directly) ──

    /** Max height to fly above obstructions. */
    private const val MAX_FLY_OVER_HEIGHT = 10

    /**
     * Inserts [FlyThrough] waypoints between checkpoints to route around solid blocks.
     *
     * For each pair of consecutive checkpoints, does a simple block-by-block raycast.
     * If any block along the line is solid, inserts a waypoint above the obstruction.
     * Cheap: at most ~50 block checks per segment (typical flight distance).
     */
    private fun insertObstacleWaypoints(checkpoints: List<Checkpoint>, level: ServerLevel): List<Checkpoint> =
        buildList {
            checkpoints.forEachIndexed { index, checkpoint ->
                add(checkpoint)
                val next = checkpoints.getOrNull(index + 1) ?: return@forEachIndexed

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
                // Found an obstruction — track the highest one
                if (highestObstruction == null || checkPos.y > highestObstruction.y) {
                    highestObstruction = checkPos
                }
            }
        }

        if (highestObstruction == null) return null

        // Route above the obstruction
        for (dy2 in 1..MAX_FLY_OVER_HEIGHT) {
            val above = highestObstruction.above(dy2)
            if (level.isLoaded(above) && level.getBlockState(above).getCollisionShape(level, above).isEmpty) {
                // Waypoint at the midpoint X/Z but above the obstruction
                val midX = (from.x + to.x) / 2
                val midZ = (from.z + to.z) / 2
                return BlockPos(midX, above.y, midZ)
            }
        }

        // Can't find clear space — fly very high
        return BlockPos((from.x + to.x) / 2, highestObstruction.y + MAX_FLY_OVER_HEIGHT, (from.z + to.z) / 2)
    }

    // ── Helpers ──

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

    private fun findBestProvider(
        network: BeeNetwork,
        missing: List<ItemStack>,
        beeId: java.util.UUID,
    ): de.devin.cbbees.content.domain.logistics.LogisticsPort? {
        val first = missing.firstOrNull() ?: return null
        val provider = network.findAvailableProvider(first.copyWithCount(1), beeId) ?: return null
        return if (provider is PortableBeeHive) null else provider
    }
}
