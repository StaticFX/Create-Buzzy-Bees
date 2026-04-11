package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import com.simibubi.create.AllDataComponents
import de.devin.cbbees.config.CBBeesClientConfig
import com.simibubi.create.content.schematics.SchematicItem
import com.simibubi.create.foundation.utility.RaycastHelper
import de.devin.cbbees.content.drone.client.DroneViewClientState
import dev.engine_room.flywheel.lib.transform.TransformStack
import net.createmod.catnip.impl.client.render.ColoringVertexConsumer
import net.createmod.catnip.levelWrappers.SchematicLevel
import net.createmod.catnip.outliner.AABBOutline
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer
import net.createmod.catnip.render.SuperRenderTypeBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.RenderType.chunkBufferLayers
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import java.util.concurrent.CompletableFuture
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the currently hovered schematic as semi-transparent ghost blocks
 * while browsing the Construction Planner HUD (state 2).
 *
 * Uses Create's exact rendering transform so that the ghost preview matches
 * the deployed schematic position perfectly. Mirror is handled by building
 * separate SchematicLevels (same approach as Create's SchematicHandler).
 */
@OnlyIn(Dist.CLIENT)
object SchematicHoverPreview {

    private const val GHOST_ALPHA = 0.5f
    private const val OUTLINE_COLOR = 0x6886c5

    private var currentSchematic: String? = null
    private var cachedTemplate: StructureTemplate? = null
    private var schematicSize: Vec3i = Vec3i.ZERO

    /** Renderers: [0]=normal, [1]=FB mirror, [2]=LR mirror. Built asynchronously. */
    private var renderers: Array<GhostSchematicRenderer?> = arrayOf(null, null, null)
    private var rendererBuilding = booleanArrayOf(false, false, false)

    /** Incremented on each [updatePreview] to invalidate in-flight async builds. */
    private var buildGeneration = 0

    private var outline: AABBOutline? = null

    /** Anchor position — the crosshair hit position representing the AABB center of the schematic. */
    var anchorPos: BlockPos? = null
        private set

    var currentRotation: Rotation = Rotation.NONE
        private set

    var currentMirror: Mirror = Mirror.NONE
        private set

    private class TransparentBuffer(
        private val delegate: SuperRenderTypeBuffer,
        private val alpha: Float
    ) : SuperRenderTypeBuffer {
        private val chunkLayers = chunkBufferLayers().toSet()
        private fun wrap(consumer: VertexConsumer): VertexConsumer =
            ColoringVertexConsumer(consumer, 1f, 1f, 1f, alpha)
        override fun getBuffer(type: RenderType): VertexConsumer {
            val redirected = if (type in chunkLayers) RenderType.translucent() else type
            return wrap(delegate.getBuffer(redirected))
        }
        override fun getEarlyBuffer(type: RenderType): VertexConsumer = wrap(delegate.getEarlyBuffer(type))
        override fun getLateBuffer(type: RenderType): VertexConsumer = wrap(delegate.getLateBuffer(type))
        override fun draw() = delegate.draw()
        override fun draw(type: RenderType) = delegate.draw(type)
    }

    // ── Public API ──────────────────────────────────────────────────────

    fun updatePreview(filename: String?) {
        if (filename == currentSchematic) return

        buildGeneration++
        currentSchematic = filename
        cachedTemplate = null
        schematicSize = Vec3i.ZERO
        renderers = arrayOf(null, null, null)
        rendererBuilding = booleanArrayOf(false, false, false)
        outline = null
        currentRotation = Rotation.NONE
        currentMirror = Mirror.NONE

        if (filename == null) return

        try {
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return
            val player = mc.player ?: return

            val fakeStack = com.simibubi.create.AllItems.SCHEMATIC.asStack()
            fakeStack.set(AllDataComponents.SCHEMATIC_FILE, filename)
            fakeStack.set(AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
            fakeStack.set(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO)
            fakeStack.set(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE)
            fakeStack.set(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE)
            fakeStack.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)

            val template = SchematicItem.loadSchematic(level, fakeStack)
            if (template.size == Vec3i.ZERO) return

            cachedTemplate = template
            schematicSize = template.size

            // Outline and axis are available immediately while geometry builds async
            outline = AABBOutline(AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)).apply {
                params.colored(OUTLINE_COLOR).lineWidth(1 / 16f)
            }

            // Start async build for normal renderer (index 0)
            startBuildRenderer(0, template, Mirror.NONE)
        } catch (_: Exception) {
            cachedTemplate = null
        }
    }

    /** Restores rotation and mirror, e.g. after re-entering browsing state. */
    fun setTransform(rotation: Rotation, mirror: Mirror) {
        currentRotation = rotation
        currentMirror = mirror
    }

    fun rotatePreview() {
        if (currentSchematic == null) return
        currentRotation = when (currentRotation) {
            Rotation.NONE -> Rotation.CLOCKWISE_90
            Rotation.CLOCKWISE_90 -> Rotation.CLOCKWISE_180
            Rotation.CLOCKWISE_180 -> Rotation.COUNTERCLOCKWISE_90
            Rotation.COUNTERCLOCKWISE_90 -> Rotation.NONE
            else -> Rotation.NONE
        }
    }

    fun mirrorPreview() {
        if (currentSchematic == null) return
        currentMirror = when (currentMirror) {
            Mirror.NONE -> Mirror.FRONT_BACK
            Mirror.FRONT_BACK -> Mirror.LEFT_RIGHT
            Mirror.LEFT_RIGHT -> Mirror.NONE
            else -> Mirror.NONE
        }
    }

    fun tick() {
        if (currentSchematic == null) {
            anchorPos = null
            return
        }

        val mc = Minecraft.getInstance()
        val player = mc.player ?: run { anchorPos = null; return }

        val hitResult = if (DroneViewClientState.active) {
            // During drone view, raycast straight down from the drone
            val drone = mc.level?.getEntity(DroneViewClientState.droneEntityId)
            if (drone != null) {
                val origin = drone.position()
                val target = origin.add(0.0, -75.0, 0.0)
                val ctx = ClipContext(origin, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
                player.level().clip(ctx)
            } else null
        } else {
            RaycastHelper.rayTraceRange(player.level(), player, 75.0)
        }

        if (hitResult == null || hitResult.type != HitResult.Type.BLOCK) {
            anchorPos = null
            return
        }

        val hit = BlockPos.containing(hitResult.location)
        val replaceable = player.level().getBlockState(hit).canBeReplaced()
        anchorPos = if (!replaceable) hit.relative(hitResult.direction) else hit
    }

    fun render(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return
        if (!CBBeesClientConfig.showSchematicPreview.get()) return
        if (!ConstructionPlannerHandler.isBrowsingPreview) return
        if (currentSchematic == null || schematicSize == Vec3i.ZERO) return

        val anchor = anchorPos ?: return

        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera.position
        val superBuffer = DefaultSuperRenderTypeBuffer.getInstance()
        val poseStack = event.poseStack

        // Compute internal target from anchor (replicates Create's fromAnchor)
        val target = fromAnchor(anchor)

        val xO = schematicSize.x / 2.0
        val zO = schematicSize.z / 2.0
        val rot = -(currentRotation.ordinal * 90.0).toFloat()

        // Render ghost blocks only if renderer is ready (built asynchronously)
        val renderer = getActiveRenderer()
        if (renderer != null) {
            val opacity = CBBeesClientConfig.ghostBlockOpacity.get().toFloat()
            val transparentBuffer = TransparentBuffer(superBuffer, opacity)
            poseStack.pushPose()

            TransformStack.of(poseStack)
                .translate(Vec3.atLowerCornerOf(target).subtract(camera))
            poseStack.translate(xO, 0.0, zO)
            TransformStack.of(poseStack).rotateYDegrees(rot)
            poseStack.translate(-xO, 0.0, -zO)

            renderer.render(poseStack, transparentBuffer)
            poseStack.popPose()
        }

        // Always render AABB outline (available immediately, lightweight)
        val aabbOutline = outline
        if (aabbOutline != null) {
            val aabb = computeTransformedAABB(target)
            aabbOutline.setBounds(aabb)
            poseStack.pushPose()
            aabbOutline.render(poseStack, superBuffer, camera, event.partialTick.getGameTimeDeltaPartialTick(false))
            poseStack.popPose()
        }

        superBuffer.draw()
        RenderSystem.enableCull()
    }

    fun clear() {
        buildGeneration++
        currentSchematic = null
        cachedTemplate = null
        schematicSize = Vec3i.ZERO
        renderers = arrayOf(null, null, null)
        rendererBuilding = booleanArrayOf(false, false, false)
        outline = null
        anchorPos = null
        currentRotation = Rotation.NONE
        currentMirror = Mirror.NONE
    }

    // ── Internal ────────────────────────────────────────────────────────

    /**
     * Returns the ready renderer for the current mirror, or null if still building.
     * Starts an async build if one hasn't been kicked off yet.
     */
    private fun getActiveRenderer(): GhostSchematicRenderer? {
        val idx = when (currentMirror) {
            Mirror.NONE -> 0
            Mirror.FRONT_BACK -> 1
            Mirror.LEFT_RIGHT -> 2
        }
        val renderer = renderers[idx]
        if (renderer != null && renderer.isReady) return renderer

        if (!rendererBuilding[idx]) {
            val template = cachedTemplate ?: return null
            startBuildRenderer(idx, template, currentMirror)
        }
        return null
    }

    /**
     * Builds a SchematicLevel + renderer asynchronously on a background thread.
     * The SchematicLevel construction and geometry tesselation both run off the
     * render thread, eliminating the frame stutter for large schematics.
     * The outline and axis indicator remain visible while the ghost loads.
     */
    private fun startBuildRenderer(index: Int, template: StructureTemplate, mirror: Mirror) {
        if (rendererBuilding[index]) return
        rendererBuilding[index] = true

        val level = Minecraft.getInstance().level ?: run {
            rendererBuilding[index] = false
            return
        }
        val size = schematicSize
        val gen = buildGeneration

        CompletableFuture.runAsync {
            val schematicLevel = SchematicLevel(level)
            val settings = StructurePlaceSettings()
            settings.mirror = mirror

            val pos = when (mirror) {
                Mirror.FRONT_BACK -> BlockPos.ZERO.east(size.x - 1)
                Mirror.LEFT_RIGHT -> BlockPos.ZERO.south(size.z - 1)
                else -> BlockPos.ZERO
            }

            template.placeInWorld(schematicLevel, pos, pos, settings, schematicLevel.random, Block.UPDATE_CLIENTS)

            for (blockEntity in schematicLevel.blockEntities) {
                blockEntity.setLevel(schematicLevel)
            }

            // Build geometry on the render thread to avoid native memory corruption
            // in ByteBufferBuilder/jemalloc from concurrent vertex buffer access
            val renderer = GhostSchematicRenderer(schematicLevel)
            Minecraft.getInstance().execute {
                try {
                    renderer.prebuildGeometry()
                } catch (e: Exception) {
                    de.devin.cbbees.CreateBuzzyBeez.LOGGER.warn("Schematic preview build failed: ${e.message}")
                }
                if (gen == buildGeneration) {
                    renderers[index] = renderer
                }
                rendererBuilding[index] = false
            }
        }.exceptionally {
            Minecraft.getInstance().execute {
                rendererBuilding[index] = false
            }
            null
        }
    }

    /**
     * Computes the rendering origin from the anchor position.
     * The anchor represents the center of the schematic AABB, so rotation
     * and mirroring pivot around this center point.
     */
    private fun fromAnchor(anchor: BlockPos): BlockPos {
        val xO = schematicSize.x / 2.0
        val zO = schematicSize.z / 2.0
        return anchor.subtract(BlockPos.containing(xO, 0.0, zO))
    }

    /**
     * Converts a center-based anchor to the corner-based anchor that the server
     * expects for [net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.placeInWorld].
     *
     * The server rotates/mirrors blocks around the placement origin (0,0,0 in local space),
     * so the corner must be adjusted per rotation and mirror to produce the same world-space
     * block positions as the visual preview.
     */
    fun computeServerAnchor(centerAnchor: BlockPos): BlockPos {
        val xO = schematicSize.x / 2.0
        val zO = schematicSize.z / 2.0
        val target = centerAnchor.subtract(BlockPos.containing(xO, 0.0, zO))

        // Find where original block (0,0,0) maps in the mirrored SchematicLevel
        val mirroredX = when (currentMirror) {
            Mirror.FRONT_BACK -> (schematicSize.x - 1).toDouble()
            else -> 0.0
        }
        val mirroredZ = when (currentMirror) {
            Mirror.LEFT_RIGHT -> (schematicSize.z - 1).toDouble()
            else -> 0.0
        }

        // Apply the visual rotation to this position (relative to center)
        val dx = mirroredX - xO
        val dz = mirroredZ - zO
        val rot = -(currentRotation.ordinal * 90.0)
        val rad = Math.toRadians(rot)
        val c = cos(rad)
        val s = sin(rad)

        return BlockPos.containing(
            target.x + xO + dx * c + dz * s,
            target.y.toDouble(),
            target.z + zO - dx * s + dz * c
        )
    }

    /** Computes the world-space AABB of the schematic after rotation, relative to target. */
    private fun computeTransformedAABB(target: BlockPos): AABB {
        val xO = schematicSize.x / 2.0
        val zO = schematicSize.z / 2.0
        val sX = schematicSize.x.toDouble()
        val sY = schematicSize.y.toDouble()
        val sZ = schematicSize.z.toDouble()

        // Transform the 4 footprint corners through the rotation
        val corners = listOf(
            transformPoint(0.0, 0.0, xO, zO),
            transformPoint(sX, 0.0, xO, zO),
            transformPoint(0.0, sZ, xO, zO),
            transformPoint(sX, sZ, xO, zO)
        )

        var minX = corners.minOf { it.first }
        var maxX = corners.maxOf { it.first }
        var minZ = corners.minOf { it.second }
        var maxZ = corners.maxOf { it.second }

        return AABB(
            target.x + minX, target.y.toDouble(), target.z + minZ,
            target.x + maxX, target.y + sY, target.z + maxZ
        )
    }

    /** Rotates a footprint point (px, pz) around origin (xO, zO) by current rotation. */
    private fun transformPoint(px: Double, pz: Double, xO: Double, zO: Double): Pair<Double, Double> {
        val rot = -(currentRotation.ordinal * 90.0)
        val rad = Math.toRadians(rot)
        val c = cos(rad)
        val s = sin(rad)
        val dx = px - xO
        val dz = pz - zO
        return Pair(xO + dx * c + dz * s, zO - dx * s + dz * c)
    }
}
