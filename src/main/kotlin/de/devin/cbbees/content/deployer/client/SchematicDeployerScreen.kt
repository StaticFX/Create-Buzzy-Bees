package de.devin.cbbees.content.deployer.client

import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import com.simibubi.create.foundation.gui.widget.Label
import com.simibubi.create.foundation.gui.widget.ScrollInput
import de.devin.cbbees.content.deployer.DeployMode
import de.devin.cbbees.content.deployer.SchematicDeployerBlockEntity
import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.network.DeployerSettingsPacket
import de.devin.cbbees.registry.AllDataComponents
import net.createmod.catnip.gui.AbstractSimiScreen
import net.createmod.catnip.platform.CatnipServices
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation

/**
 * Create-styled GUI for the Schematic Deployer with two tabs:
 * - Absolute: shows stored coordinates (read-only)
 * - Relative: shows editable X/Y/Z offset + rotation/mirror from the deployer
 */
class SchematicDeployerScreen(
    private val be: SchematicDeployerBlockEntity
) : AbstractSimiScreen(Component.translatable("block.cbbees.schematic_deployer")) {

    private val background = AllGuiTextures.SCHEMATIC

    private var currentMode: DeployMode = be.deployMode
    private var offsetX: Int = be.relativeOffset.x
    private var offsetY: Int = be.relativeOffset.y
    private var offsetZ: Int = be.relativeOffset.z
    private var currentRotation: Rotation = be.relativeRotation
    private var currentMirror: Mirror = be.relativeMirror

    private var absoluteButton: IconButton? = null
    private var relativeButton: IconButton? = null
    private var confirmButton: IconButton? = null

    private var scrollInputX: ScrollInput? = null
    private var scrollInputY: ScrollInput? = null
    private var scrollInputZ: ScrollInput? = null
    private var labelX: Label? = null
    private var labelY: Label? = null
    private var labelZ: Label? = null

    private var rotateButton: IconButton? = null
    private var mirrorButton: IconButton? = null

    override fun init() {
        setWindowSize(background.width + 50, background.height + 86)
        super.init()

        val x = guiLeft
        val y = guiTop

        // Tab buttons
        absoluteButton = IconButton(x + 7, y + 22, AllIcons.I_PLACEMENT_SETTINGS).also {
            it.setToolTip(Component.translatable("gui.cbbees.deployer.mode.absolute"))
            addRenderableWidget(it)
        }
        relativeButton = IconButton(x + 27, y + 22, AllIcons.I_TOOL_MOVE_XZ).also {
            it.setToolTip(Component.translatable("gui.cbbees.deployer.mode.relative"))
            addRenderableWidget(it)
        }

        // Confirm button
        confirmButton = IconButton(x + windowWidth - 25, y + windowHeight - 25, AllIcons.I_CONFIRM).also {
            it.setToolTip(Component.translatable("gui.cbbees.deployer.confirm"))
            addRenderableWidget(it)
        }

        updateTabHighlights()
        initRelativeWidgets()
    }

    private fun initRelativeWidgets() {
        // Remove old widgets
        scrollInputX?.let { removeWidget(it) }
        scrollInputY?.let { removeWidget(it) }
        scrollInputZ?.let { removeWidget(it) }
        labelX?.let { removeWidget(it) }
        labelY?.let { removeWidget(it) }
        labelZ?.let { removeWidget(it) }
        rotateButton?.let { removeWidget(it) }
        mirrorButton?.let { removeWidget(it) }

        if (currentMode != DeployMode.RELATIVE) {
            scrollInputX = null; scrollInputY = null; scrollInputZ = null
            labelX = null; labelY = null; labelZ = null
            rotateButton = null; mirrorButton = null
            return
        }

        val program = be.heldItem.get(AllDataComponents.SCHEMATIC_PROGRAM)
        val isConstruction = program is SchematicProgram.Construction

        // Left-aligned inputs, right after the axis labels
        val inputLeft = guiLeft + 26
        val inputTop = guiTop + 110
        val inputW = windowWidth - 36
        val inputH = 14
        val gap = 16

        labelX = Label(inputLeft + 3, inputTop + 3, Component.empty()).also {
            it.withShadow()
            addRenderableWidget(it)
        }
        labelY = Label(inputLeft + 3, inputTop + gap + 3, Component.empty()).also {
            it.withShadow()
            addRenderableWidget(it)
        }
        labelZ = Label(inputLeft + 3, inputTop + gap * 2 + 3, Component.empty()).also {
            it.withShadow()
            addRenderableWidget(it)
        }

        scrollInputX = ScrollInput(inputLeft, inputTop, inputW, inputH).also {
            it.withRange(-512, 512)
            it.setState(offsetX)
            it.titled(Component.translatable("gui.cbbees.deployer.offset.x"))
            it.writingTo(labelX!!)
            it.calling { v -> offsetX = v }
            addRenderableWidget(it)
        }
        scrollInputY = ScrollInput(inputLeft, inputTop + gap, inputW, inputH).also {
            it.withRange(-512, 512)
            it.setState(offsetY)
            it.titled(Component.translatable("gui.cbbees.deployer.offset.y"))
            it.writingTo(labelY!!)
            it.calling { v -> offsetY = v }
            addRenderableWidget(it)
        }
        scrollInputZ = ScrollInput(inputLeft, inputTop + gap * 2, inputW, inputH).also {
            it.withRange(-512, 512)
            it.setState(offsetZ)
            it.titled(Component.translatable("gui.cbbees.deployer.offset.z"))
            it.writingTo(labelZ!!)
            it.calling { v -> offsetZ = v }
            addRenderableWidget(it)
        }

        // Rotate/Mirror buttons (construction only)
        if (isConstruction) {
            val btnY = inputTop + gap * 3 + 6
            rotateButton = IconButton(guiLeft + 10, btnY, AllIcons.I_TOOL_ROTATE).also {
                it.setToolTip(Component.translatable("gui.cbbees.deployer.rotate"))
                addRenderableWidget(it)
            }
            mirrorButton = IconButton(guiLeft + 30, btnY, AllIcons.I_TOOL_MIRROR).also {
                it.setToolTip(Component.translatable("gui.cbbees.deployer.mirror"))
                addRenderableWidget(it)
            }
        }
    }

    private fun updateTabHighlights() {
        absoluteButton?.green = currentMode == DeployMode.ABSOLUTE
        relativeButton?.green = currentMode == DeployMode.RELATIVE
    }

    private fun switchMode(mode: DeployMode) {
        if (currentMode == mode) return

        // When switching to RELATIVE for the first time, initialize from the program
        if (mode == DeployMode.RELATIVE && currentMode == DeployMode.ABSOLUTE) {
            val program = be.heldItem.get(AllDataComponents.SCHEMATIC_PROGRAM)
            if (program != null) {
                if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
                    val refPoint = when (program) {
                        is SchematicProgram.Construction -> program.anchor
                        is SchematicProgram.Deconstruction -> BlockPos(
                            (program.corner1.x + program.corner2.x) / 2,
                            (program.corner1.y + program.corner2.y) / 2,
                            (program.corner1.z + program.corner2.z) / 2
                        )
                        is SchematicProgram.Pickup -> BlockPos(
                            (program.corner1.x + program.corner2.x) / 2,
                            (program.corner1.y + program.corner2.y) / 2,
                            (program.corner1.z + program.corner2.z) / 2
                        )
                    }
                    offsetX = refPoint.x - be.blockPos.x
                    offsetY = refPoint.y - be.blockPos.y
                    offsetZ = refPoint.z - be.blockPos.z
                }
                // Initialize rotation/mirror from the stored program
                if (program is SchematicProgram.Construction
                    && currentRotation == Rotation.NONE && currentMirror == Mirror.NONE
                ) {
                    currentRotation = program.rotation
                    currentMirror = program.mirror
                }
            }
        }

        currentMode = mode
        updateTabHighlights()
        initRelativeWidgets()
    }

    private fun cycleRotation() {
        currentRotation = when (currentRotation) {
            Rotation.NONE -> Rotation.CLOCKWISE_90
            Rotation.CLOCKWISE_90 -> Rotation.CLOCKWISE_180
            Rotation.CLOCKWISE_180 -> Rotation.COUNTERCLOCKWISE_90
            Rotation.COUNTERCLOCKWISE_90 -> Rotation.NONE
        }
    }

    private fun cycleMirror() {
        currentMirror = when (currentMirror) {
            Mirror.NONE -> Mirror.LEFT_RIGHT
            Mirror.LEFT_RIGHT -> Mirror.FRONT_BACK
            Mirror.FRONT_BACK -> Mirror.NONE
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (currentMode == DeployMode.RELATIVE && hasShiftDown()) {
            val input = listOf(scrollInputX, scrollInputY, scrollInputZ)
                .firstOrNull { it != null && it.isMouseOver(mouseX, mouseY) }
            if (input != null) {
                val scroll = if (scrollY != 0.0) scrollY else scrollX
                if (scroll != 0.0) {
                    val sign = Math.signum(scroll).toInt()
                    input.setState(input.getState() + sign * 16)
                    input.onChanged()
                }
                return true
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        absoluteButton?.let { btn ->
            if (btn.isMouseOver(mouseX, mouseY)) {
                switchMode(DeployMode.ABSOLUTE)
                return true
            }
        }
        relativeButton?.let { btn ->
            if (btn.isMouseOver(mouseX, mouseY)) {
                switchMode(DeployMode.RELATIVE)
                return true
            }
        }
        rotateButton?.let { btn ->
            if (btn.isMouseOver(mouseX, mouseY)) {
                cycleRotation()
                return true
            }
        }
        mirrorButton?.let { btn ->
            if (btn.isMouseOver(mouseX, mouseY)) {
                cycleMirror()
                return true
            }
        }
        confirmButton?.let { btn ->
            if (btn.isMouseOver(mouseX, mouseY)) {
                confirm()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            confirm()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun confirm() {
        val offset = BlockPos(offsetX, offsetY, offsetZ)

        // Optimistically update the client-side BE so reopening the GUI in Sable
        // immediately shows the selected mode even if the server BE lives in a
        // shipyard/sub-level coordinate space and vanilla chunk sync is delayed.
        be.deployMode = currentMode
        be.relativeOffset = offset
        be.relativeRotation = currentRotation
        be.relativeMirror = currentMirror

        CatnipServices.NETWORK.sendToServer(
            DeployerSettingsPacket(
                be.blockPos,
                currentMode,
                offset,
                currentRotation,
                currentMirror
            )
        )
        onClose()
    }

    override fun renderWindow(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        val x = guiLeft
        val y = guiTop

        // Draw dark background panel
        graphics.fill(x, y, x + windowWidth, y + windowHeight, 0xCC2A2A2A.toInt())
        // Border
        graphics.fill(x, y, x + windowWidth, y + 1, 0xFF555555.toInt())
        graphics.fill(x, y + windowHeight - 1, x + windowWidth, y + windowHeight, 0xFF555555.toInt())
        graphics.fill(x, y, x + 1, y + windowHeight, 0xFF555555.toInt())
        graphics.fill(x + windowWidth - 1, y, x + windowWidth, y + windowHeight, 0xFF555555.toInt())

        // Title bar
        graphics.fill(x + 1, y + 1, x + windowWidth - 1, y + 18, 0xFF3A3A3A.toInt())
        graphics.drawCenteredString(
            font,
            title,
            x + windowWidth / 2,
            y + 5,
            0xFFD4AA00.toInt()
        )

        // Tab labels
        val absColor = if (currentMode == DeployMode.ABSOLUTE) 0xFFFFFF else 0x888888
        val relColor = if (currentMode == DeployMode.RELATIVE) 0xFFFFFF else 0x888888
        graphics.drawString(font, "Absolute", x + 48, y + 26, absColor, false)
        graphics.drawString(font, "Relative", x + 100, y + 26, relColor, false)

        // Mode indicator line
        val lineX = if (currentMode == DeployMode.ABSOLUTE) x + 48 else x + 100
        val lineW = if (currentMode == DeployMode.ABSOLUTE) font.width("Absolute") else font.width("Relative")
        graphics.fill(lineX, y + 36, lineX + lineW, y + 37, 0xFFD4AA00.toInt())

        // Program info section
        val program = be.heldItem.get(AllDataComponents.SCHEMATIC_PROGRAM)
        val infoY = y + 42

        if (program != null) {
            renderProgramInfo(graphics, x + 10, infoY, program)
        } else {
            graphics.drawString(font, Component.translatable("cbbees.deployer.empty"), x + 10, infoY, 0xFF6666, false)
        }

        // Position section
        val posY = y + 96
        graphics.fill(x + 5, posY - 4, x + windowWidth - 5, posY - 3, 0xFF555555.toInt())

        if (currentMode == DeployMode.ABSOLUTE) {
            renderAbsoluteCoords(graphics, x + 10, posY, program)
        } else {
            renderRelativeControls(graphics, x + 10, posY, program)
        }
    }

    private fun renderProgramInfo(graphics: GuiGraphics, x: Int, y: Int, program: SchematicProgram) {
        when (program) {
            is SchematicProgram.Construction -> {
                val typeLabel =
                    Component.translatable("cbbees.program.construction", program.schematicName.removeSuffix(".nbt"))
                graphics.drawString(font, typeLabel, x, y, 0x8BDB8B, false)

                val rotStr = when (program.rotation) {
                    Rotation.NONE -> "None"
                    Rotation.CLOCKWISE_90 -> "90\u00B0"
                    Rotation.CLOCKWISE_180 -> "180\u00B0"
                    Rotation.COUNTERCLOCKWISE_90 -> "270\u00B0"
                }
                val mirrorStr = when (program.mirror) {
                    Mirror.NONE -> "None"
                    Mirror.LEFT_RIGHT -> "Left-Right"
                    Mirror.FRONT_BACK -> "Front-Back"
                }
                graphics.drawString(font, "Rotation: $rotStr  Mirror: $mirrorStr", x, y + 12, 0x999999, false)

                graphics.drawString(
                    font,
                    Component.literal("Anchor: ${program.anchor.x}, ${program.anchor.y}, ${program.anchor.z}"),
                    x, y + 24, 0x888888, false
                )
            }

            is SchematicProgram.Deconstruction -> {
                graphics.drawString(
                    font,
                    Component.translatable("cbbees.program.deconstruction"),
                    x, y, 0xDB8B8B, false
                )
                val sizeX = kotlin.math.abs(program.corner2.x - program.corner1.x) + 1
                val sizeY = kotlin.math.abs(program.corner2.y - program.corner1.y) + 1
                val sizeZ = kotlin.math.abs(program.corner2.z - program.corner1.z) + 1
                graphics.drawString(
                    font,
                    Component.translatable("cbbees.program.dimensions", sizeX, sizeY, sizeZ),
                    x, y + 12, 0x999999, false
                )
                graphics.drawString(
                    font,
                    "From: ${program.corner1.x}, ${program.corner1.y}, ${program.corner1.z}",
                    x, y + 24, 0x888888, false
                )
                graphics.drawString(
                    font,
                    "To: ${program.corner2.x}, ${program.corner2.y}, ${program.corner2.z}",
                    x, y + 36, 0x888888, false
                )
            }

            is SchematicProgram.Pickup -> {
                graphics.drawString(
                    font,
                    Component.translatable("cbbees.program.pickup"),
                    x, y, 0x8BDB8B, false
                )
                val sizeX = kotlin.math.abs(program.corner2.x - program.corner1.x) + 1
                val sizeY = kotlin.math.abs(program.corner2.y - program.corner1.y) + 1
                val sizeZ = kotlin.math.abs(program.corner2.z - program.corner1.z) + 1
                graphics.drawString(
                    font,
                    Component.translatable("cbbees.program.dimensions", sizeX, sizeY, sizeZ),
                    x, y + 12, 0x999999, false
                )
            }
        }
    }

    private fun renderAbsoluteCoords(graphics: GuiGraphics, x: Int, y: Int, program: SchematicProgram?) {
        graphics.drawString(font, "Deploy Mode: Absolute", x, y, 0xD4AA00, false)
        graphics.drawString(
            font,
            Component.literal("Coordinates are used as-is").withStyle(ChatFormatting.GRAY),
            x, y + 14, 0x888888, false
        )

        if (program != null) {
            val posText = when (program) {
                is SchematicProgram.Construction ->
                    "Build at: ${program.anchor.x}, ${program.anchor.y}, ${program.anchor.z}"

                is SchematicProgram.Deconstruction ->
                    "Area: ${program.corner1.x},${program.corner1.y},${program.corner1.z} to ${program.corner2.x},${program.corner2.y},${program.corner2.z}"

                is SchematicProgram.Pickup ->
                    "Scan: ${program.corner1.x},${program.corner1.y},${program.corner1.z} to ${program.corner2.x},${program.corner2.y},${program.corner2.z}"
            }
            graphics.drawString(font, posText, x, y + 28, 0xAAAAAA, false)
        }
    }

    private fun renderRelativeControls(graphics: GuiGraphics, x: Int, y: Int, program: SchematicProgram?) {
        graphics.drawString(font, "Offset from Deployer:", x, y, 0xD4AA00, false)

        // Axis labels left of scroll inputs
        val labelX = guiLeft + 14
        val inputTop = guiTop + 110
        val gap = 16
        graphics.drawString(font, "X:", labelX, inputTop + 3, 0xDD5555, true)
        graphics.drawString(font, "Y:", labelX, inputTop + gap + 3, 0x55DD55, true)
        graphics.drawString(font, "Z:", labelX, inputTop + gap * 2 + 3, 0x5555DD, true)

        // Rotation/Mirror display (construction only)
        val isConstruction = program is SchematicProgram.Construction
        val btnY = inputTop + gap * 3 + 6

        if (isConstruction) {
            val rotStr = when (currentRotation) {
                Rotation.NONE -> "0\u00B0"
                Rotation.CLOCKWISE_90 -> "90\u00B0"
                Rotation.CLOCKWISE_180 -> "180\u00B0"
                Rotation.COUNTERCLOCKWISE_90 -> "270\u00B0"
            }
            val mirrorStr = when (currentMirror) {
                Mirror.NONE -> "None"
                Mirror.LEFT_RIGHT -> "L-R"
                Mirror.FRONT_BACK -> "F-B"
            }
            graphics.drawString(font, rotStr, guiLeft + 52, btnY + 5, 0xCCCCCC, true)
            graphics.drawString(font, mirrorStr, guiLeft + 80, btnY + 5, 0xCCCCCC, true)
        }

        // Preview actual target position
        val targetPos = be.blockPos.offset(offsetX, offsetY, offsetZ)
        val previewY = btnY + (if (isConstruction) 22 else 0)
        graphics.drawString(
            font,
            "Target: ${targetPos.x}, ${targetPos.y}, ${targetPos.z}",
            x, previewY, 0xAAAAAA, false
        )
        graphics.drawString(
            font,
            "Scroll to adjust | Shift: x16",
            x, previewY + 12, 0x666666, false
        )
    }
}
