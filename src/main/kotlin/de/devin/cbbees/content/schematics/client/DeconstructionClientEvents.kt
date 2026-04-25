package de.devin.cbbees.content.schematics.client

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent

/**
 * Client-side event handler for the Deconstruction Planner system.
 *
 * Registers event listeners for:
 * - Client tick: Updates the DeconstructionHandler each tick for selection rendering
 * - Mouse input: Handles right-click for setting selection corners
 * - Mouse scroll: Handles scroll wheel for resizing selection
 * - Key input: Handles R key for starting deconstruction
 *
 * HUD is registered as a GUI layer in CreateBuzzyBeez.
 */
object DeconstructionClientEvents {

    /**
     * Called every client tick.
     * Updates the DeconstructionHandler to handle selection state and rendering.
     */
    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        DeconstructionHandler.tick()
        PickupHandler.tick()
        DeconstructionRenderer.update()
    }

    @SubscribeEvent
    @JvmStatic
    fun onMouseInput(event: InputEvent.MouseButton.Pre) {
        if (DeconstructionHandler.onMouseInput(event.button, event.action == org.lwjgl.glfw.GLFW.GLFW_PRESS)) {
            event.isCanceled = true
            return
        }
        if (PickupHandler.onMouseInput(event.button, event.action == org.lwjgl.glfw.GLFW.GLFW_PRESS)) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onMouseScroll(event: InputEvent.MouseScrollingEvent) {
        if (DeconstructionHandler.mouseScrolled(event.scrollDeltaY)) {
            event.isCanceled = true
            return
        }
        if (PickupHandler.onScroll(event.scrollDeltaY)) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onKeyInput(event: InputEvent.Key) {
        DeconstructionHandler.onKeyInput(event.key, event.action == org.lwjgl.glfw.GLFW.GLFW_PRESS)
        PickupHandler.onKeyInput(event.key, event.action == org.lwjgl.glfw.GLFW.GLFW_PRESS)
    }
}
