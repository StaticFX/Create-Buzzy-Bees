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
import de.devin.cbbees.compat.sable.SableRenderSupport
import de.devin.cbbees.config.CBBeesClientConfig
import de.devin.cbbees.content.beehive.client.ClientJobCache
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.util.ClientSide
import dev.engine_room.flywheel.lib.transform.TransformStack
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
import net.minecraft.core.Vec3i
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

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

    private const val CONSTRUCTION_COLOR = 0x6886c5
    private const val DECONSTRUCTION_COLOR = 0xc56868
    private const val PICKUP_COLOR = 0x68c588
    private const val STUCK_COLOR = 0xFF5555

    private val outlineCache = mutableMapOf<UUID, AABBOutline>()

    private val outlineBoundsCache = mutableMapOf<UUID, AABB>()

    private val renderedOutlineBoundsCache = mutableMapOf<UUID, AABB>()

    /** Sable projection data for the clickable frame, including non-construction jobs. */
    private val areaProjectionCache = mutableMapOf<UUID, JobAreaProjection>()

    private val rendererCache = mutableMapOf<UUID, JobRenderer>()

    private var lastDataVersion = -1L

    private var lastBlockCheckTick = 0L
    private const val BLOCK_CHECK_INTERVAL = 20L

    private data class JobAreaProjection(
        val anchor: BlockPos,
        val usesLocalCoordinates: Boolean
    )

    private class JobRenderer(
        val renderer: SchematicRenderer,
        val schematicLevel: SchematicLevel,
        val projectionAnchor: BlockPos,
        val usesLocalCoordinates: Boolean,
        val renderOrigin: BlockPos,
        val size: Vec3i,
        val rotation: Rotation,
        val bounds: AABB
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

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val profiler = level.profiler
        val showGhosts = CBBeesClientConfig.showConstructionGhostsSafe()

        val jobs = ClientJobCache.getAllJobs()
        if (jobs.isEmpty()) {
            outlineCache.clear()
            outlineBoundsCache.clear()
            renderedOutlineBoundsCache.clear()
            areaProjectionCache.clear()
            rendererCache.clear()
            lastDataVersion = -1L
            return
        }

        profiler.push("cbbees_jobAreas")

        val dataVersion = ClientJobCache.version
        val gameTick = level.gameTime
        val shouldCheckBlocks = showGhosts &&
            gameTick - lastBlockCheckTick >= BLOCK_CHECK_INTERVAL
        if (dataVersion != lastDataVersion || shouldCheckBlocks) {
            profiler.push("rebuildCache")
            rebuildCache(jobs, level, shouldCheckBlocks)
            lastDataVersion = dataVersion
            if (shouldCheckBlocks) lastBlockCheckTick = gameTick
            profiler.pop()
        }

        val jobsById = jobs.associateBy { it.jobId }
        for (job in jobs) {
            val outline = outlineCache[job.jobId] ?: continue
            outline.params.colored(colorFor(job))
        }

        val poseStack = event.poseStack
        val camera = mc.gameRenderer.mainCamera.position
        val superBuffer = DefaultSuperRenderTypeBuffer.getInstance()
        val pt = AnimationTickHolder.getPartialTicks()

        // The ghost-block setting only controls schematic blocks. Job frames
        // and their coloured volumes remain visible so players can always
        // right-click the active area to open/cancel the job.
        if (showGhosts) {
            val opacity = CBBeesClientConfig.ghostBlockOpacitySafe().toFloat()
            val transparentBuffer = TransparentBuffer(superBuffer, opacity)

            profiler.push("renderGhosts")
            for ((_, jobRenderer) in rendererCache) {
                poseStack.pushPose()
                poseStack.translate(-camera.x, -camera.y, -camera.z)

                val projected = jobRenderer.usesLocalCoordinates &&
                    SableRenderSupport.applyProjectedLocalTransform(
                        poseStack,
                        level,
                        jobRenderer.projectionAnchor
                    )

                if (projected) {
                    val localOffset = jobRenderer.renderOrigin.subtract(jobRenderer.projectionAnchor)
                    TransformStack.of(poseStack).translate(Vec3.atLowerCornerOf(localOffset))
                } else {
                    TransformStack.of(poseStack).translate(Vec3.atLowerCornerOf(jobRenderer.renderOrigin))
                }

                val xO = jobRenderer.size.x / 2.0
                val zO = jobRenderer.size.z / 2.0
                poseStack.translate(xO, 0.0, zO)
                TransformStack.of(poseStack)
                    .rotateYDegrees(-(jobRenderer.rotation.ordinal * 90.0).toFloat())
                poseStack.translate(-xO, 0.0, -zO)
                jobRenderer.renderer.render(poseStack, transparentBuffer)
                poseStack.popPose()
            }
            profiler.pop()
        }

        profiler.push("renderJobAreas")
        renderedOutlineBoundsCache.clear()
        for ((jobId, outline) in outlineCache) {
            val rawBounds = outlineBoundsCache[jobId] ?: continue
            val projection = areaProjectionCache[jobId]
                ?: JobAreaProjection(BlockPos.ZERO, false)
            val color = jobsById[jobId]?.let(::colorFor) ?: CONSTRUCTION_COLOR

            if (projection.usesLocalCoordinates) {
                val localBounds = rawBounds.move(
                    -projection.anchor.x.toDouble(),
                    -projection.anchor.y.toDouble(),
                    -projection.anchor.z.toDouble()
                )
                outline.setBounds(localBounds)

                poseStack.pushPose()
                poseStack.translate(-camera.x, -camera.y, -camera.z)
                val projected = SableRenderSupport.applyProjectedLocalTransform(
                    poseStack,
                    level,
                    projection.anchor
                )
                if (projected) {
                    AreaTintRenderer.render(
                        poseStack,
                        superBuffer,
                        localBounds,
                        color
                    )
                    outline.render(poseStack, superBuffer, Vec3.ZERO, pt)
                }
                poseStack.popPose()

                if (!projected) {
                    outline.setBounds(rawBounds)

                    poseStack.pushPose()
                    poseStack.translate(-camera.x, -camera.y, -camera.z)
                    AreaTintRenderer.render(
                        poseStack,
                        superBuffer,
                        rawBounds,
                        color
                    )
                    poseStack.popPose()

                    outline.render(poseStack, superBuffer, camera, pt)
                }

                renderedOutlineBoundsCache[jobId] =
                    SableRenderSupport.projectAabbFromAnchor(
                        level,
                        rawBounds,
                        projection.anchor
                    ) ?: rawBounds
            } else {
                renderedOutlineBoundsCache[jobId] = rawBounds
                outline.setBounds(rawBounds)

                poseStack.pushPose()
                poseStack.translate(-camera.x, -camera.y, -camera.z)
                AreaTintRenderer.render(
                    poseStack,
                    superBuffer,
                    rawBounds,
                    color
                )
                poseStack.popPose()

                outline.render(poseStack, superBuffer, camera, pt)
            }
        }
        profiler.pop()

        superBuffer.draw()
        RenderSystem.enableCull()
        profiler.pop()
    }

    private fun colorFor(job: ClientJobInfo): Int {
        if (job.reason != null) return STUCK_COLOR
        return when (job.jobType) {
            JobType.Construction -> CONSTRUCTION_COLOR
            JobType.Deconstruction -> DECONSTRUCTION_COLOR
            JobType.Pickup -> PICKUP_COLOR
        }
    }

    private fun rebuildCache(jobs: List<ClientJobInfo>, clientLevel: Level, checkBlocks: Boolean) {
        val activeJobIds = jobs.map { it.jobId }.toSet()
        outlineCache.keys.removeAll { it !in activeJobIds }
        outlineBoundsCache.keys.removeAll { it !in activeJobIds }
        renderedOutlineBoundsCache.keys.removeAll { it !in activeJobIds }
        areaProjectionCache.keys.removeAll { it !in activeJobIds }
        rendererCache.keys.removeAll { it !in activeJobIds }

        for (job in jobs) {
            var renderer = rendererCache[job.jobId]
            if (renderer != null) {
                if (checkBlocks && removePlacedBlocks(
                        renderer.schematicLevel,
                        clientLevel,
                        renderer.renderOrigin,
                        renderer.size,
                        renderer.rotation,
                        renderer.usesLocalCoordinates
                    )
                ) {
                    renderer.renderer.update()
                }
            } else {
                renderer = buildSchematicRenderer(job, clientLevel)
                if (renderer != null) {
                    rendererCache[job.jobId] = renderer
                }
            }

            val positions: Set<BlockPos> = job.batches.map { it.target }.toSet()
            val bounds = renderer?.bounds ?: if (positions.isNotEmpty()) {
                AABB(
                    positions.minOf { it.x }.toDouble(),
                    positions.minOf { it.y }.toDouble(),
                    positions.minOf { it.z }.toDouble(),
                    (positions.maxOf { it.x } + 1).toDouble(),
                    (positions.maxOf { it.y } + 1).toDouble(),
                    (positions.maxOf { it.z } + 1).toDouble()
                )
            } else {
                null
            }

            if (bounds == null) {
                outlineCache.remove(job.jobId)
                outlineBoundsCache.remove(job.jobId)
                areaProjectionCache.remove(job.jobId)
                continue
            }

            outlineBoundsCache[job.jobId] = bounds

            areaProjectionCache[job.jobId] = if (renderer != null) {
                JobAreaProjection(
                    renderer.projectionAnchor,
                    renderer.usesLocalCoordinates
                )
            } else {
                findAreaProjection(clientLevel, bounds, positions)
            }

            val outline = outlineCache.getOrPut(job.jobId) {
                AABBOutline(bounds).also {
                    it.params
                        .withFaceTexture(AllSpecialTextures.CHECKERED)
                        .lineWidth(1 / 16f)
                }
            }
            outline.setBounds(bounds)
            outline.params.colored(colorFor(job))
        }
    }

    private fun findAreaProjection(
        clientLevel: Level,
        bounds: AABB,
        positions: Set<BlockPos>
    ): JobAreaProjection {
        val center = BlockPos.containing(
            (bounds.minX + bounds.maxX) / 2.0,
            (bounds.minY + bounds.maxY) / 2.0,
            (bounds.minZ + bounds.maxZ) / 2.0
        )
        val minCorner = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ)

        val candidates = LinkedHashSet<BlockPos>()
        candidates.addAll(positions)
        candidates.add(center)
        candidates.add(minCorner)

        val anchor = candidates.firstOrNull {
            SableRenderSupport.hasProjection(clientLevel, it)
        } ?: center

        return JobAreaProjection(
            anchor = anchor,
            usesLocalCoordinates = SableRenderSupport.hasProjection(clientLevel, anchor)
        )
    }

    /**
     * Removes blocks from the [SchematicLevel] that already exist in the real world.
     * Returns true if any blocks were removed (renderer needs update).
     */
    private fun removePlacedBlocks(
        schematicLevel: SchematicLevel,
        clientLevel: Level,
        renderOrigin: BlockPos,
        size: Vec3i,
        rotation: Rotation,
        usesLocalCoordinates: Boolean
    ): Boolean {
        val blockMap = schematicLevel.blockMap
        val toRemove = mutableListOf<BlockPos>()

        for ((localPos, state) in blockMap) {
            val worldPos = transformLocalPos(localPos, renderOrigin, size, rotation)
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
        val serverAnchor = placement.anchor
        val rotation = placement.rotation
        val mirror = placement.mirror

        try {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return buildFallbackRenderer(job, clientLevel)

            val fakeStack = com.simibubi.create.AllItems.SCHEMATIC.asStack()
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_FILE, schematicFile)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_DEPLOYED, true)

            val template = SchematicItem.loadSchematic(clientLevel, fakeStack)
            if (template.size == net.minecraft.core.Vec3i.ZERO) {
                CreateBuzzyBeez.LOGGER.warn("Schematic template is empty for file: $schematicFile")
                return buildFallbackRenderer(job, clientLevel)
            }
            val size = template.size
            val renderOrigin = visualTargetFromServerAnchor(serverAnchor, size, rotation, mirror)
            val bounds = computeTransformedAABB(renderOrigin, size, rotation)
            val boundsCenter = BlockPos.containing(
                (bounds.minX + bounds.maxX) / 2.0,
                (bounds.minY + bounds.maxY) / 2.0,
                (bounds.minZ + bounds.maxZ) / 2.0
            )
            val projectionAnchor = sequenceOf(serverAnchor, boundsCenter, renderOrigin)
                .firstOrNull { SableRenderSupport.hasProjection(clientLevel, it) }
                ?: serverAnchor
            val useLocalCoordinates = SableRenderSupport.hasProjection(clientLevel, projectionAnchor)

            val schematicLevel = SchematicLevel(clientLevel)
            val settings = StructurePlaceSettings()
            settings.mirror = mirror
            val mirrorOrigin = when (mirror) {
                Mirror.FRONT_BACK -> BlockPos.ZERO.east(size.x - 1)
                Mirror.LEFT_RIGHT -> BlockPos.ZERO.south(size.z - 1)
                else -> BlockPos.ZERO
            }

            template.placeInWorld(
                schematicLevel,
                mirrorOrigin,
                mirrorOrigin,
                settings,
                schematicLevel.random,
                Block.UPDATE_CLIENTS
            )

            for (blockEntity in schematicLevel.blockEntities) {
                blockEntity.setLevel(schematicLevel)
            }
            fixControllerBlockEntities(schematicLevel)

            removePlacedBlocks(schematicLevel, clientLevel, renderOrigin, size, rotation, useLocalCoordinates)

            return JobRenderer(
                GhostSchematicRenderer(schematicLevel),
                schematicLevel,
                projectionAnchor,
                useLocalCoordinates,
                renderOrigin,
                size,
                rotation,
                bounds
            )
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
        val bounds = AABB(
            allGhosts.keys.minOf { it.x }.toDouble(),
            allGhosts.keys.minOf { it.y }.toDouble(),
            allGhosts.keys.minOf { it.z }.toDouble(),
            (allGhosts.keys.maxOf { it.x } + 1).toDouble(),
            (allGhosts.keys.maxOf { it.y } + 1).toDouble(),
            (allGhosts.keys.maxOf { it.z } + 1).toDouble()
        )
        return JobRenderer(
            GhostSchematicRenderer(ghostLevel),
            ghostLevel,
            BlockPos.ZERO,
            false,
            BlockPos.ZERO,
            Vec3i.ZERO,
            Rotation.NONE,
            bounds
        )
    }

    private fun visualTargetFromServerAnchor(
        serverAnchor: BlockPos,
        size: Vec3i,
        rotation: Rotation,
        mirror: Mirror
    ): BlockPos {
        val xO = size.x / 2.0
        val zO = size.z / 2.0
        val mirroredX = when (mirror) {
            Mirror.FRONT_BACK -> (size.x - 1).toDouble()
            else -> 0.0
        }
        val mirroredZ = when (mirror) {
            Mirror.LEFT_RIGHT -> (size.z - 1).toDouble()
            else -> 0.0
        }

        val transformed = rotateFootprintPoint(mirroredX, mirroredZ, xO, zO, rotation)
        return BlockPos.containing(
            serverAnchor.x - transformed.first,
            serverAnchor.y.toDouble(),
            serverAnchor.z - transformed.second
        )
    }

    private fun computeTransformedAABB(target: BlockPos, size: Vec3i, rotation: Rotation): AABB {
        val xO = size.x / 2.0
        val zO = size.z / 2.0
        val sX = size.x.toDouble()
        val sY = size.y.toDouble()
        val sZ = size.z.toDouble()

        val corners = listOf(
            rotateFootprintPoint(0.0, 0.0, xO, zO, rotation),
            rotateFootprintPoint(sX, 0.0, xO, zO, rotation),
            rotateFootprintPoint(0.0, sZ, xO, zO, rotation),
            rotateFootprintPoint(sX, sZ, xO, zO, rotation)
        )

        return AABB(
            target.x + corners.minOf { it.first },
            target.y.toDouble(),
            target.z + corners.minOf { it.second },
            target.x + corners.maxOf { it.first },
            target.y + sY,
            target.z + corners.maxOf { it.second }
        )
    }

    private fun transformLocalPos(localPos: BlockPos, target: BlockPos, size: Vec3i, rotation: Rotation): BlockPos {
        val xO = size.x / 2.0
        val zO = size.z / 2.0
        val transformed = rotateFootprintPoint(localPos.x.toDouble(), localPos.z.toDouble(), xO, zO, rotation)
        return BlockPos.containing(
            target.x + transformed.first,
            target.y + localPos.y.toDouble(),
            target.z + transformed.second
        )
    }

    private fun rotateFootprintPoint(
        px: Double,
        pz: Double,
        xO: Double,
        zO: Double,
        rotation: Rotation
    ): Pair<Double, Double> {
        val rot = -(rotation.ordinal * 90.0)
        val rad = Math.toRadians(rot)
        val c = cos(rad)
        val s = sin(rad)
        val dx = px - xO
        val dz = pz - zO
        return Pair(xO + dx * c + dz * s, zO - dx * s + dz * c)
    }

    /**
     * Finds the job whose AABB is hit by the given ray (from [start] in direction [dir]).
     * Returns the jobId of the closest hit, or null if no job AABB is hit within [maxRange].
     */
    fun findJobAtRay(start: Vec3, dir: Vec3, maxRange: Double): UUID? =
        findJobAtRayWithDist(start, dir, maxRange)?.first

    /**
     * Like [findJobAtRay] but also returns the hit distance from [start].
     */
    fun findJobAtRayWithDist(start: Vec3, dir: Vec3, maxRange: Double): Pair<UUID, Double>? {
        var bestDist = maxRange
        var bestJob: UUID? = null

        val hitBounds = if (renderedOutlineBoundsCache.isNotEmpty()) {
            renderedOutlineBoundsCache
        } else {
            outlineBoundsCache
        }
        for ((jobId, bounds) in hitBounds) {
            val hit = bounds.clip(start, start.add(dir.scale(maxRange)))
            if (hit.isPresent) {
                val dist = hit.get().distanceTo(start)
                if (dist < bestDist) {
                    bestDist = dist
                    bestJob = jobId
                }
            }
        }
        return bestJob?.let { it to bestDist }
    }

    /**
     * Returns true once an active job has enough client-side data to replace
     * the deployer's inactive/pending preview without creating a blank frame.
     */
    fun isRendering(jobId: UUID): Boolean {
        return rendererCache.containsKey(jobId) || outlineCache.containsKey(jobId)
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
