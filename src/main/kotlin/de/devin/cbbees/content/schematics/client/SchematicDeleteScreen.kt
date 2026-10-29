package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.icons.AllCCRGuiTextures
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import java.nio.file.Files

@OnlyIn(Dist.CLIENT)
class SchematicDeleteScreen(
    private val filenames: List<String>,
    parentScreen: Screen? = null
) : PlannerDialog(Component.translatable("gui.cbbees.schematic_delete.title"), WIDTH, 96, parentScreen) {

    override fun init() {
        super.init()
        addDialogButtons(Component.translatable("gui.cbbees.schematic_delete.confirm"), ::doDelete)
    }

    private fun doDelete() {
        filenames.forEach { filename ->
            try {
                Files.deleteIfExists(CreatePaths.SCHEMATICS_DIR.resolve(filename))
                SchematicGroupManager.removeGroup(filename)
            } catch (e: Exception) {
                CreateBuzzyBeez.LOGGER.error("Failed to delete schematic $filename", e)
            }
        }
        CreateClient.SCHEMATIC_SENDER.refresh()

        onClose()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        AllCCRGuiTextures.DIALOG_WARNING.render(guiGraphics, left + MARGIN, top + 26)

        val textX = left + MARGIN + 22
        val textWidth = WIDTH - MARGIN * 2 - 22
        val text = filenames.singleOrNull()
            ?.let { Component.translatable("gui.cbbees.schematic_delete.message", it.removeSuffix(".nbt")) }
            ?: Component.translatable("gui.cbbees.schematic_delete.message_many", filenames.size)
        val message = font.split(text, textWidth)
        val hint = font.split(Component.translatable("gui.cbbees.schematic_delete.hint"), textWidth)
        var y = top + 24
        message.forEach { guiGraphics.drawString(font, it, textX, y, 0xFF5555); y += 10 }
        hint.forEach { guiGraphics.drawString(font, it, textX, y, 0xAAAAAA); y += 10 }
    }

    companion object {
        private const val WIDTH = 220
    }
}
