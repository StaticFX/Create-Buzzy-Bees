package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import de.devin.cbbees.content.schematics.BuildMode
import de.devin.cbbees.content.schematics.BuildModes
import de.devin.cbbees.icons.AllCCRIcons
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

@ClientSide
object BuildModeBar {
    private const val HEIGHT = 30
    private const val IN_THE_WAY_COLOR = 0xFFAA33

    private class Entry(val key: String, val icon: AllIcons, val color: Int, val mode: BuildMode?)

    private val entries = listOf(
        Entry("back", AllCCRIcons.BACK, 0xCCDDFF, null),
        Entry("replace", AllCCRIcons.REPLACE, 0x88FF88, BuildModes.REPLACE),
        Entry("keep", AllCCRIcons.KEEP, 0x88CCFF, BuildModes.KEEP),
        Entry("clear", AllCCRIcons.CLEAR, 0xFFAA88, BuildModes.CLEAR),
    )

    private var lastMode = BuildModes.REPLACE
    private var selection = 1
    private var yOffset = 0f

    @JvmStatic
    fun open() {
        selection = entries.indexOfFirst { it.mode == lastMode }
        yOffset = 0f
    }

    @JvmStatic
    fun cycle(direction: Int) {
        selection = (selection + if (direction < 0) 1 else -1).mod(entries.size)
    }

    fun selected(): BuildMode? = entries[selection].mode?.also { lastMode = it }

    @JvmStatic
    fun update() {
        yOffset = if (ConstructPrompt.isOpen) yOffset + (10 - yOffset) * .1f else 0f
    }

    @JvmStatic
    fun render(graphics: GuiGraphics) {
        val mc = Minecraft.getInstance()
        val font = mc.font
        val width = entries.size * 50 + 30
        val screenWidth = graphics.guiWidth()
        val x = (screenWidth - width) / 2 + 15
        val y = graphics.guiHeight() - HEIGHT - 75
        val pose = graphics.pose()

        pose.pushPose()
        pose.translate(0f, -yOffset, 100f)

        val background = AllGuiTextures.HUD_BACKGROUND
        RenderSystem.enableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, 7 / 8f)
        graphics.blit(background.location, x - 15, y, background.startX.toFloat(), background.startY.toFloat(), width, HEIGHT, background.width, background.height)

        val tooltipAlpha = yOffset / 10
        if (tooltipAlpha > 0.25f) {
            val alpha = (tooltipAlpha * 0xFF).toInt() shl 24
            val key = entries[selection].key
            RenderSystem.setShaderColor(.7f, .7f, .8f, tooltipAlpha)
            graphics.blit(background.location, x - 15, y + 33, background.startX.toFloat(), background.startY.toFloat(), width, HEIGHT + 10, background.width, background.height)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            graphics.drawString(font, Component.translatable("gui.cbbees.build_mode.$key"), x - 10, y + 38, 0xEEEEEE + alpha, false)
            graphics.drawWordWrap(font, Component.translatable("gui.cbbees.build_mode.$key.desc"), x - 10, y + 50, width - 10, 0xCCDDFF + alpha)
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        graphics.drawCenteredString(font, Component.translatable("gui.cbbees.build_mode.hint"), screenWidth / 2, y - 10, 0xCCDDFF)
        graphics.drawCenteredString(font, Component.translatable("gui.cbbees.build_mode.in_the_way", ConstructPrompt.blocksInTheWay), screenWidth / 2, y - 22, IN_THE_WAY_COLOR)

        entries.forEachIndexed { i, entry ->
            pose.pushPose()
            var alpha = .6f
            if (i == selection) {
                pose.translate(0f, -10f, 0f)
                graphics.drawCenteredString(font, Component.translatable("gui.cbbees.build_mode.${entry.key}"), x + i * 50 + 24, y + 28, entry.color)
                alpha = 1f
            }
            RenderSystem.enableBlend()
            RenderSystem.setShaderColor(0f, 0f, 0f, alpha)
            entry.icon.render(graphics, x + i * 50 + 16, y + 12)
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha)
            entry.icon.render(graphics, x + i * 50 + 16, y + 11)
            pose.popPose()
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
        pose.popPose()
    }
}
