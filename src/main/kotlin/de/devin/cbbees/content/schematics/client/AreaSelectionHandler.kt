package de.devin.cbbees.content.schematics.client

import com.simibubi.create.foundation.utility.RaycastHelper
import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.network.ProgramSchematicPacket
import de.devin.cbbees.registry.AllKeys
import com.mojang.blaze3d.platform.InputConstants
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.math.VecHelper
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.AxisDirection
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * Reusable two-corner area selection handler. Both the Deconstruction Planner and
 * Pickup Planner delegate to this — only the translation key prefix, packet factory,
 * and program factory differ.
 *
 * Supports:
 * - Block raycast targeting (normal mode)
 * - CTRL free-aim targeting (snap point at fixed range in front of camera)
 * - CTRL+Scroll resizing by dragging faces
 * - Shift+RMB to cancel selection
 * - Automatic selection clear when the tool is switched away
 */
class AreaSelectionHandler(
    private val keyPrefix: String,
    private val isActive: () -> Boolean,
    private val createPacket: (BlockPos, BlockPos) -> CustomPacketPayload,
    private val createProgram: (BlockPos, BlockPos) -> SchematicProgram,
) {
    private var selectedFace: Direction? = null
    private var wasActive = false

    fun onScroll(delta: Double): Boolean {
        if (!isActive()) return false
        if (!isFreeAimDown()) return false

        // Adjust free-aim range when second corner not yet set
        if (DeconstructionSelection.secondPos == null) {
            DeconstructionSelection.range = Mth.clamp(DeconstructionSelection.range + delta.toInt(), 1, 100)
            return true
        }

        // Resize selection by dragging faces
        val face = selectedFace ?: return true
        val first = DeconstructionSelection.firstPos ?: return true
        val second = DeconstructionSelection.secondPos ?: return true

        var bb = AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(second))
        val vec = face.normal
        val projectedView = Minecraft.getInstance().gameRenderer.mainCamera.position

        var adjustedDelta = delta
        if (bb.contains(projectedView)) {
            adjustedDelta *= -1
        }

        val intDelta = if (adjustedDelta > 0) Math.ceil(adjustedDelta).toInt() else Math.floor(adjustedDelta).toInt()
        val x = vec.x * intDelta
        val y = vec.y * intDelta
        val z = vec.z * intDelta

        val axisDirection = face.axisDirection
        if (axisDirection == AxisDirection.NEGATIVE) {
            bb = bb.move(-x.toDouble(), -y.toDouble(), -z.toDouble())
        }

        val maxX = maxOf(bb.maxX - x * axisDirection.step, bb.minX)
        val maxY = maxOf(bb.maxY - y * axisDirection.step, bb.minY)
        val maxZ = maxOf(bb.maxZ - z * axisDirection.step, bb.minZ)

        bb = AABB(bb.minX, bb.minY, bb.minZ, maxX, maxY, maxZ)

        DeconstructionSelection.firstPos = BlockPos.containing(bb.minX, bb.minY, bb.minZ)
        DeconstructionSelection.secondPos = BlockPos.containing(bb.maxX, bb.maxY, bb.maxZ)

        val player = Minecraft.getInstance().player ?: return true
        val sizeX = (bb.xsize + 1).toInt()
        val sizeY = (bb.ysize + 1).toInt()
        val sizeZ = (bb.zsize + 1).toInt()
        player.displayClientMessage(
            Component.translatable("$keyPrefix.dimensions", sizeX, sizeY, sizeZ), true
        )
        return true
    }

    fun onMouseInput(button: Int, pressed: Boolean): Boolean {
        if (!pressed || button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false
        if (!isActive()) return false

        val player = Minecraft.getInstance().player ?: return false

        if (player.isShiftKeyDown) {
            discard()
            return true
        }

        if (DeconstructionSelection.secondPos != null) return true

        if (DeconstructionSelection.selectedPos == null) {
            player.displayClientMessage(Component.translatable("$keyPrefix.no_target"), true)
            return true
        }

        if (DeconstructionSelection.firstPos != null) {
            DeconstructionSelection.secondPos = DeconstructionSelection.selectedPos
            return true
        }

        DeconstructionSelection.firstPos = DeconstructionSelection.selectedPos
        player.displayClientMessage(Component.translatable("$keyPrefix.first_pos"), true)
        return true
    }

    fun discard() {
        DeconstructionSelection.discard()
        Minecraft.getInstance().player?.displayClientMessage(
            Component.translatable("$keyPrefix.abort"), true
        )
    }

    fun tick() {
        val active = isActive()
        if (!active) {
            // Clear selection when the tool is switched away
            if (wasActive) {
                DeconstructionSelection.discard()
                wasActive = false
            }
            return
        }
        wasActive = true

        val player = Minecraft.getInstance().player ?: return

        // Update selected position based on where player is looking
        if (DeconstructionSelection.secondPos == null) {
            if (isFreeAimDown()) {
                // CTRL free-aim: snap selection point at fixed range in front of camera
                val pt = AnimationTickHolder.getPartialTicks()
                val targetVec = player.getEyePosition(pt)
                    .add(player.lookAngle.scale(DeconstructionSelection.range.toDouble()))
                DeconstructionSelection.selectedPos = BlockPos.containing(targetVec)
            } else {
                // Normal block raycast
                val trace = RaycastHelper.rayTraceRange(player.level(), player, 75.0)
                if (trace != null && trace.type == HitResult.Type.BLOCK) {
                    var hit = trace.blockPos
                    val replaceable = player.level().getBlockState(hit)
                        .canBeReplaced(BlockPlaceContext(UseOnContext(player, InteractionHand.MAIN_HAND, trace)))
                    if (trace.direction.axis.isVertical && !replaceable) {
                        hit = hit.relative(trace.direction)
                    }
                    DeconstructionSelection.selectedPos = hit
                } else {
                    DeconstructionSelection.selectedPos = null
                }
            }
        }

        // Update selected face for resizing
        selectedFace = null
        val first = DeconstructionSelection.firstPos
        val second = DeconstructionSelection.secondPos
        if (first != null && second != null) {
            val bb = AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(second))
                .expandTowards(1.0, 1.0, 1.0)
                .inflate(0.45)
            val projectedView = Minecraft.getInstance().gameRenderer.mainCamera.position
            val inside = bb.contains(projectedView)
            val result = RaycastHelper.rayTraceUntil(player, 70.0) { pos ->
                inside xor bb.contains(VecHelper.getCenterOf(pos))
            }
            selectedFace = when {
                result.missed() -> null
                inside -> result.facing.opposite
                else -> result.facing
            }
        }

        DeconstructionRenderer.renderWorldOutline(selectedFace)
    }

    fun onKeyInput(key: Int, pressed: Boolean): Boolean {
        if (!pressed || !isActive()) return false

        if (AllKeys.START_ACTION.matches(key, 0) && DeconstructionSelection.isComplete()) {
            val first = DeconstructionSelection.firstPos!!
            val second = DeconstructionSelection.secondPos!!
            PacketDistributor.sendToServer(createPacket(first, second))
            DeconstructionSelection.discard()
            return true
        }

        if (AllKeys.STOP_ACTION.matches(key, 0)) {
            PacketDistributor.sendToServer(de.devin.cbbees.network.StopTasksPacket.INSTANCE)
            return true
        }

        if (AllKeys.PROGRAM_ACTION.matches(key, 0) && DeconstructionSelection.isComplete()) {
            val first = DeconstructionSelection.firstPos!!
            val second = DeconstructionSelection.secondPos!!
            PacketDistributor.sendToServer(ProgramSchematicPacket(createProgram(first, second)))
            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable("cbbees.schematic.programmed").withStyle { it.withColor(0x88CCFF) }, true
            )
            return true
        }

        return false
    }

    private fun isFreeAimDown(): Boolean {
        val key = AllKeys.FREE_AIM
        val window = Minecraft.getInstance().window ?: return false
        return InputConstants.isKeyDown(window.window, key.key.value)
    }
}
