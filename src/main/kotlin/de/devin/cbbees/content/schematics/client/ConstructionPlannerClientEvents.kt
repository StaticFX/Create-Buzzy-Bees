package de.devin.cbbees.content.schematics.client

import com.simibubi.create.AllBlocks
import de.devin.cbbees.content.drone.client.DroneViewClientState
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.network.WriteMissingToClipboardPacket
import de.devin.cbbees.registry.AllKeys
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.network.PacketDistributor
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
        JobLookOverlay.tick()

        if (ConstructionToolState.activeTool != ConstructionToolState.CustomTool.NONE) {
            val player = Minecraft.getInstance().player
            if (player == null || DroneViewClientState.findSchematicPlanner(player).isEmpty) {
                ConstructionToolState.activeTool = ConstructionToolState.CustomTool.NONE
            }
        }

        val player = Minecraft.getInstance().player
        if (AllKeys.OPEN_SCHEMATIC_BROWSER.consumeClick()) {
            if (player != null && !DroneViewClientState.findActivePlanner(player).isEmpty) {
                Minecraft.getInstance().setScreen(ConstructionPlannerScreen())
            }
        }

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

        if (event.key == GLFW.GLFW_KEY_BACKSPACE && event.action == GLFW.GLFW_PRESS) {
            if (ConstructionPlannerHandler.onNavigateOut()) {
                // Consumed
            }
        }
    }

    /**
     * Handles right-click on construction job bounding boxes.
     * Opens a [JobDetailScreen] when the player right-clicks while looking at a job AABB,
     * or writes the job's missing materials onto a held Clipboard.
     * If a real block is closer than the AABB, the click passes through to normal handling.
     */
    @SubscribeEvent
    @JvmStatic
    fun onMouseInput(event: InputEvent.MouseButton.Pre) {
        if (event.button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return
        if (event.action != GLFW.GLFW_PRESS) return

        val mc = Minecraft.getInstance()
        if (mc.screen != null) return

        val player = mc.player ?: return

        val eyePos = player.getEyePosition(1f)
        val lookDir = player.lookAngle
        val (jobId, aabbDist) = ConstructionRenderer.findJobAtRayWithDist(eyePos, lookDir, player.blockInteractionRange()) ?: return

        val hit = mc.hitResult
        if (hit != null && hit.type == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            val blockDist = hit.location.distanceTo(eyePos)
            if (blockDist < aabbDist) return
        }

        ConstructionRenderer.getJobInfo(jobId) ?: return
        val clipboardHand = InteractionHand.entries.firstOrNull { AllBlocks.CLIPBOARD.isIn(player.getItemInHand(it)) }
        if (clipboardHand != null) {
            PacketDistributor.sendToServer(WriteMissingToClipboardPacket(jobId, clipboardHand))
        } else {
            mc.setScreen(JobDetailScreen(jobId))
        }
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
}
