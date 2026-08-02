package de.devin.cbbees.content.deployer.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.content.schematics.SchematicItem
import com.simibubi.create.content.schematics.client.SchematicRenderer
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.compat.sable.SableRenderSupport
import de.devin.cbbees.content.deployer.DeployMode
import de.devin.cbbees.content.deployer.SchematicDeployerBlockEntity
import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.content.schematics.client.AreaTintRenderer
import de.devin.cbbees.content.schematics.client.ConstructionRenderer
import de.devin.cbbees.content.schematics.client.GhostSchematicRenderer
import de.devin.cbbees.registry.AllDataComponents
import de.devin.cbbees.util.ClientSide
import dev.engine_room.flywheel.lib.transform.TransformStack
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.impl.client.render.ColoringVertexConsumer
import net.createmod.catnip.levelWrappers.SchematicLevel
import net.createmod.catnip.outliner.AABBOutline
import net.createmod.catnip.outliner.Outliner
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a ghost block preview of the schematic when the player is looking
 * at a Schematic Deployer that has a programmed schematic loaded.
 * Also accounts for ABSOLUTE vs RELATIVE deploy mode.
 */
@ClientSide
@OnlyIn(Dist.CLIENT)
object DeployerPreviewRenderer {

    private const val GHOST_ALPHA = 0.4f
    private const val CONSTRUCTION_COLOR = 0x6886c5
    private const val DECONSTRUCTION_COLOR = 0xc56868
    private const val PICKUP_COLOR = 0x68c588

    private val outlineSlot = Any()

    private var cachedRenderer: SchematicRenderer? = null
    private var cachedRendererOrigin: BlockPos? = null
    private var cachedRendererSize: Vec3i = Vec3i.ZERO
    private var cachedRendererRotation: Rotation = Rotation.NONE
    private var cachedUsesSableProjection = false
    private var cachedOutline: AABBOutline? = null
    private var cachedOutlineColor: Int = CONSTRUCTION_COLOR
    private var cachedDeployerPos: BlockPos? = null
    private var cachedProgram: SchematicProgram? = null
    private var cachedMode: DeployMode? = null
    private var cachedOffset: BlockPos? = null
    private var cachedRotation: Rotation? = null
    private var cachedMirror: Mirror? = null
    private var cachedProgramAnchor: BlockPos? = null
    private var cachedOutlineBounds: AABB? = null

    private data class ConstructionPreview(
        val renderer: SchematicRenderer,
        val bounds: AABB,
        val renderOrigin: BlockPos,
        val projectionAnchor: BlockPos,
        val size: Vec3i,
        val rotation: Rotation
    )

    private val chunkLayers = RenderType.chunkBufferLayers().toSet()

    private class TransparentBuffer(
        private val delegate: SuperRenderTypeBuffer,
        private val alpha: Float,
        color: Int
    ) : SuperRenderTypeBuffer {
        private val red = ((color shr 16) and 0xFF) / 255f
        private val green = ((color shr 8) and 0xFF) / 255f
        private val blue = (color and 0xFF) / 255f

        private fun wrap(consumer: VertexConsumer): VertexConsumer =
            ColoringVertexConsumer(consumer, red, green, blue, alpha)

        override fun getBuffer(type: RenderType): VertexConsumer {
            val redirected = if (type in RenderType.chunkBufferLayers()) RenderType.translucent() else type
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
        val hitResult = mc.hitResult

        // Check if looking at a deployer block entity
        val be = getTargetedDeployer(level, hitResult)
        if (be == null) {
            clearCache()
            return
        }

        // Keep the deployer preview visible during the short pending handoff.
        // It is hidden only after ConstructionRenderer has received and prepared
        // render data for the active job, preventing a blank preview interval.
        val activeJobId = be.activeJobId
        if (activeJobId != null && ConstructionRenderer.isRendering(activeJobId)) {
            clearCache()
            return
        }

        val program = be.heldItem.get(AllDataComponents.SCHEMATIC_PROGRAM)
        if (program == null) {
            clearCache()
            return
        }

        // Resolve the effective program based on deploy mode
        val effectiveProgram = resolveProgram(be, program)

        // Rebuild cache if anything changed
        if (be.blockPos != cachedDeployerPos || program != cachedProgram
            || be.deployMode != cachedMode || be.relativeOffset != cachedOffset
            || be.relativeRotation != cachedRotation || be.relativeMirror != cachedMirror
        ) {
            rebuildCache(effectiveProgram, level)
            cachedDeployerPos = be.blockPos
            cachedProgram = program
            cachedMode = be.deployMode
            cachedOffset = be.relativeOffset
            cachedRotation = be.relativeRotation
            cachedMirror = be.relativeMirror
        }

        val poseStack = event.poseStack
        val camera = mc.gameRenderer.mainCamera.position
        val superBuffer = DefaultSuperRenderTypeBuffer.getInstance()
        val pt = AnimationTickHolder.getPartialTicks()
        val renderOrigin = cachedRendererOrigin ?: cachedProgramAnchor ?: be.blockPos
        val sableAnchor = cachedProgramAnchor

        // Render ghost blocks (construction only). Sable rendering follows the
        // same local-coordinate projection used by the working deconstruction
        // frame: project once at the program anchor, then apply only the local
        // offset from that anchor to the schematic render origin.
        cachedRenderer?.let { renderer ->
            val transparentBuffer = TransparentBuffer(superBuffer, GHOST_ALPHA, cachedOutlineColor)
            poseStack.pushPose()
            poseStack.translate(-camera.x, -camera.y, -camera.z)

            val projected = cachedUsesSableProjection && sableAnchor != null &&
                SableRenderSupport.applyProjectedLocalTransform(poseStack, level, sableAnchor)

            if (projected) {
                val localOffset = renderOrigin.subtract(sableAnchor!!)
                TransformStack.of(poseStack).translate(Vec3.atLowerCornerOf(localOffset))
            } else {
                TransformStack.of(poseStack).translate(Vec3.atLowerCornerOf(renderOrigin))
            }

            val size = cachedRendererSize
            val xO = size.x / 2.0
            val zO = size.z / 2.0
            poseStack.translate(xO, 0.0, zO)
            TransformStack.of(poseStack).rotateYDegrees(-(cachedRendererRotation.ordinal * 90.0).toFloat())
            poseStack.translate(-xO, 0.0, -zO)
            renderer.render(poseStack, transparentBuffer)
            poseStack.popPose()
        }

        // Draw a low-opacity colour volume for every program type:
        // construction = blue, deconstruction = red, pickup = green.
        // Use exactly the same Sable-local transform as the frame so the fill
        // can never drift away from the clickable job area.
        cachedOutlineBounds?.let { bounds ->
            if (cachedUsesSableProjection && sableAnchor != null) {
                val localBounds = bounds.move(
                    -sableAnchor.x.toDouble(),
                    -sableAnchor.y.toDouble(),
                    -sableAnchor.z.toDouble()
                )

                poseStack.pushPose()
                poseStack.translate(-camera.x, -camera.y, -camera.z)
                val projected = SableRenderSupport.applyProjectedLocalTransform(
                    poseStack,
                    level,
                    sableAnchor
                )
                if (projected) {
                    AreaTintRenderer.render(
                        poseStack,
                        superBuffer,
                        localBounds,
                        cachedOutlineColor
                    )
                }
                poseStack.popPose()

                if (!projected) {
                    poseStack.pushPose()
                    poseStack.translate(-camera.x, -camera.y, -camera.z)
                    AreaTintRenderer.render(
                        poseStack,
                        superBuffer,
                        bounds,
                        cachedOutlineColor
                    )
                    poseStack.popPose()
                }
            } else {
                poseStack.pushPose()
                poseStack.translate(-camera.x, -camera.y, -camera.z)
                AreaTintRenderer.render(
                    poseStack,
                    superBuffer,
                    bounds,
                    cachedOutlineColor
                )
                poseStack.popPose()
            }
        }

        // Render every Sable frame in coordinates local to the same stable
        // program anchor. This keeps construction, deconstruction and pickup
        // outlines on one transform path and prevents double translation.
        cachedOutlineBounds?.let { bounds ->
            val outline = cachedOutline ?: return@let
            if (cachedUsesSableProjection && sableAnchor != null) {
                val localBounds = bounds.move(
                    -sableAnchor.x.toDouble(),
                    -sableAnchor.y.toDouble(),
                    -sableAnchor.z.toDouble()
                )
                outline.setBounds(localBounds)

                poseStack.pushPose()
                poseStack.translate(-camera.x, -camera.y, -camera.z)
                val projected = SableRenderSupport.applyProjectedLocalTransform(
                    poseStack,
                    level,
                    sableAnchor
                )
                if (projected) {
                    outline.render(poseStack, superBuffer, Vec3.ZERO, pt)
                }
                poseStack.popPose()

                if (!projected) {
                    outline.setBounds(bounds)
                    poseStack.pushPose()
                    outline.render(poseStack, superBuffer, camera, pt)
                    poseStack.popPose()
                }
            } else if (cachedRenderer != null) {
                outline.setBounds(bounds)
                poseStack.pushPose()
                outline.render(poseStack, superBuffer, camera, pt)
                poseStack.popPose()
            } else {
                Outliner.getInstance()
                    .chaseAABB(outlineSlot, bounds)
                    .colored(cachedOutlineColor)
                    .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
                    .lineWidth(1 / 16f)
            }
        }

        superBuffer.draw()
        RenderSystem.enableCull()
    }

    private fun getTargetedDeployer(level: Level, hitResult: net.minecraft.world.phys.HitResult?): SchematicDeployerBlockEntity? {
        if (hitResult !is BlockHitResult) return null
        val be = level.getBlockEntity(hitResult.blockPos)
        return be as? SchematicDeployerBlockEntity
    }

    private fun resolveProgram(be: SchematicDeployerBlockEntity, storedProgram: SchematicProgram): SchematicProgram {
        return when (be.deployMode) {
            DeployMode.ABSOLUTE -> storedProgram
            DeployMode.RELATIVE -> {
                val targetPoint = be.blockPos.offset(be.relativeOffset)
                when (storedProgram) {
                    is SchematicProgram.Construction -> {
                        storedProgram.copy(
                            anchor = targetPoint,
                            rotation = be.relativeRotation,
                            mirror = be.relativeMirror
                        )
                    }
                    is SchematicProgram.Deconstruction -> {
                        val referencePoint = BlockPos(
                            (storedProgram.corner1.x + storedProgram.corner2.x) / 2,
                            (storedProgram.corner1.y + storedProgram.corner2.y) / 2,
                            (storedProgram.corner1.z + storedProgram.corner2.z) / 2
                        )
                        val delta = targetPoint.subtract(referencePoint)
                        storedProgram.relocate(delta)
                    }
                    is SchematicProgram.Pickup -> {
                        val referencePoint = BlockPos(
                            (storedProgram.corner1.x + storedProgram.corner2.x) / 2,
                            (storedProgram.corner1.y + storedProgram.corner2.y) / 2,
                            (storedProgram.corner1.z + storedProgram.corner2.z) / 2
                        )
                        val delta = targetPoint.subtract(referencePoint)
                        storedProgram.relocate(delta)
                    }
                }
            }
        }
    }

    private fun rebuildCache(program: SchematicProgram, level: Level) {
        cachedRenderer = null
        cachedRendererOrigin = null
        cachedRendererSize = Vec3i.ZERO
        cachedRendererRotation = Rotation.NONE
        cachedUsesSableProjection = false
        cachedProgramAnchor = null
        cachedOutline = null
        cachedOutlineBounds = null
        cachedOutlineColor = CONSTRUCTION_COLOR

        when (program) {
            is SchematicProgram.Construction -> {
                val preview = buildConstructionPreview(
                    program,
                    level
                )
                cachedRenderer = preview?.renderer
                cachedRendererOrigin = preview?.renderOrigin
                cachedRendererSize = preview?.size ?: Vec3i.ZERO
                cachedRendererRotation = preview?.rotation ?: Rotation.NONE
                cachedProgramAnchor = preview?.projectionAnchor ?: program.anchor
                cachedOutlineColor = CONSTRUCTION_COLOR
                cachedOutline = preview?.let { buildOutline(it.bounds, CONSTRUCTION_COLOR) }
            }
            is SchematicProgram.Deconstruction -> {
                cachedProgramAnchor = program.corner1
                cachedOutlineColor = DECONSTRUCTION_COLOR
                cachedOutline = buildAreaOutline(program.corner1, program.corner2, DECONSTRUCTION_COLOR)
            }
            is SchematicProgram.Pickup -> {
                cachedProgramAnchor = program.corner1
                cachedOutlineColor = PICKUP_COLOR
                cachedOutline = buildAreaOutline(program.corner1, program.corner2, PICKUP_COLOR)
            }
        }

        cachedUsesSableProjection = cachedProgramAnchor?.let {
            SableRenderSupport.hasProjection(level, it)
        } ?: false
    }

    private fun buildConstructionPreview(
        program: SchematicProgram.Construction,
        clientLevel: Level
    ): ConstructionPreview? {
        try {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return null

            val fakeStack = com.simibubi.create.AllItems.SCHEMATIC.asStack()
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_FILE, program.schematicName)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_DEPLOYED, true)

            val template = SchematicItem.loadSchematic(clientLevel, fakeStack)
            val size = template.size
            if (size.x == 0 && size.y == 0 && size.z == 0) return null
            val renderOrigin = visualTargetFromServerAnchor(program.anchor, size, program.rotation, program.mirror)

            val schematicLevel = SchematicLevel(clientLevel)
            val settings = StructurePlaceSettings()
            settings.mirror = program.mirror
            val mirrorOrigin = when (program.mirror) {
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

            val bounds = computeTransformedAABB(renderOrigin, size, program.rotation)
            val boundsCenter = BlockPos.containing(
                (bounds.minX + bounds.maxX) / 2.0,
                (bounds.minY + bounds.maxY) / 2.0,
                (bounds.minZ + bounds.maxZ) / 2.0
            )
            val projectionAnchor = sequenceOf(program.anchor, boundsCenter, renderOrigin)
                .firstOrNull { SableRenderSupport.hasProjection(clientLevel, it) }
                ?: program.anchor

            // Remove blocks already placed in the real world
            val blockMap = schematicLevel.blockMap
            val toRemove = mutableListOf<BlockPos>()
            for ((localPos, state) in blockMap) {
                val worldPos = transformLocalPos(localPos, renderOrigin, size, program.rotation)
                if (clientLevel.getBlockState(worldPos) == state) {
                    toRemove.add(localPos)
                }
            }
            toRemove.forEach { blockMap.remove(it) }

            return ConstructionPreview(
                GhostSchematicRenderer(schematicLevel),
                bounds,
                renderOrigin,
                projectionAnchor,
                size,
                program.rotation
            )
        } catch (e: Exception) {
            CreateBuzzyBeez.LOGGER.debug("Failed to load schematic for deployer preview: ${program.schematicName}", e)
            return null
        }
    }

    private fun buildOutline(bounds: AABB, color: Int): AABBOutline {
        cachedOutlineBounds = bounds
        return AABBOutline(bounds).also { outline ->
            outline.params
                .colored(color)
                .withFaceTexture(AllSpecialTextures.CHECKERED)
                .lineWidth(1 / 16f)
        }
    }

    private fun buildAreaOutline(c1: BlockPos, c2: BlockPos, color: Int): AABBOutline {
        val bounds = AABB(
            minOf(c1.x, c2.x).toDouble(),
            minOf(c1.y, c2.y).toDouble(),
            minOf(c1.z, c2.z).toDouble(),
            (maxOf(c1.x, c2.x) + 1).toDouble(),
            (maxOf(c1.y, c2.y) + 1).toDouble(),
            (maxOf(c1.z, c2.z) + 1).toDouble()
        )
        cachedOutlineBounds = bounds
        val outline = AABBOutline(bounds)
        outline.params
            .colored(color)
            .withFaceTexture(AllSpecialTextures.CHECKERED)
            .lineWidth(1 / 16f)
        return outline
    }

    private fun clearCache() {
        if (cachedDeployerPos != null) {
            cachedRenderer = null
            cachedRendererOrigin = null
            cachedRendererSize = Vec3i.ZERO
            cachedRendererRotation = Rotation.NONE
            cachedUsesSableProjection = false
            cachedOutline = null
            cachedDeployerPos = null
            cachedProgram = null
            cachedMode = null
            cachedOffset = null
            cachedRotation = null
            cachedMirror = null
            cachedProgramAnchor = null
            cachedOutlineBounds = null
            cachedOutlineColor = CONSTRUCTION_COLOR
        }
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
}
