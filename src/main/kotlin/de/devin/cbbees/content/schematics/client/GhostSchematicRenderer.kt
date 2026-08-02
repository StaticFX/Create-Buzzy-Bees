@file:Suppress("DEPRECATION")

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
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.client.model.data.ModelData
import java.util.BitSet

/**
 * Extends Create's [SchematicRenderer] to render ALL blocks regardless of
 * [RenderShape]. The parent only renders [RenderShape.MODEL]; this also
 * renders [RenderShape.ENTITYBLOCK_ANIMATED] blocks (crushing wheels, etc.)
 * via their baked block model.
 *
 * Maintains its own geometry buffer cache and overrides [render] to skip
 * block entity rendering, which is the dominant per-frame cost for large
 * schematics (Create blocks nearly all have block entities).
 *
 * Geometry can be pre-built on a background thread via [prebuildGeometry],
 * eliminating the frame stutter that would otherwise occur on first render.
 */
class GhostSchematicRenderer(world: SchematicLevel) : SchematicRenderer(world) {

    private val anchor: BlockPos = world.anchor

    /** Own buffer cache — parent's is private and we need to skip block entity rendering. */
    private val ghostBufferCache = LinkedHashMap<RenderType, SuperByteBuffer>()
    private var needsRedraw = true

    @Volatile
    var isReady = false
        private set

    companion object {
        private val OBJECTS = ThreadLocal.withInitial { Objects() }
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
        for (layer in RenderType.chunkBufferLayers()) {
            val buffer = drawLayer(layer)
            if (!buffer.isEmpty) {
                ghostBufferCache[layer] = buffer
            }
        }
        needsRedraw = false
        isReady = true
    }

    /**
     * Renders cached static block geometry and optionally block entities.
     * Block entity rendering is off by default for performance — controlled
     * by [CBBeesClientConfig.renderGhostBlockEntities] and capped by
     * [CBBeesClientConfig.maxGhostBlockEntities].
     */
    override fun render(ms: PoseStack, buffers: SuperRenderTypeBuffer) {
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return

        if (needsRedraw) {
            prebuildGeometry()
        }

        for ((layer, buffer) in ghostBufferCache) {
            buffer.renderInto(ms, buffers.getBuffer(layer))
        }

        if (CBBeesClientConfig.renderGhostBlockEntitiesSafe()) {
            val max = CBBeesClientConfig.maxGhostBlockEntitiesSafe()
            val allBEs = schematic.renderedBlockEntities.toList()
            val capped = if (allBEs.size > max) allBEs.subList(0, max) else allBEs
            val shouldRender = BitSet(capped.size)
            shouldRender.set(0, capped.size)
            val errored = BitSet(capped.size)
            BlockEntityRenderHelper.renderBlockEntities(
                capped, shouldRender, errored,
                null, mc.level, ms, null, buffers,
                AnimationTickHolder.getPartialTicks()
            )
        }
    }

    override fun drawLayer(layer: RenderType): SuperByteBuffer {
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
        for ((localPos, state) in renderWorld.blockMap) {
            if (state.renderShape == RenderShape.INVISIBLE) continue

            val pos = mutableBlockPos.setWithOffset(localPos, anchor)
            val model = dispatcher.getBlockModel(state)
            val blockEntity = renderWorld.getBlockEntity(localPos)
            val modelData = if (blockEntity != null) blockEntity.modelData else ModelData.EMPTY
            val finalModelData = model.getModelData(renderWorld, pos, state, modelData)
            val seed = state.getSeed(pos)
            random.setSeed(seed)
            if (model.getRenderTypes(state, random, finalModelData).contains(layer)) {
                poseStack.pushPose()
                poseStack.translate(localPos.x.toDouble(), localPos.y.toDouble(), localPos.z.toDouble())

                renderer.tesselateBlock(
                    renderWorld, model, state, pos, poseStack, sbbBuilder, true,
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
