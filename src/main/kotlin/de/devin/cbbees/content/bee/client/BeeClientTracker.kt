package de.devin.cbbees.content.bee.client

import de.devin.cbbees.content.bee.flight.BreakingProgress
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
    private val cracks = mutableMapOf<UUID, BreakingProgress>()

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
        updateCracks()
    }

    private fun updateCracks() {
        val level = Minecraft.getInstance().level ?: return
        flightBees.values.forEach { bee ->
            val progress = bee.breakingProgress()
            val shown = cracks[bee.id]
            if (progress == shown) return@forEach
            if (shown != null && shown.pos != progress?.pos) level.destroyBlockProgress(crackId(bee.id), shown.pos, -1)
            if (progress != null) {
                level.destroyBlockProgress(crackId(bee.id), progress.pos, progress.stage)
                cracks[bee.id] = progress
            } else {
                cracks.remove(bee.id)
            }
        }
        cracks.entries.removeAll { (id, shown) ->
            (id !in flightBees).also { gone -> if (gone) level.destroyBlockProgress(crackId(id), shown.pos, -1) }
        }
    }

    private fun crackId(beeId: UUID) = -(beeId.hashCode() and Int.MAX_VALUE) - 1

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
        cracks.clear()
        flightBees.clear()
        previewIds.clear()
    }
}
