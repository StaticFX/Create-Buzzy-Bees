package de.devin.cbbees.content.planner.client

import de.devin.cbbees.content.planner.MasterPlannerItem
import de.devin.cbbees.network.CyclePlannerPacket
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.network.PacketDistributor

@ClientSide
object MasterPlannerClientEvents {

    @SubscribeEvent
    @JvmStatic
    fun onMouseScroll(event: InputEvent.MouseScrollingEvent) {
        val player = Minecraft.getInstance().player ?: return
        if (!player.isShiftKeyDown || player.mainHandItem.item !is MasterPlannerItem) return

        PacketDistributor.sendToServer(CyclePlannerPacket(forward = event.scrollDeltaY < 0))
        event.isCanceled = true
    }
}
