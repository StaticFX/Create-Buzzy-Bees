package de.devin.cbbees.network

import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.TransportDispatcher
import de.devin.cbbees.content.domain.job.JobCalculationProgress
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.drone.DroneViewManager
import de.devin.cbbees.util.ServerTickScheduler
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import de.devin.cbbees.util.ServerSide

/**
 * Server-side event handler for cbbees.
 */
@ServerSide
object CCRServerEvents {

    private var tickCounter = 0
    private var syncCounter = 0
    private var beeSyncCounter = 0

    @SubscribeEvent
    @JvmStatic
    fun onServerTick(event: ServerTickEvent.Post) {
        val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() ?: return
        val overworld = server.overworld()
        val gameTime = overworld.gameTime

        val profiler = overworld.profiler

        profiler.push("cbbees")

        // Drain deferred callbacks (e.g. async task generation chunks) — exactly one
        // batch per tick. Anything scheduled inside a callback runs next tick.
        profiler.push("tickScheduler")
        ServerTickScheduler.runScheduled()
        profiler.pop()

        // ── Tick non-entity bees EVERY tick ──
        profiler.push("beeManager")
        ServerBeeManager.init(overworld)
        ServerBeeManager.tickAll(overworld, gameTime)
        profiler.pop()

        profiler.push("jobEvictions")
        JobCalculationProgress.tickEvictions(server.tickCount)
        profiler.pop()

        // No per-tick sync needed — bees use checkpoint-based flight plans

        // ── Core logic every 10 ticks (0.5 seconds) ──
        tickCounter++
        if (tickCounter >= 10) {
            tickCounter = 0

            profiler.push("networkPurge")
            ServerBeeNetworkManager.getNetworks().forEach { it.purgeStaleComponents(gameTime) }
            ServerBeeNetworkManager.rebuildIndexes()
            profiler.pop()

            profiler.push("jobPool")
            GlobalJobPool.tick(gameTime)
            profiler.pop()

            profiler.push("transportDispatcher")
            TransportDispatcher.tick(gameTime)
            profiler.pop()

            profiler.push("reservationCleanup")
            ServerBeeNetworkManager.getNetworks().forEach { it.cleanupReservations(gameTime) }
            profiler.pop()

            profiler.push("droneValidation")
            DroneViewManager.validateDrones()
            profiler.pop()

            // Sync packets every 40 ticks (2 seconds)
            syncCounter++
            if (syncCounter >= 4) {
                syncCounter = 0
                profiler.push("sync")
                server.playerList.players.forEach { player ->
                    HiveJobsSyncPacket.sendPlayerSnapshotTo(player)
                    NetworkSyncPacket.sendTo(player)
                }
                profiler.pop()
            }
        }

        profiler.pop() // cbbees
    }

    /**
     * Unregisters players as bee sources when they log out.
     */
    @SubscribeEvent
    @JvmStatic
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer
        if (player != null) {
            DroneViewManager.despawnDrone(player)
        }
        ServerBeeNetworkManager.unregisterWorker(event.entity.uuid)
    }

    @SubscribeEvent
    @JvmStatic
    fun onPlayerDeath(event: LivingDeathEvent) {
        val player = event.entity as? ServerPlayer ?: return
        DroneViewManager.despawnDrone(player)
    }

    /**
     * Clears networks on server stop to prevent stale data between world loads.
     */
    @SubscribeEvent
    @JvmStatic
    fun onServerStopping(event: ServerStoppingEvent) {
        // Return bees to hives FIRST, before clearing networks/jobs
        ServerBeeManager.clear()
        ServerBeeNetworkManager.getNetworks().forEach { it.clearReservations() }
        ServerBeeNetworkManager.clear()
        GlobalJobPool.clear()
        TransportDispatcher.clear()
        BeeDebug.clear()
        PlannerUploadPacket.shutdown()
        DroneViewManager.clear()
        ServerTickScheduler.clear()
    }
}
