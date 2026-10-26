package de.devin.cbbees.content.logistics.ports

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider

/** Glows while bees use the port, and in the crafting colour while its storage network crafts for them. */
class LogisticsPortRenderer(context: BlockEntityRendererProvider.Context) :
    SmartBlockEntityRenderer<LogisticPortBlockEntity>(context) {

    override fun renderSafe(
        be: LogisticPortBlockEntity,
        partialTicks: Float,
        ms: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay)
        val state = be.blockState
        when {
            PortBulbGlow.busyGlow(state) ->
                PortBulbGlow.render(state, be.level, ms, buffer, PortPartialModels.LOGISTICS_BUSY_GLOW, PortBulbGlow.BUSY_PULSE_TICKS)
            be.crafting.isNotEmpty() ->
                PortBulbGlow.render(state, be.level, ms, buffer, PortPartialModels.CRAFTING_GLOW, PortBulbGlow.CRAFTING_PULSE_TICKS, PortPartialModels.CRAFTING_BULB)
        }
    }
}
