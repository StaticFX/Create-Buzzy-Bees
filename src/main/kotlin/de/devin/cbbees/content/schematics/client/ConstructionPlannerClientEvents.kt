package de.devin.cbbees.content.schematics.client

import de.devin.cbbees.content.drone.client.DroneViewClientState
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.registry.AllKeys
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.lwjgl.glfw.GLFW

/**
 * Client-side event handler for the Construction Planner HUD.
 *
 * Registers event listeners for:
 * - Client tick: refreshes the schematic list, manages browsing preview
 * - Mouse scroll: Alt+Scroll cycles through schematics/groups
 * - Key input: Backspace for group navigation, keybind for full screen
 * - Render GUI: draws the schematic selector above the hotbar
 * - Render level: draws ghost blocks during browsing preview (state 2)
 */
object ConstructionPlannerClientEvents {

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        ConstructionPlannerHandler.tick()
        ConstructionPlannerHUD.update()
        checkStuckJobTooltip()

        // Clear custom tool state if player is no longer holding the planner
        if (ConstructionToolState.activeTool != ConstructionToolState.CustomTool.NONE) {
            val player = Minecraft.getInstance().player
            if (player == null || DroneViewClientState.findActivePlanner(player).isEmpty) {
                ConstructionToolState.activeTool = ConstructionToolState.CustomTool.NONE
            }
        }

        // Always drain the browser keybind to prevent queued presses from firing
        // when the player switches to holding the planner
        val player = Minecraft.getInstance().player
        if (AllKeys.OPEN_SCHEMATIC_BROWSER.consumeClick()) {
            if (player != null && !DroneViewClientState.findActivePlanner(player).isEmpty) {
                Minecraft.getInstance().setScreen(ConstructionPlannerScreen())
            }
        }

        // Rotate/mirror ghost preview keybinds
        if (ConstructionPlannerHandler.isBrowsingPreview) {
            if (AllKeys.ROTATE_PREVIEW.consumeClick()) {
                SchematicHoverPreview.rotatePreview()
            }
            if (AllKeys.MIRROR_PREVIEW.consumeClick()) {
                SchematicHoverPreview.mirrorPreview()
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onMouseScroll(event: InputEvent.MouseScrollingEvent) {
        if (ConstructionPlannerHandler.onScroll(event.scrollDeltaY)) {
            event.isCanceled = true
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onKeyInput(event: InputEvent.Key) {
        if (!ConstructionPlannerHandler.isActive()) return

        // Backspace navigates up in group hierarchy
        if (event.key == GLFW.GLFW_KEY_BACKSPACE && event.action == GLFW.GLFW_PRESS) {
            if (ConstructionPlannerHandler.onNavigateOut()) {
                // Consumed
            }
        }
    }

    /**
     * Handles right-click on construction job bounding boxes.
     * Opens a [JobDetailScreen] when the player right-clicks while looking at a job AABB
     * and not targeting a real block (i.e. the crosshair is in the air).
     */
    @SubscribeEvent
    @JvmStatic
    fun onMouseInput(event: InputEvent.MouseButton.Pre) {
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return
        if (event.action != GLFW.GLFW_PRESS) return

        val mc = Minecraft.getInstance()
        if (mc.screen != null) return

        val player = mc.player ?: return

        // Only intercept when no real block is targeted — avoids stealing block interactions
        val hit = mc.hitResult
        if (hit != null && hit.type == net.minecraft.world.phys.HitResult.Type.BLOCK) return

        val eyePos = player.getEyePosition(1f)
        val lookDir = player.lookAngle
        val jobId = ConstructionRenderer.findJobAtRay(eyePos, lookDir, 5.0) ?: return

        ConstructionRenderer.getJobInfo(jobId) ?: return
        mc.setScreen(JobDetailScreen(jobId))
        event.isCanceled = true
    }

    /**
     * Renders ghost blocks and AABB outline during browsing preview (state 2).
     * Positioned at the crosshair block position, independent of Create.
     */
    @SubscribeEvent
    @JvmStatic
    fun onRenderLevel(event: RenderLevelStageEvent) {
        SchematicHoverPreview.render(event)
    }

    /**
     * Shows the stall reason as an action bar message when the player
     * looks at a stuck job's red AABB outline.
     */
    private fun checkStuckJobTooltip() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.screen != null) return

        val eyePos = player.getEyePosition(1f)
        val lookDir = player.lookAngle
        val jobId = ConstructionRenderer.findJobAtRay(eyePos, lookDir, 5.0) ?: return
        val job = ConstructionRenderer.getJobInfo(jobId) ?: return
        val reason = job.reason ?: return

        val reasonText = if (reason.startsWith("cbbees.")) {
            Component.translatable(reason)
        } else {
            Component.literal(reason)
        }
        player.displayClientMessage(
            Component.literal("${ChatFormatting.RED}${ChatFormatting.BOLD}! ")
                .append(reasonText.copy().withStyle(ChatFormatting.RED)),
            true
        )
    }
}
