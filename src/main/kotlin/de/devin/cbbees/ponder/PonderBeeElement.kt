package de.devin.cbbees.ponder

import com.mojang.math.Axis
import de.devin.cbbees.content.bee.client.BeeWorldRenderer
import de.devin.cbbees.content.bee.server.BeeType
import net.createmod.catnip.math.AngleHelper
import net.createmod.ponder.api.level.PonderLevel
import net.createmod.ponder.foundation.PonderScene
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3

/**
 * Bee shown in Ponder scenes, drawn with the same model code as bees in the world.
 */
class PonderBeeElement(
    private val type: BeeType,
    var position: Vec3,
    var yRot: Float,
) : AnimatedSceneElementBase() {

    private var previousPosition = position
    private var previousYRot = yRot

    override fun tick(scene: PonderScene) {
        super.tick(scene)
        previousPosition = position
        previousYRot = yRot
    }

    override fun renderLast(world: PonderLevel, buffer: MultiBufferSource, graphics: GuiGraphics, fade: Float, pt: Float) {
        val poseStack = graphics.pose()
        val renderPosition = previousPosition.lerp(position, pt.toDouble())

        poseStack.pushPose()
        poseStack.translate(renderPosition.x, renderPosition.y, renderPosition.z)
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - AngleHelper.angleLerp(pt.toDouble(), previousYRot.toDouble(), yRot.toDouble())))
        BeeWorldRenderer.renderBee(poseStack, buffer, type, lightCoordsFromFade(fade))
        poseStack.popPose()
    }
}
