package de.devin.cbbees.content.logistics.dock

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer
import de.devin.cbbees.content.logistics.ports.PortBulbGlow
import de.devin.cbbees.content.logistics.ports.PortPartialModels
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider

class DockPortRenderer(context: BlockEntityRendererProvider.Context) :
    SmartBlockEntityRenderer<DockPortBlockEntity>(context) {

    override fun renderSafe(
        be: DockPortBlockEntity,
        partialTicks: Float,
        ms: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay)
        if (PortBulbGlow.busyGlow(be.blockState))
            PortBulbGlow.render(be.blockState, be.level, ms, buffer, PortPartialModels.DOCK_BUSY_GLOW, PortBulbGlow.BUSY_PULSE_TICKS)
    }
}
