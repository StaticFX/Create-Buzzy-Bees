package de.devin.cbbees.content.bee.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.compat.sable.FlightPose
import de.devin.cbbees.config.CBBeesClientConfig
import de.devin.cbbees.content.bee.flight.ClientBeeFlightData
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import software.bernie.geckolib.cache.GeckoLibCache
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.cache.`object`.GeoBone
import kotlin.math.sin

/**
 * Renders non-entity bees during [RenderLevelStageEvent].
 *
 * Uses GeckoLib's baked model cache for geometry and applies wing animation
 * via PoseStack transforms (doesn't mutate the shared baked model).
 */
@ClientSide
object BeeWorldRenderer {

    private val BEE_MODEL = CreateBuzzyBeez.asResource("geo/mechanical_bee.geo.json")
    private val BEE_TEXTURE = CreateBuzzyBeez.asResource("textures/entity/mechanical_bee.png")
    private val BUMBLE_MODEL = CreateBuzzyBeez.asResource("geo/mechanical_bumble_bee.geo.json")
    private val BUMBLE_TEXTURE = CreateBuzzyBeez.asResource("textures/entity/mechanical_bumble_bee.png")

    private val WING_BONES = setOf("leftwing_bone", "rightwing_bone")
    private const val GEAR_BONE = "gear2"

    /** Generous box around a bee for culling, so wings and carried items don't pop at the screen edge. */
    private const val BEE_SIZE = 1.5
    private const val FULL_BRIGHT_LIGHT = 0xF000F0
    private const val WING_FLAP_SPEED = 25f
    private const val WING_FLAP_AMPLITUDE = 0.6f
    private const val GEAR_ROTATION_PERIOD = 4f
    private const val CARRIED_ITEM_OFFSET_Y = -0.0
    private const val CARRIED_ITEM_SCALE = 0.35f

    @SubscribeEvent
    @JvmStatic
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val profiler = level.profiler
        val camPos = mc.gameRenderer.mainCamera.position
        val partialTick = event.partialTick.realtimeDeltaTicks

        val poseStack = event.poseStack
        val bufferSource = mc.renderBuffers().bufferSource()

        val flightBees = BeeClientTracker.getFlightBees()
        if (flightBees.isEmpty()) return

        profiler.push("cbbees_beeRender")
        val renderDist = CBBeesClientConfig.beeRenderDistance.get().toDouble()
        val itemDist = CBBeesClientConfig.beeItemRenderDistance.get().toDouble()
        val itemDistSq = itemDist * itemDist

        nearestBees(flightBees, camPos, renderDist * renderDist, event.frustum, partialTick, CBBeesClientConfig.maxRenderedBees.get()).forEach { (bee, pose, distSq) ->
            poseStack.pushPose()
            poseStack.translate(pose.position.x - camPos.x, pose.position.y - camPos.y, pose.position.z - camPos.z)
            pose.rotation?.let(poseStack::mulPose)
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - bee.yRot()))

            renderBee(poseStack, bufferSource, bee.type, FULL_BRIGHT_LIGHT)
            if (!bee.carriedItem.isEmpty && distSq <= itemDistSq) {
                renderCarriedItem(poseStack, bufferSource, bee.carriedItem, bee.id.hashCode())
            }

            poseStack.popPose()
        }

        bufferSource.endBatch()
        profiler.pop()
    }

    private data class VisibleBee(val bee: ClientBeeFlightData, val pose: FlightPose, val distSq: Double)

    /** Bees in view and within [maxDistSq], cut down to the [limit] nearest to the camera. */
    private fun nearestBees(
        bees: Collection<ClientBeeFlightData>,
        camPos: Vec3,
        maxDistSq: Double,
        frustum: Frustum,
        partialTick: Float,
        limit: Int,
    ): List<VisibleBee> {
        val visible = bees.mapNotNull { bee ->
            val pose = bee.lerpPose(partialTick)
            val distSq = pose.position.distanceToSqr(camPos)
            if (distSq > maxDistSq || !frustum.isVisible(AABB.ofSize(pose.position, BEE_SIZE, BEE_SIZE, BEE_SIZE))) null
            else VisibleBee(bee, pose, distSq)
        }
        return if (visible.size <= limit) visible else visible.sortedBy { it.distSq }.take(limit)
    }

    fun renderCarriedItem(poseStack: PoseStack, bufferSource: MultiBufferSource, item: ItemStack, seed: Int) {
        val mc = Minecraft.getInstance()
        poseStack.pushPose()
        poseStack.translate(0.0, CARRIED_ITEM_OFFSET_Y + 0.1, 0.0)
        poseStack.scale(CARRIED_ITEM_SCALE, CARRIED_ITEM_SCALE, CARRIED_ITEM_SCALE)
        mc.itemRenderer.renderStatic(
            item,
            ItemDisplayContext.FIXED,
            FULL_BRIGHT_LIGHT,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            bufferSource,
            mc.level,
            seed
        )
        poseStack.popPose()
    }

    fun renderBee(poseStack: PoseStack, bufferSource: MultiBufferSource, type: BeeType, packedLight: Int) {
        val (modelRes, textureRes) = when (type) {
            BeeType.CONSTRUCTION -> BEE_MODEL to BEE_TEXTURE
            BeeType.TRANSPORT -> BUMBLE_MODEL to BUMBLE_TEXTURE
        }
        val bakedModel: BakedGeoModel = GeckoLibCache.getBakedModels()[modelRes] ?: return
        val buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(textureRes))
        val time = System.nanoTime() / 1_000_000_000.0f

        val wingAngle = sin(time * WING_FLAP_SPEED) * WING_FLAP_AMPLITUDE
        val gearAngle = (time % GEAR_ROTATION_PERIOD) / GEAR_ROTATION_PERIOD * Math.PI.toFloat() * 2f

        bakedModel.topLevelBones.forEach { bone ->
            renderBone(poseStack, buffer, bone, packedLight, wingAngle, gearAngle)
        }
    }

    private fun renderBone(
        poseStack: PoseStack,
        buffer: VertexConsumer,
        bone: GeoBone,
        packedLight: Int,
        wingAngle: Float,
        gearAngle: Float,
    ) {
        if (bone.isHidden) return

        poseStack.pushPose()

        val px = bone.pivotX / 16f
        val py = bone.pivotY / 16f
        val pz = bone.pivotZ / 16f

        poseStack.translate(px.toDouble(), py.toDouble(), pz.toDouble())

        if (bone.rotX != 0f || bone.rotY != 0f || bone.rotZ != 0f) {
            poseStack.mulPose(Axis.ZP.rotation(bone.rotZ))
            poseStack.mulPose(Axis.YP.rotation(bone.rotY))
            poseStack.mulPose(Axis.XP.rotation(bone.rotX))
        }

        when (bone.name) {
            in WING_BONES -> {
                val sign = if (bone.name.startsWith("left")) 1f else -1f
                poseStack.mulPose(Axis.ZP.rotation(wingAngle * sign))
            }

            GEAR_BONE -> {
                poseStack.mulPose(Axis.YN.rotationDegrees(90f))
                poseStack.mulPose(Axis.XP.rotationDegrees(90f))
                poseStack.mulPose(Axis.YP.rotation(gearAngle))
            }
        }

        poseStack.translate(-px.toDouble(), -py.toDouble(), -pz.toDouble())

        val matrix = poseStack.last()
        bone.cubes.forEach { cube ->
            cube.quads().forEach { quad ->
                val normal = quad.normal()
                quad.vertices().forEach { vertex ->
                    buffer.addVertex(matrix.pose(), vertex.position().x(), vertex.position().y(), vertex.position().z())
                        .setColor(1f, 1f, 1f, 1f)
                        .setUv(vertex.texU(), vertex.texV())
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(packedLight)
                        .setNormal(matrix, normal.x(), normal.y(), normal.z())
                }
            }
        }

        bone.childBones.forEach { child ->
            renderBone(poseStack, buffer, child, packedLight, wingAngle, gearAngle)
        }

        poseStack.popPose()
    }
}
