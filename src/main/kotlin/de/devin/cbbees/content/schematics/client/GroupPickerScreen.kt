package de.devin.cbbees.content.schematics.client

import de.devin.cbbees.icons.AllCCRGuiTextures
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.lwjgl.glfw.GLFW

/**
 * Popup for choosing an existing group path for a schematic or creating a new one.
 */
@OnlyIn(Dist.CLIENT)
class GroupPickerScreen(
    private val callback: (String) -> Unit,
    private val currentGroup: String = "",
    parentScreen: Screen? = null
) : PlannerDialog(Component.translatable("gui.cbbees.group_picker.title"), WIDTH, HEIGHT, parentScreen) {

    private class GroupEntry(val path: String, val label: Component, val depth: Int)

    private val entries = mutableListOf<GroupEntry>()
    private var selectedPath = currentGroup
    private var scroll = 0
    private var draggingScrollbar = false
    private lateinit var newGroupField: EditBox

    private val listLeft get() = left + MARGIN
    private val listTop get() = top + 32
    private val rowsLeft get() = listLeft + 2
    private val rowsTop get() = listTop + 2
    private val rowsWidth get() = WIDTH - MARGIN * 2 - 10
    private val trackLeft get() = rowsLeft + rowsWidth + 2

    override fun init() {
        super.init()
        entries.clear()
        entries += GroupEntry("", Component.translatable("gui.cbbees.group_picker.root"), 0)
        SchematicGroupManager.getAllGroupPaths().sorted().forEach { path ->
            entries += GroupEntry(path, Component.literal(path.substringAfterLast('/')), path.count { it == '/' })
        }
        scroll = scroll.coerceIn(0, maxScroll())

        val previous = if (::newGroupField.isInitialized) newGroupField.value else ""
        newGroupField = addInput(left + MARGIN, top + 140, WIDTH - MARGIN * 2, Component.translatable("gui.cbbees.group_picker.new_group"))
        newGroupField.setMaxLength(200)
        newGroupField.setHint(Component.translatable("gui.cbbees.group_picker.new_group_hint"))
        newGroupField.value = previous

        addDialogButtons(Component.translatable("gui.cbbees.group_picker.confirm"), ::confirm)
    }

    private fun maxScroll() = (entries.size - VISIBLE_ROWS).coerceAtLeast(0)

    private fun visibleEntries() = entries.drop(scroll).take(VISIBLE_ROWS)

    private fun entryAt(mouseX: Double, mouseY: Double): GroupEntry? {
        if (mouseX < rowsLeft || mouseX >= rowsLeft + rowsWidth || mouseY < rowsTop || mouseY >= rowsTop + VISIBLE_ROWS * ROW_HEIGHT) return null
        return visibleEntries().getOrNull(((mouseY - rowsTop) / ROW_HEIGHT).toInt())
    }

    private fun overTrack(mouseX: Double, mouseY: Double) =
        mouseX >= trackLeft && mouseX < trackLeft + 4 && mouseY >= rowsTop && mouseY < rowsTop + VISIBLE_ROWS * ROW_HEIGHT

    private fun scrollTo(mouseY: Double) {
        val progress = ((mouseY - rowsTop) / (VISIBLE_ROWS * ROW_HEIGHT)).coerceIn(0.0, 1.0)
        scroll = (progress * maxScroll() + 0.5).toInt()
    }

    private fun confirm() {
        val newGroup = newGroupField.value.trim().trim('/')
        callback(newGroup.ifEmpty { selectedPath })
        onClose()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawString(font, Component.translatable("gui.cbbees.group_picker.existing"), left + MARGIN, top + 22, 0xAAAAAA, false)

        AllCCRGuiTextures.PLANNER_PANEL.renderNineSlice(guiGraphics, listLeft, listTop, WIDTH - MARGIN * 2, VISIBLE_ROWS * ROW_HEIGHT + 4, 2)
        AllCCRGuiTextures.PLANNER_SCROLL_TRACK.renderNineSlice(guiGraphics, trackLeft, rowsTop, 4, VISIBLE_ROWS * ROW_HEIGHT, 0, 2)

        val hovered = entryAt(mouseX.toDouble(), mouseY.toDouble())
        visibleEntries().forEachIndexed { i, entry ->
            val y = rowsTop + i * ROW_HEIGHT
            val selected = entry.path == selectedPath && newGroupField.value.isBlank()
            when {
                selected -> AllCCRGuiTextures.PLANNER_ROW_SELECTED.renderNineSlice(guiGraphics, rowsLeft, y, rowsWidth, ROW_HEIGHT, 2)
                entry === hovered -> AllCCRGuiTextures.PLANNER_ROW_HOVER.renderNineSlice(guiGraphics, rowsLeft, y, rowsWidth, ROW_HEIGHT, 2)
            }
            val iconX = rowsLeft + 2 + entry.depth * INDENT
            AllCCRGuiTextures.PLANNER_ICON_GROUP.render(guiGraphics, iconX, y + 2)
            val color = when {
                selected -> 0xFFFFFF
                entry.path.isEmpty() -> 0xAAAAAA
                else -> 0x88CCFF
            }
            val textX = iconX + 14
            AbstractWidget.renderScrollingString(guiGraphics, font, entry.label, textX, textX, y, rowsLeft + rowsWidth - 2, y + ROW_HEIGHT, color)
        }

        if (maxScroll() > 0) {
            val thumb = AllCCRGuiTextures.PLANNER_SCROLL_THUMB
            thumb.render(guiGraphics, trackLeft, rowsTop + (VISIBLE_ROWS * ROW_HEIGHT - thumb.height) * scroll / maxScroll())
        }

        drawDivider(guiGraphics, top + 120)
        guiGraphics.drawString(font, Component.translatable("gui.cbbees.group_picker.or_create"), left + MARGIN, top + 128, 0xAAAAAA, false)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true
        if (button == 0 && overTrack(mouseX, mouseY)) {
            draggingScrollbar = true
            scrollTo(mouseY)
            return true
        }
        val entry = entryAt(mouseX, mouseY) ?: return false
        val wasSelected = entry.path == selectedPath && newGroupField.value.isBlank()
        selectedPath = entry.path
        newGroupField.value = ""
        if (wasSelected) confirm()
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingScrollbar = false
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingScrollbar) {
            scrollTo(mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scroll = (scroll - scrollY.toInt()).coerceIn(0, maxScroll())
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        private const val WIDTH = 220
        private const val HEIGHT = 200
        private const val ROW_HEIGHT = 16
        private const val VISIBLE_ROWS = 5
        private const val INDENT = 8
    }
}
