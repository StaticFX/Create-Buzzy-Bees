package de.devin.cbbees.content.bee

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.item.ItemDisplayContext
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.renderer.GeoRenderer
import software.bernie.geckolib.renderer.layer.GeoRenderLayer

/**
 * Render layer that displays the first carried item below the bumble bee.
 */
class BumbleBeeCarriedItemLayer(renderer: GeoRenderer<MechanicalBumbleBeeEntity>) :
    GeoRenderLayer<MechanicalBumbleBeeEntity>(renderer) {

    override fun render(
        poseStack: PoseStack,
        animatable: MechanicalBumbleBeeEntity,
        bakedModel: BakedGeoModel,
        renderType: RenderType?,
        bufferSource: MultiBufferSource,
        buffer: VertexConsumer?,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int
    ) {
        // Skip item rendering for distant bees
        val cam = Minecraft.getInstance().gameRenderer.mainCamera.position
        val itemDist = de.devin.cbbees.config.CBBeesClientConfig.beeItemRenderDistanceSafe()
        if (animatable.distanceToSqr(cam) > itemDist.toLong() * itemDist) return

        val contents = animatable.getInventoryContents()
        if (contents.isEmpty()) return

        val stack = contents[0]

        poseStack.pushPose()

        // Position below the bee body
        poseStack.translate(0.0, -0.35, 0.0)
        poseStack.scale(0.35f, 0.35f, 0.35f)

        // Slow spin for visual flair
        val rotation = (animatable.tickCount + partialTick) * 2f
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation))

        Minecraft.getInstance().itemRenderer.renderStatic(
            stack,
            ItemDisplayContext.FIXED,
            packedLight,
            packedOverlay,
            poseStack,
            bufferSource,
            animatable.level(),
            animatable.id
        )

        poseStack.popPose()
    }
}
