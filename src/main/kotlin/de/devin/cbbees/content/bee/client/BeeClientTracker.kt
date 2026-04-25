package de.devin.cbbees.content.bee.client

import de.devin.cbbees.content.bee.NetworkedBee
import de.devin.cbbees.content.bee.flight.ClientBeeFlightData
import de.devin.cbbees.util.ClientSide
import java.util.UUID

/**
 * Client-side registry of all bees for rendering.
 *
 * Tracks:
 * - Legacy entity bees via [onBeeAdded]/[onBeeRemoved] (migration)
 * - Checkpoint-based bees via [applyFlightPlan]/[removeFlightData]
 *
 * @see ClientBeeFlightData
 * @see de.devin.cbbees.network.BeeFlightPlanPacket
 */
@ClientSide
object BeeClientTracker {

    private val entityBees = mutableSetOf<NetworkedBee>()
    private val flightBees = mutableMapOf<UUID, ClientBeeFlightData>()

    // ── Legacy entity tracking ──

    fun onBeeAdded(bee: NetworkedBee) { entityBees.add(bee) }
    fun onBeeRemoved(bee: NetworkedBee) { entityBees.remove(bee) }
    fun getBees(): Set<NetworkedBee> = entityBees

    // ── Checkpoint flight data ──

    /** Apply a new or updated flight plan from the server. */
    fun applyFlightPlan(data: ClientBeeFlightData) {
        flightBees[data.id] = data
    }

    /** Remove a bee immediately (entered hive, discarded). */
    fun removeFlightData(id: UUID) {
        flightBees.remove(id)
    }

    /** Server confirmed a checkpoint action completed — unblock the bee's flight. */
    fun confirmCheckpoint(beeId: UUID, checkpointIndex: Int) {
        flightBees[beeId]?.confirmCheckpoint(checkpointIndex)
    }

    /** All active checkpoint-based bees. */
    fun getFlightBees(): Collection<ClientBeeFlightData> = flightBees.values

    /** Notify all bees of pause state change for wall-clock freeze. */
    fun setPaused(paused: Boolean) {
        flightBees.values.forEach { it.setPaused(paused) }
    }

    /** Called every client tick for rotation smoothing. */
    fun tickClient() {
        flightBees.values.forEach { it.tickClient() }
        flightBees.values.removeAll { it.isComplete && it.id !in previewIds }
    }

    // ── Legacy position-based data (kept for backward compat during migration) ──

    fun applySync(bees: List<ClientBeeData>) { /* no-op — replaced by flight plans */ }
    fun getDataBees(): Collection<ClientBeeData> = emptyList()

    // ── Preview bees (spawned via /cbbees preview) ──

    private val previewIds = mutableSetOf<UUID>()

    /** Mark a bee as a preview so it isn't auto-removed when its flight completes. */
    fun addPreviewId(id: UUID) { previewIds.add(id) }

    /** Remove all preview bees. */
    fun clearPreviews() {
        previewIds.forEach { flightBees.remove(it) }
        previewIds.clear()
    }

    fun clear() {
        entityBees.clear()
        flightBees.clear()
        previewIds.clear()
    }
}
