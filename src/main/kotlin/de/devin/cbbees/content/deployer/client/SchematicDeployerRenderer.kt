package de.devin.cbbees.content.deployer.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer
import de.devin.cbbees.content.deployer.SchematicDeployerBlockEntity
import net.createmod.catnip.animation.AnimationTickHolder
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.world.item.ItemDisplayContext
import kotlin.math.sin

/**
 * Renders the held Programmed Schematic floating above the Schematic Deployer,
 * gently bobbing and rotating so players can see at a glance what's loaded.
 */
class SchematicDeployerRenderer(context: BlockEntityRendererProvider.Context) :
    SmartBlockEntityRenderer<SchematicDeployerBlockEntity>(context) {

    override fun renderSafe(
        be: SchematicDeployerBlockEntity,
        partialTicks: Float,
        ms: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay)

        if (be.heldItem.isEmpty) return

        ms.pushPose()

        // Position: centered on the block, floating above it
        val level = be.level ?: return
        val time = AnimationTickHolder.getRenderTime(level)
        val bob = sin((time / 12.0)) * (1.0 / 32.0)
        ms.translate(0.5, 0.8 + bob, 0.5)

        // Slow rotation
        ms.mulPose(Axis.YP.rotationDegrees(time * 2f))

        // Scale down a bit
        ms.scale(0.5f, 0.5f, 0.5f)

        Minecraft.getInstance().itemRenderer.renderStatic(
            be.heldItem,
            ItemDisplayContext.FIXED,
            light,
            overlay,
            ms,
            buffer,
            level,
            0
        )

        ms.popPose()
    }
}
