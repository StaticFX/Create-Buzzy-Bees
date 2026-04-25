package de.devin.cbbees.content.drone.client

import com.simibubi.create.CreateClient
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.content.schematics.client.ConstructionPlannerHandler
import de.devin.cbbees.network.MoveDronePacket
import de.devin.cbbees.network.ToggleDroneViewPacket
import de.devin.cbbees.registry.AllKeys
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.client.event.RenderHandEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

@ClientSide
object DroneViewClientEvents {

    private var lastDroneBlockPos: BlockPos? = null

    /** Tracks which WASD keys were down last tick, for nudge mode edge detection. */
    private var prevUp = false
    private var prevDown = false
    private var prevLeft = false
    private var prevRight = false

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        // Consume key press for toggle
        while (AllKeys.DRONE_VIEW.consumeClick()) {
            PacketDistributor.sendToServer(ToggleDroneViewPacket())
        }

        DroneViewClientState.tick()

        // Send drone movement input + move schematic with drone
        if (DroneViewClientState.active) {
            sendDroneMovement()
            updateSchematicPosition()
        } else {
            lastDroneBlockPos = null
        }
    }

    /**
     * Moves the deployed schematic to follow the drone's XZ position.
     */
    private fun updateSchematicPosition() {
        val mc = Minecraft.getInstance()
        val drone = mc.level?.getEntity(DroneViewClientState.droneEntityId) ?: return
        val currentPos = drone.blockPosition()

        val last = lastDroneBlockPos
        lastDroneBlockPos = currentPos

        if (last == null || last == currentPos) return

        val dx = currentPos.x - last.x
        val dz = currentPos.z - last.z
        if (dx == 0 && dz == 0) return

        val handler = CreateClient.SCHEMATIC_HANDLER
        if (handler.isDeployed) {
            handler.transformation.move(dx, 0, dz)
            handler.markDirty()
        }
    }

    private fun sendDroneMovement() {
        val mc = Minecraft.getInstance()
        val opts = mc.options

        val up = opts.keyUp.isDown
        val down = opts.keyDown.isDown
        val left = opts.keyLeft.isDown
        val right = opts.keyRight.isDown

        val shiftHeld = GLFW.glfwGetKey(mc.window.window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(mc.window.window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS

        if (shiftHeld) {
            // Nudge mode: move exactly 1 block per key press
            var dx = 0f
            var dz = 0f
            if (up && !prevUp) dz -= 1f
            if (down && !prevDown) dz += 1f
            if (left && !prevLeft) dx -= 1f
            if (right && !prevRight) dx += 1f

            prevUp = up; prevDown = down; prevLeft = left; prevRight = right

            if (dx != 0f || dz != 0f) {
                PacketDistributor.sendToServer(MoveDronePacket(dx, dz))
                val drone = mc.level?.getEntity(DroneViewClientState.droneEntityId)
                if (drone != null) {
                    drone.xo = drone.x
                    drone.yo = drone.y
                    drone.zo = drone.z
                    drone.setPos(drone.x + dx, drone.y, drone.z + dz)
                }
            }
            return
        }

        prevUp = up; prevDown = down; prevLeft = left; prevRight = right

        var dx = 0f
        var dz = 0f

        // Fixed orientation: yaw=180 means north (-Z) is "up" on screen
        // W = north (-Z), S = south (+Z), A = west (-X), D = east (+X)
        if (up) dz -= 1f
        if (down) dz += 1f
        if (left) dx -= 1f
        if (right) dx += 1f

        if (dx == 0f && dz == 0f) return

        // Normalize diagonal movement
        val len = kotlin.math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
        val speed = CBBeesConfig.droneMoveSpeed.get().toFloat()
        dx = dx / len * speed
        dz = dz / len * speed

        PacketDistributor.sendToServer(MoveDronePacket(dx, dz))

        val drone = mc.level?.getEntity(DroneViewClientState.droneEntityId)
        if (drone != null) {
            // Preserve old position for smooth interpolation between ticks
            drone.xo = drone.x
            drone.yo = drone.y
            drone.zo = drone.z
            drone.setPos(drone.x + dx, drone.y, drone.z + dz)
        }
    }

    /**
     * Suppress player movement when drone view is active.
     * The player stays in place; WASD controls the drone instead.
     */
    @SubscribeEvent
    @JvmStatic
    fun onMovementInput(event: MovementInputUpdateEvent) {
        if (DroneViewClientState.active) {
            val input = event.input
            input.forwardImpulse = 0f
            input.leftImpulse = 0f
            input.jumping = false
            input.shiftKeyDown = false
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onRenderHand(event: RenderHandEvent) {
        if (DroneViewClientState.active) {
            event.isCanceled = true
        }
    }

    /**
     * Intercept right-click during drone view to route to the Construction Planner
     * and Create's SchematicHandler, since the player may not be holding the planner.
     */
    @SubscribeEvent
    @JvmStatic
    fun onMouseButton(event: InputEvent.MouseButton.Pre) {
        if (!DroneViewClientState.active) return
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || event.action != GLFW.GLFW_PRESS) return

        val mc = Minecraft.getInstance()
        if (mc.screen != null) return
        val player = mc.player ?: return
        val planner = DroneViewClientState.findActivePlanner(player)
        if (planner.isEmpty) return

        val handler = CreateClient.SCHEMATIC_HANDLER
        if (handler.isActive && handler.isDeployed) {
            // Deployed state: route to Create's tool handler
            handler.onMouseInput(GLFW.GLFW_MOUSE_BUTTON_RIGHT, true)
        } else if (!ConstructionPlannerItem.hasSchematic(planner)) {
            // Browsing state: enter group or select schematic
            // Use raw GLFW check because onMovementInput clears shiftKeyDown during drone view
            val shiftHeld = GLFW.glfwGetKey(mc.window.window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(mc.window.window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
            if (shiftHeld) {
                ConstructionPlannerHandler.instantConstruct()
            } else {
                ConstructionPlannerHandler.confirmSelection()
            }
        }
        event.isCanceled = true
    }

    /**
     * Block all vanilla interactions (attack, use, pick block) during drone view.
     * We handle right-click ourselves via onMouseButton above.
     */
    @SubscribeEvent
    @JvmStatic
    fun onInteraction(event: InputEvent.InteractionKeyMappingTriggered) {
        if (DroneViewClientState.active) {
            event.isCanceled = true
            event.setSwingHand(false)
        }
    }

    /**
     * Freeze player head rotation by zeroing mouse sensitivity during drone view.
     */
    @SubscribeEvent
    @JvmStatic
    fun onPlayerTurn(event: CalculatePlayerTurnEvent) {
        if (DroneViewClientState.active) {
            event.mouseSensitivity = 0.0
        }
    }

    /**
     * Block hotbar scrolling during drone view.
     * Low priority so Create's tool scroll and our planner scroll run first.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    @JvmStatic
    fun onMouseScroll(event: InputEvent.MouseScrollingEvent) {
        if (DroneViewClientState.active) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onCameraAngles(event: ViewportEvent.ComputeCameraAngles) {
        if (DroneViewClientState.active) {
            event.pitch = 90f
            event.yaw = 180f // North = up on screen
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
        DroneViewClientState.reset()
        lastDroneBlockPos = null
        prevUp = false; prevDown = false; prevLeft = false; prevRight = false
    }
}
