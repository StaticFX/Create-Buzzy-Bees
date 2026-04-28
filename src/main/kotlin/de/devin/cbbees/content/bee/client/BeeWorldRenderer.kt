package de.devin.cbbees.content.bee.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
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

    private const val MAX_RENDER_DIST_SQ = 64.0 * 64.0
    private const val FULL_BRIGHT_LIGHT = 0xF000F0
    private const val WING_FLAP_SPEED = 25f
    private const val WING_FLAP_AMPLITUDE = 0.6f
    private const val GEAR_ROTATION_PERIOD = 4f

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
        val maxDistSq = MAX_RENDER_DIST_SQ
        val time = System.nanoTime() / 1_000_000_000.0f

        flightBees.forEach { bee ->
            val pos = bee.lerpPos(partialTick)
            val dx = pos.x - camPos.x
            val dy = pos.y - camPos.y
            val dz = pos.z - camPos.z
            if (dx * dx + dy * dy + dz * dz > maxDistSq) return@forEach

            val (modelRes, textureRes) = when (bee.type) {
                BeeType.CONSTRUCTION -> BEE_MODEL to BEE_TEXTURE
                BeeType.TRANSPORT -> BUMBLE_MODEL to BUMBLE_TEXTURE
            }

            poseStack.pushPose()
            poseStack.translate(dx, dy, dz)
            poseStack.mulPose(Axis.YP.rotationDegrees(180f - bee.yRot()))

            renderModel(poseStack, bufferSource, modelRes, textureRes, time)

            poseStack.popPose()
        }

        bufferSource.endBatch()
        profiler.pop()
    }

    private fun renderModel(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        modelRes: ResourceLocation,
        textureRes: ResourceLocation,
        time: Float,
    ) {
        val bakedModel: BakedGeoModel = GeckoLibCache.getBakedModels()[modelRes] ?: return
        val renderType = RenderType.entityCutoutNoCull(textureRes)
        val buffer = bufferSource.getBuffer(renderType)
        val packedLight = FULL_BRIGHT_LIGHT

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
