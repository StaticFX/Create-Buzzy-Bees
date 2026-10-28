package de.devin.cbbees.content.schematics.client

import de.devin.cbbees.icons.AllCCRGuiTextures
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

@OnlyIn(Dist.CLIENT)
class PlannerButton(x: Int, y: Int, message: Component, private val action: () -> Unit) :
    AbstractButton(x, y, AllCCRGuiTextures.PLANNER_BUTTON.width, AllCCRGuiTextures.PLANNER_BUTTON.height, message) {

    override fun onPress() = action()

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val texture = when {
            !active -> AllCCRGuiTextures.PLANNER_BUTTON_DISABLED
            isHovered -> AllCCRGuiTextures.PLANNER_BUTTON_HOVER
            else -> AllCCRGuiTextures.PLANNER_BUTTON
        }
        texture.render(graphics, x, y)
        val font = Minecraft.getInstance().font
        graphics.drawCenteredString(font, message, x + width / 2, y + (height - 8) / 2, if (active) 0xFFFFFF else 0xA0A0A0)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
}

@OnlyIn(Dist.CLIENT)
class PlannerIconButton(
    x: Int,
    y: Int,
    private val glyph: AllCCRGuiTextures,
    tooltip: Component,
    private val action: () -> Unit,
) : AbstractButton(x, y, glyph.width, glyph.height, tooltip) {

    init {
        setTooltip(Tooltip.create(tooltip))
    }

    override fun onPress() = action()

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val background = when {
            !active -> AllCCRGuiTextures.PLANNER_ICON_BUTTON_DISABLED
            isHovered -> AllCCRGuiTextures.PLANNER_ICON_BUTTON_HOVER
            else -> AllCCRGuiTextures.PLANNER_ICON_BUTTON
        }
        background.render(graphics, x, y)
        glyph.render(graphics, x, y)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
}
