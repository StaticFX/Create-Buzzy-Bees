package de.devin.cbbees.content.bee.client

import de.devin.cbbees.content.bee.flight.ClientBeeFlightData
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.util.UUID

/**
 * Client-side registry of all bees for rendering, fed by flight plans from the server.
 *
 * @see ClientBeeFlightData
 * @see de.devin.cbbees.network.BeeFlightPlanPacket
 */
@ClientSide
object BeeClientTracker {

    private val flightBees = mutableMapOf<UUID, ClientBeeFlightData>()

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        val paused = mc.isPaused
        setPaused(paused)
        if (paused) return

        val profiler = mc.level?.profiler
        profiler?.push("cbbees_beeClientTick")
        tickClient()
        profiler?.pop()
    }

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

    fun setCarriedItem(beeId: UUID, item: ItemStack) {
        flightBees[beeId]?.carriedItem = item
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
        flightBees.clear()
        previewIds.clear()
    }
}
