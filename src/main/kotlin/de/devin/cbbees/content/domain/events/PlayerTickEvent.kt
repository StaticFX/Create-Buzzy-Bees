package de.devin.cbbees.content.domain.events

import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.job.JobCalculationProgress
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.network.JobProgressPacket
import de.devin.cbbees.util.ServerSide
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import de.devin.cbbees.compat.CuriosCompat

@ServerSide
class PlayerTickEvent {

    /**
     * Registers the portable beehive immediately on login so that bees
     * loaded from disk can reconnect before the first tick-based check.
     */
    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity
        if (player.level().isClientSide) return
        if (hasPortableHive(player)) {
            val hive = PortableBeeHive(player)
            hive.networkId = ServerBeeNetworkManager.stableNetworkId(player.uuid)
            ServerBeeNetworkManager.registerWorker(hive)
        }

        // Replay any cached calculation progress so the player resumes seeing
        // live progress for jobs that were running while they were offline.
        if (player is ServerPlayer) {
            JobCalculationProgress.snapshotsForOwner(player.uuid).forEach { snap ->
                PacketDistributor.sendToPlayer(
                    player,
                    JobProgressPacket(snap.jobId, snap.phase, snap.labelKey, snap.processedBlocks, snap.expectedBlocks, snap.resultKey, snap.resultCount),
                )
            }
        }
    }

    @SubscribeEvent
    fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity
        if (player.level().isClientSide) return

        val profiler = player.level().profiler

        profiler.push("cbbees_flight")
        handleFlightUpgrade(player)
        profiler.pop()

        if (player.tickCount % 40 != 0) return

        profiler.push("cbbees_portableHive")
        val pool = ServerBeeNetworkManager

        val existingHive =
            pool.getNetworks().flatMap { it.hives }.filterIsInstance<PortableBeeHive>().find { it.player.uuid == player.uuid }

        if (hasPortableHive(player)) {
            if (existingHive == null) {
                val hive = PortableBeeHive(player)
                hive.networkId = pool.stableNetworkId(player.uuid)
                pool.registerWorker(hive)
            } else {
                pool.reconnectPortableHive(existingHive)
            }
        } else {
            if (existingHive != null) {
                pool.unregisterWorker(player.uuid)
            }
        }
        profiler.pop()
    }

    @Suppress("DEPRECATION")
    private fun handleFlightUpgrade(player: net.minecraft.world.entity.player.Player) {
        if (player.isCreative || player.isSpectator) return
        // Mechanical Wings upgrade was removed in 1.3.0. Gracefully disable flight
        // for players who had it active from a previous version.
        if (player.abilities.mayfly && !player.isCreative && !player.isSpectator) {
            player.abilities.mayfly = false
            player.abilities.flying = false
            player.onUpdateAbilities()
        }
    }

    private fun hasPortableHive(player: net.minecraft.world.entity.player.Player): Boolean {
        val curios = CuriosCompat.findFirstCurio(player) { it.item is PortableBeehiveItem }
        if (!curios.isEmpty) return true
        return player.inventory.armor[2].item is PortableBeehiveItem
    }
}