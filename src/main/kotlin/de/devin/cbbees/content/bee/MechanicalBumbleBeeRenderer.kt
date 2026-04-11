package de.devin.cbbees.content.bee

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRendererProvider
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Renderer for the Mechanical Bumble Bee entity using GeckoLib.
 */
class MechanicalBumbleBeeRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<MechanicalBumbleBeeEntity>(context, MechanicalBumbleBeeModel()) {

    init {
        this.shadowRadius = 0.3f
        addRenderLayer(BumbleBeeCarriedItemLayer(this))
    }

    override fun render(
        entity: MechanicalBumbleBeeEntity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        // Distance-based shadow culling
        val cam = Minecraft.getInstance().gameRenderer.mainCamera.position
        val distSq = entity.distanceToSqr(cam)
        val shadowDist = de.devin.cbbees.config.CBBeesClientConfig.beeShadowDistance.get()
        this.shadowRadius = if (distSq < shadowDist.toLong() * shadowDist) 0.3f else 0.0f

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)
    }
}
