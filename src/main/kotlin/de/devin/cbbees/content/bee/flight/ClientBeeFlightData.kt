package de.devin.cbbees.content.bee.flight

import de.devin.cbbees.content.bee.server.BeeType
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Client-side autonomous bee flight data.
 *
 * Position is computed directly from [System.nanoTime] at render time for
 * perfectly smooth movement at any frame rate and TPS. The clock pauses
 * when the game is paused (tracked via [paused] flag set by [setPaused]).
 *
 * @see FlightPlan
 */
class ClientBeeFlightData(
    val id: UUID,
    val type: BeeType,
    val speed: Float,
    val checkpoints: List<ClientCheckpoint>,
    startIndex: Int = 0,
    /** Unused — kept for packet compat. Sync uses startIndex instead. */
    elapsedNanoOffset: Long = 0,
) {
    private val positions: List<Vec3> = checkpoints.map { Vec3.atCenterOf(it.pos) }

    private val arrivalNanos: LongArray = LongArray(checkpoints.size).also { arr ->
        var cumulative = 0L
        arr[0] = 0
        for (i in 1 until checkpoints.size) {
            val travelTicks = FlightPlan.travelTicks(checkpoints[i - 1].pos, checkpoints[i].pos, speed)
            cumulative += (travelTicks + checkpoints[i - 1].pauseTicks) * NANOS_PER_TICK
            arr[i] = cumulative
        }
    }

    /**
     * Wall-clock start time, shifted so the client begins at [startIndex].
     * The client starts its timer as if it had been flying since checkpoint 0,
     * but with the clock already advanced to the arrival time of [startIndex].
     * This means the bee visually appears at checkpoint [startIndex] and flies forward.
     */
    private var startNano: Long = System.nanoTime() - arrivalNanos.getOrElse(startIndex) { 0L }

    /** Accumulated pause duration to subtract from elapsed time. */
    private var pauseOffsetNano: Long = 0

    /** nanoTime when the game was last paused. */
    private var pauseStartNano: Long = 0

    /** Whether the game is currently paused. */
    private var isPaused = false

    // ── Checkpoint confirmation wait ──
    /** Sorted indices of checkpoints that require server confirmation before advancing. */
    private val actionCheckpointIndices: IntArray = checkpoints.indices
        .filter { checkpoints[it].awaitConfirm }
        .toIntArray()
    /** Pointer into [actionCheckpointIndices] — the next action checkpoint to check. */
    private var nextActionPtr: Int = 0
    /** Checkpoint index we're currently blocked at, or -1 if flying freely. */
    private var waitingAtIdx: Int = -1
    /** nanoTime when we started waiting at the current checkpoint. */
    private var waitStartNano: Long = 0L
    /** Total accumulated wait time across all confirmed checkpoints. */
    private var totalWaitNano: Long = 0L
    /** Confirmations received before the client reached the checkpoint (race condition buffer). */
    private val earlyConfirms = mutableSetOf<Int>()

    private var currentYRot: Float = 0f

    private val separationAngle = (id.hashCode() * 2654435761L and 0xFFFF).toDouble() / 0xFFFF * Math.PI * 2
    private val wobblePhase = (id.leastSignificantBits * 2246822519L and 0xFFFF).toDouble() / 0xFFFF * Math.PI * 2
    private val ySeparationPhase = (id.mostSignificantBits * 1597334677L and 0xFF).toDouble() / 0xFF

    val isComplete: Boolean get() =
        checkpoints.size < 2 || elapsedNano() >= arrivalNanos.last()

    /** Call when game pauses/unpauses. */
    fun setPaused(paused: Boolean) {
        if (paused && !isPaused) {
            pauseStartNano = System.nanoTime()
        } else if (!paused && isPaused) {
            pauseOffsetNano += System.nanoTime() - pauseStartNano
        }
        isPaused = paused
    }

    /**
     * Server confirmed a checkpoint action completed — unblock the bee's flight.
     * If the client hasn't reached this checkpoint yet, the confirmation is buffered
     * in [earlyConfirms] and the checkpoint is skipped (no wait) when reached.
     */
    fun confirmCheckpoint(index: Int) {
        if (waitingAtIdx == index) {
            // Currently blocked here — unblock immediately
            val now = if (isPaused) pauseStartNano else System.nanoTime()
            totalWaitNano += now - waitStartNano
            waitingAtIdx = -1
        } else {
            // Not there yet — buffer so we don't block when we arrive
            earlyConfirms.add(index)
        }
    }

    /** Smooth rotation update — call every client tick. */
    fun tickClient() {
        currentYRot = lerpAngle(currentYRot, computeSegmentYRot(), 0.25f)
    }

    /**
     * Elapsed flight time in nanos, excluding paused and wait periods.
     * If the bee has reached an unconfirmed action checkpoint, the returned
     * value is clamped at that checkpoint's arrival time until [confirmCheckpoint]
     * is called.
     */
    private fun elapsedNano(): Long {
        val now = if (isPaused) pauseStartNano else System.nanoTime()

        // Currently blocked at a checkpoint waiting for server confirmation
        if (waitingAtIdx >= 0) {
            return arrivalNanos[waitingAtIdx]
        }

        val elapsed = now - startNano - pauseOffsetNano - totalWaitNano

        // Check if we've reached the next action checkpoint that needs confirmation
        while (nextActionPtr < actionCheckpointIndices.size) {
            val cpIdx = actionCheckpointIndices[nextActionPtr]
            if (elapsed < arrivalNanos[cpIdx]) break // haven't reached it yet
            nextActionPtr++
            // If the server already confirmed this checkpoint (arrived before us), skip it
            if (earlyConfirms.remove(cpIdx)) continue
            // Block here until the server confirms
            waitingAtIdx = cpIdx
            waitStartNano = now
            return arrivalNanos[cpIdx]
        }

        return elapsed
    }

    /**
     * Exact position at this instant. Called every render frame.
     * Reads [System.nanoTime] directly — smooth at any FPS/TPS.
     */
    fun lerpPos(@Suppress("UNUSED_PARAMETER") partialTick: Float): Vec3 {
        if (checkpoints.size < 2) return positions.firstOrNull() ?: Vec3.ZERO
        return computePositionAtNano(elapsedNano())
    }

    fun yRot(): Float = currentYRot

    // ── Position computation ──

    private fun computePositionAtNano(nano: Long): Vec3 {
        val (segIndex, segStartNano, segEndNano) = findSegment(nano)
        if (segIndex >= positions.size - 1) return positions.last().add(flightOffset(nano)).add(separationOffset())

        val pauseNano = checkpoints[segIndex].pauseTicks.toLong() * NANOS_PER_TICK
        val travelStartNano = segStartNano + pauseNano
        val travelDuration = (segEndNano - travelStartNano).coerceAtLeast(1)

        val rawT = if (nano <= travelStartNano) 0f
        else ((nano - travelStartNano).toFloat() / travelDuration).coerceIn(0f, 1f)

        val t = easeIn(rawT).toDouble()

        return positions[segIndex].lerp(positions[segIndex + 1], t)
            .add(flightOffset(nano))
            .add(separationOffset())
    }

    private fun findSegment(nano: Long): Triple<Int, Long, Long> {
        for (i in 1 until arrivalNanos.size) {
            if (nano < arrivalNanos[i]) return Triple(i - 1, arrivalNanos[i - 1], arrivalNanos[i])
        }
        val last = arrivalNanos.size - 1
        return Triple((last - 1).coerceAtLeast(0), arrivalNanos[(last - 1).coerceAtLeast(0)], arrivalNanos[last])
    }

    private fun computeSegmentYRot(): Float {
        val (segIndex, _, _) = findSegment(elapsedNano())
        val from = positions.getOrNull(segIndex) ?: return currentYRot
        val to = positions.getOrNull(segIndex + 1) ?: return currentYRot
        val dx = to.x - from.x
        val dz = to.z - from.z
        return if (dx * dx + dz * dz < 0.01) currentYRot
        else (atan2(-dx, dz) * (180.0 / Math.PI)).toFloat()
    }

    private fun flightOffset(nano: Long): Vec3 {
        val t = nano / 1_000_000_000.0
        val bobY = sin(t * 2.8 + separationAngle) * 0.07
        val wobbleX = sin(t * 1.3 + wobblePhase) * 0.12 + sin(t * 3.7 + separationAngle) * 0.04
        val wobbleZ = cos(t * 1.7 + wobblePhase) * 0.10 + cos(t * 4.1 + separationAngle) * 0.03
        return Vec3(wobbleX, bobY, wobbleZ)
    }

    private fun separationOffset() = Vec3(
        cos(separationAngle) * 0.4,
        ySeparationPhase * 0.6 - 0.3,
        sin(separationAngle) * 0.4,
    )

    private fun easeIn(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * 0.7f + (c * c) * 0.3f
    }

    companion object {
        private const val NANOS_PER_TICK = 50_000_000L

        private fun lerpAngle(from: Float, to: Float, factor: Float): Float {
            var delta = (to - from) % 360f
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            return from + delta * factor
        }
    }
}

data class ClientCheckpoint(
    val pos: BlockPos,
    val pauseTicks: Int = 0,
    /** If true, the client holds the bee at this checkpoint until the server confirms completion. */
    val awaitConfirm: Boolean = false,
)
