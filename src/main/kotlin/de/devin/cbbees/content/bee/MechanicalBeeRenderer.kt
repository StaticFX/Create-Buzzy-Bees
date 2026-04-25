package de.devin.cbbees.content.bee

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRendererProvider
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Renderer for the Mechanical Bee entity using GeckoLib.
 *
 * Uses the custom model and flying animation.
 */
class MechanicalBeeRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<MechanicalBeeEntity>(context, MechanicalBeeModel()) {

    init {
        // Shadow radius for the bee
        this.shadowRadius = 0.3f
    }

    override fun render(
        entity: MechanicalBeeEntity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        // Drones are invisible to all players — they exist only as a camera anchor
        if (entity.isDrone) return

        // Distance-based shadow culling
        val cam = Minecraft.getInstance().gameRenderer.mainCamera.position
        val distSq = entity.distanceToSqr(cam)
        val shadowDist = de.devin.cbbees.config.CBBeesClientConfig.beeShadowDistance.get()
        this.shadowRadius = if (distSq < shadowDist.toLong() * shadowDist) 0.3f else 0.0f

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)
    }
}
