package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.lwjgl.glfw.GLFW
import java.nio.file.Files

@OnlyIn(Dist.CLIENT)
class SchematicRenameScreen(
    private val filename: String,
    parentScreen: Screen? = null
) : PlannerDialog(Component.translatable("gui.cbbees.schematic_rename.title"), WIDTH, 80, parentScreen) {

    private lateinit var nameField: EditBox
    private lateinit var confirmButton: PlannerButton

    override fun init() {
        super.init()
        val previous = if (::nameField.isInitialized) nameField.value else filename.removeSuffix(".nbt")
        nameField = addInput(left + MARGIN, top + 26, WIDTH - MARGIN * 2, Component.translatable("gui.cbbees.schematic_rename.field"))
        nameField.setMaxLength(200)
        nameField.value = previous
        nameField.setResponder { updateConfirmButton() }
        setInitialFocus(nameField)
        confirmButton = addDialogButtons(Component.translatable("gui.cbbees.schematic_rename.confirm"), ::doRename)
        updateConfirmButton()
    }

    private fun updateConfirmButton() {
        val newName = nameField.value.trim()
        confirmButton.active = newName.isNotEmpty() && "$newName.nbt" != filename
    }

    private fun doRename() {
        val newName = nameField.value.trim()
        if (newName.isEmpty()) return

        val newFilename = "$newName.nbt"
        if (newFilename == filename) return

        val oldPath = CreatePaths.SCHEMATICS_DIR.resolve(filename)
        val newPath = CreatePaths.SCHEMATICS_DIR.resolve(newFilename)

        if (Files.exists(newPath)) return // don't overwrite existing

        try {
            Files.move(oldPath, newPath)
            SchematicGroupManager.renameEntry(filename, newFilename)
            CreateClient.SCHEMATIC_SENDER.refresh()
        } catch (e: Exception) {
            CreateBuzzyBeez.LOGGER.error("Failed to rename schematic $filename to $newFilename", e)
        }

        onClose()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (confirmButton.active) doRename()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    companion object {
        private const val WIDTH = 220
    }
}
