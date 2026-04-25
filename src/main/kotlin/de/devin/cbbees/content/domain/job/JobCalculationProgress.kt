package de.devin.cbbees.content.domain.job

import de.devin.cbbees.network.JobProgressPacket
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Server-side singleton tracking calculation progress for long-running job setup
 * (schematic build/removal task generation). Drives [JobProgressPacket] broadcasts
 * to job owners and caches the latest snapshot per active job so that re-joining
 * players can resume seeing live progress.
 *
 * Trackers are created via [newTracker] by packet handlers. The bridge advances
 * each tracker once per processed chunk by calling [Tracker.advance] with the
 * actual block count consumed in that chunk. The chunk size is purely a server
 * pacing concern — clients only see real block counts.
 *
 * Completed/failed entries linger in the cache for [EVICTION_DELAY_TICKS] so a
 * player who joins right at the end still sees the final state briefly.
 */
object JobCalculationProgress {

    enum class Phase { STARTED, IN_PROGRESS, COMPLETED, FAILED }

    /** Immutable progress snapshot — used for the in-memory cache and packet payload. */
    data class Snapshot(
        val jobId: UUID,
        val ownerId: UUID,
        val labelKey: String,
        val expectedBlocks: Int,
        val processedBlocks: Int,
        val phase: Phase,
        /** Translation key for the completion message (only set when [phase] is COMPLETED). */
        val resultKey: String = "",
        /** Argument passed to the completion translation (e.g. task count). */
        val resultCount: Int = 0,
    )

    /**
     * Mutable per-job tracker. Created by packet handlers, passed into
     * `SchematicCreateBridge` and advanced per chunk. Mutated only on the
     * server thread; reads are safe via @Volatile.
     */
    class Tracker internal constructor(
        val jobId: UUID,
        val ownerId: UUID,
        val labelKey: String,
        val expectedBlocks: Int,
        private val server: MinecraftServer,
    ) {
        @Volatile private var processedBlocks: Int = 0
        @Volatile private var phase: Phase = Phase.STARTED
        @Volatile private var resultKey: String = ""
        @Volatile private var resultCount: Int = 0

        /** Register, broadcast STARTED. */
        fun start() {
            register(this)
            broadcast()
        }

        /** Add [blocksThisChunk] to the running total and broadcast. Clamped at [expectedBlocks]. */
        fun advance(blocksThisChunk: Int) {
            processedBlocks = min(processedBlocks + blocksThisChunk, expectedBlocks)
            phase = Phase.IN_PROGRESS
            broadcast()
        }

        /**
         * Force processedBlocks = expectedBlocks, broadcast COMPLETED, schedule eviction.
         * @param completionKey translation key for the completion message (e.g. `cbbees.construction.started`)
         * @param count argument passed to the translation (e.g. number of task batches)
         */
        fun complete(completionKey: String = "", count: Int = 0) {
            processedBlocks = expectedBlocks
            phase = Phase.COMPLETED
            resultKey = completionKey
            resultCount = count
            broadcast()
            scheduleEviction()
        }

        /** Broadcast FAILED, schedule eviction. */
        fun fail() {
            phase = Phase.FAILED
            broadcast()
            scheduleEviction()
        }

        internal fun snapshot(): Snapshot =
            Snapshot(jobId, ownerId, labelKey, expectedBlocks, processedBlocks, phase, resultKey, resultCount)

        private fun broadcast() {
            val snap = snapshot()
            val player = server.playerList.getPlayer(ownerId) ?: return
            PacketDistributor.sendToPlayer(
                player,
                JobProgressPacket(snap.jobId, snap.phase, snap.labelKey, snap.processedBlocks, snap.expectedBlocks, snap.resultKey, snap.resultCount),
            )
        }

        private fun scheduleEviction() {
            // Defer eviction to a later tick so the final packet still resolves from
            // the cache for any player who joins within the next ~5s.
            val targetTick = server.tickCount + EVICTION_DELAY_TICKS
            evictionQueue[jobId] = targetTick
        }
    }

    private val active = ConcurrentHashMap<UUID, Tracker>()
    private val evictionQueue = ConcurrentHashMap<UUID, Int>()

    private const val EVICTION_DELAY_TICKS = 100 // 5 seconds at 20 TPS

    fun newTracker(
        jobId: UUID,
        ownerId: UUID,
        labelKey: String,
        expectedBlocks: Int,
        server: MinecraftServer,
    ): Tracker = Tracker(jobId, ownerId, labelKey, expectedBlocks.coerceAtLeast(1), server)

    /** Returns snapshots of all active jobs owned by [ownerId]. Used for late-joiner replay. */
    fun snapshotsForOwner(ownerId: UUID): List<Snapshot> =
        active.values.filter { it.ownerId == ownerId }.map { it.snapshot() }

    /** Called every server tick to evict expired entries. */
    fun tickEvictions(currentTick: Int) {
        if (evictionQueue.isEmpty()) return
        val expired = evictionQueue.entries.filter { currentTick >= it.value }.map { it.key }
        for (id in expired) {
            evictionQueue.remove(id)
            active.remove(id)
        }
    }

    private fun register(tracker: Tracker) {
        active[tracker.jobId] = tracker
        evictionQueue.remove(tracker.jobId)
    }
}
