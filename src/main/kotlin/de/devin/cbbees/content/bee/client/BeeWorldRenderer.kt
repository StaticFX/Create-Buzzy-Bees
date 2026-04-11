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

    private val WING_BONES = setOf("lwing", "rwing")
    private const val LIGHT_BONE = "light"
    private const val LIGHT_ALPHA = 0.45f

    private const val MAX_RENDER_DIST_SQ = 64.0 * 64.0
    private const val FULL_BRIGHT_LIGHT = 0xF000F0
    private const val WING_FLAP_SPEED = 25f
    private const val WING_FLAP_AMPLITUDE = 0.6f

    @SubscribeEvent
    @JvmStatic
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return

        val mc = Minecraft.getInstance()
        mc.level ?: return
        val camPos = mc.gameRenderer.mainCamera.position
        val partialTick = event.partialTick.realtimeDeltaTicks

        val poseStack = event.poseStack
        val bufferSource = mc.renderBuffers().bufferSource()

        val flightBees = BeeClientTracker.getFlightBees()
        if (flightBees.isEmpty()) return

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
    }

    private fun renderModel(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        modelRes: ResourceLocation,
        textureRes: ResourceLocation,
        time: Float,
    ) {
        val bakedModel: BakedGeoModel = GeckoLibCache.getBakedModels()[modelRes] ?: return
        val opaqueBuffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(textureRes))
        val packedLight = FULL_BRIGHT_LIGHT

        // Wing flap oscillation — same for both bee and bumble bee models
        val wingAngle = sin(time * WING_FLAP_SPEED) * WING_FLAP_AMPLITUDE

        // Opaque pass — everything except the light bone
        bakedModel.topLevelBones.forEach { bone ->
            if (bone.name != LIGHT_BONE) {
                renderBone(poseStack, opaqueBuffer, bone, packedLight, wingAngle, 1f)
            }
        }

        // Translucent pass — light bone only (if present)
        bakedModel.topLevelBones.find { it.name == LIGHT_BONE }?.let { lightBone ->
            val translucentBuffer = bufferSource.getBuffer(RenderType.entityTranslucent(textureRes))
            renderBone(poseStack, translucentBuffer, lightBone, packedLight, wingAngle, LIGHT_ALPHA)
        }
    }

    private fun renderBone(
        poseStack: PoseStack,
        buffer: VertexConsumer,
        bone: GeoBone,
        packedLight: Int,
        wingAngle: Float,
        alpha: Float,
    ) {
        if (bone.isHidden) return

        poseStack.pushPose()

        // Mirror the pivot X for left wing — both wings share pivot [-3, 7, -2]
        // in the model, but lwing's cube is on the positive X side
        val rawPx = bone.pivotX / 16f
        val px = if (bone.name == "lwing") -rawPx else rawPx
        val py = bone.pivotY / 16f
        val pz = bone.pivotZ / 16f

        poseStack.translate(px.toDouble(), py.toDouble(), pz.toDouble())

        // Apply stored rotation from the model
        if (bone.rotX != 0f || bone.rotY != 0f || bone.rotZ != 0f) {
            poseStack.mulPose(Axis.ZP.rotation(bone.rotZ))
            poseStack.mulPose(Axis.YP.rotation(bone.rotY))
            poseStack.mulPose(Axis.XP.rotation(bone.rotX))
        }

        // Wing flap — lwing and rwing mirror each other on Z axis
        if (bone.name in WING_BONES) {
            val sign = if (bone.name == "lwing") 1f else -1f
            poseStack.mulPose(Axis.ZP.rotation(wingAngle * sign))
        }

        poseStack.translate(-px.toDouble(), -py.toDouble(), -pz.toDouble())

        // Render cubes
        val isWing = bone.name in WING_BONES
        val matrix = poseStack.last()
        bone.cubes.forEach { cube ->
            cube.quads().forEach { quad ->
                val normal = quad.normal()
                // Wings are zero-height planes — skip the bottom face to prevent z-fighting
                if (isWing && normal.y() < 0) return@forEach
                quad.vertices().forEach { vertex ->
                    buffer.addVertex(matrix.pose(), vertex.position().x(), vertex.position().y(), vertex.position().z())
                        .setColor(1f, 1f, 1f, alpha)
                        .setUv(vertex.texU(), vertex.texV())
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(packedLight)
                        .setNormal(matrix, normal.x(), normal.y(), normal.z())
                }
            }
        }

        // Recurse children
        bone.childBones.forEach { child ->
            renderBone(poseStack, buffer, child, packedLight, wingAngle, alpha)
        }

        poseStack.popPose()
    }
}
