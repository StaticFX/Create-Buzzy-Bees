package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Axis
import com.simibubi.create.content.schematics.requirement.ItemRequirement
import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.CreateBuzzyBeez
import com.mojang.blaze3d.platform.Lighting
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.levelWrappers.SchematicLevel
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.Util
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.level.material.Fluid
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidType
import org.joml.Vector4f
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.CompletableFuture
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders 3D previews of schematics in GUI space: interactive ones driven by a [PreviewCamera], and
 * spinning ones for chat and the zapper.
 *
 * Schematics are read in the background and kept in a small LRU cache; the
 * [SchematicLevel] is built on the render thread once the file has been parsed.
 */
@OnlyIn(Dist.CLIENT)
object SchematicPreviewRenderer {

    /** Material entry: an item stack (with count=1 for display), total amount, and a short label for slots. */
    data class MaterialEntry(val icon: ItemStack, val amount: String, val name: Component, val slotLabel: String)

    enum class State { LOADING, READY, FAILED }

    private class Preview(val renderer: GhostSchematicRenderer, val size: Vec3i, val materials: List<MaterialEntry>)

    private sealed interface Slot
    private class Loading(val template: CompletableFuture<StructureTemplate>) : Slot
    private class Ready(val preview: Preview) : Slot
    private data object Failed : Slot

    private const val SPIN_PITCH = 30f
    private const val SPIN_SPEED = 1.5f
    private const val SPIN_FILL = 0.85

    private const val CACHE_SIZE = 4
    private const val MAX_NBT_BYTES = 0x20000000L

    private val cache = object : LinkedHashMap<String, Slot>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Slot>): Boolean {
            if (size <= CACHE_SIZE) return false
            (eldest.value as? Loading)?.template?.cancel(true)
            return true
        }
    }

    fun loadFromFile(filename: String) = request(filename) {
        val dir = CreatePaths.SCHEMATICS_DIR
        val path = dir.resolve(filename).normalize()
        require(path.startsWith(dir)) { "Schematic path escapes the schematics folder: $filename" }
        NbtIo.readCompressed(path, NbtAccounter.create(MAX_NBT_BYTES))
    }

    fun loadFromNbtBytes(key: String, nbtBytes: ByteArray) = request(key) {
        NbtIo.readCompressed(ByteArrayInputStream(nbtBytes), NbtAccounter.create(MAX_NBT_BYTES))
    }

    private fun request(key: String, read: () -> CompoundTag) {
        if (cache.containsKey(key)) return
        val blocks = Minecraft.getInstance().level?.holderLookup(Registries.BLOCK) ?: return
        cache[key] = Loading(CompletableFuture.supplyAsync({ StructureTemplate().apply { load(blocks, read()) } }, Util.backgroundExecutor()))
    }

    fun state(key: String): State? {
        preview(key)
        return when (cache[key]) {
            is Ready -> State.READY
            is Loading -> State.LOADING
            Failed -> State.FAILED
            null -> null
        }
    }

    private fun preview(key: String): Preview? = when (val slot = cache[key]) {
        is Ready -> slot.preview
        is Loading -> if (slot.template.isDone) finish(key, slot) else null
        else -> null
    }

    private fun finish(key: String, slot: Loading): Preview? {
        val preview = runCatching { build(slot.template.join()) }
            .onFailure { CreateBuzzyBeez.LOGGER.warn("Failed to load schematic preview $key", it) }
            .getOrNull()
        cache[key] = preview?.let(::Ready) ?: Failed
        return preview
    }

    private fun build(template: StructureTemplate): Preview {
        require(template.size != Vec3i.ZERO) { "Schematic is empty" }
        val level = SchematicLevel(Minecraft.getInstance().level ?: error("No client level"))
        val settings = StructurePlaceSettings().setRotation(Rotation.NONE).setMirror(Mirror.NONE)
        template.placeInWorld(level, BlockPos.ZERO, BlockPos.ZERO, settings, level.random, Block.UPDATE_CLIENTS)
        level.blockEntities.forEach { it.setLevel(level) }
        val renderer = GhostSchematicRenderer(level, renderAllBlockEntities = true, sliced = true).also { it.prebuildGeometry() }
        return Preview(renderer, template.size, buildMaterialList(level))
    }

    /**
     * Renders a 3D preview with custom rotation, zoom and pan.
     *
     * @param rotationX  pitch in degrees (default isometric: 30)
     * @param rotationY  yaw in degrees (default isometric: -45)
     * @param zoom       multiplier on the auto-fit scale (1.0 = fit to rect)
     * @param panX       horizontal offset of the schematic in GUI pixels
     * @param panY       vertical offset of the schematic in GUI pixels
     * @param maxLayer   highest visible Y layer, everything above is hidden
     */
    fun renderPreview(
        filename: String, guiGraphics: GuiGraphics,
        x: Int, y: Int, w: Int, h: Int,
        rotationX: Float, rotationY: Float, zoom: Float,
        panX: Float = 0f, panY: Float = 0f,
        maxLayer: Int = Int.MAX_VALUE
    ) {
        val preview = preview(filename)
        if (preview == null) {
            renderStatus(guiGraphics, state(filename), x, y, w, h)
            return
        }
        val size = preview.size
        // Fit the largest side into the rect, then apply the user's zoom
        val scale = min(w, h) / (max(size.x, max(size.y, size.z)) * 1.6f) * zoom
        guiGraphics.enableScissor(x, y, x + w, y + h)
        draw(guiGraphics, preview.renderer, size, x + w / 2.0 + panX, y + h / 2.0 + panY, scale, rotationX, rotationY, maxLayer)
        guiGraphics.disableScissor()
    }

    /** Renders [renderer] turning slowly around its vertical axis, fit into the rect. Callers clip it themselves. */
    fun renderSpinning(guiGraphics: GuiGraphics, renderer: GhostSchematicRenderer, size: Vec3i, x: Int, y: Int, w: Int, h: Int) {
        val pitch = Math.toRadians(SPIN_PITCH.toDouble())
        val spread = sqrt((size.x * size.x + size.z * size.z).toDouble())
        val height = size.y * cos(pitch) + spread * sin(pitch)
        val scale = (min(w / spread, h / height) * SPIN_FILL).toFloat()
        val yaw = AnimationTickHolder.getRenderTime() * SPIN_SPEED % 360f
        draw(guiGraphics, renderer, size, x + w / 2.0, y + h / 2.0, scale, SPIN_PITCH, yaw, Int.MAX_VALUE)
    }

    /** Draws [renderer] centred on ([centerX], [centerY]) at [scale] GUI pixels per block, turned by [pitch] and [yaw]. */
    private fun draw(
        guiGraphics: GuiGraphics, renderer: GhostSchematicRenderer, size: Vec3i,
        centerX: Double, centerY: Double, scale: Float, pitch: Float, yaw: Float, maxLayer: Int,
    ) {
        val pose = guiGraphics.pose()
        pose.pushPose()
        // Z depth keeps the schematic in front of the screen background
        pose.translate(centerX, centerY, 150.0)
        pose.scale(scale, -scale, scale) // Negative Y because GUI Y is downward
        pose.mulPose(Axis.XP.rotationDegrees(pitch))
        pose.mulPose(Axis.YP.rotationDegrees(yaw))
        pose.translate(-size.x / 2.0, -size.y / 2.0, -size.z / 2.0)

        RenderSystem.enableDepthTest()
        Lighting.setupFor3DItems()
        val buffers = DefaultSuperRenderTypeBuffer.getInstance()
        renderer.render(pose, buffers, maxLayer)
        buffers.draw()
        pose.popPose()
    }

    /**
     * Renders a small XYZ axis indicator in the bottom-left of the given rectangle.
     * Applies the same rotation so the indicator matches the preview orientation.
     */
    fun renderAxisIndicator(
        guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int,
        rotationX: Float, rotationY: Float
    ) {
        val pose = guiGraphics.pose()
        pose.pushPose()

        // Build transform: position in bottom-left of preview, scale, then rotate
        val axisLen = 16f
        val cx = (x + 26).toDouble()
        val cy = (y + h - 26).toDouble()
        pose.translate(cx, cy, 0.0)
        pose.scale(axisLen, -axisLen, axisLen) // negative Y so +Y points up on screen
        pose.mulPose(Axis.XP.rotationDegrees(rotationX))
        pose.mulPose(Axis.YP.rotationDegrees(rotationY))

        val matrix = pose.last().pose()

        // Project axis endpoints and label positions to screen space
        val origin = Vector4f(0f, 0f, 0f, 1f).also { matrix.transform(it) }
        val xEnd = Vector4f(1f, 0f, 0f, 1f).also { matrix.transform(it) }
        val yEnd = Vector4f(0f, 1f, 0f, 1f).also { matrix.transform(it) }
        val zEnd = Vector4f(0f, 0f, 1f, 1f).also { matrix.transform(it) }
        val labelDist = 1.3f
        val xLabel = Vector4f(labelDist, 0f, 0f, 1f).also { matrix.transform(it) }
        val yLabel = Vector4f(0f, labelDist, 0f, 1f).also { matrix.transform(it) }
        val zLabel = Vector4f(0f, 0f, labelDist, 1f).also { matrix.transform(it) }

        pose.popPose()

        val ox = origin.x.toInt()
        val oy = origin.y.toInt()

        // Draw axis lines as 2D filled quads (reliable in GUI context, unlike RenderType.lines())
        drawLine2D(guiGraphics, ox, oy, xEnd.x.toInt(), xEnd.y.toInt(), 0xFFFF4444.toInt())
        drawLine2D(guiGraphics, ox, oy, yEnd.x.toInt(), yEnd.y.toInt(), 0xFF44FF44.toInt())
        drawLine2D(guiGraphics, ox, oy, zEnd.x.toInt(), zEnd.y.toInt(), 0xFF4444FF.toInt())

        // Draw axis labels
        val font = Minecraft.getInstance().font
        guiGraphics.drawString(font, "X", xLabel.x.toInt() - 2, xLabel.y.toInt() - 4, 0xFF4444, false)
        guiGraphics.drawString(font, "Y", yLabel.x.toInt() - 2, yLabel.y.toInt() - 4, 0x44FF44, false)
        guiGraphics.drawString(font, "Z", zLabel.x.toInt() - 2, zLabel.y.toInt() - 4, 0x4444FF, false)
    }

    /** Draws a 2-pixel-wide line between two screen points using [GuiGraphics.fill]. */
    private fun drawLine2D(guiGraphics: GuiGraphics, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        val dx = x2 - x1
        val dy = y2 - y1
        val steps = max(Math.abs(dx), Math.abs(dy))
        if (steps == 0) return
        for (i in 0..steps) {
            val px = x1 + dx * i / steps
            val py = y1 + dy * i / steps
            guiGraphics.fill(px, py, px + 2, py + 2, color)
        }
    }

    private fun renderStatus(guiGraphics: GuiGraphics, state: State?, x: Int, y: Int, w: Int, h: Int) {
        val key = when (state) {
            State.FAILED -> "gui.cbbees.schematic_preview.failed"
            State.LOADING -> "gui.cbbees.browse_online.loading_preview"
            else -> return
        }
        val font = Minecraft.getInstance().font
        val lines = font.split(Component.translatable(key), w - 8)
        lines.forEachIndexed { i, line ->
            guiGraphics.drawCenteredString(font, line, x + w / 2, y + h / 2 - lines.size * 5 + i * 10, 0x888888)
        }
    }

    /** Returns the material list for the given schematic, or an empty list while it is loading. */
    fun getMaterials(key: String): List<MaterialEntry> = preview(key)?.materials ?: emptyList()

    /** Returns the schematic size, or [Vec3i.ZERO] while it is loading. */
    fun getSize(key: String): Vec3i = preview(key)?.size ?: Vec3i.ZERO

    /**
     * Builds the material list using Create's [ItemRequirement] system.
     * This correctly handles multi-block structures (belts), encased blocks,
     * double slabs, and other special cases.
     */
    private fun buildMaterialList(level: SchematicLevel): List<MaterialEntry> {
        val counts = mutableMapOf<Item, Int>()
        val fluidAmounts = mutableMapOf<Fluid, Int>()
        val bounds = level.bounds

        for (blockPos in BlockPos.betweenClosed(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()
        )) {
            val state = level.getBlockState(blockPos)
            if (state.isAir) continue

            if (state.fluidState.isSource) fluidAmounts.merge(state.fluidState.type, FluidType.BUCKET_VOLUME, Int::plus)

            val blockEntity = level.getBlockEntity(blockPos)
            val requirement = ItemRequirement.of(state, blockEntity)

            if (requirement.isEmpty || requirement.isInvalid) continue

            for (stackReq in requirement.requiredItems) {
                if (stackReq.usage != ItemRequirement.ItemUseType.CONSUME) continue
                val item = stackReq.stack.item
                val amount = stackReq.stack.count
                counts[item] = (counts[item] ?: 0) + amount
            }
        }

        val items = counts.entries
            .sortedByDescending { it.value }
            .map { (item, count) -> ItemStack(item).let { MaterialEntry(it, "x$count", it.hoverName, compactCount(count)) } }
        val fluids = fluidAmounts.entries
            .sortedByDescending { it.value }
            .map { (fluid, amount) ->
                val bucket = fluid.bucket.takeIf { it != Items.AIR } ?: Items.BUCKET
                MaterialEntry(ItemStack(bucket), "${amount}mB", FluidStack(fluid, amount).hoverName, compactCount(amount / FluidType.BUCKET_VOLUME))
            }
        return items + fluids
    }

    private fun compactCount(count: Int): String = when {
        count < 1000 -> count.toString()
        count < 10_000 -> "%.1fk".format(Locale.ROOT, count / 1000f)
        else -> "${count / 1000}k"
    }

    /** Clears the cache. Call when the screen closes. */
    fun clear() {
        cache.values.filterIsInstance<Loading>().forEach { it.template.cancel(true) }
        cache.clear()
    }
}

/**
 * Camera of an interactive schematic preview: left-drag rotates, right-drag pans and scrolling zooms.
 * Screens forward the mouse presses that start on the preview, plus drags, releases and scrolls.
 */
@OnlyIn(Dist.CLIENT)
class PreviewCamera {

    private var rotX = DEFAULT_ROT_X
    private var rotY = DEFAULT_ROT_Y
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f

    private var pressedButton: Int? = null
    private var dragDistance = 0.0

    fun reset() {
        rotX = DEFAULT_ROT_X
        rotY = DEFAULT_ROT_Y
        zoom = 1f
        panX = 0f
        panY = 0f
    }

    /** A press of [button] on the preview, which starts a drag. */
    fun press(button: Int) {
        pressedButton = button
        dragDistance = 0.0
    }

    /** Rotates or pans while a press on the preview is held; false when there is none. */
    fun drag(button: Int, dragX: Double, dragY: Double): Boolean {
        if (button != pressedButton) return false
        dragDistance += hypot(dragX, dragY)
        when (button) {
            LEFT -> {
                rotY += dragX.toFloat()
                rotX = (rotX - dragY.toFloat()).coerceIn(-90f, 90f)
            }
            RIGHT -> {
                panX += dragX.toFloat()
                panY += dragY.toFloat()
            }
        }
        return true
    }

    /** Ends the press of [button]: true if it was a click rather than a drag, null if it didn't start on the preview. */
    fun release(button: Int): Boolean? {
        if (button != pressedButton) return null
        pressedButton = null
        return dragDistance <= CLICK_DISTANCE
    }

    fun scroll(scrollY: Double) {
        zoom = (zoom + scrollY.toFloat() * 0.1f).coerceIn(0.2f, 5f)
    }

    fun render(key: String, guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, maxLayer: Int = Int.MAX_VALUE) {
        SchematicPreviewRenderer.renderPreview(key, guiGraphics, x, y, w, h, rotX, rotY, zoom, panX, panY, maxLayer)
        SchematicPreviewRenderer.renderAxisIndicator(guiGraphics, x, y, w, h, rotX, rotY)
    }

    private companion object {
        const val LEFT = 0
        const val RIGHT = 1
        const val DEFAULT_ROT_X = 30f
        const val DEFAULT_ROT_Y = -45f
        const val CLICK_DISTANCE = 4.0
    }
}
