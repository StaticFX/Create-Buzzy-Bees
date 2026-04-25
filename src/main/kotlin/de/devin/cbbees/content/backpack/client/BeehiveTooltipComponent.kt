package de.devin.cbbees.content.backpack.client

import com.simibubi.create.foundation.gui.AllGuiTextures
import de.devin.cbbees.content.backpack.BeehiveTooltipData
import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.upgrades.UpgradeGrid
import de.devin.cbbees.content.upgrades.UpgradeType
import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

/**
 * Client-side renderer for the backpack tooltip preview.
 *
 * Renders robot slots and a mini 6x4 upgrade grid.
 */
class BeehiveTooltipComponent(val data: BeehiveTooltipData) : ClientTooltipComponent {

    private val items: NonNullList<ItemStack> = NonNullList.withSize(PortableBeehiveItem.TOTAL_SLOTS, ItemStack.EMPTY)
    private val grid: UpgradeGrid?

    init {
        val contents = data.stack.get(DataComponents.CONTAINER)
        contents?.copyInto(items)
        grid = data.stack.get(AllDataComponents.UPGRADE_GRID.get())
    }

    companion object {
        const val MINI_CELL = 6  // Each mini grid cell size in pixels
    }

    override fun getHeight(): Int {
        // Robot row (18px) + gap (2px) + mini grid (ROWS * MINI_CELL) + padding
        return 18 + 2 + UpgradeGrid.ROWS * MINI_CELL + 4
    }

    override fun getWidth(font: Font): Int {
        // Wider of: robot slots or mini grid
        val robotWidth = PortableBeehiveItem.ROBOT_SLOTS * 18 + 2
        val gridWidth = UpgradeGrid.COLS * MINI_CELL + 2
        return maxOf(robotWidth, gridWidth)
    }

    override fun renderImage(font: Font, x: Int, y: Int, guiGraphics: GuiGraphics) {
        var currentY = y

        // Render robots
        for (i in 0 until PortableBeehiveItem.ROBOT_SLOTS) {
            val stack = items[i]
            renderSlot(guiGraphics, x + i * 18, currentY, stack, font)
        }

        // Render mini upgrade grid below robots
        currentY += 18 + 2
        renderMiniGrid(guiGraphics, x, currentY)
    }

    private fun renderMiniGrid(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val g = grid ?: return

        // Draw empty cell backgrounds
        for (row in 0 until UpgradeGrid.ROWS) {
            for (col in 0 until UpgradeGrid.COLS) {
                val cx = x + col * MINI_CELL
                val cy = y + row * MINI_CELL
                guiGraphics.fill(cx, cy, cx + MINI_CELL - 1, cy + MINI_CELL - 1, 0xFF333333.toInt())
            }
        }

        // Draw colored cells for placed upgrades
        for (row in 0 until UpgradeGrid.ROWS) {
            for (col in 0 until UpgradeGrid.COLS) {
                val type = g.occupied[row][col] ?: continue
                val cx = x + col * MINI_CELL
                val cy = y + row * MINI_CELL
                guiGraphics.fill(cx, cy, cx + MINI_CELL - 1, cy + MINI_CELL - 1, getUpgradeColor(type))
            }
        }
    }

    private fun getUpgradeColor(type: UpgradeType): Int = when (type) {
        UpgradeType.RAPID_WINGS -> 0xFFFF6600.toInt()         // Orange
        UpgradeType.SWARM_INTELLIGENCE -> 0xFF00AAFF.toInt()   // Blue
        UpgradeType.HONEY_EFFICIENCY -> 0xFFFFDD00.toInt()     // Yellow
        UpgradeType.SOFT_TOUCH -> 0xFF00FF88.toInt()           // Green
        UpgradeType.DROP_ITEMS -> 0xFFFF4444.toInt()           // Red
        UpgradeType.HONEY_TANK -> 0xFFD97F00.toInt()          // Amber
        UpgradeType.DRONE_VIEW -> 0xFF9933FF.toInt()             // Purple
        UpgradeType.DRONE_RANGE -> 0xFF00CCCC.toInt()            // Cyan
        UpgradeType.INVENTORY_ACCESS -> 0xFFCC8844.toInt()      // Brown
    }

    private fun renderSlot(guiGraphics: GuiGraphics, x: Int, y: Int, stack: ItemStack, font: Font) {
        AllGuiTextures.JEI_SLOT.render(guiGraphics, x, y)

        if (!stack.isEmpty) {
            guiGraphics.renderItem(stack, x + 1, y + 1)
            guiGraphics.renderItemDecorations(font, stack, x + 1, y + 1)
        }
    }
}
