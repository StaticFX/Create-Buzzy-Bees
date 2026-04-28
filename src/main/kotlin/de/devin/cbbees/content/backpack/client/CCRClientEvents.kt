package de.devin.cbbees.content.backpack.client

import de.devin.cbbees.content.backpack.BeehiveTooltipData
import de.devin.cbbees.content.bee.client.BeeClientTracker
import de.devin.cbbees.content.domain.network.ClientBeeNetworkManager
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.bus.api.SubscribeEvent

/**
 * Client-side event handler for general CCR features.
 *
 * Handles:
 * - Tooltip component registration (on MOD_BUS)
 * - Network cleanup (on NeoForge EVENT_BUS)
 */
object CCRClientEvents {

    @SubscribeEvent
    @JvmStatic
    fun registerTooltipComponents(event: RegisterClientTooltipComponentFactoriesEvent) {
        event.register(BeehiveTooltipData::class.java) { data: BeehiveTooltipData -> BeehiveTooltipComponent(data) }
    }
}

/**
 * Event handler for NeoForge EVENT_BUS events related to bee networks on client.
 */
object BeeNetworkClientEvents {
    @SubscribeEvent
    @JvmStatic
    fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        ClientBeeNetworkManager.clear()
        BeeClientTracker.clear()
    }

    @SubscribeEvent
    @JvmStatic
    fun onDimensionChange(event: PlayerEvent.PlayerChangedDimensionEvent) {
        if (event.entity.level().isClientSide) {
            ClientBeeNetworkManager.clear()
            BeeClientTracker.clear()
        }
    }
}
