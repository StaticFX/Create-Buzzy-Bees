package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.content.schematics.SchematicItem
import com.simibubi.create.content.schematics.client.SchematicRenderer
import net.minecraft.client.renderer.RenderType.chunkBufferLayers
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.config.CBBeesClientConfig
import de.devin.cbbees.content.beehive.client.ClientJobCache
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.util.ClientSide
import net.minecraft.world.phys.Vec3
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.impl.client.render.ColoringVertexConsumer
import net.createmod.catnip.levelWrappers.SchematicLevel
import net.createmod.catnip.outliner.AABBOutline
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer
import net.createmod.catnip.render.SuperRenderTypeBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import java.util.UUID

/**
 * Renders ghost blocks for incomplete construction tasks using Create's
 * [SchematicRenderer] pipeline. Loads the actual `.nbt` schematic file
 * on the client and uses [SchematicLevel] + [StructurePlaceSettings] to
 * populate a virtual world with full BlockEntity data — identical to
 * Create's own SchematicHandler.setupRenderer().
 *
 * Blocks that are already placed in the real world are removed from the
 * virtual level to prevent z-fighting. The entire preview is rendered
 * semi-transparently using [ColoringVertexConsumer].
 */
@ClientSide
object ConstructionRenderer {

    private const val GHOST_ALPHA = 0.5f

    private const val NORMAL_COLOR = 0x6886c5
    private const val STUCK_COLOR = 0xFF5555

    private val outlineCache = mutableMapOf<UUID, AABBOutline>()

    private val outlineBoundsCache = mutableMapOf<UUID, AABB>()

    private val rendererCache = mutableMapOf<UUID, JobRenderer>()

    private var lastDataVersion = -1L

    private var lastBlockCheckTick = 0L
    private const val BLOCK_CHECK_INTERVAL = 20L

    private class JobRenderer(
        val renderer: SchematicRenderer,
        val schematicLevel: SchematicLevel,
        val anchor: BlockPos
    )

    /**
     * Wraps a [SuperRenderTypeBuffer] to apply semi-transparency to all
     * vertex output via [ColoringVertexConsumer].
     */
    private class TransparentBuffer(
        private val delegate: SuperRenderTypeBuffer,
        private val alpha: Float
    ) : SuperRenderTypeBuffer {

        private val chunkLayers = chunkBufferLayers().toSet()

        private fun wrap(consumer: VertexConsumer): VertexConsumer =
            ColoringVertexConsumer(consumer, 1f, 1f, 1f, alpha)

        override fun getBuffer(type: RenderType): VertexConsumer {
            // Redirect opaque render types to translucent variants so GL
            // blending is enabled and the vertex alpha from ColoringVertexConsumer
            // actually takes effect. This covers both chunk layers (solid, cutout)
            // and entity layers (entitySolid, entityCutout — used by block entity
            // renderers like chests, signs, beds, etc.).
            val redirected = if (type in chunkLayers) RenderType.translucent() else type
            return wrap(delegate.getBuffer(redirected))
        }

        override fun getEarlyBuffer(type: RenderType): VertexConsumer = wrap(delegate.getEarlyBuffer(type))
        override fun getLateBuffer(type: RenderType): VertexConsumer = wrap(delegate.getLateBuffer(type))
        override fun draw() = delegate.draw()
        override fun draw(type: RenderType) = delegate.draw(type)
    }

    @SubscribeEvent
    @JvmStatic
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return
        if (!CBBeesClientConfig.showConstructionGhosts.get()) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val profiler = level.profiler

        val jobs = ClientJobCache.getAllJobs()
        if (jobs.isEmpty()) {
            outlineCache.clear()
            outlineBoundsCache.clear()
            rendererCache.clear()
            lastDataVersion = -1L
            return
        }

        profiler.push("cbbees_constructionGhosts")

        val dataVersion = ClientJobCache.version
        val gameTick = level.gameTime
        val shouldCheckBlocks = gameTick - lastBlockCheckTick >= BLOCK_CHECK_INTERVAL
        if (dataVersion != lastDataVersion || shouldCheckBlocks) {
            profiler.push("rebuildCache")
            rebuildCache(jobs, level, shouldCheckBlocks)
            lastDataVersion = dataVersion
            if (shouldCheckBlocks) lastBlockCheckTick = gameTick
            profiler.pop()
        }

        for (job in jobs) {
            val outline = outlineCache[job.jobId] ?: continue
            val color = if (job.reason != null) STUCK_COLOR else NORMAL_COLOR
            outline.params.colored(color)
        }

        val poseStack = event.poseStack
        val camera = mc.gameRenderer.mainCamera.position
        val superBuffer = DefaultSuperRenderTypeBuffer.getInstance()
        val opacity = CBBeesClientConfig.ghostBlockOpacity.get().toFloat()
        val transparentBuffer = TransparentBuffer(superBuffer, opacity)

        profiler.push("renderGhosts")
        for ((_, jobRenderer) in rendererCache) {
            poseStack.pushPose()
            poseStack.translate(-camera.x, -camera.y, -camera.z)
            jobRenderer.renderer.render(poseStack, transparentBuffer)
            poseStack.popPose()
        }
        profiler.pop()

        profiler.push("renderOutlines")
        val pt = AnimationTickHolder.getPartialTicks()
        for ((_, outline) in outlineCache) {
            outline.render(poseStack, superBuffer, camera, pt)
        }
        profiler.pop()

        superBuffer.draw()
        RenderSystem.enableCull()
        profiler.pop()
    }

    private fun rebuildCache(jobs: List<ClientJobInfo>, clientLevel: Level, checkBlocks: Boolean) {
        val activeJobIds = jobs.map { it.jobId }.toSet()
        outlineCache.keys.removeAll { it !in activeJobIds }
        outlineBoundsCache.keys.removeAll { it !in activeJobIds }
        rendererCache.keys.removeAll { it !in activeJobIds }

        for (job in jobs) {
            val existing = rendererCache[job.jobId]
            if (existing != null) {
                if (checkBlocks && removePlacedBlocks(existing.schematicLevel, clientLevel)) {
                    existing.renderer.update()
                }
            } else {
                val renderer = buildSchematicRenderer(job, clientLevel)
                if (renderer != null) {
                    rendererCache[job.jobId] = renderer
                }

                if (job.jobId !in outlineBoundsCache) {
                    val positions: Set<BlockPos> = if (renderer != null) {
                        renderer.schematicLevel.blockMap.keys
                    } else {
                        job.batches.map { it.target }.toSet()
                    }
                    if (positions.isNotEmpty()) {
                        val bounds = AABB(
                            positions.minOf { it.x }.toDouble(),
                            positions.minOf { it.y }.toDouble(),
                            positions.minOf { it.z }.toDouble(),
                            (positions.maxOf { it.x } + 1).toDouble(),
                            (positions.maxOf { it.y } + 1).toDouble(),
                            (positions.maxOf { it.z } + 1).toDouble()
                        )
                        outlineBoundsCache[job.jobId] = bounds
                        val outline = AABBOutline(bounds)
                        val color = if (job.reason != null) STUCK_COLOR else NORMAL_COLOR
                        outline.params
                            .colored(color)
                            .withFaceTexture(AllSpecialTextures.CHECKERED)
                            .lineWidth(1 / 16f)
                        outlineCache[job.jobId] = outline
                    }
                }
            }

        }
    }

    /**
     * Removes blocks from the [SchematicLevel] that already exist in the real world.
     * Returns true if any blocks were removed (renderer needs update).
     */
    private fun removePlacedBlocks(schematicLevel: SchematicLevel, clientLevel: Level): Boolean {
        val blockMap = schematicLevel.blockMap
        val toRemove = mutableListOf<BlockPos>()

        for ((localPos, state) in blockMap) {
            val worldPos = localPos.offset(schematicLevel.anchor)
            if (clientLevel.getBlockState(worldPos) == state) {
                toRemove.add(localPos)
            }
        }

        if (toRemove.isEmpty()) return false

        for (pos in toRemove) {
            blockMap.remove(pos)
        }
        return true
    }

    /**
     * Loads the schematic `.nbt` file from disk and builds a [SchematicRenderer]
     * using the same approach as Create's SchematicHandler.setupRenderer().
     */
    private fun buildSchematicRenderer(job: ClientJobInfo, clientLevel: Level): JobRenderer? {
        val placement = job.schematicPlacement ?: return buildFallbackRenderer(job, clientLevel)
        val schematicFile = placement.file
        val anchor = placement.anchor
        val rotation = placement.rotation
        val mirror = placement.mirror

        try {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return buildFallbackRenderer(job, clientLevel)

            val fakeStack = com.simibubi.create.AllItems.SCHEMATIC.asStack()
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_FILE, schematicFile)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ANCHOR, anchor)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ROTATION, rotation)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_MIRROR, mirror)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_DEPLOYED, true)

            val template = SchematicItem.loadSchematic(clientLevel, fakeStack)
            if (template.size == net.minecraft.core.Vec3i.ZERO) {
                CreateBuzzyBeez.LOGGER.warn("Schematic template is empty for file: $schematicFile")
                return buildFallbackRenderer(job, clientLevel)
            }

            val schematicLevel = SchematicLevel(clientLevel)
            val settings = StructurePlaceSettings()
            settings.rotation = rotation
            settings.mirror = mirror

            template.placeInWorld(schematicLevel, anchor, anchor, settings, schematicLevel.random, Block.UPDATE_CLIENTS)

            for (blockEntity in schematicLevel.blockEntities) {
                blockEntity.setLevel(schematicLevel)
            }
            fixControllerBlockEntities(schematicLevel)

            removePlacedBlocks(schematicLevel, clientLevel)

            return JobRenderer(GhostSchematicRenderer(schematicLevel), schematicLevel, anchor)
        } catch (e: Exception) {
            CreateBuzzyBeez.LOGGER.error("Failed to load schematic for rendering: $schematicFile", e)
            return buildFallbackRenderer(job, clientLevel)
        }
    }

    /**
     * Fallback: build renderer from ghost block data when schematic file
     * is unavailable (e.g., file deleted, non-schematic job).
     */
    private fun buildFallbackRenderer(job: ClientJobInfo, clientLevel: Level): JobRenderer? {
        val allGhosts = mutableMapOf<BlockPos, BlockState>()
        for (batch in job.batches) {
            if (batch.status == "COMPLETED") continue
            for ((pos, state) in batch.ghostBlocks) {
                if (clientLevel.getBlockState(pos) == state) continue
                allGhosts[pos] = state
            }
        }
        if (allGhosts.isEmpty()) return null

        val ghostLevel = GhostBlockLevel(clientLevel)
        ghostLevel.populate(allGhosts)
        return JobRenderer(GhostSchematicRenderer(ghostLevel), ghostLevel, BlockPos.ZERO)
    }

    /**
     * Finds the job whose AABB is hit by the given ray (from [start] in direction [dir]).
     * Returns the jobId of the closest hit, or null if no job AABB is hit within [maxRange].
     */
    fun findJobAtRay(start: Vec3, dir: Vec3, maxRange: Double): UUID? {
        var bestDist = maxRange
        var bestJob: UUID? = null

        for ((jobId, bounds) in outlineBoundsCache) {
            val hit = bounds.clip(start, start.add(dir.scale(maxRange)))
            if (hit.isPresent) {
                val dist = hit.get().distanceTo(start)
                if (dist < bestDist) {
                    bestDist = dist
                    bestJob = jobId
                }
            }
        }
        return bestJob
    }

    /**
     * Returns the cached [ClientJobInfo] for a given job ID, or null.
     */
    fun getJobInfo(jobId: UUID): ClientJobInfo? {
        return ClientJobCache.getAllJobs().firstOrNull { it.jobId == jobId }
    }

    /**
     * Mirrors Create's SchematicHandler.fixControllerBlockEntities() —
     * adjusts multi-block controller references that may have shifted
     * during template placement.
     */
    private fun fixControllerBlockEntities(level: SchematicLevel) {
        for (blockEntity in level.blockEntities) {
            if (blockEntity !is IMultiBlockEntityContainer) continue
            val lastKnown = blockEntity.lastKnownPos ?: continue
            val current = blockEntity.blockPos ?: continue
            if (blockEntity.isController) continue
            if (lastKnown != current) {
                val newControllerPos = blockEntity.controller.offset(current.subtract(lastKnown))
                if (blockEntity is SmartBlockEntity) {
                    blockEntity.markVirtual()
                }
                blockEntity.controller = newControllerPos
            }
        }
    }
}
