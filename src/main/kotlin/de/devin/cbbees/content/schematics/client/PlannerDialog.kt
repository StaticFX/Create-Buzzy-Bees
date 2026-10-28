package de.devin.cbbees.content.schematics.client

import de.devin.cbbees.icons.AllCCRGuiTextures
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

@OnlyIn(Dist.CLIENT)
abstract class PlannerDialog(
    title: Component,
    private val dialogWidth: Int,
    private val dialogHeight: Int,
    private val parentScreen: Screen?,
) : Screen(title) {

    protected var left = 0
        private set
    protected var top = 0
        private set

    private val inputFrames = mutableListOf<EditBox>()

    override fun init() {
        left = (width - dialogWidth) / 2
        top = (height - dialogHeight) / 2
        inputFrames.clear()
        super.init()
    }

    protected fun addInput(x: Int, y: Int, w: Int, narration: Component): EditBox {
        val field = EditBox(font, x + 3, y + 3, w - 6, INPUT_HEIGHT - 4, narration)
        field.setBordered(false)
        inputFrames += field
        return addRenderableWidget(field)
    }

    protected fun addDialogButtons(confirm: Component, onConfirm: () -> Unit): PlannerButton {
        val buttonWidth = AllCCRGuiTextures.PLANNER_BUTTON.width
        val y = top + dialogHeight - 24
        val confirmButton = addRenderableWidget(PlannerButton(left + dialogWidth / 2 - buttonWidth - 4, y, confirm, onConfirm))
        addRenderableWidget(PlannerButton(left + dialogWidth / 2 + 4, y, Component.translatable("gui.cbbees.group_picker.cancel"), ::onClose))
        return confirmButton
    }

    protected fun drawDivider(guiGraphics: GuiGraphics, y: Int) {
        AllCCRGuiTextures.DIALOG_DIVIDER.renderNineSlice(guiGraphics, left + MARGIN, y, dialogWidth - MARGIN * 2, AllCCRGuiTextures.DIALOG_DIVIDER.height, 2, 0)
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(guiGraphics)
        AllCCRGuiTextures.DIALOG.renderNineSlice(guiGraphics, left, top, dialogWidth, dialogHeight, 4)
        guiGraphics.drawCenteredString(font, title, left + dialogWidth / 2, top + 8, 0xFFFFFF)
        inputFrames.forEach {
            AllCCRGuiTextures.PLANNER_SEARCH.renderNineSlice(guiGraphics, it.x - 3, it.y - 3, it.width + 6, INPUT_HEIGHT, 2)
        }
    }

    override fun onClose() {
        minecraft?.setScreen(parentScreen)
    }

    companion object {
        const val MARGIN = 10
        const val INPUT_HEIGHT = 14
    }
}
