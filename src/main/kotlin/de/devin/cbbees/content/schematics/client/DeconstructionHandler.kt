package de.devin.cbbees.content.schematics.client

import com.simibubi.create.foundation.utility.RaycastHelper
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.network.ProgramSchematicPacket
import de.devin.cbbees.network.StartDeconstructionPacket
import de.devin.cbbees.network.StopTasksPacket
import de.devin.cbbees.registry.AllKeys
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.math.VecHelper
import net.minecraft.client.Minecraft
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
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * Client-side handler for the Deconstruction Planner tool.
 *
 * This mirrors Create's SchematicAndQuillHandler but renders in red
 * and opens a deconstruction prompt instead of saving a schematic.
 *
 * Selection flow:
 * 1. Player holds Deconstruction Planner
 * 2. Right-click to set first corner
 * 3. Right-click again to set second corner
 * 4. Scroll wheel to resize selection
 * 5. Right-click to open deconstruction prompt
 * 6. Shift+Right-click to cancel selection
 */
object DeconstructionHandler {

    /** Currently selected face (for resizing) */
    private var selectedFace: Direction? = null

    /**
     * Handles mouse scroll input for resizing the selection.
     * @return true if the scroll was handled
     */
    fun mouseScrolled(delta: Double): Boolean {
        if (!isActive()) return false

        // Only handle scroll when CTRL is held
        if (!isCtrlDown()) return false

        if (DeconstructionSelection.secondPos == null) {
            DeconstructionSelection.range = Mth.clamp(DeconstructionSelection.range + delta.toInt(), 1, 100)
        }

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

        // Round away from zero
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
            Component.translatable("cbbees.deconstruction.dimensions", sizeX, sizeY, sizeZ),
            true
        )

        return true
    }

    /**
     * Handles mouse button input.
     * @param button The mouse button (0=left, 1=right)
     * @param pressed Whether the button was pressed (true) or released (false)
     * @return true if the input was handled
     */
    fun onMouseInput(button: Int, pressed: Boolean): Boolean {
        if (!pressed || button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false
        if (!isActive()) return false

        val player = Minecraft.getInstance().player ?: return false

        // Shift + right-click to discard selection
        if (player.isShiftKeyDown) {
            discard()
            return true
        }

        // If both corners are set, we don't open the prompt screen anymore.
        // The deconstruction is now started via the HUD button or R key.
        if (DeconstructionSelection.secondPos != null) {
            return true
        }

        // No target selected
        if (DeconstructionSelection.selectedPos == null) {
            player.displayClientMessage(
                Component.translatable("cbbees.deconstruction.no_target"),
                true
            )
            return true
        }

        // Set second corner if first is already set
        if (DeconstructionSelection.firstPos != null) {
            DeconstructionSelection.secondPos = DeconstructionSelection.selectedPos
            player.displayClientMessage(
                Component.translatable("cbbees.deconstruction.second_pos", AllKeys.START_ACTION.translatedKeyMessage),
                true
            )
            return true
        }

        // Set first corner
        DeconstructionSelection.firstPos = DeconstructionSelection.selectedPos
        player.displayClientMessage(
            Component.translatable("cbbees.deconstruction.first_pos"),
            true
        )
        return true
    }

    /**
     * Discards the current selection.
     */
    fun discard() {
        DeconstructionSelection.discard()
        Minecraft.getInstance().player?.displayClientMessage(
            Component.translatable("cbbees.deconstruction.abort"),
            true
        )
    }

    /**
     * Called every client tick to update selection state and rendering.
     */
    fun tick() {
        if (!isActive()) return

        val player = Minecraft.getInstance().player ?: return

        // Update selected position based on where player is looking
        if (isCtrlDown()) {
            // Free-aim mode when CTRL is held
            val pt = AnimationTickHolder.getPartialTicks()
            val targetVec =
                player.getEyePosition(pt).add(player.lookAngle.scale(DeconstructionSelection.range.toDouble()))
            DeconstructionSelection.selectedPos = BlockPos.containing(targetVec)
        } else {
            // Normal raycast mode
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

        // Render the selection box
        DeconstructionRenderer.renderWorldOutline(selectedFace)
    }

    /**
     * Handles key input for deconstruction.
     */
    fun onKeyInput(key: Int, pressed: Boolean): Boolean {
        if (!pressed || !isActive()) return false

        if (AllKeys.START_ACTION.matches(key, 0) && DeconstructionSelection.isComplete()) {
            val first = DeconstructionSelection.firstPos!!
            val second = DeconstructionSelection.secondPos!!

            // Send packet to server to start deconstruction
            PacketDistributor.sendToServer(StartDeconstructionPacket(first, second))

            // Show feedback
            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable("message.cbbees.planner.started")
                    .withStyle { it.withColor(0x00FF00) },
                true
            )

            discard()
            return true
        }

        if (AllKeys.STOP_ACTION.matches(key, 0)) {
            PacketDistributor.sendToServer(StopTasksPacket.INSTANCE)
            return true
        }

        if (AllKeys.PROGRAM_ACTION.matches(key, 0) && DeconstructionSelection.isComplete()) {
            val first = DeconstructionSelection.firstPos!!
            val second = DeconstructionSelection.secondPos!!

            val program = SchematicProgram.Deconstruction(first, second)
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

    /**
     * Checks if the deconstruction handler should be active.
     * Active when player is holding the Deconstruction Planner item.
     */
    fun isActive(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val mainHandItem = player.mainHandItem
        return AllItems.DECONSTRUCTION_PLANNER.isIn(mainHandItem)
    }

    /**
     * Checks if CTRL key is held.
     */
    private fun isCtrlDown(): Boolean {
        return Minecraft.getInstance().window?.let { window ->
            GLFW.glfwGetKey(window.window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                    GLFW.glfwGetKey(window.window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        } ?: false
    }
}
