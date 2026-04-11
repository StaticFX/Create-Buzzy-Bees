package de.devin.cbbees.content.schematics.client

import com.simibubi.create.foundation.utility.RaycastHelper
import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.network.ProgramSchematicPacket
import de.devin.cbbees.network.StartPickupPacket
import de.devin.cbbees.registry.AllKeys
import net.createmod.catnip.math.VecHelper
import net.minecraft.client.Minecraft
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * Client-side handler for the Pickup Planner tool.
 *
 * Reuses [DeconstructionSelection] for the two-corner selection state and
 * [DeconstructionRenderer] for the outline rendering (with a green tint).
 * Sends [StartPickupPacket] on confirm or [ProgramSchematicPacket] with
 * [SchematicProgram.Pickup] for deployer programming.
 */
object PickupHandler {

    private var selectedFace: Direction? = null

    fun onScroll(delta: Double): Boolean {
        if (!isActive()) return false
        if (!isCtrlDown()) return false
        if (!DeconstructionSelection.isComplete()) return false

        val first = DeconstructionSelection.firstPos ?: return false
        val second = DeconstructionSelection.secondPos ?: return false
        val face = selectedFace ?: return false

        val axisDirection = face.axisDirection
        val x = face.stepX
        val y = face.stepY
        val z = face.stepZ
        val step = if (delta > 0) 1 else -1

        var bb = AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(second))
            .expandTowards(1.0, 1.0, 1.0)

        val maxX = maxOf(bb.maxX + x * step * axisDirection.step, bb.minX)
        val maxY = maxOf(bb.maxY + y * step * axisDirection.step, bb.minY)
        val maxZ = maxOf(bb.maxZ + z * step * axisDirection.step, bb.minZ)

        bb = AABB(bb.minX, bb.minY, bb.minZ, maxX, maxY, maxZ)

        DeconstructionSelection.firstPos = net.minecraft.core.BlockPos.containing(bb.minX, bb.minY, bb.minZ)
        DeconstructionSelection.secondPos = net.minecraft.core.BlockPos.containing(bb.maxX, bb.maxY, bb.maxZ)

        val player = Minecraft.getInstance().player ?: return true
        val sizeX = (bb.xsize + 1).toInt()
        val sizeY = (bb.ysize + 1).toInt()
        val sizeZ = (bb.zsize + 1).toInt()
        player.displayClientMessage(
            Component.translatable("cbbees.deconstruction.dimensions", sizeX, sizeY, sizeZ),
            true
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
            player.displayClientMessage(Component.translatable("cbbees.deconstruction.no_target"), true)
            return true
        }

        if (DeconstructionSelection.firstPos != null) {
            DeconstructionSelection.secondPos = DeconstructionSelection.selectedPos
            player.displayClientMessage(
                Component.translatable("cbbees.deconstruction.second_pos", AllKeys.START_ACTION.translatedKeyMessage),
                true
            )
            return true
        }

        DeconstructionSelection.firstPos = DeconstructionSelection.selectedPos
        player.displayClientMessage(Component.translatable("cbbees.deconstruction.first_pos"), true)
        return true
    }

    fun discard() {
        DeconstructionSelection.discard()
        Minecraft.getInstance().player?.displayClientMessage(
            Component.translatable("cbbees.deconstruction.abort"), true
        )
    }

    fun tick() {
        if (!isActive()) return

        val player = Minecraft.getInstance().player ?: return

        // Raycast for block selection
        if (DeconstructionSelection.secondPos == null) {
            val result = RaycastHelper.rayTraceRange(player.level(), player, 75.0)
            if (result.type == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                DeconstructionSelection.selectedPos = net.minecraft.core.BlockPos.containing(result.location)
            } else {
                DeconstructionSelection.selectedPos = null
            }
        }

        // Update selected face for resizing
        selectedFace = null
        val first = DeconstructionSelection.firstPos
        val second = DeconstructionSelection.secondPos
        if (first != null && second != null) {
            var bb = AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(second))
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

        // Render with green outline
        DeconstructionRenderer.renderWorldOutline(selectedFace)
    }

    fun onKeyInput(key: Int, pressed: Boolean): Boolean {
        if (!pressed || !isActive()) return false

        if (AllKeys.START_ACTION.matches(key, 0) && DeconstructionSelection.isComplete()) {
            val first = DeconstructionSelection.firstPos!!
            val second = DeconstructionSelection.secondPos!!

            PacketDistributor.sendToServer(StartPickupPacket(first, second))

            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable("message.cbbees.planner.started")
                    .withStyle { it.withColor(0x00FF00) },
                true
            )
            discard()
            return true
        }

        if (AllKeys.STOP_ACTION.matches(key, 0)) {
            PacketDistributor.sendToServer(de.devin.cbbees.network.StopTasksPacket.INSTANCE)
            return true
        }

        if (AllKeys.PROGRAM_ACTION.matches(key, 0) && DeconstructionSelection.isComplete()) {
            val first = DeconstructionSelection.firstPos!!
            val second = DeconstructionSelection.secondPos!!

            val program = SchematicProgram.Pickup(first, second)
            PacketDistributor.sendToServer(ProgramSchematicPacket(program))

            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable("cbbees.schematic.programmed")
                    .withStyle { it.withColor(0x88CCFF) },
                true
            )
            return true
        }

        return false
    }

    fun isActive(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return AllItems.PICKUP_PLANNER.isIn(player.mainHandItem)
    }

    private fun isCtrlDown(): Boolean {
        return Minecraft.getInstance().window?.let { window ->
            GLFW.glfwGetKey(window.window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window.window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        } ?: false
    }
}
