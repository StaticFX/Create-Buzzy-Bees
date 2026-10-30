package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Axis
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.Vec3i
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@OnlyIn(Dist.CLIENT)
object SpinningPreview {

    private const val PITCH = 30f
    private const val SPIN_SPEED = 1.5f
    private const val FILL = 0.85

    fun render(graphics: GuiGraphics, renderer: GhostSchematicRenderer, size: Vec3i, x: Int, y: Int, w: Int, h: Int) {
        val pitch = Math.toRadians(PITCH.toDouble())
        val spread = sqrt((size.x * size.x + size.z * size.z).toDouble())
        val height = size.y * cos(pitch) + spread * sin(pitch)
        val scale = (min(w / spread, h / height) * FILL).toFloat()

        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(x + w / 2.0, y + h / 2.0, 150.0)
        pose.scale(scale, -scale, scale)
        pose.mulPose(Axis.XP.rotationDegrees(PITCH))
        pose.mulPose(Axis.YP.rotationDegrees(AnimationTickHolder.getRenderTime() * SPIN_SPEED % 360f))
        pose.translate(-size.x / 2.0, -size.y / 2.0, -size.z / 2.0)

        RenderSystem.enableDepthTest()
        Lighting.setupFor3DItems()
        val buffers = DefaultSuperRenderTypeBuffer.getInstance()
        renderer.render(pose, buffers)
        buffers.draw()
        pose.popPose()
    }
}
