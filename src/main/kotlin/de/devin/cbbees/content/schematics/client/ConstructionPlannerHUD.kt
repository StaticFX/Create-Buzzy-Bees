package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.simibubi.create.foundation.gui.AllGuiTextures
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import de.devin.cbbees.registry.AllKeys
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

/**
 * Renders the in-game HUD overlay for the Construction Planner's inline schematic selector.
 *
 * The main panel shows the selected entry with arrows and a counter.
 * When Alt is held, a separate hint panel extends below with context-sensitive
 * action hints (mirroring Create's [ToolSelectionScreen] tooltip behavior).
 * Selection changes and group navigation trigger smooth horizontal slide animations.
 */
@OnlyIn(Dist.CLIENT)
object ConstructionPlannerHUD {

    /** Eased offset controlling hint panel visibility (0 = hidden, 10 = fully visible). */
    private var yOffset = 0f

    /** Horizontal slide offset for smooth selection/group transitions. */
    private var slideOffset = 0f

    /**
     * Called each client tick to update animation state.
     * Mirrors Create's [ToolSelectionScreen.update] easing pattern.
     */
    fun update() {
        if (!ConstructionPlannerHandler.isActive()) {
            yOffset = 0f
            slideOffset = 0f
            return
        }

        if (isAltDown()) {
            yOffset += (10f - yOffset) * 0.1f
        } else {
            yOffset *= 0.9f
        }

        slideOffset *= 0.75f
        if (abs(slideOffset) < 0.5f) slideOffset = 0f
    }

    /** Triggers a large horizontal slide for group navigation. */
    fun onGroupChanged(entering: Boolean) {
        slideOffset = if (entering) 60f else -60f
    }

    /** Triggers a smaller horizontal slide for scrolling between entries. */
    fun onSelectionChanged(forward: Boolean) {
        slideOffset = if (forward) 30f else -30f
    }

    fun renderHUD(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        if (!ConstructionPlannerHandler.isActive()) return

        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.screen != null) return

        val screenWidth = guiGraphics.guiWidth()
        val screenHeight = guiGraphics.guiHeight()
        val gray = AllGuiTextures.HUD_BACKGROUND
        val centerX = screenWidth / 2

        guiGraphics.pose().pushPose()

        val currentItems = ConstructionPlannerHandler.getCurrentItems()
        val selectedIndex = ConstructionPlannerHandler.selectedIndex
        val currentGroupPath = ConstructionPlannerHandler.currentGroupPath

        if (currentItems.isEmpty()) {
            renderEmpty(guiGraphics, mc, gray, centerX, screenHeight)
            guiGraphics.pose().popPose()
            return
        }

        val entry = currentItems[selectedIndex]
        val nameComp = entryDisplayComp(entry)
        val nameColor = entryColor(entry)

        // Measure name line
        val leftArrow = "< "
        val rightArrow = " >"
        val nameWidth = mc.font.width(nameComp)
        val leftW = mc.font.width(leftArrow)
        val rightW = mc.font.width(rightArrow)
        val nameLineW = leftW + nameWidth + rightW
        val counterText = Component.literal("${selectedIndex + 1}/${currentItems.size}")
        val counterWidth = mc.font.width(counterText)

        // Context-sensitive hint
        val hintText = when (entry) {
            is ConstructionPlannerHandler.HudEntry.SchematicEntry ->
                Component.translatable("gui.cbbees.construction_planner.hint_schematic")

            is ConstructionPlannerHandler.HudEntry.GroupEntry ->
                Component.translatable("gui.cbbees.construction_planner.hint_group")

            is ConstructionPlannerHandler.HudEntry.ParentEntry ->
                Component.translatable("gui.cbbees.construction_planner.hint_back")
        }
        val hintWidth = mc.font.width(hintText)

        // Second hint line: keybinds (rotation/mirror for schematics, browser for all)
        val browserKey = AllKeys.OPEN_SCHEMATIC_BROWSER.getTranslatedKeyMessage().string
        val secondHint = if (entry is ConstructionPlannerHandler.HudEntry.SchematicEntry) {
            val rotateKey = AllKeys.ROTATE_PREVIEW.getTranslatedKeyMessage().string
            val mirrorKey = AllKeys.MIRROR_PREVIEW.getTranslatedKeyMessage().string
            Component.translatable("gui.cbbees.construction_planner.hint_transform", rotateKey, mirrorKey, browserKey)
        } else {
            Component.translatable("gui.cbbees.construction_planner.hint_browser", browserKey)
        }
        val secondHintWidth = mc.font.width(secondHint)

        // Compact hint (shown above panel when Alt not held)
        val compactHint = Component.translatable("gui.cbbees.construction_planner.hint_alt", AllKeys.SCHEMATIC_MODIFIER.translatedKeyMessage)
        val compactHintWidth = mc.font.width(compactHint)

        // Breadcrumb
        val hasBreadcrumb = currentGroupPath.isNotEmpty()
        val breadcrumbComp = if (hasBreadcrumb) {
            Component.literal(buildBreadcrumb(currentGroupPath)).withStyle { it.withColor(0x888888) }
        } else null
        val breadcrumbWidth = breadcrumbComp?.let { mc.font.width(it) } ?: 0

        // Layout — main panel holds breadcrumb + name only (no hint line)
        val mainHeight = if (hasBreadcrumb) 32 else 20
        val bgWidth = maxOf(
            nameLineW + counterWidth + 10,
            compactHintWidth,
            hintWidth,
            secondHintWidth,
            breadcrumbWidth
        ) + 30
        val bgX = centerX - bgWidth / 2
        val bgY = screenHeight - if (hasBreadcrumb) 90 else 78

        val hintAlpha = yOffset / 10f
        val stringAlphaComponent = ((hintAlpha * 0xFF).toInt().coerceIn(0, 255)) shl 24

        RenderSystem.enableBlend()

        // === "Hold Alt" text above main panel (no background, fades out when Alt held) ===
        if (hintAlpha < 0.7f) {
            val fade = 1f - (hintAlpha / 0.7f)
            val a = (fade * 0xFF).toInt().coerceIn(0, 255)
            guiGraphics.drawString(
                mc.font, compactHint,
                centerX - compactHintWidth / 2, bgY - 14,
                (a shl 24) or 0xCCDDFF, true
            )
        }

        // === Main panel background ===
        RenderSystem.setShaderColor(1f, 1f, 1f, if (hintAlpha > 0.5f) 7f / 8f else 3f / 4f)
        guiGraphics.blit(
            gray.location, bgX, bgY,
            gray.startX.toFloat(), gray.startY.toFloat(),
            bgWidth, mainHeight, gray.width, gray.height
        )
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        var yPos = bgY + 5

        // Breadcrumb (always visible when in subgroup)
        if (hasBreadcrumb && breadcrumbComp != null) {
            guiGraphics.drawString(mc.font, breadcrumbComp, centerX - breadcrumbWidth / 2, yPos, 0x888888, false)
            yPos += 12
        }

        // Scissor clip carousel to main panel bounds
        guiGraphics.enableScissor(bgX, bgY, bgX + bgWidth, bgY + mainHeight)

        // Name line with slide animation and carousel neighbors
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(slideOffset, 0f, 0f)

        // Faded prev/next neighbors
        if (currentItems.size > 1) {
            val prevEntry = currentItems[(selectedIndex - 1 + currentItems.size) % currentItems.size]
            val nextEntry = currentItems[(selectedIndex + 1) % currentItems.size]
            val prevComp = entryDisplayComp(prevEntry)
            val nextComp = entryDisplayComp(nextEntry)
            val prevW = mc.font.width(prevComp)
            val neighborAlpha = 0x59 // ~35%

            // Previous: right-aligned before left arrow
            guiGraphics.drawString(
                mc.font, prevComp,
                centerX - nameLineW / 2 - 10 - prevW, yPos,
                (neighborAlpha shl 24) or entryColor(prevEntry), false
            )
            // Next: left-aligned after right arrow
            guiGraphics.drawString(
                mc.font, nextComp,
                centerX + nameLineW / 2 + 10, yPos,
                (neighborAlpha shl 24) or entryColor(nextEntry), false
            )
        }

        // Current item with arrows
        var drawX = centerX - nameLineW / 2
        guiGraphics.drawString(mc.font, leftArrow, drawX, yPos, 0x999999, false)
        drawX += leftW
        guiGraphics.drawString(mc.font, nameComp, drawX, yPos, nameColor, false)
        drawX += nameWidth
        guiGraphics.drawString(mc.font, rightArrow, drawX, yPos, 0x999999, false)

        guiGraphics.pose().popPose()
        guiGraphics.disableScissor()

        // Counter (fixed position, no slide)
        guiGraphics.drawString(mc.font, counterText, bgX + bgWidth - counterWidth - 6, yPos, 0x666666, false)

        // === Extended hint panel (separate background, extends below main when Alt held) ===
        if (hintAlpha > 0.25f) {
            val hintBgY = bgY + mainHeight + 2

            RenderSystem.setShaderColor(0.7f, 0.7f, 0.8f, hintAlpha)
            guiGraphics.blit(
                gray.location, bgX, hintBgY,
                gray.startX.toFloat(), gray.startY.toFloat(),
                bgWidth, 30, gray.width, gray.height
            )
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            guiGraphics.drawString(
                mc.font, hintText,
                centerX - hintWidth / 2, hintBgY + 4,
                stringAlphaComponent or 0xCCDDFF, false
            )
            guiGraphics.drawString(
                mc.font, secondHint,
                centerX - secondHintWidth / 2, hintBgY + 16,
                stringAlphaComponent or 0xCCCCDD, false
            )
        }

        RenderSystem.disableBlend()
        guiGraphics.pose().popPose()
    }

    private fun renderEmpty(
        guiGraphics: GuiGraphics,
        mc: Minecraft,
        gray: AllGuiTextures,
        centerX: Int,
        screenHeight: Int
    ) {
        val text = Component.translatable("gui.cbbees.construction_planner.no_schematics")
        val textWidth = mc.font.width(text)
        val bgWidth = textWidth + 20
        val bgX = centerX - bgWidth / 2
        val bgY = screenHeight - 75

        RenderSystem.enableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.75f)
        guiGraphics.blit(
            gray.location, bgX, bgY,
            gray.startX.toFloat(), gray.startY.toFloat(),
            bgWidth, 18, gray.width, gray.height
        )
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        guiGraphics.drawString(mc.font, text, centerX - textWidth / 2, bgY + 5, 0x888888, false)
        RenderSystem.disableBlend()
    }

    private fun isAltDown(): Boolean = de.devin.cbbees.registry.AllKeys.SCHEMATIC_MODIFIER.isDown

    private fun entryDisplayComp(entry: ConstructionPlannerHandler.HudEntry): Component {
        val name = when (entry) {
            is ConstructionPlannerHandler.HudEntry.ParentEntry -> "Back"
            is ConstructionPlannerHandler.HudEntry.GroupEntry -> entry.name
            is ConstructionPlannerHandler.HudEntry.SchematicEntry -> entry.filename.removeSuffix(".nbt")
        }
        val icon = when (entry) {
            is ConstructionPlannerHandler.HudEntry.ParentEntry -> "\u25C0 "
            is ConstructionPlannerHandler.HudEntry.GroupEntry -> "\uD83D\uDCC1 "
            is ConstructionPlannerHandler.HudEntry.SchematicEntry -> ""
        }
        return Component.literal("$icon$name")
    }

    private fun entryColor(entry: ConstructionPlannerHandler.HudEntry): Int {
        return when (entry) {
            is ConstructionPlannerHandler.HudEntry.ParentEntry -> 0x999999
            is ConstructionPlannerHandler.HudEntry.GroupEntry -> 0x88CCFF
            is ConstructionPlannerHandler.HudEntry.SchematicEntry -> 0xFFFF00
        }
    }

    private fun buildBreadcrumb(groupPath: String): String {
        if (groupPath.isEmpty()) return "Root"
        val parts = groupPath.split("/")
        return "Root > " + parts.joinToString(" > ")
    }
}
