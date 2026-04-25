package de.devin.cbbees.content.schematics.client

import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.createmod.catnip.gui.AbstractSimiScreen
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.lwjgl.glfw.GLFW

/**
 * Popup screen for selecting or creating a group path for a schematic.
 * Styled to match the Schematic Deployer screen.
 */
@OnlyIn(Dist.CLIENT)
class GroupPickerScreen(
    private val callback: (String) -> Unit,
    private val currentGroup: String = "",
    private val parentScreen: Screen? = null
) : AbstractSimiScreen(Component.translatable("gui.cbbees.group_picker.title")) {

    companion object {
        private const val PANEL_W = 220
        private const val PANEL_H = 210
        private const val MARGIN = 10

        private const val BG_PANEL = 0xCC2A2A2A.toInt()
        private const val BG_TITLE = 0xFF3A3A3A.toInt()
        private const val BORDER = 0xFF555555.toInt()
        private const val GOLD = 0xFFD4AA00.toInt()
        private const val LABEL = 0xAAAAAA
    }

    private var groupList: GroupSelectionList? = null
    private var newGroupField: EditBox? = null
    private var selectedPath: String = currentGroup
    private var confirmButton: IconButton? = null
    private var cancelButton: IconButton? = null

    override fun init() {
        setWindowSize(PANEL_W, PANEL_H)
        super.init()

        val x = guiLeft
        val y = guiTop
        val innerLeft = x + MARGIN
        val innerWidth = PANEL_W - MARGIN * 2

        val listTop = y + 38
        val listHeight = 94
        groupList = GroupSelectionList(minecraft!!, innerWidth, listHeight, listTop, 16, innerLeft)
        addWidget(groupList!!)

        groupList!!.addGroupEntry(GroupEntry("", Component.translatable("gui.cbbees.group_picker.root")))
        for (path in SchematicGroupManager.getAllGroupPaths().sorted()) {
            val depth = path.count { it == '/' }
            val indent = "  ".repeat(depth)
            val displayName = path.substringAfterLast("/")
            groupList!!.addGroupEntry(GroupEntry(path, Component.literal("$indent$displayName")))
        }
        groupList!!.children().find { it.path == currentGroup }?.let { groupList!!.selected = it }

        val fieldY = y + PANEL_H - 46
        newGroupField = EditBox(font, innerLeft, fieldY, innerWidth, 16,
            Component.translatable("gui.cbbees.group_picker.new_group"))
        newGroupField!!.setMaxLength(200)
        newGroupField!!.value = ""
        newGroupField!!.setHint(Component.translatable("gui.cbbees.group_picker.new_group_hint"))
        addRenderableWidget(newGroupField!!)

        confirmButton = IconButton(x + PANEL_W - 25, y + PANEL_H - 25, AllIcons.I_CONFIRM).also {
            it.setToolTip(Component.translatable("gui.cbbees.group_picker.confirm"))
            addRenderableWidget(it)
        }
        cancelButton = IconButton(x + 5, y + PANEL_H - 25, AllIcons.I_DISABLE).also {
            it.setToolTip(Component.translatable("gui.cbbees.group_picker.cancel"))
            addRenderableWidget(it)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        confirmButton?.let { if (it.isMouseOver(mouseX, mouseY)) { confirm(); return true } }
        cancelButton?.let { if (it.isMouseOver(mouseX, mouseY)) { onClose(); return true } }
        groupList?.let { if (it.mouseClicked(mouseX, mouseY, button)) return true }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        groupList?.let { if (it.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        groupList?.let { if (it.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        groupList?.let { if (it.mouseReleased(mouseX, mouseY, button)) return true }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm(); return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun confirm() {
        val newGroup = newGroupField?.value?.trim() ?: ""
        val result = if (newGroup.isNotEmpty()) newGroup.trim('/') else selectedPath
        callback(result)
        minecraft?.setScreen(parentScreen)
    }

    override fun onClose() {
        minecraft?.setScreen(parentScreen)
    }

    override fun renderWindow(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val x = guiLeft
        val y = guiTop
        val w = windowWidth
        val h = windowHeight

        graphics.fill(x, y, x + w, y + h, BG_PANEL)
        graphics.fill(x, y, x + w, y + 1, BORDER)
        graphics.fill(x, y + h - 1, x + w, y + h, BORDER)
        graphics.fill(x, y, x + 1, y + h, BORDER)
        graphics.fill(x + w - 1, y, x + w, y + h, BORDER)

        graphics.fill(x + 1, y + 1, x + w - 1, y + 18, BG_TITLE)
        graphics.drawCenteredString(font, title, x + w / 2, y + 5, GOLD)

        graphics.drawString(font, Component.translatable("gui.cbbees.group_picker.existing"),
            x + MARGIN, y + 22, LABEL, false)

        groupList?.render(graphics, mouseX, mouseY, partialTicks)

        val sepY = y + PANEL_H - 62
        graphics.fill(x + MARGIN, sepY, x + w - MARGIN, sepY + 1, BORDER)

        graphics.drawString(font, Component.translatable("gui.cbbees.group_picker.or_create"),
            x + MARGIN, sepY + 3, LABEL, false)
    }

    override fun isPauseScreen(): Boolean = false

    inner class GroupEntry(val path: String, private val display: Component) :
        ObjectSelectionList.Entry<GroupEntry>() {

        override fun getNarration(): Component = display

        override fun render(
            guiGraphics: GuiGraphics, index: Int,
            top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float
        ) {
            val selected = groupList?.selected === this
            val list = groupList ?: return
            val hlLeft = list.getRowLeft()
            val hlRight = hlLeft + list.getRowWidth() + 12

            if (selected) {
                guiGraphics.fill(hlLeft, top, hlRight, top + height, 0x50FFFFFF)
            } else if (hovered) {
                guiGraphics.fill(hlLeft, top, hlRight, top + height, 0x20FFFFFF)
            }

            val color = when {
                selected -> 0xFFFF00
                path.isEmpty() -> LABEL
                hovered -> 0xFFFFFF
                else -> 0xCCCCCC
            }
            guiGraphics.drawString(font, display, left + 2, top + 3, color, false)
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            groupList?.setSelected(this)
            selectedPath = path
            newGroupField?.value = ""
            return true
        }
    }

    inner class GroupSelectionList(
        mc: Minecraft, width: Int, height: Int, top: Int, itemHeight: Int,
        private val listLeft: Int
    ) : ObjectSelectionList<GroupEntry>(mc, width, height, top, itemHeight) {

        override fun getRowLeft(): Int = listLeft
        override fun getRowWidth(): Int = this.width - 12
        override fun isSelectedItem(index: Int): Boolean = false

        fun addGroupEntry(entry: GroupEntry): Int = super.addEntry(entry)

        override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            guiGraphics.enableScissor(listLeft, this.y, listLeft + this.width, this.y + this.height)
            guiGraphics.fill(listLeft, this.y, listLeft + this.width, this.y + this.height, 0x40000000)
            renderListItems(guiGraphics, mouseX, mouseY, partialTick)
            guiGraphics.disableScissor()
        }
    }
}
