package de.devin.cbbees.compat.sable

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.util.ServerSide
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

@ServerSide
object SubLevelNetworkTracker {

    private const val INTERVAL_TICKS = 10

    @SubscribeEvent
    @JvmStatic
    fun onServerTick(event: ServerTickEvent.Post) {
        if (event.server.tickCount % INTERVAL_TICKS != 0) return
        val split = splitDisconnected()
        val merged = mergeReconnected()
        if (split || merged) ServerBeeNetworkManager.rebuildIndexes()
    }

    private fun splitDisconnected(): Boolean =
        movingNetworks().map { network ->
            ServerBeeNetworkManager.splitIfDisconnected(network).also { didSplit ->
                if (didSplit) CreateBuzzyBeez.LOGGER.info("[NET] Network ${network.id} split after its sub-level moved")
            }
        }.any { it }

    private fun mergeReconnected(): Boolean {
        var merged = false
        movingNetworks().forEach { network ->
            if (network !in ServerBeeNetworkManager.getNetworks()) return@forEach
            val anchors = network.components.filter { it.isAnchor() }
            ServerBeeNetworkManager.getNetworks()
                .filter { other -> other !== network && !other.isPortable() && anchors.any(other::canConnect) }
                .forEach { other ->
                    ServerBeeNetworkManager.mergeNetworks(network, other)
                    CreateBuzzyBeez.LOGGER.info("[NET] Network ${other.id} merged into ${network.id} after a sub-level moved into range")
                    merged = true
                }
        }
        return merged
    }

    private fun movingNetworks() = ServerBeeNetworkManager.getNetworks().filter { network ->
        !network.isPortable() && network.components.any { it.world.subLevelId(it.pos) != null }
    }

    private fun BeeNetwork.isPortable() = components.any { it is PortableBeeHive }
}
