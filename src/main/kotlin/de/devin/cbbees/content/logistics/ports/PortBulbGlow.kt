package de.devin.cbbees.content.logistics.ports

import com.mojang.blaze3d.vertex.PoseStack
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import dev.engine_room.flywheel.lib.transform.TransformStack
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACING
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.sin

/**
 * A pulsing glow over a port's bulb, drawn like Create's display link bulb: an optional full-bright
 * bulb in the glow's colour, then the glow itself with additive blending ([PortRenderTypes.GLOW]).
 */
object PortBulbGlow {

    const val BUSY_PULSE_TICKS = 3f
    const val CRAFTING_PULSE_TICKS = 8f

    private const val GLOW_MIN = 60
    private const val GLOW_MAX = 200

    fun busyGlow(state: BlockState): Boolean = state.getValue(PortBlock.PORT_STATE) == PortState.BUSY

    fun render(
        state: BlockState,
        level: Level?,
        ms: PoseStack,
        buffer: MultiBufferSource,
        glow: PartialModel,
        pulseTicks: Float,
        bulb: PartialModel? = null,
    ) {
        val pulse = (sin(AnimationTickHolder.getRenderTime(level) / pulseTicks) + 1) / 2
        val brightness = (GLOW_MIN + (GLOW_MAX - GLOW_MIN) * pulse).toInt()

        ms.pushPose()
        TransformStack.of(ms)
            .center()
            .rotateYDegrees(-state.getValue(FACING).toYRot())
            .rotateXDegrees(-PortBlock.modelRotationX(state.getValue(FACE)).toFloat())
            .uncenter()

        bulb?.let {
            CachedBuffers.partial(it, state)
                .light<SuperByteBuffer>(LightTexture.FULL_BRIGHT)
                .renderInto(ms, buffer.getBuffer(RenderType.translucent()))
        }

        CachedBuffers.partial(glow, state)
            .light<SuperByteBuffer>(LightTexture.FULL_BRIGHT)
            .color<SuperByteBuffer>(brightness, brightness, brightness, 255)
            .disableDiffuse<SuperByteBuffer>()
            .renderInto(ms, buffer.getBuffer(PortRenderTypes.GLOW))

        ms.popPose()
    }
}
