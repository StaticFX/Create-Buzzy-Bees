package de.devin.cbbees.content.domain.network.client

import com.simibubi.create.AllItems
import com.simibubi.create.foundation.utility.RaycastHelper
import de.devin.cbbees.content.domain.network.ClientBeeNetworkManager
import de.devin.cbbees.content.domain.network.INetworkComponent
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import de.devin.cbbees.util.ClientSide
import kotlin.math.sin

/**
 * Handles the blinking network highlight when hovering over any component of a bee network.
 */
@ClientSide
object NetworkHighlightHandler {

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (mc.screen != null) return

        // Only show network indicators while holding a wrench
        val holdingWrench = AllItems.WRENCH.isIn(player.mainHandItem) || AllItems.WRENCH.isIn(player.offhandItem)
        if (!holdingWrench) return

        // Raycast to see if we are looking at a Network Component
        val trace = RaycastHelper.rayTraceRange(level, player, 20.0)
        if (trace != null && trace.type == HitResult.Type.BLOCK) {
            val be = level.getBlockEntity(trace.blockPos)
            if (be is INetworkComponent) {
                renderNetworkBlink(be)
            }
        }
    }

    private fun renderNetworkBlink(component: INetworkComponent) {
        val networkId = component.networkId
        val network = ClientBeeNetworkManager.getNetwork(networkId)

        if (network.components.isEmpty()) return

        // Create-style smooth blinking
        val time = AnimationTickHolder.getRenderTime()
        // Oscillate alpha between 0.3 and 0.9
        val alpha = (0.6 + 0.3 * sin(time * 0.6))
        val color = ((alpha * 255).toInt() shl 24) or 0xFFFFFF

        for (comp in network.components) {
            val pos = comp.pos
            val box = AABB(pos)
            Outliner.getInstance()
                .chaseAABB("network_blink_${comp.id}", box)
                .colored(color)
                .lineWidth(1 / 16f)
        }
    }
}
