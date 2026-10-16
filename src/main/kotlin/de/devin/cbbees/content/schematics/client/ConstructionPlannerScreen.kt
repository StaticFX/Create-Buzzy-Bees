package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import de.devin.cbbees.network.InstantConstructionPacket
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.schematics.external.ExternalSchematicSource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW
import kotlin.math.min

/**
 * Full-screen schematic browser with group navigation, 3D preview, material list,
 * and group management.
 *
 * Layout (three panels):
 * - Left: scrollable list of groups/schematics with search
 * - Center: isometric 3D preview + schematic info
 * - Right: scrollable material/item list with icons and counts
 * - Bottom: action buttons (Select, Assign Group, Cancel)
 */
@OnlyIn(Dist.CLIENT)
class ConstructionPlannerScreen : Screen(Component.translatable("gui.cbbees.construction_planner.title")) {

    private lateinit var entryList: BrowserList
    private lateinit var selectButton: Button
    private lateinit var groupButton: Button
    private lateinit var backButton: Button
    private lateinit var renameButton: Button
    private lateinit var deleteButton: Button
    private lateinit var searchBox: EditBox
    private var selectedEntry: BrowserEntry? = null
    private var currentGroupPath: String = ""
    private var allSchematics: List<String> = emptyList()
    private var searchActive = false

    // Material list scroll state
    private var materialScrollOffset = 0
    private var maxMaterialScroll = 0

    // Preview camera state
    private var previewRotX = 30f
    private var previewRotY = -45f
    private var previewZoom = 1f

    // Layout constants
    private val MARGIN = 8
    private val GAP = 6

    // Computed layout values (three panels)
    private var listLeft = 0
    private var listWidth = 0
    private var previewLeft = 0
    private var previewWidth = 0
    private var materialsLeft = 0
    private var materialsWidth = 0
    private var panelTop = 0
    private var panelHeight = 0

    override fun init() {
        super.init()

        // Load schematics
        CreateClient.SCHEMATIC_SENDER.refresh()
        allSchematics = CreateClient.SCHEMATIC_SENDER.availableSchematics.map { it.string }
        SchematicGroupManager.ensureLoaded()
        SchematicGroupManager.reconcile(allSchematics)

        // Sync with HUD navigation state
        currentGroupPath = ConstructionPlannerHandler.currentGroupPath

        // Three-panel layout: list (35%) | preview (40%) | materials (25%)
        val totalWidth = width - MARGIN * 2
        listWidth = (totalWidth * 0.33).toInt()
        previewWidth = (totalWidth * 0.4).toInt()
        materialsWidth = totalWidth - listWidth - previewWidth - GAP * 2

        listLeft = MARGIN
        previewLeft = listLeft + listWidth + GAP
        materialsLeft = previewLeft + previewWidth + GAP
        panelTop = 36
        panelHeight = height - panelTop - 50

        // Search box
        searchBox = EditBox(
            font, listLeft, 5, listWidth, 16,
            Component.translatable("gui.cbbees.construction_planner.search")
        )
        searchBox.setMaxLength(100)
        searchBox.setResponder { query ->
            searchActive = query.isNotBlank()
            rebuildList()
        }
        addRenderableWidget(searchBox)

        // Entry list
        val listTop = 36
        val listHeight = panelHeight
        entryList = BrowserList(minecraft!!, listWidth + MARGIN, listHeight, listTop, 18, listLeft)
        addWidget(entryList)

        // Bottom buttons
        val buttonY = height - 36
        val buttonWidth = 90

        selectButton = Button.builder(Component.translatable("gui.cbbees.construction_planner.select")) {
            onSelectAction()
        }.bounds(listLeft, buttonY, buttonWidth, 20).build()
        selectButton.active = false
        addRenderableWidget(selectButton)

        groupButton = Button.builder(Component.translatable("gui.cbbees.construction_planner.assign_group")) {
            onAssignGroup()
        }.bounds(listLeft + buttonWidth + 4, buttonY, buttonWidth + 10, 20).build()
        groupButton.active = false
        addRenderableWidget(groupButton)

        val cancelButton = Button.builder(Component.translatable("gui.done")) {
            onClose()
        }.bounds(listLeft + buttonWidth * 2 + 18, buttonY, buttonWidth, 20).build()
        addRenderableWidget(cancelButton)

        // Browse Online button — opens the external schematic browser
        val browseOnlineButton = Button.builder(Component.translatable("gui.cbbees.browse_online.button")) {
            val source = ExternalSchematicSource.active
            if (source != null) {
                minecraft?.setScreen(BrowseOnlineScreen(source, this))
            }
        }.bounds(width - MARGIN - buttonWidth - 10, buttonY, buttonWidth + 10, 20).build()
        browseOnlineButton.active = ExternalSchematicSource.active != null && CBBeesConfig.enableExternalBrowser.get()
        addRenderableWidget(browseOnlineButton)

        // Back button — visible only when inside a group
        backButton = Button.builder(Component.translatable("gui.cbbees.construction_planner.back")) {
            navigateUp()
        }.bounds(listLeft, 22, 40, 14).build()
        backButton.visible = currentGroupPath.isNotEmpty()
        addRenderableWidget(backButton)

        // Top-right rename/delete buttons — visible when a schematic is selected
        val actionBtnWidth = 50
        val actionBtnY = 5
        renameButton = Button.builder(Component.translatable("gui.cbbees.construction_planner.rename")) {
            onRenameAction()
        }.bounds(width - MARGIN - actionBtnWidth * 2 - 4, actionBtnY, actionBtnWidth, 16).build()
        renameButton.visible = false
        addRenderableWidget(renameButton)

        deleteButton = Button.builder(Component.translatable("gui.cbbees.construction_planner.delete")) {
            onDeleteAction()
        }.bounds(width - MARGIN - actionBtnWidth, actionBtnY, actionBtnWidth, 16).build()
        deleteButton.visible = false
        addRenderableWidget(deleteButton)

        rebuildList()
    }

    private fun rebuildList() {
        val previousSelection = selectedEntry?.value
        entryList.clearEntries()
        selectedEntry = null
        selectButton.active = false
        groupButton.active = false
        renameButton.visible = false
        deleteButton.visible = false
        materialScrollOffset = 0
        backButton.visible = currentGroupPath.isNotEmpty()

        if (searchActive) {
            val query = searchBox.value.lowercase()
            for (filename in allSchematics) {
                val group = SchematicGroupManager.getGroup(filename)
                val displayPath = if (group.isEmpty()) filename else "$group/$filename"
                if (displayPath.lowercase().contains(query)) {
                    entryList.addEntry(
                        BrowserEntry(
                            BrowserEntryType.SCHEMATIC, filename, filename.removeSuffix(".nbt"),
                            if (group.isNotEmpty()) group else ""
                        )
                    )
                }
            }
        } else {
            if (currentGroupPath.isNotEmpty()) {
                entryList.addEntry(BrowserEntry(BrowserEntryType.PARENT, "..", "..", ""))
            }

            val (subgroups, schematics) = SchematicGroupManager.getItemsAtLevel(currentGroupPath, allSchematics)

            for (group in subgroups) {
                val fullPath = if (currentGroupPath.isEmpty()) group else "$currentGroupPath/$group"
                entryList.addEntry(BrowserEntry(BrowserEntryType.GROUP, fullPath, group, ""))
            }

            for (filename in schematics) {
                entryList.addEntry(
                    BrowserEntry(
                        BrowserEntryType.SCHEMATIC, filename, filename.removeSuffix(".nbt"), ""
                    )
                )
            }
        }

        if (previousSelection != null) {
            entryList.children().find { it.value == previousSelection }?.let {
                entryList.setSelected(it)
            }
        }
    }

    private fun onSelectAction() {
        val entry = selectedEntry ?: return
        when (entry.type) {
            BrowserEntryType.GROUP -> navigateToGroup(entry.value)
            BrowserEntryType.PARENT -> navigateUp()
            BrowserEntryType.SCHEMATIC -> selectSchematic(entry.value)
        }
    }

    private fun onRenameAction() {
        val entry = selectedEntry ?: return
        if (entry.type != BrowserEntryType.SCHEMATIC) return
        minecraft?.setScreen(SchematicRenameScreen(entry.value, this))
    }

    private fun onDeleteAction() {
        val entry = selectedEntry ?: return
        if (entry.type != BrowserEntryType.SCHEMATIC) return
        minecraft?.setScreen(SchematicDeleteScreen(entry.value, this))
    }

    private fun onAssignGroup() {
        val entry = selectedEntry ?: return
        if (entry.type != BrowserEntryType.SCHEMATIC) return

        val filename = entry.value
        val currentGroup = SchematicGroupManager.getGroup(filename)

        val browserToReturn = ConstructionPlannerScreen()
        minecraft?.setScreen(GroupPickerScreen({ newGroup ->
            SchematicGroupManager.setGroup(filename, newGroup)
        }, currentGroup, browserToReturn))
    }

    private fun navigateToGroup(groupPath: String) {
        currentGroupPath = groupPath
        searchBox.value = ""
        searchActive = false
        rebuildList()
    }

    private fun navigateUp() {
        val lastSlash = currentGroupPath.lastIndexOf('/')
        currentGroupPath = if (lastSlash >= 0) currentGroupPath.substring(0, lastSlash) else ""
        rebuildList()
    }

    private fun selectSchematic(filename: String) {
        ConstructionPlannerHandler.confirmSchematicByName(filename)
        onClose()
    }

    private fun instantConstruct(filename: String) {
        val mc = Minecraft.getInstance()
        val hitResult = mc.hitResult
        val centerAnchor = if (hitResult is BlockHitResult) {
            hitResult.blockPos.relative(hitResult.direction)
        } else {
            mc.player?.blockPosition() ?: return
        }

        // Convert center anchor to corner anchor for server placement
        val anchor = SchematicHoverPreview.computeServerAnchor(centerAnchor)

        PacketDistributor.sendToServer(
            InstantConstructionPacket(
                filename, anchor,
                SchematicHoverPreview.currentRotation,
                SchematicHoverPreview.currentMirror
            )
        )
        onClose()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        entryList.render(guiGraphics, mouseX, mouseY, partialTick)

        // Title
        guiGraphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF)

        // Breadcrumb (offset right when back button is visible)
        val breadcrumb = buildBreadcrumb()
        val breadcrumbX = if (currentGroupPath.isNotEmpty()) listLeft + 44 else listLeft
        guiGraphics.drawString(font, breadcrumb, breadcrumbX, 26, 0x888888, false)

        // Empty list message
        if (entryList.children().isEmpty()) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.cbbees.construction_planner.no_schematics"),
                listLeft + listWidth / 2, panelTop + 40,
                0x888888
            )
        }

        // Center panel: 3D preview + info
        renderPreviewPanel(guiGraphics, mouseX, mouseY, partialTick)

        // Right panel: material list
        renderMaterialPanel(guiGraphics, mouseX, mouseY)
    }

    private fun renderPreviewPanel(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Panel background
        guiGraphics.fill(
            previewLeft - 1, panelTop - 1,
            previewLeft + previewWidth + 1, panelTop + panelHeight + 1, 0x40FFFFFF
        )
        guiGraphics.fill(
            previewLeft, panelTop,
            previewLeft + previewWidth, panelTop + panelHeight, 0xC0101010.toInt()
        )

        val entry = selectedEntry

        if (entry == null || entry.type != BrowserEntryType.SCHEMATIC) {
            val hint = when {
                entry?.type == BrowserEntryType.GROUP ->
                    Component.translatable("gui.cbbees.construction_planner.group_hint")

                else ->
                    Component.translatable("gui.cbbees.construction_planner.select_hint")
            }
            guiGraphics.drawCenteredString(
                font, hint,
                previewLeft + previewWidth / 2,
                panelTop + panelHeight / 2 - 4,
                0x666666
            )
            return
        }

        // 3D preview (upper portion)
        val infoHeight = 42
        val previewBoxHeight = panelHeight - infoHeight

        SchematicPreviewRenderer.renderPreview(
            entry.value, guiGraphics,
            previewLeft + 4, panelTop + 4,
            previewWidth - 8, previewBoxHeight - 8,
            previewRotX, previewRotY, previewZoom
        )
        SchematicPreviewRenderer.renderAxisIndicator(
            guiGraphics,
            previewLeft + 4, panelTop + 4,
            previewWidth - 8, previewBoxHeight - 8,
            previewRotX, previewRotY
        )

        // Info area below preview
        val infoY = panelTop + previewBoxHeight + 2
        val infoX = previewLeft + 6

        guiGraphics.drawString(font, entry.displayName, infoX, infoY, 0xFFFF00, false)

        val group = SchematicGroupManager.getGroup(entry.value)
        val groupText = if (group.isEmpty()) {
            Component.translatable("gui.cbbees.construction_planner.no_group")
        } else {
            Component.literal(group)
        }
        guiGraphics.drawString(font, groupText, infoX, infoY + 12, 0x88CCFF, false)

        // Size info
        val size = SchematicPreviewRenderer.getSize(entry.value)
        if (size != net.minecraft.core.Vec3i.ZERO) {
            val sizeText = "${size.x} x ${size.y} x ${size.z}"
            guiGraphics.drawString(font, sizeText, infoX, infoY + 24, 0x666666, false)
        }
    }

    private fun renderMaterialPanel(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        // Panel background
        guiGraphics.fill(
            materialsLeft - 1, panelTop - 1,
            materialsLeft + materialsWidth + 1, panelTop + panelHeight + 1, 0x40FFFFFF
        )
        guiGraphics.fill(
            materialsLeft, panelTop,
            materialsLeft + materialsWidth, panelTop + panelHeight, 0xC0101010.toInt()
        )

        // Header
        guiGraphics.drawString(
            font,
            Component.translatable("gui.cbbees.construction_planner.materials"),
            materialsLeft + 4, panelTop + 4, 0xAAAAAA, false
        )

        val entry = selectedEntry
        if (entry == null || entry.type != BrowserEntryType.SCHEMATIC) {
            guiGraphics.drawCenteredString(
                font,
                Component.literal("-"),
                materialsLeft + materialsWidth / 2,
                panelTop + panelHeight / 2,
                0x444444
            )
            return
        }

        val materials = SchematicPreviewRenderer.getMaterials(entry.value)
        if (materials.isEmpty()) return

        val itemSize = 18
        val startY = panelTop + 16
        val visibleHeight = panelHeight - 20
        val visibleCount = visibleHeight / itemSize

        // Clamp scroll
        maxMaterialScroll = (materials.size - visibleCount).coerceAtLeast(0)
        materialScrollOffset = materialScrollOffset.coerceIn(0, maxMaterialScroll)

        guiGraphics.enableScissor(materialsLeft, startY, materialsLeft + materialsWidth, panelTop + panelHeight)

        for (i in materialScrollOffset until min(materialScrollOffset + visibleCount, materials.size)) {
            val mat = materials[i]
            val y = startY + (i - materialScrollOffset) * itemSize
            val x = materialsLeft + 4

            // Hover highlight
            if (mouseX >= materialsLeft && mouseX < materialsLeft + materialsWidth
                && mouseY >= y && mouseY < y + itemSize
            ) {
                guiGraphics.fill(materialsLeft, y, materialsLeft + materialsWidth, y + itemSize, 0x20FFFFFF)
            }

            // Item icon
            guiGraphics.renderItem(mat.stack, x, y)

            // Count
            val countStr = "x${mat.count}"
            guiGraphics.drawString(font, countStr, x + 20, y + 4, 0xCCCCCC, false)

            // Item name (truncated to fit)
            val name = mat.stack.hoverName
            val nameX = x + 20 + font.width(countStr) + 4
            val maxNameWidth = materialsLeft + materialsWidth - nameX - 4
            if (maxNameWidth > 10) {
                val fullName = name.string
                val truncated = if (font.width(fullName) > maxNameWidth) {
                    var s = fullName
                    while (s.isNotEmpty() && font.width("$s..") > maxNameWidth) {
                        s = s.dropLast(1)
                    }
                    "$s.."
                } else {
                    fullName
                }
                guiGraphics.drawString(font, truncated, nameX, y + 4, 0x888888, false)
            }
        }

        guiGraphics.disableScissor()

        // Scroll indicator
        if (maxMaterialScroll > 0) {
            val trackHeight = panelHeight - 20
            val thumbHeight = (visibleCount.toFloat() / materials.size * trackHeight).toInt().coerceAtLeast(8)
            val thumbY =
                startY + (materialScrollOffset.toFloat() / maxMaterialScroll * (trackHeight - thumbHeight)).toInt()
            val scrollX = materialsLeft + materialsWidth - 3
            guiGraphics.fill(scrollX, startY, scrollX + 2, startY + trackHeight, 0x30FFFFFF)
            guiGraphics.fill(scrollX, thumbY, scrollX + 2, thumbY + thumbHeight, 0x80FFFFFF.toInt())
        }
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(guiGraphics)
    }

    private fun buildBreadcrumb(): Component {
        if (searchActive) return Component.translatable("gui.cbbees.construction_planner.search_results")
            .withStyle { it.withColor(0xAAAAAA) }

        val parts = if (currentGroupPath.isEmpty()) {
            listOf("Root")
        } else {
            listOf("Root") + currentGroupPath.split("/")
        }
        return Component.literal(parts.joinToString(" > ")).withStyle { it.withColor(0x888888) }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 1) {
            val entry = selectedEntry
            if (entry != null) {
                when (entry.type) {
                    BrowserEntryType.GROUP -> {
                        navigateToGroup(entry.value)
                        return true
                    }

                    BrowserEntryType.SCHEMATIC -> {
                        if (hasShiftDown()) {
                            instantConstruct(entry.value)
                        } else {
                            selectSchematic(entry.value)
                        }
                        return true
                    }

                    BrowserEntryType.PARENT -> {
                        navigateUp()
                        return true
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        // Zoom preview when hovering over it
        if (mouseX >= previewLeft && mouseX < previewLeft + previewWidth
            && mouseY >= panelTop && mouseY < panelTop + panelHeight
        ) {
            previewZoom = (previewZoom + scrollY.toFloat() * 0.1f).coerceIn(0.2f, 5f)
            return true
        }
        // Scroll material list when hovering over it
        if (mouseX >= materialsLeft && mouseX < materialsLeft + materialsWidth
            && mouseY >= panelTop && mouseY < panelTop + panelHeight
        ) {
            materialScrollOffset = (materialScrollOffset - scrollY.toInt()).coerceIn(0, maxMaterialScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (button == 0
            && mouseX >= previewLeft && mouseX < previewLeft + previewWidth
            && mouseY >= panelTop && mouseY < panelTop + panelHeight
        ) {
            previewRotY += dragX.toFloat()
            previewRotX = (previewRotX - dragY.toFloat()).coerceIn(-90f, 90f)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchBox.isFocused) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers)
                    || super.keyPressed(keyCode, scanCode, modifiers)
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchActive) {
            navigateUp()
            return true
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            onSelectAction()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun removed() {
        super.removed()
        SchematicPreviewRenderer.clear()
    }

    // --- Entry types ---

    enum class BrowserEntryType { GROUP, SCHEMATIC, PARENT }

    inner class BrowserEntry(
        val type: BrowserEntryType,
        val value: String,
        val displayName: String,
        val extra: String
    ) : ObjectSelectionList.Entry<BrowserEntry>() {

        override fun getNarration(): Component = Component.literal(displayName)

        override fun render(
            guiGraphics: GuiGraphics, index: Int,
            top: Int, left: Int, width: Int, height: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, partialTick: Float
        ) {
            val selected = entryList.selected === this
            val isFolder = type == BrowserEntryType.GROUP || type == BrowserEntryType.PARENT

            val prefix = when (type) {
                BrowserEntryType.GROUP -> "\u00A79\u25B6 "
                BrowserEntryType.PARENT -> "\u00A77\u25C0 "
                BrowserEntryType.SCHEMATIC -> "  "
            }

            val nameColor = when {
                selected -> 0xFFFF00
                isFolder -> 0x88CCFF
                hovered -> 0xFFFFFF
                else -> 0xCCCCCC
            }

            if (selected) {
                guiGraphics.fill(left, top, left + width, top + height, 0x30FFFFFF)
            } else if (hovered) {
                guiGraphics.fill(left, top, left + width, top + height, 0x18FFFFFF)
            }

            val text = "$prefix$displayName"
            guiGraphics.drawString(font, text, left + 2, top + 4, nameColor, false)

            if (extra.isNotEmpty()) {
                val extraWidth = font.width(extra)
                guiGraphics.drawString(font, extra, left + width - extraWidth - 4, top + 4, 0x555555, false)
            }
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (button == 0) {
                val wasSelected = entryList.selected === this
                entryList.setSelected(this)

                if (wasSelected) {
                    when (type) {
                        BrowserEntryType.GROUP -> navigateToGroup(value)
                        BrowserEntryType.PARENT -> navigateUp()
                        BrowserEntryType.SCHEMATIC -> selectSchematic(value)
                    }
                }
            }
            return true
        }
    }

    inner class BrowserList(
        mc: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        itemHeight: Int,
        private val listLeft: Int
    ) : ObjectSelectionList<BrowserEntry>(mc, width, height, top, itemHeight) {

        init {
            x = listLeft
        }

        override fun getRowLeft(): Int = listLeft
        override fun getRowWidth(): Int = this.width - 12

        public override fun addEntry(entry: BrowserEntry): Int = super.addEntry(entry)

        public override fun clearEntries() {
            super.clearEntries()
        }

        override fun setSelected(entry: BrowserEntry?) {
            super.setSelected(entry)
            selectedEntry = entry
            selectButton.active = entry != null
            val isSchematic = entry != null && entry.type == BrowserEntryType.SCHEMATIC
            groupButton.active = isSchematic
            renameButton.visible = isSchematic
            deleteButton.visible = isSchematic
            materialScrollOffset = 0
            previewRotX = 30f
            previewRotY = -45f
            previewZoom = 1f
        }
    }
}
