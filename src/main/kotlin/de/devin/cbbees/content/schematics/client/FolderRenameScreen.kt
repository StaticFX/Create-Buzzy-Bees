package de.devin.cbbees.content.schematics.client

import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.lwjgl.glfw.GLFW

@OnlyIn(Dist.CLIENT)
class FolderRenameScreen(
    private val folder: String,
    parentScreen: Screen? = null
) : PlannerDialog(Component.translatable("gui.cbbees.folder_rename.title"), WIDTH, 80, parentScreen) {

    private lateinit var nameField: EditBox
    private lateinit var confirmButton: PlannerButton

    private val newName get() = nameField.value.replace('/', ' ').trim()
    private val newPath get() = SchematicGroupManager.normalize("${folder.substringBeforeLast('/', "")}/$newName")

    override fun init() {
        super.init()
        val previous = if (::nameField.isInitialized) nameField.value else folder.substringAfterLast('/')
        nameField = addInput(left + MARGIN, top + 26, WIDTH - MARGIN * 2, Component.translatable("gui.cbbees.schematic_rename.field"))
        nameField.setMaxLength(200)
        nameField.value = previous
        nameField.setResponder { updateConfirmButton() }
        setInitialFocus(nameField)
        confirmButton = addDialogButtons(Component.translatable("gui.cbbees.schematic_rename.confirm"), ::rename)
        updateConfirmButton()
    }

    private fun updateConfirmButton() {
        confirmButton.active = newName.isNotEmpty() && newPath !in SchematicGroupManager.getAllGroupPaths()
    }

    private fun rename() {
        SchematicGroupManager.renameFolder(folder, newName)
        onClose()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (confirmButton.active) rename()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    companion object {
        private const val WIDTH = 220
    }
}
