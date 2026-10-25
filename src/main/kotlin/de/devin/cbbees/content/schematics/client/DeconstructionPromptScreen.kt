package de.devin.cbbees.content.schematics.client

import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import de.devin.cbbees.content.planner.client.AreaSelection
import de.devin.cbbees.network.StartDeconstructionPacket
import net.createmod.catnip.gui.AbstractSimiScreen
import net.createmod.catnip.platform.CatnipServices
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/**
 * Screen displayed after selecting an area with the Deconstruction Planner.
 *
 * Shows the selected area dimensions and provides buttons to:
 * - Start deconstruction (sends packet to server)
 * - Cancel/discard the selection
 *
 * Similar to Create's SchematicPromptScreen but themed in red for deconstruction.
 */
class DeconstructionPromptScreen : AbstractSimiScreen(
    Component.translatable("cbbees.deconstruction.title")
) {

    private val background = AllGuiTextures.SCHEMATIC_PROMPT

    private val startLabel = Component.translatable("cbbees.deconstruction.start")
    private val cancelLabel = Component.translatable("action.discard")

    private var startButton: IconButton? = null
    private var cancelButton: IconButton? = null

    override fun init() {
        setWindowSize(background.width, background.height)
        super.init()

        val x = guiLeft
        val y = guiTop + 2

        // Cancel button (left side)
        cancelButton = IconButton(x + 7, y + 53, AllIcons.I_TRASH).also { btn ->
            btn.setToolTip(cancelLabel)
            addRenderableWidget(btn)
        }

        // Start deconstruction button (right side)
        startButton = IconButton(x + 158, y + 53, AllIcons.I_CONFIRM).also { btn ->
            btn.setToolTip(startLabel)
            addRenderableWidget(btn)
        }
    }

    override fun renderWindow(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val x = guiLeft
        val y = guiTop

        // Render background
        background.render(graphics, x, y)

        // Render title (centered)
        graphics.drawString(
            font,
            title,
            x + (background.width - 8 - font.width(title)) / 2,
            y + 4,
            0x505050,
            false
        )

        // Render area info
        val first = AreaSelection.firstPos
        val second = AreaSelection.secondPos

        if (first != null && second != null) {
            val minX = minOf(first.x, second.x)
            val minY = minOf(first.y, second.y)
            val minZ = minOf(first.z, second.z)
            val maxX = maxOf(first.x, second.x)
            val maxY = maxOf(first.y, second.y)
            val maxZ = maxOf(first.z, second.z)

            val sizeX = maxX - minX + 1
            val sizeY = maxY - minY + 1
            val sizeZ = maxZ - minZ + 1
            val totalBlocks = sizeX * sizeY * sizeZ

            // Draw dimensions info
            val dimensionsText = Component.translatable(
                "cbbees.deconstruction.area_info",
                sizeX, sizeY, sizeZ, totalBlocks
            )
            graphics.drawString(
                font,
                dimensionsText,
                x + 10,
                y + 26,
                0xc56868,  // Red color for deconstruction
                false
            )

            // Draw position info
            val posText = Component.translatable(
                "cbbees.deconstruction.position_info",
                minX, minY, minZ
            )
            graphics.drawString(
                font,
                posText,
                x + 10,
                y + 38,
                0x606060,
                false
            )
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Check if cancel button was clicked
        cancelButton?.let { btn ->
            if (btn.isMouseOver(mouseX, mouseY)) {
                AreaSelection.discard()
                onClose()
                return true
            }
        }

        // Check if start button was clicked
        startButton?.let { btn ->
            if (btn.isMouseOver(mouseX, mouseY)) {
                startDeconstruction()
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Enter key starts deconstruction
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            startDeconstruction()
            return true
        }

        // Escape closes the screen
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
            onClose()
            return true
        }

        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    /**
     * Starts the deconstruction process by sending a packet to the server.
     */
    private fun startDeconstruction() {
        val first = AreaSelection.firstPos
        val second = AreaSelection.secondPos

        if (first != null && second != null) {
            // Send packet to server to start deconstruction
            CatnipServices.NETWORK.sendToServer(StartDeconstructionPacket(first, second))

            // Clear the selection
            AreaSelection.discard()
        }

        onClose()
    }
}
