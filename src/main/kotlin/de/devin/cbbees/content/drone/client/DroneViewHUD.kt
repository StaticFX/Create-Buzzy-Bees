package de.devin.cbbees.content.drone.client

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

@ClientSide
object DroneViewHUD {

    fun renderHUD(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        if (!DroneViewClientState.active) return

        val mc = Minecraft.getInstance()
        val font = mc.font
        val screenWidth = guiGraphics.guiWidth()

        // Title bar
        val title = Component.translatable("cbbees.drone_view.hud")
        val titleWidth = font.width(title)
        val titleX = (screenWidth - titleWidth) / 2
        val titleY = 5

        guiGraphics.fill(titleX - 4, titleY - 2, titleX + titleWidth + 4, titleY + font.lineHeight + 2, 0x80000000.toInt())
        guiGraphics.drawString(font, title, titleX, titleY, 0xFF9933FF.toInt(), true)

        // Range indicator
        val player = mc.player ?: return
        val drone = mc.level?.getEntity(DroneViewClientState.droneEntityId) as? MechanicalBeeEntity ?: return

        val dx = drone.x - player.x
        val dz = drone.z - player.z
        val dist = kotlin.math.sqrt(dx * dx + dz * dz)
        val maxRange = DroneViewClientState.maxRange

        val rangeText = Component.translatable(
            "cbbees.drone_view.hud.range",
            String.format("%.0f", dist),
            String.format("%.0f", maxRange)
        )
        val rangeWidth = font.width(rangeText)
        val rangeX = (screenWidth - rangeWidth) / 2
        val rangeY = titleY + font.lineHeight + 4

        // Color shifts from green to red as range fills
        val ratio = (dist / maxRange).coerceIn(0.0, 1.0)
        val rangeColor = if (ratio > 0.85) 0xFFFF4444.toInt()
        else if (ratio > 0.6) 0xFFFFDD00.toInt()
        else 0xFF00FF88.toInt()

        guiGraphics.fill(rangeX - 4, rangeY - 2, rangeX + rangeWidth + 4, rangeY + font.lineHeight + 2, 0x80000000.toInt())
        guiGraphics.drawString(font, rangeText, rangeX, rangeY, rangeColor, true)

        // Movement hint
        val hintText = Component.translatable("cbbees.drone_view.hud.controls")
        val hintWidth = font.width(hintText)
        val hintX = (screenWidth - hintWidth) / 2
        val hintY = rangeY + font.lineHeight + 4

        guiGraphics.fill(hintX - 4, hintY - 2, hintX + hintWidth + 4, hintY + font.lineHeight + 2, 0x80000000.toInt())
        guiGraphics.drawString(font, hintText, hintX, hintY, 0xFFAAAAAA.toInt(), true)
    }
}
