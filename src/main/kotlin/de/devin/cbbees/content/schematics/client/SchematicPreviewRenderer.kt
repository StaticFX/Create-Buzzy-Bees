package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Axis
import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.schematics.SchematicItem
import com.simibubi.create.content.schematics.requirement.ItemRequirement
import net.createmod.catnip.levelWrappers.SchematicLevel
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.util.RandomSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.level.material.Fluid
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.client.model.data.ModelData
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidType
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Renders an isometric 3D preview of a schematic in GUI space.
 *
 * Loads the schematic into a [SchematicLevel] and renders each block
 * using [net.minecraft.client.renderer.block.BlockRenderDispatcher.renderSingleBlock].
 * Caches the loaded level per filename.
 */
@OnlyIn(Dist.CLIENT)
object SchematicPreviewRenderer {

    /** Material entry: an item stack (with count=1 for display) and total count. */
    data class MaterialEntry(val icon: ItemStack, val amount: String, val name: Component)

    private var cachedFilename: String? = null
    private var cachedLevel: SchematicLevel? = null
    private var cachedSize: Vec3i = Vec3i.ZERO
    private var cachedMaterials: List<MaterialEntry> = emptyList()

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
        val level = getOrLoadLevel(filename) ?: return
        val size = cachedSize
        if (size == Vec3i.ZERO) return

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

        // Render each block
        val dispatcher = Minecraft.getInstance().blockRenderer
        val bufferSource = guiGraphics.bufferSource()
        val bounds = level.bounds

        RenderSystem.enableDepthTest()

        for (blockPos in BlockPos.betweenClosed(
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()
        )) {
            val state = level.getBlockState(blockPos)
            if (state.isAir) continue
            if (state.renderShape == RenderShape.INVISIBLE) continue

            pose.pushPose()
            pose.translate(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())

            try {
                // Query block entity for model data (Create blocks need this for correct rendering)
                val blockEntity = level.getBlockEntity(blockPos)
                val bakedModel = dispatcher.getBlockModel(state)
                val modelData = if (blockEntity != null) {
                    val beData = blockEntity.modelData
                    bakedModel.getModelData(level, blockPos, state, beData)
                } else {
                    ModelData.EMPTY
                }

                // Render the baked block model directly instead of using renderSingleBlock,
                // which would render ENTITYBLOCK_ANIMATED blocks (shafts, belts, crushing wheels,
                // gearboxes) as their item form rather than their actual block model.
                val color = Minecraft.getInstance().blockColors.getColor(state, null, null, 0)
                val r = (color shr 16 and 0xFF) / 255f
                val g = (color shr 8 and 0xFF) / 255f
                val b = (color and 0xFF) / 255f
                val random = RandomSource.create(state.getSeed(blockPos))

                for (renderType in bakedModel.getRenderTypes(state, random, modelData)) {
                    dispatcher.modelRenderer.renderModel(
                        pose.last(), bufferSource.getBuffer(renderType),
                        state, bakedModel, r, g, b,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        modelData, renderType
                    )
                }
            } catch (_: Exception) {
                // Skip blocks that fail to render
            }

            pose.popPose()
        }

        // Render block entities (belts, kinetic blocks, etc.) via their BlockEntityRenderers.
        // The baked model pass above handles static geometry; this pass adds the dynamic parts
        // that only exist in BlockEntityRenderer (e.g. the belt strip, rotating shafts).
        val beDispatcher = Minecraft.getInstance().blockEntityRenderDispatcher
        for (blockEntity in level.renderedBlockEntities) {
            @Suppress("UNCHECKED_CAST")
            val beRenderer = beDispatcher.getRenderer(blockEntity) as? BlockEntityRenderer<Any> ?: continue
            val pos = blockEntity.blockPos

            pose.pushPose()
            pose.translate(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

            try {
                beRenderer.render(
                    blockEntity, 0f, pose, bufferSource,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
                )
            } catch (_: Exception) {
                // Skip block entities that fail to render
            }

            pose.popPose()
        }

        bufferSource.endBatch()
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

    private fun getOrLoadLevel(filename: String): SchematicLevel? {
        if (filename == cachedFilename) return cachedLevel

        cachedFilename = filename
        cachedLevel = null
        cachedSize = Vec3i.ZERO

        try {
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return null
            val player = mc.player ?: return null

            val fakeStack = com.simibubi.create.AllItems.SCHEMATIC.asStack()
            fakeStack.set(AllDataComponents.SCHEMATIC_FILE, filename)
            fakeStack.set(AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
            fakeStack.set(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO)
            fakeStack.set(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE)
            fakeStack.set(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE)
            fakeStack.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)

            val template = SchematicItem.loadSchematic(level, fakeStack)
            if (template.size == Vec3i.ZERO) return null

            cachedSize = template.size

            val schematicLevel = SchematicLevel(level)
            val settings = StructurePlaceSettings()
            settings.rotation = Rotation.NONE
            settings.mirror = Mirror.NONE

            template.placeInWorld(
                schematicLevel, BlockPos.ZERO, BlockPos.ZERO,
                settings, schematicLevel.random, Block.UPDATE_CLIENTS
            )

            for (blockEntity in schematicLevel.blockEntities) {
                blockEntity.setLevel(schematicLevel)
            }

            cachedLevel = schematicLevel
            buildMaterialList(schematicLevel)
            return schematicLevel
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Loads a schematic from raw (GZIPped) NBT bytes into the cache.
     * Use [key] as the cache identifier for subsequent [renderPreview] / [getMaterials] calls.
     *
     * @return true if the schematic was loaded successfully
     */
    fun loadFromNbtBytes(key: String, nbtBytes: ByteArray): Boolean {
        if (key == cachedFilename) return cachedLevel != null

        cachedFilename = key
        cachedLevel = null
        cachedSize = Vec3i.ZERO
        cachedMaterials = emptyList()

        try {
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return false

            val stream = DataInputStream(BufferedInputStream(GZIPInputStream(ByteArrayInputStream(nbtBytes))))
            val nbt = NbtIo.read(stream, NbtAccounter.create(0x20000000L))
            val template = StructureTemplate()
            template.load(level.holderLookup(Registries.BLOCK), nbt)

            if (template.size == Vec3i.ZERO) return false

            cachedSize = template.size

            val schematicLevel = SchematicLevel(level)
            val settings = StructurePlaceSettings()
            settings.rotation = Rotation.NONE
            settings.mirror = Mirror.NONE

            template.placeInWorld(
                schematicLevel, BlockPos.ZERO, BlockPos.ZERO,
                settings, schematicLevel.random, Block.UPDATE_CLIENTS
            )

            for (blockEntity in schematicLevel.blockEntities) {
                blockEntity.setLevel(schematicLevel)
            }

            cachedLevel = schematicLevel
            buildMaterialList(schematicLevel)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Returns the material list for the given schematic.
     * Sorted by count descending. Loads the schematic if not cached.
     */
    fun getMaterials(filename: String): List<MaterialEntry> {
        getOrLoadLevel(filename)
        return cachedMaterials
    }

    /** Returns the schematic size, or [Vec3i.ZERO] if not loaded. */
    fun getSize(filename: String): Vec3i {
        getOrLoadLevel(filename)
        return cachedSize
    }

    /**
     * Builds the material list using Create's [ItemRequirement] system.
     * This correctly handles multi-block structures (belts), encased blocks,
     * double slabs, and other special cases.
     */
    private fun buildMaterialList(level: SchematicLevel) {
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
            .map { (item, count) -> ItemStack(item).let { MaterialEntry(it, "x$count", it.hoverName) } }
        val fluids = fluidAmounts.entries
            .sortedByDescending { it.value }
            .map { (fluid, amount) ->
                val bucket = fluid.bucket.takeIf { it != Items.AIR } ?: Items.BUCKET
                MaterialEntry(ItemStack(bucket), "${amount}mB", FluidStack(fluid, amount).hoverName)
            }
        cachedMaterials = items + fluids
    }

    /** Clears the cache. Call when the screen closes. */
    fun clear() {
        cachedFilename = null
        cachedLevel = null
        cachedSize = Vec3i.ZERO
        cachedMaterials = emptyList()
    }
}
