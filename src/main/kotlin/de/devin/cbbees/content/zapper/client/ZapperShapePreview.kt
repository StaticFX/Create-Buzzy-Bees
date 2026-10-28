package de.devin.cbbees.content.zapper.client

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Axis
import de.devin.cbbees.content.schematics.client.GhostSchematicRenderer
import de.devin.cbbees.content.zapper.shape.ShapeSettings
import de.devin.cbbees.content.zapper.shape.SolidShape
import de.devin.cbbees.content.zapper.shape.ZapperShape
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.levelWrappers.SchematicLevel
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@OnlyIn(Dist.CLIENT)
class ZapperShapePreview {

    private data class Key(val shape: ZapperShape, val settings: ShapeSettings, val block: BlockState)

    private sealed interface Content
    private class Ready(val renderer: GhostSchematicRenderer, val size: Vec3i) : Content
    private class Message(val text: Component) : Content

    private var shown: Key? = null
    private var content: Content? = null
    private var pending: Key? = null
    private var changedAt = 0L

    fun render(graphics: GuiGraphics, shape: ZapperShape, settings: ShapeSettings, block: BlockState, x: Int, y: Int, w: Int, h: Int) {
        val key = Key(shape, settings.clampedTo(shape), block)
        if (key != shown) {
            if (key != pending) {
                pending = key
                changedAt = Util.getMillis()
            }
            if (content == null || Util.getMillis() - changedAt >= REBUILD_DELAY_MS) {
                content = build(key)
                shown = key
                pending = null
            }
        }
        when (val current = content) {
            is Ready -> renderShape(graphics, current, x, y, w, h)
            is Message -> renderMessage(graphics, current.text, x, y, w, h)
            null -> {}
        }
    }

    private fun build(key: Key): Content {
        val shape = key.shape as? SolidShape ?: return Message(Component.translatable("gui.cbbees.zapper.preview.terrain"))
        val offsets = shape.offsets(key.settings, Direction.NORTH)
        if (offsets.isEmpty()) return Message(Component.translatable("gui.cbbees.zapper.preview.empty"))
        val all = offsets.toHashSet()
        val shell = offsets.filter { pos -> Direction.entries.any { pos.relative(it) !in all } }
        if (shell.size > MAX_BLOCKS) return Message(Component.translatable("gui.cbbees.zapper.preview.too_large"))

        val bounds = BoundingBox.encapsulatingPositions(shell).orElseThrow()
        val min = BlockPos(bounds.minX(), bounds.minY(), bounds.minZ())
        val level = SchematicLevel(Minecraft.getInstance().level ?: error("No client level"))
        shell.forEach { level.setBlock(it.subtract(min), key.block, Block.UPDATE_CLIENTS) }
        level.blockEntities.forEach { it.setLevel(level) }
        val renderer = GhostSchematicRenderer(level, renderAllBlockEntities = true).also { it.prebuildGeometry() }
        return Ready(renderer, Vec3i(bounds.xSpan, bounds.ySpan, bounds.zSpan))
    }

    private fun renderShape(graphics: GuiGraphics, ready: Ready, x: Int, y: Int, w: Int, h: Int) {
        val size = ready.size
        val pitch = Math.toRadians(PITCH.toDouble())
        val spread = sqrt((size.x * size.x + size.z * size.z).toDouble())
        val height = size.y * cos(pitch) + spread * sin(pitch)
        val scale = (min(w / spread, h / height) * FILL).toFloat()

        graphics.enableScissor(x, y, x + w, y + h)
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
        ready.renderer.render(pose, buffers)
        buffers.draw()
        pose.popPose()
        graphics.disableScissor()
    }

    private fun renderMessage(graphics: GuiGraphics, text: Component, x: Int, y: Int, w: Int, h: Int) {
        val font = Minecraft.getInstance().font
        val lines = font.split(text, w - 12)
        lines.forEachIndexed { i, line ->
            graphics.drawCenteredString(font, line, x + w / 2, y + h / 2 - lines.size * 5 + i * 10, 0xAAAAAA)
        }
    }

    private companion object {
        const val PITCH = 30f
        const val SPIN_SPEED = 1.5f
        const val FILL = 0.85
        const val MAX_BLOCKS = 40_000
        const val REBUILD_DELAY_MS = 150L
    }
}
