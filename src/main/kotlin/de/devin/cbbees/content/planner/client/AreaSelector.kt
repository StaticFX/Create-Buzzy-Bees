package de.devin.cbbees.content.planner.client

import com.mojang.blaze3d.platform.InputConstants
import com.simibubi.create.AllSoundEvents
import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.foundation.utility.RaycastHelper
import de.devin.cbbees.registry.AllKeys
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.math.VecHelper
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.AxisDirection
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor

object AreaSelector {

    private val outlineSlot = Any()
    private var selectedFace: Direction? = null

    fun onScroll(delta: Double): Boolean {
        if (!isFreeAimDown()) return false

        if (AreaSelection.secondPos == null) {
            AreaSelection.range = Mth.clamp(AreaSelection.range + delta.toInt(), 1, 100)
            return true
        }

        val face = selectedFace ?: return true
        val first = AreaSelection.firstPos ?: return true
        val second = AreaSelection.secondPos ?: return true

        var bb = AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(second))
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        val adjusted = if (bb.contains(camera)) -delta else delta
        val step = if (adjusted > 0) ceil(adjusted).toInt() else floor(adjusted).toInt()
        val x = face.normal.x * step
        val y = face.normal.y * step
        val z = face.normal.z * step

        val axisDirection = face.axisDirection
        if (axisDirection == AxisDirection.NEGATIVE) {
            bb = bb.move(-x.toDouble(), -y.toDouble(), -z.toDouble())
        }
        bb = AABB(
            bb.minX, bb.minY, bb.minZ,
            maxOf(bb.maxX - x * axisDirection.step, bb.minX),
            maxOf(bb.maxY - y * axisDirection.step, bb.minY),
            maxOf(bb.maxZ - z * axisDirection.step, bb.minZ),
        )

        AreaSelection.firstPos = BlockPos.containing(bb.minX, bb.minY, bb.minZ)
        AreaSelection.secondPos = BlockPos.containing(bb.maxX, bb.maxY, bb.maxZ)
        Minecraft.getInstance().player?.displayClientMessage(
            Component.translatable("cbbees.area.dimensions", (bb.xsize + 1).toInt(), (bb.ysize + 1).toInt(), (bb.zsize + 1).toInt()), true
        )
        return true
    }

    fun onRightClick(): Boolean {
        val player = Minecraft.getInstance().player ?: return false

        if (player.isShiftKeyDown) {
            if (AreaSelection.firstPos != null) {
                Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(AllSoundEvents.CLIPBOARD_ERASE.mainEvent, 1f, 0.5f))
            }
            discard()
            return true
        }

        if (AreaSelection.secondPos != null) return true

        val selected = AreaSelection.selectedPos
        if (selected == null) {
            player.displayClientMessage(Component.translatable("cbbees.area.no_target"), true)
            return true
        }

        if (AreaSelection.firstPos != null) {
            AreaSelection.secondPos = selected
            return true
        }

        AreaSelection.firstPos = selected
        player.displayClientMessage(Component.translatable("cbbees.area.first_pos"), true)
        return true
    }

    fun discard() {
        AreaSelection.discard()
        Minecraft.getInstance().player?.displayClientMessage(Component.translatable("cbbees.area.abort"), true)
    }

    fun tick(color: Int) {
        val player = Minecraft.getInstance().player ?: return

        if (AreaSelection.secondPos == null) {
            AreaSelection.selectedPos = if (isFreeAimDown()) {
                val eye = player.getEyePosition(AnimationTickHolder.getPartialTicks())
                BlockPos.containing(eye.add(player.lookAngle.scale(AreaSelection.range.toDouble())))
            } else {
                targetedBlock()
            }
        }

        selectedFace = null
        val first = AreaSelection.firstPos
        val second = AreaSelection.secondPos
        if (first != null && second != null) {
            val bb = AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(second))
                .expandTowards(1.0, 1.0, 1.0)
                .inflate(0.45)
            val inside = bb.contains(Minecraft.getInstance().gameRenderer.mainCamera.position)
            val result = RaycastHelper.rayTraceUntil(player, 70.0) { inside xor bb.contains(VecHelper.getCenterOf(it)) }
            selectedFace = when {
                result.missed() -> null
                inside -> result.facing.opposite
                else -> result.facing
            }
        }

        val box = AreaSelection.getSelectionBox() ?: return
        Outliner.getInstance()
            .chaseAABB(outlineSlot, box)
            .colored(color)
            .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
            .lineWidth(1 / 16f)
            .highlightFace(selectedFace)
    }

    private fun targetedBlock(): BlockPos? {
        val player = Minecraft.getInstance().player ?: return null
        val trace = RaycastHelper.rayTraceRange(player.level(), player, 75.0)
        if (trace == null || trace.type != HitResult.Type.BLOCK) return null
        val replaceable = player.level().getBlockState(trace.blockPos)
            .canBeReplaced(BlockPlaceContext(UseOnContext(player, InteractionHand.MAIN_HAND, trace)))
        return if (trace.direction.axis.isVertical && !replaceable) trace.blockPos.relative(trace.direction) else trace.blockPos
    }

    private fun isFreeAimDown(): Boolean {
        val window = Minecraft.getInstance().window ?: return false
        return InputConstants.isKeyDown(window.window, AllKeys.FREE_AIM.key.value)
    }
}
