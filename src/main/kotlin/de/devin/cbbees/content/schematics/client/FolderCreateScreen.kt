package de.devin.cbbees.content.schematics.client

import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.lwjgl.glfw.GLFW

@OnlyIn(Dist.CLIENT)
class FolderCreateScreen(
    private val parentFolder: String,
    parentScreen: Screen? = null
) : PlannerDialog(Component.translatable("gui.cbbees.folder_create.title"), WIDTH, 80, parentScreen) {

    private lateinit var nameField: EditBox
    private lateinit var confirmButton: PlannerButton

    private val path get() = SchematicGroupManager.normalize("$parentFolder/${nameField.value}")

    override fun init() {
        super.init()
        val previous = if (::nameField.isInitialized) nameField.value else ""
        nameField = addInput(left + MARGIN, top + 26, WIDTH - MARGIN * 2, Component.translatable("gui.cbbees.folder_create.field"))
        nameField.setMaxLength(200)
        nameField.setHint(Component.translatable("gui.cbbees.folder_create.hint"))
        nameField.value = previous
        nameField.setResponder { updateConfirmButton() }
        setInitialFocus(nameField)
        confirmButton = addDialogButtons(Component.translatable("gui.cbbees.folder_create.confirm"), ::create)
        updateConfirmButton()
    }

    private fun updateConfirmButton() {
        confirmButton.active = path != SchematicGroupManager.normalize(parentFolder) && path !in SchematicGroupManager.getAllGroupPaths()
    }

    private fun create() {
        SchematicGroupManager.createFolder(path)
        onClose()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (confirmButton.active) create()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    companion object {
        private const val WIDTH = 220
    }
}
