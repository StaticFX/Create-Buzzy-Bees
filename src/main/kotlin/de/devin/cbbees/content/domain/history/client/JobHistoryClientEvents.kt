package de.devin.cbbees.content.domain.history.client

import de.devin.cbbees.content.planner.AllPlanners
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.network.JobHistoryPacket
import de.devin.cbbees.registry.AllKeys
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.network.PacketDistributor

object JobHistoryClientEvents {

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        val undo = AllKeys.UNDO.drainClicks()
        val redo = AllKeys.REDO.drainClicks()
        if (!holdsBeeTool()) return
        repeat(undo) { PacketDistributor.sendToServer(JobHistoryPacket(redo = false)) }
        repeat(redo) { PacketDistributor.sendToServer(JobHistoryPacket(redo = true)) }
    }

    private fun KeyMapping.drainClicks(): Int {
        var clicks = 0
        while (consumeClick()) clicks++
        return clicks
    }

    private fun holdsBeeTool(): Boolean {
        val mc = Minecraft.getInstance()
        if (mc.screen != null) return false
        val stack = mc.player?.mainHandItem ?: return false
        return AllPlanners.ALL.any { it.isIn(stack) } || AllItems.SCHEMATIC_ZAPPER.isIn(stack)
    }
}
