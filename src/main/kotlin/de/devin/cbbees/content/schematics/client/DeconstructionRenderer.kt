package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.systems.RenderSystem
import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.foundation.gui.AllGuiTextures
import de.devin.cbbees.registry.AllKeys
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import de.devin.cbbees.util.ClientSide
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

/**
 * Handles HUD and world rendering for the deconstruction planner.
 *
 * HUD matches the construction planner style: main panel above hotbar with
 * an extending hint panel when Alt is held.
 */
@ClientSide
@OnlyIn(Dist.CLIENT)
object DeconstructionRenderer {
    private val outlineSlot = Any()
    private const val DECONSTRUCTION_COLOR = 0xc56868
    private const val PICKUP_COLOR = 0x68c588

    /** Eased offset controlling hint panel visibility (0 = hidden, 10 = fully visible). */
    private var yOffset = 0f

    fun renderWorldOutline(selectedFace: Direction?) {
        val box = DeconstructionSelection.getSelectionBox() ?: return
        val color = if (PickupHandler.isActive()) PICKUP_COLOR else DECONSTRUCTION_COLOR

        Outliner.getInstance()
            .chaseAABB(outlineSlot, box)
            .colored(color)
            .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
            .lineWidth(1 / 16f)
            .highlightFace(selectedFace)
    }

    private fun isAnyPlannerActive() = DeconstructionHandler.isActive() || PickupHandler.isActive()

    fun update() {
        if (!isAnyPlannerActive()) {
            yOffset = 0f
            return
        }

        if (isAltDown()) {
            yOffset += (10f - yOffset) * 0.1f
        } else {
            yOffset *= 0.9f
        }
    }

    fun renderHUD(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        if (!isAnyPlannerActive()) return
        val isPickup = PickupHandler.isActive()

        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.screen != null) return

        val first = DeconstructionSelection.firstPos ?: return

        val screenWidth = guiGraphics.guiWidth()
        val screenHeight = guiGraphics.guiHeight()
        val gray = AllGuiTextures.HUD_BACKGROUND
        val centerX = screenWidth / 2

        guiGraphics.pose().pushPose()

        val second = DeconstructionSelection.secondPos

        // Title
        val titleKey = if (isPickup) "cbbees.pickup.title" else "cbbees.deconstruction.title"
        val titleText = Component.translatable(titleKey)
        val titleWidth = mc.font.width(titleText)

        // Info line (dimensions or "first corner set")
        val infoText: Component
        val infoColor: Int
        if (second != null) {
            val sizeX = abs(first.x - second.x) + 1
            val sizeY = abs(first.y - second.y) + 1
            val sizeZ = abs(first.z - second.z) + 1
            infoText = Component.translatable("cbbees.deconstruction.dimensions", sizeX, sizeY, sizeZ)
            infoColor = 0xCCDDFF
        } else {
            infoText = Component.translatable("cbbees.deconstruction.first_pos")
            infoColor = 0xAAAAAA
        }
        val infoWidth = mc.font.width(infoText)

        // Context-sensitive hints
        val hintPrefix = if (isPickup) "gui.cbbees.pickup" else "gui.cbbees.deconstruction"
        val hintText = if (second != null) {
            Component.translatable(
                "$hintPrefix.hint_ready",
                AllKeys.START_ACTION.translatedKeyMessage
            )
        } else {
            Component.translatable("$hintPrefix.hint_select")
        }
        val hintWidth = mc.font.width(hintText)

        val secondHint = if (second != null) {
            Component.translatable(
                "$hintPrefix.hint_program",
                AllKeys.PROGRAM_ACTION.translatedKeyMessage
            )
        } else {
            Component.translatable("$hintPrefix.hint_scroll")
        }
        val secondHintWidth = mc.font.width(secondHint)

        // Layout
        val mainHeight = 32
        val bgWidth = maxOf(
            titleWidth,
            infoWidth,
            hintWidth,
            secondHintWidth
        ) + 30
        val bgX = centerX - bgWidth / 2
        val bgY = screenHeight - 90

        val hintAlpha = yOffset / 10f
        val stringAlphaComponent = ((hintAlpha * 0xFF).toInt().coerceIn(0, 255)) shl 24

        RenderSystem.enableBlend()


        // === Main panel background ===
        RenderSystem.setShaderColor(1f, 1f, 1f, if (hintAlpha > 0.5f) 7f / 8f else 3f / 4f)
        guiGraphics.blit(
            gray.location, bgX, bgY,
            gray.startX.toFloat(), gray.startY.toFloat(),
            bgWidth, mainHeight, gray.width, gray.height
        )
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        // Title
        val titleColor = if (isPickup) 0xCCFFCC else 0xFFCCCC
        guiGraphics.drawString(
            mc.font, titleText,
            centerX - titleWidth / 2, bgY + 5,
            titleColor, false
        )

        // Info line
        guiGraphics.drawString(
            mc.font, infoText,
            centerX - infoWidth / 2, bgY + 18,
            infoColor, false
        )

        // === Extended hint panel (extends below main when Alt held) ===
        if (hintAlpha > 0.25f) {
            val hintBgY = bgY + mainHeight + 2

            if (isPickup) RenderSystem.setShaderColor(0.7f, 0.8f, 0.7f, hintAlpha)
            else RenderSystem.setShaderColor(0.8f, 0.7f, 0.7f, hintAlpha)
            guiGraphics.blit(
                gray.location, bgX, hintBgY,
                gray.startX.toFloat(), gray.startY.toFloat(),
                bgWidth, 30, gray.width, gray.height
            )
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

            val hintColor = if (isPickup) 0xCCFFCC else 0xFFCCCC
            guiGraphics.drawString(
                mc.font, hintText,
                centerX - hintWidth / 2, hintBgY + 4,
                stringAlphaComponent or hintColor, false
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

    private fun isAltDown(): Boolean = de.devin.cbbees.registry.AllKeys.SCHEMATIC_MODIFIER.isDown
}
