package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.vertex.PoseStack
import net.createmod.catnip.render.SuperRenderTypeBuffer
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB

/**
 * Draws a low-opacity coloured volume inside a job AABB.
 *
 * The caller owns the pose transform. For normal-world bounds, translate the
 * pose stack by the negative camera position first. For Sable bounds, apply
 * the projected local transform and pass bounds relative to the same anchor.
 */
object AreaTintRenderer {
    const val DEFAULT_ALPHA = 0.12f

    fun render(
        poseStack: PoseStack,
        buffer: SuperRenderTypeBuffer,
        bounds: AABB,
        color: Int,
        alpha: Float = DEFAULT_ALPHA
    ) {
        val red = ((color shr 16) and 0xFF) / 255f
        val green = ((color shr 8) and 0xFF) / 255f
        val blue = (color and 0xFF) / 255f
        val consumer = buffer.getBuffer(RenderType.debugQuads())

        // debugQuads is translucent and no-cull, so the coloured layer remains
        // visible both outside and inside the selected area.
        for (direction in Direction.entries) {
            LevelRenderer.renderFace(
                poseStack,
                consumer,
                direction,
                bounds.minX.toFloat(),
                bounds.minY.toFloat(),
                bounds.minZ.toFloat(),
                bounds.maxX.toFloat(),
                bounds.maxY.toFloat(),
                bounds.maxZ.toFloat(),
                red,
                green,
                blue,
                alpha
            )
        }
    }
}
