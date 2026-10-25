package de.devin.cbbees.icons

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.simibubi.create.foundation.gui.AllIcons
import de.devin.cbbees.CreateBuzzyBeez
import net.createmod.catnip.gui.element.DelegatedStencilElement
import net.createmod.catnip.theme.Color
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.joml.Matrix4f

@OnlyIn(Dist.CLIENT)
class AllCCRIcons(val iconX: Int, val iconY: Int) : AllIcons(iconX, iconY) {

    companion object {
        @JvmField
        val ICON_ATLAS = CreateBuzzyBeez.asResource("textures/gui/icons.png")

        const val ICON_ATLAS_SIZE = 256

        val LP_PICK_UP = AllCCRIcons(0, 0)

        val LP_DROP_OFF = AllCCRIcons(16, 0)

        val LP_FORCE_PICKUP = AllCCRIcons(32, 0)

        val COPY = AllCCRIcons(48, 0)

        val CUT = AllCCRIcons(64, 0)

        @JvmField
        val PROGRAM = AllCCRIcons(80, 0)
    }

    override fun render(graphics: GuiGraphics, x: Int, y: Int) {
        graphics.blit(ICON_ATLAS, x, y, 0, iconX.toFloat(), iconY.toFloat(), 16, 16, 256, 256)
    }

    @OnlyIn(Dist.CLIENT)
    override fun bind() {
        RenderSystem.setShaderTexture(0, ICON_ATLAS)
    }


    @OnlyIn(Dist.CLIENT)
    override fun render(ms: PoseStack, buffer: MultiBufferSource, color: Int) {
        val builder = buffer.getBuffer(RenderType.text(ICON_ATLAS))
        val matrix = ms.last().pose()
        val rgb = Color(color)
        val light = LightTexture.FULL_BRIGHT

        val vec1 = Vec3(0.0, 0.0, 0.0)
        val vec2 = Vec3(0.0, 1.0, 0.0)
        val vec3 = Vec3(1.0, 1.0, 0.0)
        val vec4 = Vec3(1.0, 0.0, 0.0)

        val u1 = iconX * 1f / ICON_ATLAS_SIZE
        val u2 = (iconX + 16) * 1f / ICON_ATLAS_SIZE
        val v1 = iconY * 1f / ICON_ATLAS_SIZE
        val v2 = (iconY + 16) * 1f / ICON_ATLAS_SIZE

        vertex(builder, matrix, vec1, rgb, u1, v1, light)
        vertex(builder, matrix, vec2, rgb, u1, v2, light)
        vertex(builder, matrix, vec3, rgb, u2, v2, light)
        vertex(builder, matrix, vec4, rgb, u2, v1, light)
    }

    @OnlyIn(Dist.CLIENT)
    private fun vertex(
        builder: VertexConsumer,
        matrix: Matrix4f,
        vec: Vec3,
        rgb: Color,
        u: Float,
        v: Float,
        light: Int
    ) {
        builder.addVertex(matrix, vec.x.toFloat(), vec.y.toFloat(), vec.z.toFloat())
            .setColor(rgb.red, rgb.green, rgb.blue, 255)
            .setUv(u, v)
            .setLight(light)
    }

    @OnlyIn(Dist.CLIENT)
    override fun asStencil(): DelegatedStencilElement {
        return DelegatedStencilElement().withStencilRenderer<DelegatedStencilElement> { ms: GuiGraphics?, w: Int, h: Int, alpha: Float ->
            this.render(
                ms!!,
                0,
                0
            )
        }.withBounds(16, 16)
    }

}