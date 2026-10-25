package de.devin.cbbees.content.planner.client

import com.mojang.blaze3d.systems.RenderSystem
import com.simibubi.create.AllKeys
import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.utility.CreateLang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

open class PlannerTool(val key: String, val icon: AllIcons, val color: Int) {
    val name: Component get() = Component.translatable("gui.cbbees.tool.$key")
    val description: Component get() = Component.translatable("gui.cbbees.tool.$key.desc")
}

class PlannerToolbar<T : PlannerTool>(private val tools: List<T>) {

    private var focused = false
    private var selection = 0
    private var yOffset = 0f

    val selected: T get() = tools[selection]

    fun onToolMenuKey(key: Int, action: Int): Boolean {
        if (!AllKeys.TOOL_MENU.doesModifierAndCodeMatch(key)) return false
        focused = action != GLFW.GLFW_RELEASE
        return true
    }

    fun onScroll(delta: Double): Boolean {
        if (!focused) return false
        selection = (selection + if (delta < 0) 1 else -1).mod(tools.size)
        return true
    }

    fun update() {
        yOffset = if (focused) yOffset + (10 - yOffset) * .1f else yOffset * .9f
    }

    fun reset() {
        focused = false
        yOffset = 0f
    }

    fun render(graphics: GuiGraphics, hints: List<Component>) {
        val font = Minecraft.getInstance().font
        val w = maxOf(tools.size * 50 + 30, 220)
        val h = 30
        val x = (graphics.guiWidth() - w) / 2 + 15
        val iconsX = graphics.guiWidth() / 2 - (tools.size - 1) * 25 - 24
        val y = graphics.guiHeight() - h - 75
        val gray = AllGuiTextures.HUD_BACKGROUND
        val pose = graphics.pose()

        pose.pushPose()
        pose.translate(0f, -yOffset, if (focused) 100f else 0f)
        RenderSystem.enableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, if (focused) 7 / 8f else 1 / 2f)
        graphics.blit(gray.location, x - 15, y, gray.startX.toFloat(), gray.startY.toFloat(), w, h, gray.width, gray.height)

        val tooltipAlpha = yOffset / 10
        if (tooltipAlpha > 0.25f) {
            val alpha = (tooltipAlpha * 0xFF).toInt() shl 24
            RenderSystem.setShaderColor(.7f, .7f, .8f, tooltipAlpha)
            graphics.blit(gray.location, x - 15, y + 33, gray.startX.toFloat(), gray.startY.toFloat(), w, h + 22, gray.width, gray.height)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            val lines = listOf(selected.name, selected.description) + hints
            lines.take(LINE_COLORS.size).forEachIndexed { i, line ->
                graphics.drawString(font, line, x - 10, y + 38 + i * 11, LINE_COLORS[i] + alpha, false)
            }
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        val prompt = if (focused) CreateLang.translateDirect("gui.toolmenu.cycle")
        else CreateLang.translateDirect("gui.toolmenu.focusKey", AllKeys.TOOL_MENU.boundKey)
        graphics.drawCenteredString(font, prompt, graphics.guiWidth() / 2, y - 10, 0xCCDDFF)

        tools.forEachIndexed { i, tool ->
            pose.pushPose()
            var alpha = if (focused) 1f else .2f
            if (i == selection) {
                pose.translate(0f, -10f, 0f)
                graphics.drawCenteredString(font, tool.name, iconsX + i * 50 + 24, y + 28, tool.color)
                alpha = 1f
            }
            RenderSystem.setShaderColor(0f, 0f, 0f, alpha)
            tool.icon.render(graphics, iconsX + i * 50 + 16, y + 12)
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha)
            tool.icon.render(graphics, iconsX + i * 50 + 16, y + 11)
            pose.popPose()
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
        pose.popPose()
    }

    private companion object {
        val LINE_COLORS = listOf(0xEEEEEE, 0xCCDDFF, 0xCCDDFF, 0xCCCCDD)
    }
}
