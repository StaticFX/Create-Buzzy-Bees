package de.devin.cbbees.content.schematics.client

import com.simibubi.create.AllDataComponents
import com.simibubi.create.CreateClient
import com.simibubi.create.content.schematics.SchematicItem
import de.devin.cbbees.content.drone.client.DroneViewClientState
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import com.simibubi.create.foundation.utility.RaycastHelper
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.network.InstantConstructionPacket
import de.devin.cbbees.network.SelectSchematicPacket
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/**
 * Client-side handler for the Construction Planner's inline schematic selector HUD.
 *
 * ## States
 * - **State 1** (Item in Hand): Player holds empty Construction Planner. Our HUD shows.
 *   Create is dormant — no data on the item.
 * - **State 2** (Schematic Hovered): Player has scrolled to a schematic. Still no data
 *   on the item. Our HUD shows + ghost blocks at crosshair + AABB outline. Create dormant.
 * - **State 3** (Schematic Selected/Deployed): Player RMB-confirmed. We set SCHEMATIC_FILE +
 *   DEPLOYED=true + ANCHOR on the item. Create activates with full tools.
 *
 * Browsing state (state 2) is purely internal — no data components on the item.
 */
@OnlyIn(Dist.CLIENT)
object ConstructionPlannerHandler {

    /** Represents an item in the HUD list — a parent nav entry, group folder, or schematic file. */
    sealed class HudEntry {
        /** Navigate up one level in the group hierarchy. */
        data object ParentEntry : HudEntry()
        data class GroupEntry(val name: String, val fullPath: String) : HudEntry()
        data class SchematicEntry(val filename: String) : HudEntry()
    }

    private var allSchematics: List<String> = emptyList()
    private var currentItems: List<HudEntry> = emptyList()

    /** Returns the current HUD item list (groups + schematics at the current level). */
    fun getCurrentItems(): List<HudEntry> = currentItems
    var selectedIndex = 0
        private set

    /** Current group navigation path. Empty string = root. */
    var currentGroupPath: String = ""
        private set

    /**
     * True while browsing schematics with a ghost preview visible.
     * This is purely internal state — no data on the item.
     * Accessible from Java mixins via `ConstructionPlannerHandler.INSTANCE.isBrowsingPreview()`.
     */
    var isBrowsingPreview = false
        private set

    /**
     * Tracks whether Create's SchematicHandler has been active for the current deployment.
     * Used to distinguish "Create hasn't ticked yet" from "Create was active and then stopped"
     * (e.g. after Print). We only clear on the latter.
     */
    private var createWasActive = false

    /** Filename currently being previewed. Internal state only. */
    private var browsingFilename: String? = null

    /** Saved state from the last deployment, so we can restore after construction completes. */
    private var lastDeployedFilename: String? = null
    private var lastDeployedRotation: Rotation = Rotation.NONE
    private var lastDeployedMirror: Mirror = Mirror.NONE

    private var lastRefreshTick = 0L
    private const val REFRESH_INTERVAL = 40L // 2 seconds

    /**
     * Our HUD is active when the player holds a Construction Planner that has
     * no schematic deployed. In state 3 (deployed), Create owns the UI.
     */
    fun isActive(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val stack = DroneViewClientState.findActivePlanner(player)
        if (stack.isEmpty) return false
        // State 3: deployed → Create owns it, our HUD is inactive
        if (stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false)) return false
        return true
    }

    fun tick() {
        val player = Minecraft.getInstance().player

        // Pump upload chunks
        SchematicUploader.tick()

        // Not holding a planner — pause preview rendering but keep internal state
        val stack = if (player != null) DroneViewClientState.findActivePlanner(player) else null
        if (stack == null || stack.isEmpty) {
            createWasActive = false
            if (isBrowsingPreview) {
                isBrowsingPreview = false
                SchematicHoverPreview.clear()
            }
            return
        }

        // Detect exit from state 3: we were in Create's overlay (createWasActive) but the
        // item is no longer deployed. This covers all exit paths:
        //  - Construct tool (mixin clears everything immediately)
        //  - Unselect tool (mixin clears everything immediately)
        //  - Create's own Print action (clears DEPLOYED, keeps FILE)
        // Restore browsing state with the same schematic and rotation.
        if (createWasActive
            && !stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false)) {
            createWasActive = false

            // Clean up leftover data components (Create's Print leaves FILE on the item)
            if (stack.has(AllDataComponents.SCHEMATIC_FILE)) {
                ConstructionPlannerItem.clearSchematic(stack)
            }

            if (lastDeployedFilename != null) {
                browsingFilename = lastDeployedFilename
                isBrowsingPreview = true
                SchematicHoverPreview.updatePreview(lastDeployedFilename)
                SchematicHoverPreview.setTransform(lastDeployedRotation, lastDeployedMirror)
            }
            return
        }

        // State 3: deployed → Create owns it, nothing for us to do
        if (stack.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false)) {
            if (CreateClient.SCHEMATIC_HANDLER.isActive) {
                createWasActive = true
            }
            if (isBrowsingPreview) {
                isBrowsingPreview = false
                browsingFilename = null
                SchematicHoverPreview.clear()
            }
            return
        }

        // State 1/2: holding empty planner → restore browsing if we had one
        if (!isBrowsingPreview && browsingFilename != null) {
            isBrowsingPreview = true
            SchematicHoverPreview.updatePreview(browsingFilename)
        }

        // Update crosshair position for ghost preview
        if (isBrowsingPreview) {
            SchematicHoverPreview.tick()
        }

        // Refresh schematic list periodically
        val tick = Minecraft.getInstance().level?.gameTime ?: 0L
        if (allSchematics.isEmpty() || tick - lastRefreshTick >= REFRESH_INTERVAL) {
            refreshSchematics()
            lastRefreshTick = tick
        }
    }

    private fun refreshSchematics() {
        CreateClient.SCHEMATIC_SENDER.refresh()
        allSchematics = CreateClient.SCHEMATIC_SENDER.availableSchematics.map { it.string }

        SchematicGroupManager.ensureLoaded()
        SchematicGroupManager.reconcile(allSchematics)

        rebuildItems()
    }

    /** Rebuilds the HUD item list for the current group path. */
    private fun rebuildItems() {
        val (subgroups, schematics) = SchematicGroupManager.getItemsAtLevel(currentGroupPath, allSchematics)

        val items = mutableListOf<HudEntry>()
        if (currentGroupPath.isNotEmpty()) {
            items.add(HudEntry.ParentEntry)
        }
        for (group in subgroups) {
            val fullPath = if (currentGroupPath.isEmpty()) group else "$currentGroupPath/$group"
            items.add(HudEntry.GroupEntry(group, fullPath))
        }
        for (filename in schematics) {
            items.add(HudEntry.SchematicEntry(filename))
        }

        currentItems = items
        if (selectedIndex >= currentItems.size) {
            selectedIndex = maxOf(0, currentItems.size - 1)
        }

        updateBrowsingPreview()
    }

    /**
     * Updates the ghost preview based on the current HUD selection.
     * Only sets internal state — never touches item data components.
     */
    private fun updateBrowsingPreview() {
        val entry = currentItems.getOrNull(selectedIndex)
        if (entry is HudEntry.SchematicEntry) {
            browsingFilename = entry.filename
            isBrowsingPreview = true
            SchematicHoverPreview.updatePreview(entry.filename)
        } else {
            browsingFilename = null
            isBrowsingPreview = false
            SchematicHoverPreview.updatePreview(null)
        }
    }

    /**
     * Cancels browsing and clears all internal preview state.
     * Called from Java mixins via `ConstructionPlannerHandler.INSTANCE.clearBrowsingPreview()`.
     */
    fun clearBrowsingPreview() {
        isBrowsingPreview = false
        browsingFilename = null
        createWasActive = false
        SchematicHoverPreview.clear()
    }

    /**
     * Called from scroll events. Cycles through items when Alt is held.
     * @return true if the scroll was consumed
     */
    fun onScroll(delta: Double): Boolean {
        if (!isActive()) return false
        if (!isAltDown()) return false
        if (currentItems.isEmpty()) return false

        val forward = delta < 0
        selectedIndex = if (forward) {
            (selectedIndex + 1) % currentItems.size
        } else {
            (selectedIndex - 1 + currentItems.size) % currentItems.size
        }

        ConstructionPlannerHUD.onSelectionChanged(forward)
        updateBrowsingPreview()
        return true
    }

    /**
     * Navigates into the selected group.
     * @return true if navigation occurred
     */
    fun onNavigateIn(): Boolean {
        val entry = currentItems.getOrNull(selectedIndex) ?: return false
        if (entry !is HudEntry.GroupEntry) return false

        currentGroupPath = entry.fullPath
        selectedIndex = 0
        rebuildItems()
        ConstructionPlannerHUD.onGroupChanged(entering = true)
        return true
    }

    /**
     * Navigates up one level in the group hierarchy.
     * @return true if navigation occurred (false if already at root)
     */
    fun onNavigateOut(): Boolean {
        if (currentGroupPath.isEmpty()) return false

        val lastSlash = currentGroupPath.lastIndexOf('/')
        currentGroupPath = if (lastSlash >= 0) currentGroupPath.substring(0, lastSlash) else ""
        selectedIndex = 0
        rebuildItems()
        ConstructionPlannerHUD.onGroupChanged(entering = false)
        return true
    }

    /**
     * Confirms the current selection via RMB.
     * - For groups: navigates into the group.
     * - For schematics: sets data on the item (FILE + DEPLOYED=true + ANCHOR) so
     *   Create activates with full tools (state 3). Syncs filename to server.
     */
    fun confirmSelection(): Boolean {
        val entry = currentItems.getOrNull(selectedIndex) ?: return false

        return when (entry) {
            is HudEntry.ParentEntry -> onNavigateOut()
            is HudEntry.GroupEntry -> onNavigateIn()
            is HudEntry.SchematicEntry -> deploySchematic(entry.filename)
        }
    }

    /**
     * Called from the full-screen browser to deploy a schematic by name.
     * Uses the current crosshair position for the anchor.
     */
    fun confirmSchematicByName(filename: String): Boolean {
        return deploySchematic(filename)
    }

    /**
     * Sets schematic data on the item and transitions to state 3 (Create's deploy).
     * The anchor comes from the hover preview if available, otherwise from a fresh
     * raycast (needed when confirming from the full-screen GUI where the preview is inactive).
     *
     * If the schematic file hasn't been uploaded to the server yet, starts a chunked
     * upload first. The [SelectSchematicPacket] is sent after the upload completes (or
     * immediately if the file already exists on the server). NeoForge guarantees packet
     * ordering, so the server will have the file by the time it processes the select packet.
     */
    private fun deploySchematic(filename: String): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val stack = DroneViewClientState.findActivePlanner(player)
        if (stack.isEmpty) return false

        if (SchematicUploader.isUploading()) return false

        val centerAnchor = SchematicHoverPreview.anchorPos ?: run {
            // Hover preview inactive (e.g. confirming from GUI) — raycast from player
            val hit = RaycastHelper.rayTraceRange(player.level(), player, 75.0)
            if (hit.type != net.minecraft.world.phys.HitResult.Type.BLOCK) {
                // No block in range — place at player's feet as fallback
                player.blockPosition()
            } else {
                val hitPos = net.minecraft.core.BlockPos.containing(hit.location)
                val replaceable = player.level().getBlockState(hitPos).canBeReplaced()
                if (!replaceable) hitPos.relative(hit.direction) else hitPos
            }
        }

        // Transfer rotation/mirror from the ghost preview so the deployed schematic matches
        val rotation = SchematicHoverPreview.currentRotation
        val mirror = SchematicHoverPreview.currentMirror

        // Convert center anchor to corner anchor for Create's placement system
        val anchor = SchematicHoverPreview.computeServerAnchor(centerAnchor)

        // Closure that finishes deployment — called immediately or after upload completes
        val finishDeploy = {
            val currentStack = DroneViewClientState.findActivePlanner(player)
            if (!currentStack.isEmpty) {
                // Set all data components on the client item — Create will pick this up
                currentStack.set(AllDataComponents.SCHEMATIC_FILE, filename)
                currentStack.set(AllDataComponents.SCHEMATIC_OWNER, player.gameProfile.name)
                currentStack.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)
                currentStack.set(AllDataComponents.SCHEMATIC_ANCHOR, anchor)
                currentStack.set(AllDataComponents.SCHEMATIC_ROTATION, rotation)
                currentStack.set(AllDataComponents.SCHEMATIC_MIRROR, mirror)

                // Write bounds so Create can render properly
                try {
                    SchematicItem.writeSize(player.level(), currentStack)
                } catch (_: Exception) {}

                // Sync all data to server so inventory sync doesn't clobber client state
                PacketDistributor.sendToServer(SelectSchematicPacket(filename, anchor, rotation, mirror))

                player.displayClientMessage(
                    Component.translatable(
                        "gui.cbbees.construction_planner.selected",
                        filename.removeSuffix(".nbt")
                    ).withStyle { it.withColor(0xFFFF00) },
                    true
                )
            }
        }

        val owner = player.gameProfile.name
        if (SchematicUploader.isAlreadyUploaded(owner, filename)) {
            finishDeploy()
        } else {
            SchematicUploader.startUpload(filename, finishDeploy)
        }

        // Save state so we can restore after construction completes
        lastDeployedFilename = filename
        lastDeployedRotation = rotation
        lastDeployedMirror = mirror

        // Clear our browsing state — Create takes over on next tick (or after upload)
        clearBrowsingPreview()
        return true
    }

    /**
     * Shift+RMB: instantly places and starts construction at the crosshair position.
     * If the schematic needs uploading, the construction packet is sent after upload.
     * @return true if the action was dispatched
     */
    fun instantConstruct(): Boolean {
        val entry = currentItems.getOrNull(selectedIndex) ?: return false
        if (entry !is HudEntry.SchematicEntry) return false

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false

        if (SchematicUploader.isUploading()) return false

        val centerAnchor = SchematicHoverPreview.anchorPos ?: return false
        val rotation = SchematicHoverPreview.currentRotation
        val mirror = SchematicHoverPreview.currentMirror

        // Convert center anchor to corner anchor for server placement
        val anchor = SchematicHoverPreview.computeServerAnchor(centerAnchor)
        val filename = entry.filename

        val sendConstruction = {
            PacketDistributor.sendToServer(
                InstantConstructionPacket(filename, anchor, rotation, mirror)
            )

            // Feedback handled server-side via TaskProgressReporter
        }

        val owner = player.gameProfile.name
        if (SchematicUploader.isAlreadyUploaded(owner, filename)) {
            sendConstruction()
        } else {
            SchematicUploader.startUpload(filename, sendConstruction)
        }

        // Keep browsingFilename so tick() restores the ghost preview on the next frame.
        // Unlike deploySchematic(), instant construction never sets SCHEMATIC_DEPLOYED on
        // the item, so the handler stays in state 1/2 and can immediately re-show the ghost.
        // Don't clear SchematicHoverPreview — just hide it. Next tick, updatePreview()
        // will early-return (same filename) and preserve the rotation/mirror.
        isBrowsingPreview = false
        return true
    }

    /** Returns the currently selected entry, if any. */
    fun getSelectedEntry(): HudEntry? = currentItems.getOrNull(selectedIndex)

    private fun isAltDown(): Boolean = de.devin.cbbees.registry.AllKeys.SCHEMATIC_MODIFIER.isDown

}
