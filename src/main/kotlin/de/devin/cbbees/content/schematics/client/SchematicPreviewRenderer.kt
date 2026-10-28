package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Axis
import com.simibubi.create.content.schematics.requirement.ItemRequirement
import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.CreateBuzzyBeez
import com.mojang.blaze3d.platform.Lighting
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
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min

/**
 * Renders an isometric 3D preview of a schematic in GUI space.
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
        val renderer = GhostSchematicRenderer(level, renderAllBlockEntities = true).also { it.prebuildGeometry() }
        return Preview(renderer, template.size, buildMaterialList(level))
    }

    /**
     * Renders an isometric 3D preview of the given schematic in the specified GUI rectangle.
     * Uses fixed isometric angles (30° X, -45° Y) and auto-fit zoom.
     */
    fun renderPreview(filename: String, guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        renderPreview(filename, guiGraphics, x, y, w, h, 30f, -45f, 1f)
    }

    /**
     * Renders a 3D preview with custom rotation and zoom.
     *
     * @param rotationX  pitch in degrees (default isometric: 30)
     * @param rotationY  yaw in degrees (default isometric: -45)
     * @param zoom       multiplier on the auto-fit scale (1.0 = fit to rect)
     */
    fun renderPreview(
        filename: String, guiGraphics: GuiGraphics,
        x: Int, y: Int, w: Int, h: Int,
        rotationX: Float, rotationY: Float, zoom: Float
    ) {
        val preview = preview(filename)
        if (preview == null) {
            renderStatus(guiGraphics, state(filename), x, y, w, h)
            return
        }
        val size = preview.size

        guiGraphics.enableScissor(x, y, x + w, y + h)

        val pose = guiGraphics.pose()
        pose.pushPose()

        // Position at center of preview area, with Z depth for proper ordering
        pose.translate((x + w / 2.0), (y + h / 2.0), 150.0)

        // Scale to fit the preview rectangle, then apply user zoom
        val maxDim = max(size.x, max(size.y, size.z)).toFloat()
        val scale = min(w, h) / (maxDim * 1.6f) * zoom
        pose.scale(scale, -scale, scale) // Negative Y because GUI Y is downward

        // Rotation (isometric default: 30° X, -45° Y)
        pose.mulPose(Axis.XP.rotationDegrees(rotationX))
        pose.mulPose(Axis.YP.rotationDegrees(rotationY))

        // Center the schematic at origin
        pose.translate(-size.x / 2.0, -size.y / 2.0, -size.z / 2.0)

        RenderSystem.enableDepthTest()
        Lighting.setupFor3DItems()
        val buffers = DefaultSuperRenderTypeBuffer.getInstance()
        preview.renderer.render(pose, buffers)
        buffers.draw()
        pose.popPose()
        guiGraphics.disableScissor()
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
        val origin = org.joml.Vector4f(0f, 0f, 0f, 1f).also { matrix.transform(it) }
        val xEnd = org.joml.Vector4f(1f, 0f, 0f, 1f).also { matrix.transform(it) }
        val yEnd = org.joml.Vector4f(0f, 1f, 0f, 1f).also { matrix.transform(it) }
        val zEnd = org.joml.Vector4f(0f, 0f, 1f, 1f).also { matrix.transform(it) }
        val labelDist = 1.3f
        val xLabel = org.joml.Vector4f(labelDist, 0f, 0f, 1f).also { matrix.transform(it) }
        val yLabel = org.joml.Vector4f(0f, labelDist, 0f, 1f).also { matrix.transform(it) }
        val zLabel = org.joml.Vector4f(0f, 0f, labelDist, 1f).also { matrix.transform(it) }

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
