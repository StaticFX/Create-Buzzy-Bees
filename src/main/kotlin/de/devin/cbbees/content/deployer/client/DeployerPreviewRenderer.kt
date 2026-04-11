package de.devin.cbbees.content.deployer.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.content.schematics.SchematicItem
import com.simibubi.create.content.schematics.client.SchematicRenderer
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.deployer.DeployMode
import de.devin.cbbees.content.deployer.SchematicDeployerBlockEntity
import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.content.schematics.client.GhostSchematicRenderer
import de.devin.cbbees.registry.AllDataComponents
import de.devin.cbbees.util.ClientSide
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
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

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

    private var cachedRenderer: SchematicRenderer? = null
    private var cachedOutline: AABBOutline? = null
    private var cachedDeployerPos: BlockPos? = null
    private var cachedProgram: SchematicProgram? = null
    private var cachedMode: DeployMode? = null
    private var cachedOffset: BlockPos? = null
    private var cachedRotation: Rotation? = null
    private var cachedMirror: Mirror? = null

    private val chunkLayers = RenderType.chunkBufferLayers().toSet()

    private class TransparentBuffer(
        private val delegate: SuperRenderTypeBuffer,
        private val alpha: Float
    ) : SuperRenderTypeBuffer {
        private fun wrap(consumer: VertexConsumer): VertexConsumer =
            ColoringVertexConsumer(consumer, 1f, 1f, 1f, alpha)

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
        if (be == null || be.activeJobId != null) {
            // Not looking at a deployer, or it has an active job
            // (active jobs are rendered by ConstructionRenderer via ClientJobCache)
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

        // Render ghost blocks (construction only)
        cachedRenderer?.let { renderer ->
            val transparentBuffer = TransparentBuffer(superBuffer, GHOST_ALPHA)
            poseStack.pushPose()
            poseStack.translate(-camera.x, -camera.y, -camera.z)
            renderer.render(poseStack, transparentBuffer)
            poseStack.popPose()
        }

        // Render outline
        cachedOutline?.let { outline ->
            val pt = AnimationTickHolder.getPartialTicks()
            outline.render(poseStack, superBuffer, camera, pt)
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
        cachedOutline = null

        when (program) {
            is SchematicProgram.Construction -> {
                cachedRenderer = buildConstructionRenderer(program, level)
                cachedOutline = buildConstructionOutline(program, level)
            }
            is SchematicProgram.Deconstruction -> {
                cachedOutline = buildDeconstructionOutline(program)
            }
            is SchematicProgram.Pickup -> {
                cachedOutline = buildDeconstructionOutline(
                    SchematicProgram.Deconstruction(program.corner1, program.corner2)
                )
            }
        }
    }

    private fun buildConstructionRenderer(program: SchematicProgram.Construction, clientLevel: Level): SchematicRenderer? {
        try {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return null

            val fakeStack = com.simibubi.create.AllItems.SCHEMATIC.asStack()
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_FILE, program.schematicName)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ANCHOR, program.anchor)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ROTATION, program.rotation)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_MIRROR, program.mirror)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_DEPLOYED, true)

            val template = SchematicItem.loadSchematic(clientLevel, fakeStack)
            if (template.size.x == 0 && template.size.y == 0 && template.size.z == 0) return null

            val schematicLevel = SchematicLevel(clientLevel)
            val settings = StructurePlaceSettings()
            settings.rotation = program.rotation
            settings.mirror = program.mirror

            template.placeInWorld(schematicLevel, program.anchor, program.anchor, settings, schematicLevel.random, Block.UPDATE_CLIENTS)

            for (blockEntity in schematicLevel.blockEntities) {
                blockEntity.setLevel(schematicLevel)
            }

            // Remove blocks already placed in the real world
            val blockMap = schematicLevel.blockMap
            val toRemove = mutableListOf<BlockPos>()
            for ((localPos, state) in blockMap) {
                val worldPos = localPos.offset(schematicLevel.anchor)
                if (clientLevel.getBlockState(worldPos) == state) {
                    toRemove.add(localPos)
                }
            }
            toRemove.forEach { blockMap.remove(it) }

            return GhostSchematicRenderer(schematicLevel)
        } catch (e: Exception) {
            CreateBuzzyBeez.LOGGER.debug("Failed to load schematic for deployer preview: ${program.schematicName}", e)
            return null
        }
    }

    private fun buildConstructionOutline(program: SchematicProgram.Construction, clientLevel: Level): AABBOutline? {
        try {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return null

            val fakeStack = com.simibubi.create.AllItems.SCHEMATIC.asStack()
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_FILE, program.schematicName)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ANCHOR, program.anchor)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ROTATION, program.rotation)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_MIRROR, program.mirror)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_DEPLOYED, true)

            val template = SchematicItem.loadSchematic(clientLevel, fakeStack)
            val rawSize = template.size
            if (rawSize.x == 0 && rawSize.y == 0 && rawSize.z == 0) return null

            val effectiveSize = when (program.rotation) {
                Rotation.NONE, Rotation.CLOCKWISE_180 -> BlockPos(rawSize.x, rawSize.y, rawSize.z)
                Rotation.CLOCKWISE_90, Rotation.COUNTERCLOCKWISE_90 -> BlockPos(rawSize.z, rawSize.y, rawSize.x)
            }
            val anchor = program.anchor
            val bounds = AABB(
                anchor.x.toDouble(), anchor.y.toDouble(), anchor.z.toDouble(),
                (anchor.x + effectiveSize.x).toDouble(),
                (anchor.y + effectiveSize.y).toDouble(),
                (anchor.z + effectiveSize.z).toDouble()
            )
            val outline = AABBOutline(bounds)
            outline.params
                .colored(CONSTRUCTION_COLOR)
                .withFaceTexture(AllSpecialTextures.CHECKERED)
                .lineWidth(1 / 16f)
            return outline
        } catch (_: Exception) {
            return null
        }
    }

    private fun buildDeconstructionOutline(program: SchematicProgram.Deconstruction): AABBOutline {
        val c1 = program.corner1
        val c2 = program.corner2
        val bounds = AABB(
            minOf(c1.x, c2.x).toDouble(),
            minOf(c1.y, c2.y).toDouble(),
            minOf(c1.z, c2.z).toDouble(),
            (maxOf(c1.x, c2.x) + 1).toDouble(),
            (maxOf(c1.y, c2.y) + 1).toDouble(),
            (maxOf(c1.z, c2.z) + 1).toDouble()
        )
        val outline = AABBOutline(bounds)
        outline.params
            .colored(DECONSTRUCTION_COLOR)
            .withFaceTexture(AllSpecialTextures.CHECKERED)
            .lineWidth(1 / 16f)
        return outline
    }

    private fun clearCache() {
        if (cachedDeployerPos != null) {
            cachedRenderer = null
            cachedOutline = null
            cachedDeployerPos = null
            cachedProgram = null
            cachedMode = null
            cachedOffset = null
            cachedRotation = null
            cachedMirror = null
        }
    }
}
