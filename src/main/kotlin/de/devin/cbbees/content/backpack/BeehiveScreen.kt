package de.devin.cbbees.content.backpack

import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen
import com.simibubi.create.foundation.gui.widget.IconButton
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.backpack.client.UpgradeGridWidget
import de.devin.cbbees.content.upgrades.BeeUpgradeItem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

/**
 * Screen/GUI for the Constructor Backpack.
 *
 * Displays:
 * - 2 bee slots (stacked vertically)
 * - 6x5 upgrade grid (separate widget)
 * - Shape preview for carried / hovered upgrade
 * - Player inventory
 */
class BeehiveScreen(
    menu: BeehiveContainer,
    playerInventory: Inventory,
    title: Component
) : AbstractSimiContainerScreen<BeehiveContainer>(menu, playerInventory, title) {

    companion object {
        val PLAYER_INV = AllGuiTextures.PLAYER_INVENTORY

        val TEXTURE: ResourceLocation = CreateBuzzyBeez.asResource("textures/gui/portable_beehive.png")
        const val TEXTURE_SIZE = 256

        // Custom background dimensions
        const val BG_WIDTH = 199
        const val BG_HEIGHT = 112

        // Fuel gauge position and size within the GUI
        const val FUEL_W = 16
        const val FUEL_X = 8
        const val FUEL_Y = 71
        const val FUEL_H = 37

        // Filled fuel bar on the atlas
        const val FUEL_U = 210
        const val FUEL_V = 5

        // Grid widget origin relative to the window
        const val GRID_X = 29
        const val GRID_Y = 19
    }

    private lateinit var confirmButton: IconButton
    private lateinit var gridWidget: UpgradeGridWidget

    init {
        imageWidth = maxOf(BG_WIDTH, PLAYER_INV.width)
        imageHeight = BG_HEIGHT + 4 + PLAYER_INV.height
    }

    override fun init() {
        super.init()

        // Confirm button
        confirmButton = IconButton(leftPos + 166, topPos + 88, AllIcons.I_CONFIRM)
        confirmButton.withCallback<IconButton>(Runnable {
            minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, 0)
        })
        addRenderableWidget(confirmButton)

        // Upgrade grid widget
        gridWidget = UpgradeGridWidget(
            leftPos + GRID_X,
            topPos + GRID_Y,
            menu
        ) { menu.backpackStack }
        addRenderableWidget(gridWidget)
    }

    // ── background ──

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos
        val y = topPos

        // Custom background texture
        guiGraphics.blit(TEXTURE, x, y, 0f, 0f, BG_WIDTH, BG_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE)

        // Player inventory
        val invX = leftPos + (imageWidth - PLAYER_INV.width) / 2
        val invY = topPos + BG_HEIGHT + 3
        renderPlayerInventory(guiGraphics, invX, invY)
    }

    // ── foreground (above slots, below tooltips) ──

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // Fuel gauge — rendered above the upgrade grid, fills from bottom up
        renderFuelGauge(guiGraphics)

        // Fuel gauge tooltip
        if (isHoveringFuelGauge(mouseX, mouseY)) {
            val fuel = menu.fuelData.get(0)
            val maxFuel = menu.fuelData.get(1)
            guiGraphics.renderTooltip(
                font,
                Component.translatable("tooltip.cbbees.beehive.honey", fuel, maxFuel),
                mouseX, mouseY
            )
        }

        // Render tooltip last
        renderTooltip(guiGraphics, mouseX, mouseY)
    }

    private fun isHoveringFuelGauge(mouseX: Int, mouseY: Int): Boolean {
        val gx = leftPos + FUEL_X
        val gy = topPos + FUEL_Y
        return mouseX >= gx && mouseX < gx + FUEL_W && mouseY >= gy && mouseY < gy + FUEL_H
    }

    private fun renderFuelGauge(guiGraphics: GuiGraphics) {
        val fuel = menu.fuelData.get(0)
        val maxFuel = menu.fuelData.get(1)
        if (maxFuel <= 0 || fuel <= 0) return

        val fillHeight = (fuel * FUEL_H / maxFuel).coerceAtMost(FUEL_H)
        val emptyHeight = FUEL_H - fillHeight
        guiGraphics.blit(
            TEXTURE,
            leftPos + FUEL_X, topPos + FUEL_Y + emptyHeight,
            FUEL_U.toFloat(), (FUEL_V + emptyHeight).toFloat(),
            FUEL_W, fillHeight,
            TEXTURE_SIZE, TEXTURE_SIZE
        )
    }

    // ── mouse events ──

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Right-click anywhere with upgrade on cursor: rotate preview
        if (button == 1 && ::gridWidget.isInitialized) {
            val carried = menu.carried
            if (!carried.isEmpty && carried.item is BeeUpgradeItem) {
                gridWidget.currentRotation = (gridWidget.currentRotation + 1) % 4
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        // Scroll anywhere with upgrade on cursor: rotate preview
        if (::gridWidget.isInitialized) {
            val carried = menu.carried
            if (!carried.isEmpty && carried.item is BeeUpgradeItem) {
                gridWidget.currentRotation = if (scrollY > 0) {
                    (gridWidget.currentRotation + 1) % 4
                } else {
                    (gridWidget.currentRotation + 3) % 4
                }
                return true
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }
}
