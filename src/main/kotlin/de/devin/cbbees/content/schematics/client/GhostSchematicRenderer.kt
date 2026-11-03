package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.content.schematics.client.SchematicRenderer
import com.simibubi.create.foundation.render.BlockEntityRenderHelper
import de.devin.cbbees.config.CBBeesClientConfig
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.levelWrappers.SchematicLevel
import net.createmod.catnip.render.ShadedBlockSbbBuilder
import net.createmod.catnip.render.SuperByteBuffer
import net.createmod.catnip.render.SuperRenderTypeBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.block.ModelBlockRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.client.model.data.ModelData
import java.util.BitSet
import java.util.TreeMap
import org.joml.Vector3f

/**
 * Extends Create's [SchematicRenderer] to render ALL blocks regardless of
 * [RenderShape]. The parent only renders [RenderShape.MODEL]; this also
 * renders [RenderShape.ENTITYBLOCK_ANIMATED] blocks (crushing wheels, etc.)
 * via their baked block model.
 *
 * Maintains its own geometry buffer cache and overrides [render] to skip
 * block entity rendering, which is the dominant per-frame cost for large
 * schematics (Create blocks nearly all have block entities). Blocks without a
 * model of their own, like belts, are drawn by their block entity only, so
 * those block entities are always rendered.
 *
 * Geometry can be pre-built on a background thread via [prebuildGeometry],
 * eliminating the frame stutter that would otherwise occur on first render.
 *
 * With [sliced], geometry is cached per Y layer, each built as if nothing sat above it,
 * so [render] can hide upper layers without rebuilding and the cut layer stays closed.
 */
class GhostSchematicRenderer(
    world: SchematicLevel,
    private val origin: BlockPos = BlockPos.ZERO,
    private val renderAllBlockEntities: Boolean = false,
    private val sliced: Boolean = false,
) : SchematicRenderer(world) {

    private val anchor: BlockPos = world.anchor

    /** Own buffer cache — parent's is private and we need to skip block entity rendering. */
    private val ghostBufferCache = LinkedHashMap<RenderType, SuperByteBuffer>()
    private val sliceCache = TreeMap<Int, Map<RenderType, SuperByteBuffer>>()
    private var modelOnlyBlockEntities: List<BlockEntity> = emptyList()
    private var needsRedraw = true

    /** Schematic positions per 16³ section, for [renderGhost]. */
    private val sectionBlocks: Map<Long, List<BlockPos>> by lazy {
        schematic.blockMap.keys.map(BlockPos::immutable).groupBy(::sectionKey)
    }
    private val sectionMeshes = HashMap<Long, GhostMesh>()
    private val dirtySections = LinkedHashSet<Long>()
    private var sectionAlpha = Float.NaN
    private var blockEntitiesStale = true

    @Volatile
    var isReady = false
        private set

    companion object {
        private val OBJECTS = ThreadLocal.withInitial { Objects() }
        private const val SECTION_BUILD_BUDGET_NANOS = 3_000_000L
        private val SECTION_NEIGHBOURS = listOf(BlockPos.ZERO) + Direction.entries.map { BlockPos.ZERO.relative(it) }
    }

    private class LayerCutLevel(private val level: BlockAndTintGetter, private val maxY: Int) : BlockAndTintGetter by level {
        override fun getBlockState(pos: BlockPos): BlockState =
            if (pos.y > maxY) Blocks.AIR.defaultBlockState() else level.getBlockState(pos)

        override fun getFluidState(pos: BlockPos): FluidState =
            if (pos.y > maxY) Fluids.EMPTY.defaultFluidState() else level.getFluidState(pos)

        override fun getBlockEntity(pos: BlockPos): BlockEntity? =
            if (pos.y > maxY) null else level.getBlockEntity(pos)

        override fun getBrightness(layer: LightLayer, pos: BlockPos): Int = level.getBrightness(layer, pos)

        override fun getRawBrightness(pos: BlockPos, amount: Int): Int = level.getRawBrightness(pos, amount)

        override fun canSeeSky(pos: BlockPos): Boolean = level.canSeeSky(pos)
    }

    private class Objects {
        val poseStack = PoseStack()
        val random: RandomSource = RandomSource.createNewThreadLocalInstance()
        val mutableBlockPos = BlockPos.MutableBlockPos()
        val sbbBuilder: ShadedBlockSbbBuilder = ShadedBlockSbbBuilder.create()
    }

    /**
     * Marks geometry for rebuild on the next [render] call.
     * Overrides the parent to also set our own [needsRedraw] flag,
     * since our [render] override uses [ghostBufferCache] instead of the parent's.
     */
    override fun update() {
        super.update()
        needsRedraw = true
    }

    /**
     * Pre-builds geometry for all render layers. Safe to call from a background thread
     * (uses ThreadLocal objects; block model lookups are read-only).
     * After this returns, [render] will use the cached buffers with no stutter.
     */
    fun prebuildGeometry() {
        ghostBufferCache.clear()
        sliceCache.clear()
        if (sliced) {
            schematic.blockMap.entries.groupBy { it.key.y }.forEach { (y, blocks) ->
                val level = LayerCutLevel(schematic, y + anchor.y)
                sliceCache[y] = RenderType.chunkBufferLayers().associateWith { tesselate(it, blocks, level) }.filterValues { !it.isEmpty }
            }
        } else {
            for (layer in RenderType.chunkBufferLayers()) {
                val buffer = drawLayer(layer)
                if (!buffer.isEmpty) {
                    ghostBufferCache[layer] = buffer
                }
            }
        }
        modelOnlyBlockEntities = schematic.renderedBlockEntities.filter { hasNoModel(it.blockState) }
        blockEntitiesStale = false
        needsRedraw = false
        isReady = true
    }

    private fun hasNoModel(state: BlockState): Boolean {
        val model = Minecraft.getInstance().blockRenderer.getBlockModel(state)
        val random = OBJECTS.get().random
        return (Direction.entries + null).all { model.getQuads(state, it, random, ModelData.EMPTY, null).isEmpty() }
    }

    /**
     * Renders cached static block geometry and block entities. Only blocks without a model of their
     * own render their block entity by default; [CBBeesClientConfig.renderGhostBlockEntities] or
     * [renderAllBlockEntities] add all others, and [CBBeesClientConfig.maxGhostBlockEntities] caps the total. The schematic is passed as
     * the level so Flywheel does not claim block entities that only exist in the preview.
     */
    override fun render(ms: PoseStack, buffers: SuperRenderTypeBuffer) = render(ms, buffers, Int.MAX_VALUE)

    /** Renders like [render], hiding every layer above [maxY] (schematic-local) when [sliced]. */
    fun render(ms: PoseStack, buffers: SuperRenderTypeBuffer, maxY: Int) {
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return

        if (needsRedraw) {
            prebuildGeometry()
        }

        for ((layer, buffer) in ghostBufferCache) {
            buffer.renderInto(ms, buffers.getBuffer(layer))
        }
        sliceCache.headMap(maxY, true).values.forEach { slice ->
            for ((layer, buffer) in slice) buffer.renderInto(ms, buffers.getBuffer(layer))
        }
        renderBlockEntities(ms, buffers, maxY)
    }

    /**
     * Renders the geometry translucent at [alpha] from [GhostMesh]es on the GPU, for ghosts drawn in
     * the world every frame. Block entities still go to [buffers], which should apply the same alpha.
     *
     * The geometry is split into 16³ sections, so [blocksChanged] only rebuilds the sections it touches,
     * and each frame builds sections for at most [SECTION_BUILD_BUDGET_NANOS]; a large ghost appears
     * over a few frames instead of stalling one.
     */
    fun renderGhost(ms: PoseStack, buffers: SuperRenderTypeBuffer, alpha: Float) {
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return

        val pose = ms.last().pose()
        val camera = GhostMesh.cameraIn(pose)
        if (alpha != sectionAlpha) {
            sectionAlpha = alpha
            dirtySections += sectionBlocks.keys
        }
        buildDirtySections(alpha, camera)

        GhostMesh.drawAll(sectionMeshes.entries.sortedByDescending { (key, _) -> sectionCenterDistSq(key, camera) }.map { it.value }, pose)

        if (blockEntitiesStale) {
            modelOnlyBlockEntities = schematic.renderedBlockEntities.filter { hasNoModel(it.blockState) }
            blockEntitiesStale = false
        }
        renderBlockEntities(ms, buffers, Int.MAX_VALUE)
    }

    /** Rebuilds only the sections around [positions] (schematic-local) after blocks were removed from the schematic. */
    fun blocksChanged(positions: Collection<BlockPos>) {
        for (pos in positions) {
            // A removed block on a section edge uncovers faces in the neighbouring section
            for (offset in SECTION_NEIGHBOURS) {
                val key = sectionKey(pos.offset(offset))
                if (key in sectionBlocks) dirtySections += key
            }
        }
        blockEntitiesStale = true
        needsRedraw = true
    }

    /** Frees the GPU meshes. Call on the render thread once this renderer is no longer used. */
    fun close() {
        sectionMeshes.values.forEach(GhostMesh::close)
        sectionMeshes.clear()
        sectionAlpha = Float.NaN
    }

    private fun buildDirtySections(alpha: Float, camera: Vector3f) {
        val deadline = System.nanoTime() + SECTION_BUILD_BUDGET_NANOS
        val iterator = dirtySections.iterator()
        while (iterator.hasNext() && System.nanoTime() < deadline) {
            val key = iterator.next()
            iterator.remove()
            val blocks = buildMap {
                sectionBlocks[key]?.forEach { pos -> schematic.blockMap[pos]?.let { put(pos, it) } }
            }
            val geometry = RenderType.chunkBufferLayers().map { tesselate(it, blocks.entries, schematic) }.filterNot { it.isEmpty }
            val mesh = if (geometry.isEmpty()) null else GhostMesh.build(geometry, alpha, camera)
            sectionMeshes.remove(key)?.close()
            mesh?.let { sectionMeshes[key] = it }
        }
    }

    private fun sectionCenterDistSq(key: Long, camera: Vector3f): Float {
        val dx = SectionPos.sectionToBlockCoord(SectionPos.x(key), 8) - origin.x - camera.x
        val dy = SectionPos.sectionToBlockCoord(SectionPos.y(key), 8) - origin.y - camera.y
        val dz = SectionPos.sectionToBlockCoord(SectionPos.z(key), 8) - origin.z - camera.z
        return dx * dx + dy * dy + dz * dz
    }

    private fun sectionKey(pos: BlockPos): Long =
        SectionPos.asLong(SectionPos.blockToSectionCoord(pos.x), SectionPos.blockToSectionCoord(pos.y), SectionPos.blockToSectionCoord(pos.z))

    private fun renderBlockEntities(ms: PoseStack, buffers: SuperRenderTypeBuffer, maxY: Int) {
        val candidates = if (renderAllBlockEntities || CBBeesClientConfig.renderGhostBlockEntities.get())
            (modelOnlyBlockEntities + schematic.renderedBlockEntities).distinct()
        else modelOnlyBlockEntities
        val blockEntities = candidates.filter { it.blockPos.y - anchor.y <= maxY }.take(CBBeesClientConfig.maxGhostBlockEntities.get())
        if (blockEntities.isEmpty()) return

        val shouldRender = BitSet(blockEntities.size)
        shouldRender.set(0, blockEntities.size)
        val errored = BitSet(blockEntities.size)
        ms.pushPose()
        ms.translate(-origin.x.toDouble(), -origin.y.toDouble(), -origin.z.toDouble())
        BlockEntityRenderHelper.renderBlockEntities(
            blockEntities, shouldRender, errored,
            null, schematic, ms, null, buffers,
            AnimationTickHolder.getPartialTicks()
        )
        ms.popPose()
    }

    override fun drawLayer(layer: RenderType): SuperByteBuffer = tesselate(layer, schematic.blockMap.entries, schematic)

    private fun tesselate(layer: RenderType, blocks: Collection<Map.Entry<BlockPos, BlockState>>, level: BlockAndTintGetter): SuperByteBuffer {
        val dispatcher = Minecraft.getInstance().blockRenderer
        val renderer = dispatcher.modelRenderer
        val objects = OBJECTS.get()

        val poseStack = objects.poseStack
        val random = objects.random
        val mutableBlockPos = objects.mutableBlockPos
        val renderWorld = schematic

        val sbbBuilder = objects.sbbBuilder
        sbbBuilder.begin()

        renderWorld.renderMode = true
        ModelBlockRenderer.enableCaching()

        // Iterate only over blocks that actually exist in the schematic,
        // not the entire bounding volume (which can be orders of magnitude larger)
        for ((localPos, state) in blocks) {
            if (state.renderShape == RenderShape.INVISIBLE) continue

            val pos = mutableBlockPos.setWithOffset(localPos, anchor)
            val model = dispatcher.getBlockModel(state)
            val blockEntity = renderWorld.getBlockEntity(localPos)
            val modelData = if (blockEntity != null) blockEntity.modelData else ModelData.EMPTY
            val finalModelData = model.getModelData(level, pos, state, modelData)
            val seed = state.getSeed(pos)
            random.setSeed(seed)
            if (model.getRenderTypes(state, random, finalModelData).contains(layer)) {
                poseStack.pushPose()
                poseStack.translate(
                    (localPos.x - origin.x).toDouble(),
                    (localPos.y - origin.y).toDouble(),
                    (localPos.z - origin.z).toDouble()
                )

                renderer.tesselateBlock(
                    level, model, state, pos, poseStack, sbbBuilder, true,
                    random, seed, OverlayTexture.NO_OVERLAY, finalModelData, layer
                )

                poseStack.popPose()
            }
        }

        ModelBlockRenderer.clearCache()
        renderWorld.renderMode = false

        return sbbBuilder.end()
    }
}
