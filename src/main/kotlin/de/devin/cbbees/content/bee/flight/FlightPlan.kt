package de.devin.cbbees.content.bee.flight

import de.devin.cbbees.compat.sable.worldDistanceSqr
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.bee.server.ServerBeeData
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.sqrt

/**
 * A bee's full mission represented as an ordered list of [Checkpoint] waypoints.
 *
 * The flight plan is computed once when a bee is assigned a task (or recomputed when
 * a checkpoint fails validation). Both server and client advance through the same
 * checkpoints deterministically — the server executes [CheckpointAction]s on arrival,
 * while the client just interpolates position between checkpoint positions.
 *
 * ## Lifecycle
 * 1. Bee spawns → [FlightPlanComputer] builds a plan from the task batch
 * 2. Plan sent to client via `BeeFlightPlanPacket`
 * 3. Server advances checkpoints by timing (distance / speed)
 * 4. On arrival, the [Checkpoint.action] validates and executes
 * 5. If action fails (e.g., port destroyed), [FlightPlanComputer] replans from current position
 * 6. Plan complete → bee enters hive → `BeeRemovePacket` sent to client
 *
 * @see FlightPlanComputer
 * @see CheckpointAction
 * @see Checkpoint
 */
data class FlightPlan(
    val beeId: UUID,
    val type: BeeType,
    val speed: Float,
    val checkpoints: List<Checkpoint>,
    val segmentTicks: List<Long>,
) {
    /** Estimated total flight ticks (sum of distances / speed + pause ticks). */
    val estimatedDurationTicks: Long
        get() = (1 until checkpoints.size).sumOf(::ticksToReach)

    /**
     * Pre-computes cumulative arrival ticks for each checkpoint.
     * Used by the server for checkpoint advancement; the client receives [segmentTicks]
     * directly, which guarantees identical timing on both sides.
     */
    fun computeArrivalTicks(): LongArray = LongArray(checkpoints.size).also { arr ->
        var cumulative = 0L
        for (i in 1 until checkpoints.size) {
            cumulative += ticksToReach(i)
            arr[i] = cumulative
        }
    }

    fun ticksToReach(index: Int): Long = segmentTicks[index] + checkpoints[index].workTicks + checkpoints[index - 1].clientPauseTicks

    fun scheduled(startTick: Long, reserve: (Long) -> Long): FlightPlan {
        val waypoints = checkpoints.toMutableList()
        val segments = segmentTicks.toMutableList()
        var tick = startTick
        for (i in 1 until waypoints.size) {
            tick += segments[i] + waypoints[i].workTicks + waypoints[i - 1].clientPauseTicks
            if (waypoints[i].action !is ExecuteBeeAction) continue
            val delay = reserve(tick) - tick
            if (delay <= 0) continue
            val stretch = minOf(delay, segments[i])
            segments[i] += stretch
            waypoints[i - 1] = waypoints[i - 1].let { it.copy(clientPauseTicks = it.clientPauseTicks + (delay - stretch).toInt()) }
            tick += delay
        }
        return copy(checkpoints = waypoints, segmentTicks = segments)
    }

    companion object {
        fun of(beeId: UUID, type: BeeType, speed: Float, checkpoints: List<Checkpoint>, level: Level?) = FlightPlan(
            beeId, type, speed, checkpoints,
            checkpoints.indices.map { i ->
                if (i == 0) 0L else travelTicks(checkpoints[i - 1].pos, checkpoints[i].pos, speed, level)
            }
        )

        /**
         * Euclidean travel time between two block positions at the given speed.
         * Single source of truth — used by server, client, and duration estimates.
         */
        fun travelTicks(from: BlockPos, to: BlockPos, speed: Float, level: Level? = null): Long {
            val distSqr = level?.worldDistanceSqr(from, to) ?: Vec3.atCenterOf(from).distanceToSqr(Vec3.atCenterOf(to))
            return (sqrt(distSqr) / speed).toLong().coerceAtLeast(1)
        }
    }
}

/**
 * A single waypoint in a [FlightPlan].
 *
 * The bee flies toward [pos] at the plan's speed. On arrival, [action] is invoked
 * server-side to perform the checkpoint's work (pick up items, place a block, enter
 * the hive, etc.). The client only sees [pos] and [clientPauseTicks] — it doesn't
 * need the action logic.
 *
 * @see CheckpointAction
 * @see FlightPlan
 */
data class Checkpoint(
    val pos: BlockPos,
    val action: CheckpointAction,
    /** How long the client should visually pause at this checkpoint (ticks). */
    val clientPauseTicks: Int = 0,
    val workTicks: Int = 0,
)

/**
 * Composable action executed when a bee arrives at a [Checkpoint].
 *
 * Each checkpoint type is a standalone class implementing this interface — no enum,
 * no central switch statement. Adding new checkpoint behaviors is just creating a new
 * class. The server calls [onArrival] each tick while the bee is at the checkpoint.
 *
 * ## Built-in implementations
 * - [FlyThrough] — pass-through waypoint, always succeeds
 * - [GatherFromPort] — pick up items from a logistics port
 * - [ExecuteBeeAction] — run a [de.devin.cbbees.content.domain.action.BeeAction] with global throttle
 * - [EnterHive] — return the bee to its hive
 * - [RechargeSpring] — wait at hive until spring is full
 * - [PickupTransport] / [DepositTransport] — bumble bee cargo operations
 *
 * ## Return value contract
 * - `true` → checkpoint complete, bee advances to next waypoint
 * - `false` → not done yet, will be called again next tick (throttle, recharge timer, etc.)
 *
 * @see Checkpoint
 * @see FlightPlanComputer
 */
fun interface CheckpointAction {
    /**
     * Called each server tick while the bee is at this checkpoint.
     *
     * @param bee the bee's server-side data
     * @param level the server level
     * @param gameTime current game tick
     * @return `true` to advance to the next checkpoint, `false` to retry next tick
     */
    fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean
}
