package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.schematics.external.ExternalSchematicSource
import de.devin.cbbees.icons.AllCCRGuiTextures
import de.devin.cbbees.network.InstantConstructionPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.Vec3i
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * Full-screen schematic browser with group navigation, 3D preview and material list.
 * Panels stretch with the screen and are drawn as nine-slices from the `construction_planner` sheet.
 */
@OnlyIn(Dist.CLIENT)
class ConstructionPlannerScreen : Screen(Component.translatable("gui.cbbees.construction_planner.title")) {

    private data class Area(val x: Int, val y: Int, val w: Int, val h: Int) {
        val right get() = x + w
        val bottom get() = y + h
        fun contains(mouseX: Double, mouseY: Double) = mouseX >= x && mouseX < right && mouseY >= y && mouseY < bottom
    }

    private lateinit var selectButton: PlannerButton
    private lateinit var groupButton: PlannerButton
    private lateinit var backButton: PlannerIconButton
    private lateinit var renameButton: PlannerIconButton
    private lateinit var deleteButton: PlannerIconButton
    private lateinit var resetViewButton: PlannerIconButton
    private lateinit var searchBox: EditBox

    private val entries = mutableListOf<BrowserEntry>()
    private var selectedEntry: BrowserEntry? = null
    private var listScroll = 0
    private var currentGroupPath = ""
    private var allSchematics: List<String> = emptyList()
    private var searchActive = false

    private var materialScroll = 0
    private var draggingScrollbar: Area? = null

    private var previewRotX = DEFAULT_ROT_X
    private var previewRotY = DEFAULT_ROT_Y
    private var previewZoom = 1f

    private var search = Area(0, 0, 0, 0)
    private var titleArea = Area(0, 0, 0, 0)
    private var listPanel = Area(0, 0, 0, 0)
    private var breadcrumbArea = Area(0, 0, 0, 0)
    private var rows = Area(0, 0, 0, 0)
    private var listTrack = Area(0, 0, 0, 0)
    private var previewPanel = Area(0, 0, 0, 0)
    private var viewport = Area(0, 0, 0, 0)
    private var info = Area(0, 0, 0, 0)
    private var materialsPanel = Area(0, 0, 0, 0)
    private var grid = Area(0, 0, 0, 0)
    private var materialsTrack = Area(0, 0, 0, 0)
    private var visibleRows = 0
    private var gridColumns = 0
    private var gridRows = 0

    private fun layout() {
        val panelTop = 22
        val panelHeight = height - panelTop - 28
        val listWidth = (width * 0.28).toInt().coerceIn(110, 220)
        gridColumns = ((width * 0.22).toInt() - 14).div(SLOT_SIZE).coerceIn(3, 9)
        val materialsWidth = gridColumns * SLOT_SIZE + 14

        search = Area(MARGIN, 5, listWidth, 12)
        listPanel = Area(MARGIN, panelTop, listWidth, panelHeight)
        materialsPanel = Area(width - MARGIN - materialsWidth, panelTop, materialsWidth, panelHeight)
        previewPanel = Area(listPanel.right + GAP, panelTop, materialsPanel.x - GAP - listPanel.right - GAP, panelHeight)
        titleArea = Area(previewPanel.x, 5, previewPanel.w, 12)

        breadcrumbArea = Area(listPanel.x + 16, listPanel.y + 4, listPanel.w - 18, 9)
        visibleRows = (panelHeight - 18) / ROW_HEIGHT
        rows = Area(listPanel.x + 2, listPanel.y + 16, listPanel.w - 10, visibleRows * ROW_HEIGHT)
        listTrack = Area(rows.right + 2, rows.y, 4, rows.h)

        viewport = Area(previewPanel.x + 2, previewPanel.y + 2, previewPanel.w - 4, previewPanel.h - 34)
        info = Area(viewport.x, viewport.bottom + 2, viewport.w, 28)

        gridRows = (panelHeight - 18) / SLOT_SIZE
        grid = Area(materialsPanel.x + 4, materialsPanel.y + 14, gridColumns * SLOT_SIZE, gridRows * SLOT_SIZE)
        materialsTrack = Area(grid.right + 2, grid.y, 4, grid.h)
    }

    override fun init() {
        super.init()
        layout()

        CreateClient.SCHEMATIC_SENDER.refresh()
        allSchematics = CreateClient.SCHEMATIC_SENDER.availableSchematics.map { it.string }
        SchematicGroupManager.ensureLoaded()
        SchematicGroupManager.reconcile(allSchematics)
        currentGroupPath = ConstructionPlannerHandler.currentGroupPath

        val previousSearch = if (::searchBox.isInitialized) searchBox.value else ""
        searchBox = EditBox(font, search.x + 3, search.y + 2, search.w - 6, search.h - 2, Component.translatable("gui.cbbees.construction_planner.search"))
        searchBox.setBordered(false)
        searchBox.setMaxLength(100)
        searchBox.setHint(Component.translatable("gui.cbbees.construction_planner.search"))
        searchBox.value = previousSearch
        searchBox.setResponder { query ->
            searchActive = query.isNotBlank()
            listScroll = 0
            rebuildList()
        }
        addRenderableWidget(searchBox)

        val buttonY = height - 22
        selectButton = addRenderableWidget(PlannerButton(MARGIN, buttonY, Component.translatable("gui.cbbees.construction_planner.select"), ::onSelectAction))
        groupButton = addRenderableWidget(PlannerButton(MARGIN + BUTTON_WIDTH + 4, buttonY, Component.translatable("gui.cbbees.construction_planner.assign_group"), ::onAssignGroup))
        addRenderableWidget(PlannerButton(width - MARGIN - BUTTON_WIDTH * 2 - 4, buttonY, Component.translatable("gui.cbbees.browse_online.button")) {
            ExternalSchematicSource.active?.let { minecraft?.setScreen(BrowseOnlineScreen(it, this)) }
        }).active = ExternalSchematicSource.active != null && CBBeesConfig.enableExternalBrowser.get()
        addRenderableWidget(PlannerButton(width - MARGIN - BUTTON_WIDTH, buttonY, Component.translatable("gui.done"), ::onClose))

        backButton = addRenderableWidget(PlannerIconButton(listPanel.x + 2, listPanel.y + 2, AllCCRGuiTextures.PLANNER_GLYPH_BACK, Component.translatable("gui.cbbees.construction_planner.back"), ::navigateUp))
        renameButton = addRenderableWidget(PlannerIconButton(width - MARGIN - 26, 5, AllCCRGuiTextures.PLANNER_GLYPH_RENAME, Component.translatable("gui.cbbees.construction_planner.rename"), ::onRenameAction))
        deleteButton = addRenderableWidget(PlannerIconButton(width - MARGIN - 12, 5, AllCCRGuiTextures.PLANNER_GLYPH_DELETE, Component.translatable("gui.cbbees.construction_planner.delete"), ::onDeleteAction))
        resetViewButton = addWidget(PlannerIconButton(viewport.right - 14, viewport.y + 2, AllCCRGuiTextures.PLANNER_GLYPH_RESET_VIEW, Component.translatable("gui.cbbees.construction_planner.reset_view"), ::resetView))

        rebuildList()
    }

    private val selectedSchematic: BrowserEntry?
        get() = selectedEntry?.takeIf { it.type == BrowserEntryType.SCHEMATIC }

    private fun rebuildList() {
        val previousSelection = selectedEntry?.value
        entries.clear()

        if (searchActive) {
            val query = searchBox.value.lowercase()
            allSchematics.forEach { filename ->
                val group = SchematicGroupManager.getGroup(filename)
                val displayPath = if (group.isEmpty()) filename else "$group/$filename"
                if (displayPath.lowercase().contains(query)) {
                    entries += BrowserEntry(BrowserEntryType.SCHEMATIC, filename, filename.removeSuffix(".nbt"), group)
                }
            }
        } else {
            if (currentGroupPath.isNotEmpty()) entries += BrowserEntry(BrowserEntryType.PARENT, "..", "..", "")
            val (subgroups, schematics) = SchematicGroupManager.getItemsAtLevel(currentGroupPath, allSchematics)
            subgroups.forEach { group ->
                val fullPath = if (currentGroupPath.isEmpty()) group else "$currentGroupPath/$group"
                entries += BrowserEntry(BrowserEntryType.GROUP, fullPath, group, "")
            }
            schematics.forEach { entries += BrowserEntry(BrowserEntryType.SCHEMATIC, it, it.removeSuffix(".nbt"), "") }
        }

        listScroll = listScroll.coerceIn(0, maxListScroll())
        select(entries.find { it.value == previousSelection })
    }

    private fun select(entry: BrowserEntry?) {
        val changed = entry?.value != selectedEntry?.value
        selectedEntry = entry
        val isSchematic = selectedSchematic != null
        selectButton.active = entry != null
        groupButton.active = isSchematic
        renameButton.visible = isSchematic
        deleteButton.visible = isSchematic
        resetViewButton.visible = isSchematic
        backButton.visible = currentGroupPath.isNotEmpty() && !searchActive
        if (changed) {
            materialScroll = 0
            resetView()
        }
    }

    private fun activate(entry: BrowserEntry) = when (entry.type) {
        BrowserEntryType.GROUP -> navigateToGroup(entry.value)
        BrowserEntryType.PARENT -> navigateUp()
        BrowserEntryType.SCHEMATIC -> selectSchematic(entry.value)
    }

    private fun onSelectAction() {
        selectedEntry?.let(::activate)
    }

    private fun onRenameAction() {
        selectedSchematic?.let { minecraft?.setScreen(SchematicRenameScreen(it.value, this)) }
    }

    private fun onDeleteAction() {
        selectedSchematic?.let { minecraft?.setScreen(SchematicDeleteScreen(it.value, this)) }
    }

    private fun onAssignGroup() {
        val filename = selectedSchematic?.value ?: return
        val currentGroup = SchematicGroupManager.getGroup(filename)
        minecraft?.setScreen(GroupPickerScreen({ newGroup ->
            SchematicGroupManager.setGroup(filename, newGroup)
        }, currentGroup, ConstructionPlannerScreen()))
    }

    private fun resetView() {
        previewRotX = DEFAULT_ROT_X
        previewRotY = DEFAULT_ROT_Y
        previewZoom = 1f
    }

    private fun navigateToGroup(groupPath: String) {
        currentGroupPath = groupPath
        searchBox.value = ""
        searchActive = false
        listScroll = 0
        rebuildList()
    }

    private fun navigateUp() {
        if (currentGroupPath.isEmpty()) return
        currentGroupPath = currentGroupPath.substringBeforeLast('/', "")
        listScroll = 0
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
        val anchor = SchematicHoverPreview.computeServerAnchor(centerAnchor)
        PacketDistributor.sendToServer(
            InstantConstructionPacket(filename, anchor, SchematicHoverPreview.currentRotation, SchematicHoverPreview.currentMirror)
        )
        onClose()
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(guiGraphics)
        AllCCRGuiTextures.PLANNER_SEARCH.renderNineSlice(guiGraphics, search.x, search.y, search.w, search.h, 2)
        listOf(listPanel, previewPanel, materialsPanel).forEach {
            AllCCRGuiTextures.PLANNER_PANEL.renderNineSlice(guiGraphics, it.x, it.y, it.w, it.h, 2)
        }
        AllCCRGuiTextures.PLANNER_VIEWPORT.renderTiled(guiGraphics, viewport.x, viewport.y, viewport.w, viewport.h)
        AllCCRGuiTextures.PLANNER_SLOT.renderTiled(guiGraphics, grid.x, grid.y, grid.w, grid.h)
        listOf(listTrack, materialsTrack).forEach {
            AllCCRGuiTextures.PLANNER_SCROLL_TRACK.renderNineSlice(guiGraphics, it.x, it.y, it.w, it.h, 0, 2)
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, titleArea.x + titleArea.w / 2, titleArea.y + 2, 0xFFFFFF)
        renderList(guiGraphics, mouseX, mouseY)
        renderPreview(guiGraphics)
        renderMaterials(guiGraphics, mouseX, mouseY)
        if (resetViewButton.visible) resetViewButton.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderList(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val breadcrumbX = if (backButton.visible) breadcrumbArea.x else breadcrumbArea.x - 14
        val breadcrumb = font.plainSubstrByWidth(breadcrumbText(), breadcrumbArea.right - breadcrumbX)
        guiGraphics.drawString(font, breadcrumb, breadcrumbX, breadcrumbArea.y, 0xAAAAAA, false)

        if (entries.isEmpty()) {
            guiGraphics.drawWordWrap(font, Component.translatable("gui.cbbees.construction_planner.no_schematics"), rows.x + 4, rows.y + 4, rows.w - 8, 0x888888)
            return
        }

        val hovered = rowAt(mouseX.toDouble(), mouseY.toDouble())
        visibleEntries().forEachIndexed { i, entry ->
            val x = rows.x
            val y = rows.y + i * ROW_HEIGHT
            when {
                entry === selectedEntry -> AllCCRGuiTextures.PLANNER_ROW_SELECTED.renderNineSlice(guiGraphics, x, y, rows.w, ROW_HEIGHT, 2)
                entry === hovered -> AllCCRGuiTextures.PLANNER_ROW_HOVER.renderNineSlice(guiGraphics, x, y, rows.w, ROW_HEIGHT, 2)
            }
            val icon = when (entry.type) {
                BrowserEntryType.GROUP -> AllCCRGuiTextures.PLANNER_ICON_GROUP
                BrowserEntryType.PARENT -> AllCCRGuiTextures.PLANNER_ICON_PARENT
                BrowserEntryType.SCHEMATIC -> null
            }
            icon?.render(guiGraphics, x + 2, y + 3)
            val color = when {
                entry === selectedEntry -> 0xFFFFFF
                entry.type != BrowserEntryType.SCHEMATIC -> 0x88CCFF
                else -> 0xDDDDDD
            }
            val textX = if (icon != null) x + 16 else x + 4
            val textRight = x + rows.w - 2
            if (entry === selectedEntry || entry === hovered) {
                AbstractWidget.renderScrollingString(guiGraphics, font, Component.literal(entry.displayName), textX, textX, y, textRight, y + ROW_HEIGHT, color)
            } else {
                guiGraphics.drawString(font, font.plainSubstrByWidth(entry.displayName, textRight - textX), textX, y + 5, color, true)
            }
        }

        renderScrollbar(guiGraphics, listTrack, listScroll, maxListScroll())
    }

    private fun renderPreview(guiGraphics: GuiGraphics) {
        val entry = selectedEntry
        val schematic = selectedSchematic
        if (schematic == null) {
            val hint = if (entry?.type == BrowserEntryType.GROUP) "gui.cbbees.construction_planner.group_hint" else "gui.cbbees.construction_planner.select_hint"
            val lines = font.split(Component.translatable(hint), viewport.w - 16)
            lines.forEachIndexed { i, line ->
                guiGraphics.drawCenteredString(font, line, viewport.x + viewport.w / 2, viewport.y + viewport.h / 2 - lines.size * 5 + i * 10, 0x777777)
            }
            return
        }

        SchematicPreviewRenderer.loadFromFile(schematic.value)
        SchematicPreviewRenderer.renderPreview(
            schematic.value, guiGraphics,
            viewport.x, viewport.y, viewport.w, viewport.h,
            previewRotX, previewRotY, previewZoom
        )
        SchematicPreviewRenderer.renderAxisIndicator(
            guiGraphics, viewport.x, viewport.y, viewport.w, viewport.h, previewRotX, previewRotY
        )

        val x = info.x + 3
        val y = info.y + 1
        AbstractWidget.renderScrollingString(guiGraphics, font, Component.literal(schematic.displayName), x, x, y - 1, info.right - 3, y + 8, 0xFFFF55)
        val group = SchematicGroupManager.getGroup(schematic.value)
        val groupText = if (group.isEmpty()) Component.translatable("gui.cbbees.construction_planner.no_group") else Component.literal(group)
        guiGraphics.drawString(font, groupText, x, y + 9, 0x88CCFF, false)
        val size = SchematicPreviewRenderer.getSize(schematic.value)
        if (size != Vec3i.ZERO) guiGraphics.drawString(font, "${size.x} x ${size.y} x ${size.z}", x, y + 18, 0x888888, false)
    }

    private fun renderMaterials(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        guiGraphics.drawString(font, Component.translatable("gui.cbbees.construction_planner.materials"), materialsPanel.x + 4, materialsPanel.y + 4, 0xAAAAAA, false)
        val schematic = selectedSchematic ?: return
        val materials = SchematicPreviewRenderer.getMaterials(schematic.value)
        materialScroll = materialScroll.coerceIn(0, maxMaterialScroll(materials.size))

        var hovered: SchematicPreviewRenderer.MaterialEntry? = null
        materials.drop(materialScroll * gridColumns).take(gridColumns * gridRows).forEachIndexed { i, material ->
            val x = grid.x + (i % gridColumns) * SLOT_SIZE + 1
            val y = grid.y + (i / gridColumns) * SLOT_SIZE + 1
            guiGraphics.renderItem(material.icon, x, y)
            renderSlotLabel(guiGraphics, material.slotLabel, x, y)
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                guiGraphics.fill(x, y, x + 16, y + 16, 200, 0x80FFFFFF.toInt())
                hovered = material
            }
        }

        renderScrollbar(guiGraphics, materialsTrack, materialScroll, maxMaterialScroll(materials.size))
        hovered?.let {
            guiGraphics.renderComponentTooltip(font, listOf(it.name, Component.literal(it.amount).withStyle { s -> s.withColor(0xAAAAAA) }), mouseX, mouseY)
        }
    }

    private fun renderSlotLabel(guiGraphics: GuiGraphics, label: String, x: Int, y: Int) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        pose.translate(x + 17f, y + 17f, 200f)
        pose.scale(SLOT_LABEL_SCALE, SLOT_LABEL_SCALE, 1f)
        guiGraphics.drawString(font, label, -font.width(label), -font.lineHeight + 1, 0xFFFFFF, true)
        pose.popPose()
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics, track: Area, scroll: Int, maxScroll: Int) {
        if (maxScroll <= 0) return
        val thumb = AllCCRGuiTextures.PLANNER_SCROLL_THUMB
        val y = track.y + (track.h - thumb.height) * scroll / maxScroll
        thumb.render(guiGraphics, track.x, y)
    }

    private fun breadcrumbText(): String = when {
        searchActive -> Component.translatable("gui.cbbees.construction_planner.search_results").string
        else -> (listOf("Root") + currentGroupPath.split('/').filter { it.isNotEmpty() }).joinToString(" > ")
    }

    private fun visibleEntries() = entries.drop(listScroll).take(visibleRows)

    private fun maxListScroll() = (entries.size - visibleRows).coerceAtLeast(0)

    private fun maxMaterialScroll(count: Int) = ((count + gridColumns - 1) / gridColumns - gridRows).coerceAtLeast(0)

    private fun currentMaterialCount() = selectedSchematic?.let { SchematicPreviewRenderer.getMaterials(it.value).size } ?: 0

    private fun rowAt(mouseX: Double, mouseY: Double): BrowserEntry? {
        if (!rows.contains(mouseX, mouseY)) return null
        return visibleEntries().getOrNull(((mouseY - rows.y) / ROW_HEIGHT).toInt())
    }

    private fun scrollFromTrack(track: Area, mouseY: Double, maxScroll: Int): Int {
        val progress = ((mouseY - track.y) / track.h).coerceIn(0.0, 1.0)
        return (progress * maxScroll + 0.5).toInt()
    }

    private fun dragScrollbar(track: Area, mouseY: Double) {
        when (track) {
            listTrack -> listScroll = scrollFromTrack(track, mouseY, maxListScroll())
            materialsTrack -> materialScroll = scrollFromTrack(track, mouseY, maxMaterialScroll(currentMaterialCount()))
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true

        listOf(listTrack, materialsTrack).firstOrNull { it.contains(mouseX, mouseY) }?.let { track ->
            if (button == 0) {
                draggingScrollbar = track
                dragScrollbar(track, mouseY)
                return true
            }
        }

        val row = rowAt(mouseX, mouseY)
        if (row != null) {
            val wasSelected = row === selectedEntry
            select(row)
            when {
                button == 1 && row.type == BrowserEntryType.SCHEMATIC && hasShiftDown() -> instantConstruct(row.value)
                button == 1 || wasSelected -> activate(row)
            }
            return true
        }

        if (button == 1) {
            val entry = selectedEntry ?: return false
            if (entry.type == BrowserEntryType.SCHEMATIC && hasShiftDown()) instantConstruct(entry.value) else activate(entry)
            return true
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingScrollbar = null
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val delta = scrollY.toInt()
        when {
            viewport.contains(mouseX, mouseY) && selectedSchematic != null ->
                previewZoom = (previewZoom + scrollY.toFloat() * 0.1f).coerceIn(0.2f, 5f)

            materialsPanel.contains(mouseX, mouseY) ->
                materialScroll = (materialScroll - delta).coerceIn(0, maxMaterialScroll(currentMaterialCount()))

            listPanel.contains(mouseX, mouseY) ->
                listScroll = (listScroll - delta).coerceIn(0, maxListScroll())

            else -> return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }
        return true
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        draggingScrollbar?.let {
            dragScrollbar(it, mouseY)
            return true
        }
        if (button == 0 && selectedSchematic != null && viewport.contains(mouseX, mouseY)) {
            previewRotY += dragX.toFloat()
            previewRotX = (previewRotX - dragY.toFloat()).coerceIn(-90f, 90f)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchBox.isFocused) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers)
        }
        when (keyCode) {
            GLFW.GLFW_KEY_BACKSPACE -> if (!searchActive) navigateUp()
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> onSelectAction()
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    override fun removed() {
        super.removed()
        SchematicPreviewRenderer.clear()
    }

    enum class BrowserEntryType { GROUP, SCHEMATIC, PARENT }

    data class BrowserEntry(val type: BrowserEntryType, val value: String, val displayName: String, val group: String)

    companion object {
        private const val DEFAULT_ROT_X = 30f
        private const val DEFAULT_ROT_Y = -45f
        private const val MARGIN = 6
        private const val GAP = 4
        private const val ROW_HEIGHT = 18
        private const val SLOT_SIZE = 18
        private const val SLOT_LABEL_SCALE = 0.75f
        private val BUTTON_WIDTH = AllCCRGuiTextures.PLANNER_BUTTON.width
    }
}
