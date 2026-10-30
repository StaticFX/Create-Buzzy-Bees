package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import de.devin.cbbees.content.schematics.external.ExternalSchematicSource
import de.devin.cbbees.content.schematics.external.RemoteSchematic
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.minecraft.Util
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.math.min

/**
 * Full-screen detail view for a single remote schematic.
 *
 * Shows a large 3D preview (auto-loaded), schematic metadata, material list,
 * and a download button. Supports mouse-drag rotation and scroll-to-zoom.
 */
@OnlyIn(Dist.CLIENT)
class SchematicDetailScreen(
    private val source: ExternalSchematicSource?,
    private val parentScreen: Screen?,
    private val schematic: RemoteSchematic,
    private val bytesCache: MutableMap<String, ByteArray>,
    private val savedFiles: MutableMap<String, String>
) : Screen(Component.literal(schematic.name)) {

    private lateinit var downloadButton: Button

    private var previewKey: String? = null
    private var previewLoading = false
    private var previewFailed = false
    private var statusMessage: Component? = null

    // Camera
    private var rotX = 30f
    private var rotY = -45f
    private var zoom = 1f

    // Layout regions
    private var previewLeft = 0
    private var previewTop = 0
    private var previewW = 0
    private var previewH = 0
    private var infoLeft = 0
    private var infoTop = 0
    private var infoWidth = 0
    private var infoHeight = 0

    // Material scroll
    private var materialScrollOffset = 0
    private var maxMaterialScroll = 0
    private var materialListTop = 0

    companion object {
        private const val MARGIN = 10
    }

    override fun init() {
        super.init()

        // Layout: preview takes left ~60%, info panel takes right ~40%
        val totalW = width - MARGIN * 3
        previewW = (totalW * 0.6).toInt()
        infoWidth = totalW - previewW

        previewLeft = MARGIN
        previewTop = 36
        previewH = height - previewTop - 40

        infoLeft = previewLeft + previewW + MARGIN
        infoTop = previewTop
        infoHeight = previewH

        // Buttons
        val buttonY = height - 30

        downloadButton = Button.builder(Component.translatable("gui.cbbees.browse_online.download")) {
            onDownload()
        }.bounds(infoLeft, buttonY, 80, 20).build()
        downloadButton.active = !savedFiles.containsKey(schematic.id)
        addRenderableWidget(downloadButton)

        source?.let { online ->
            addRenderableWidget(Button.builder(Component.translatable("gui.cbbees.browse_online.open_web")) {
                Util.getPlatform().openUri(URI.create("${online.baseUrl}/schematics/${schematic.id}"))
            }.bounds(MARGIN, buttonY, 120, 20).build())
        }

        val backButton = Button.builder(Component.translatable("gui.back")) {
            minecraft?.setScreen(parentScreen)
        }.bounds(width - MARGIN - 60, buttonY, 60, 20).build()
        addRenderableWidget(backButton)

        // Auto-load preview
        loadPreview()
    }

    private fun loadPreview() {
        val id = schematic.id
        materialScrollOffset = 0

        val cached = bytesCache[id]
        if (cached != null) {
            val key = "preview:$id"
            SchematicPreviewRenderer.loadFromNbtBytes(key, cached)
            previewKey = key
            return
        }

        val online = source ?: return
        previewLoading = true
        statusMessage = Component.translatable("gui.cbbees.browse_online.loading_preview")
            .withStyle(ChatFormatting.YELLOW)

        online.downloadBytes(schematic).thenAccept { nbtBytes ->
            Minecraft.getInstance().execute {
                bytesCache[id] = nbtBytes
                val key = "preview:$id"
                SchematicPreviewRenderer.loadFromNbtBytes(key, nbtBytes)
                previewKey = key
                previewLoading = false
                statusMessage = null
            }
        }.exceptionally { ex ->
            Minecraft.getInstance().execute {
                previewLoading = false
                previewFailed = true
                statusMessage = Component.literal("Preview failed: ${ex.cause?.message ?: ex.message}")
                    .withStyle(ChatFormatting.RED)
            }
            null
        }
    }

    private fun onDownload() {
        val saved = savedFiles[schematic.id]
        if (saved != null) {
            statusMessage = Component.translatable("gui.cbbees.browse_online.downloaded", saved)
                .withStyle(ChatFormatting.GREEN)
            return
        }

        val cached = bytesCache[schematic.id]
        if (cached != null) {
            downloadButton.active = false
            saveToDisk(cached)
            return
        }

        val online = source ?: return
        downloadButton.active = false
        statusMessage = Component.translatable("gui.cbbees.browse_online.downloading")
            .withStyle(ChatFormatting.YELLOW)

        online.download(schematic).thenAccept { filename ->
            Minecraft.getInstance().execute {
                savedFiles[schematic.id] = filename
                statusMessage = Component.translatable("gui.cbbees.browse_online.downloaded", filename)
                    .withStyle(ChatFormatting.GREEN)
                downloadButton.active = false
            }
        }.exceptionally { ex ->
            Minecraft.getInstance().execute {
                statusMessage = Component.literal("Download failed: ${ex.cause?.message ?: ex.message}")
                    .withStyle(ChatFormatting.RED)
                downloadButton.active = true
            }
            null
        }
    }

    private fun saveToDisk(nbtBytes: ByteArray) {
        CompletableFuture.supplyAsync {
            SchematicFiles.saveUnique(schematic.id, nbtBytes)
        }.thenAccept { filename ->
            Minecraft.getInstance().execute {
                CreateClient.SCHEMATIC_SENDER.refresh()
                savedFiles[schematic.id] = filename
                statusMessage = Component.translatable("gui.cbbees.browse_online.downloaded", filename)
                    .withStyle(ChatFormatting.GREEN)
                downloadButton.active = false
            }
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // Title
        guiGraphics.drawCenteredString(font, schematic.name, width / 2, 6, 0xFFFF55)
        if (schematic.author.isNotEmpty()) {
            val subtitle = Component.translatable("gui.cbbees.browse_online.author", schematic.author)
            guiGraphics.drawCenteredString(font, subtitle, width / 2, 18, 0xAAAAAA)
        }

        // Preview area background
        guiGraphics.fill(previewLeft - 1, previewTop - 1, previewLeft + previewW + 1, previewTop + previewH + 1, 0x40FFFFFF)
        guiGraphics.fill(previewLeft, previewTop, previewLeft + previewW, previewTop + previewH, 0xC0101010.toInt())

        val key = previewKey
        if (key != null) {
            SchematicPreviewRenderer.renderPreview(
                key, guiGraphics,
                previewLeft + 4, previewTop + 4,
                previewW - 8, previewH - 8,
                rotX, rotY, zoom
            )
            SchematicPreviewRenderer.renderAxisIndicator(
                guiGraphics,
                previewLeft + 4, previewTop + 4,
                previewW - 8, previewH - 8,
                rotX, rotY
            )
        } else if (previewLoading) {
            guiGraphics.drawCenteredString(
                font,
                Component.translatable("gui.cbbees.browse_online.loading_preview"),
                previewLeft + previewW / 2, previewTop + previewH / 2, 0xAAAAAA
            )
        } else if (previewFailed) {
            guiGraphics.drawCenteredString(
                font, Component.translatable("gui.cbbees.schematic_preview.failed"),
                previewLeft + previewW / 2, previewTop + previewH / 2, 0x888888
            )
        }

        // Info panel background
        guiGraphics.fill(infoLeft - 1, infoTop - 1, infoLeft + infoWidth + 1, infoTop + infoHeight + 1, 0x40FFFFFF)
        guiGraphics.fill(infoLeft, infoTop, infoLeft + infoWidth, infoTop + infoHeight, 0xC0101010.toInt())

        renderInfoPanel(guiGraphics, mouseX, mouseY)

        // Status message
        statusMessage?.let {
            guiGraphics.drawCenteredString(font, it, width / 2, height - 10, 0xFFFFFF)
        }
    }

    private fun renderInfoPanel(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val x = infoLeft + 8
        var y = infoTop + 8
        val labelColor = 0x888888
        val valueColor = 0xCCCCCC
        val maxTextW = infoWidth - 16

        // Size + block count
        val sizeText = "${schematic.sizeX} x ${schematic.sizeY} x ${schematic.sizeZ}"
        guiGraphics.drawString(font, sizeText, x, y, valueColor, false)
        if (schematic.blockCount > 0) {
            val blockText = Component.translatable("gui.cbbees.browse_online.blocks", formatNumber(schematic.blockCount))
            guiGraphics.drawString(font, blockText, x + font.width(sizeText) + 8, y, labelColor, false)
        }
        y += 12

        // Downloads + Views
        if (source != null) {
            val dlText = Component.translatable("gui.cbbees.browse_online.downloads", formatNumber(schematic.downloads))
            guiGraphics.drawString(font, dlText, x, y, labelColor, false)
            if (schematic.views > 0) {
                val viewsText = Component.translatable("gui.cbbees.browse_online.views", formatNumber(schematic.views))
                guiGraphics.drawString(font, viewsText, x + font.width(dlText) + 10, y, labelColor, false)
            }
            y += 12
        }

        // Rating
        if (schematic.rating.isNotEmpty() && schematic.ratingCount > 0) {
            val ratingText = Component.translatable("gui.cbbees.browse_online.rating", schematic.rating, schematic.ratingCount)
            guiGraphics.drawString(font, ratingText, x, y, 0xFFAA00, false)
            y += 12
        }

        // Version info
        val versionParts = mutableListOf<String>()
        if (schematic.minecraftVersion.isNotEmpty()) versionParts.add("MC ${schematic.minecraftVersion}")
        if (schematic.createmodVersion.isNotEmpty()) versionParts.add("Create ${schematic.createmodVersion}")
        if (versionParts.isNotEmpty()) {
            guiGraphics.drawString(font, versionParts.joinToString("  "), x, y, labelColor, false)
            y += 12
        }

        // Categories + upload date
        val metaParts = mutableListOf<String>()
        if (schematic.categories.isNotEmpty()) metaParts.add(schematic.categories.joinToString(", "))
        if (schematic.createdHumanReadable.isNotEmpty()) metaParts.add(schematic.createdHumanReadable)
        if (metaParts.isNotEmpty()) {
            guiGraphics.drawString(font, metaParts.joinToString("  ·  "), x, y, labelColor, false)
            y += 12
        }

        y += 4

        // Description
        if (schematic.description.isNotEmpty()) {
            val lines = font.split(Component.literal(schematic.description), maxTextW)
            for (line in lines) {
                guiGraphics.drawString(font, line, x, y, 0xAAAAAA, false)
                y += 10
            }
            y += 4
        }

        // Already downloaded indicator
        val saved = savedFiles[schematic.id]
        if (saved != null) {
            guiGraphics.drawString(
                font,
                Component.translatable("gui.cbbees.browse_online.downloaded", saved).withStyle(ChatFormatting.GREEN),
                x, y, 0xFFFFFF, false
            )
            y += 14
        }

        // Material list header
        val key = previewKey ?: return
        guiGraphics.drawString(
            font,
            Component.translatable("gui.cbbees.construction_planner.materials"),
            x, y, 0xAAAAAA, false
        )
        y += 12

        materialListTop = y
        renderMaterialList(guiGraphics, key, x, y, mouseX, mouseY)
    }

    private fun renderMaterialList(
        guiGraphics: GuiGraphics, key: String,
        startX: Int, startY: Int, mouseX: Int, mouseY: Int
    ) {
        val materials = SchematicPreviewRenderer.getMaterials(key)
        if (materials.isEmpty()) return

        val listBottom = infoTop + infoHeight - 4
        val visibleHeight = listBottom - startY
        val itemSize = 18
        val visibleCount = visibleHeight / itemSize

        maxMaterialScroll = (materials.size - visibleCount).coerceAtLeast(0)
        materialScrollOffset = materialScrollOffset.coerceIn(0, maxMaterialScroll)

        guiGraphics.enableScissor(infoLeft, startY, infoLeft + infoWidth, listBottom)

        for (i in materialScrollOffset until min(materialScrollOffset + visibleCount, materials.size)) {
            val mat = materials[i]
            val y = startY + (i - materialScrollOffset) * itemSize

            if (mouseX >= infoLeft && mouseX < infoLeft + infoWidth && mouseY >= y && mouseY < y + itemSize) {
                guiGraphics.fill(infoLeft, y, infoLeft + infoWidth, y + itemSize, 0x20FFFFFF)
            }

            guiGraphics.renderItem(mat.icon, startX, y)

            val countStr = mat.amount
            guiGraphics.drawString(font, countStr, startX + 20, y + 4, 0xCCCCCC, false)

            val nameX = startX + 20 + font.width(countStr) + 4
            val maxNameW = infoLeft + infoWidth - nameX - 8
            if (maxNameW > 10) {
                val fullName = mat.name.string
                val truncated = if (font.width(fullName) > maxNameW) {
                    var s = fullName
                    while (s.isNotEmpty() && font.width("$s..") > maxNameW) s = s.dropLast(1)
                    "$s.."
                } else fullName
                guiGraphics.drawString(font, truncated, nameX, y + 4, 0x888888, false)
            }
        }

        guiGraphics.disableScissor()

        // Scrollbar
        if (maxMaterialScroll > 0) {
            val trackH = visibleHeight
            val thumbH = (visibleCount.toFloat() / materials.size * trackH).toInt().coerceAtLeast(8)
            val thumbY = startY + (materialScrollOffset.toFloat() / maxMaterialScroll * (trackH - thumbH)).toInt()
            val scrollX = infoLeft + infoWidth - 4
            guiGraphics.fill(scrollX, startY, scrollX + 2, startY + trackH, 0x30FFFFFF)
            guiGraphics.fill(scrollX, thumbY, scrollX + 2, thumbY + thumbH, 0x80FFFFFF.toInt())
        }
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderTransparentBackground(guiGraphics)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        // Zoom on preview area
        if (mouseX >= previewLeft && mouseX < previewLeft + previewW
            && mouseY >= previewTop && mouseY < previewTop + previewH
        ) {
            zoom = (zoom + scrollY.toFloat() * 0.1f).coerceIn(0.2f, 5f)
            return true
        }
        // Scroll material list on info panel
        if (mouseX >= infoLeft && mouseX < infoLeft + infoWidth
            && mouseY >= materialListTop && mouseY < infoTop + infoHeight
        ) {
            materialScrollOffset = (materialScrollOffset - scrollY.toInt()).coerceIn(0, maxMaterialScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (button == 0 && previewKey != null
            && mouseX >= previewLeft && mouseX < previewLeft + previewW
            && mouseY >= previewTop && mouseY < previewTop + previewH
        ) {
            rotY += dragX.toFloat()
            rotX = (rotX - dragY.toFloat()).coerceIn(-90f, 90f)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun removed() {
        super.removed()
        SchematicPreviewRenderer.clear()
    }

    private fun formatNumber(n: Int): String = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
        n >= 1_000 -> "%.1fK".format(n / 1_000.0)
        else -> n.toString()
    }
}
