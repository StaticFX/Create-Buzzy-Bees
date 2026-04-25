package de.devin.cbbees.content.backpack.client

import com.simibubi.create.foundation.gui.AllGuiTextures
import de.devin.cbbees.content.upgrades.BeeUpgradeItem
import de.devin.cbbees.content.upgrades.UpgradeGrid
import de.devin.cbbees.content.upgrades.UpgradeType
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.network.GridPlaceUpgradePacket
import de.devin.cbbees.network.GridRemoveUpgradePacket
import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Self-contained widget for the 5x3 upgrade grid using standard 18x18 item slots.
 *
 * Maintains a client-side [UpgradeGrid] for instant feedback (optimistic prediction).
 * Server is authoritative — packets are sent for every mutation.
 */
class UpgradeGridWidget(
    x: Int,
    y: Int,
    private val menu: AbstractContainerMenu,
    private val backpackStackGetter: () -> ItemStack
) : AbstractWidget(x, y, UpgradeGrid.COLS * CELL_SIZE, UpgradeGrid.ROWS * CELL_SIZE, Component.empty()) {

    companion object {
        const val CELL_SIZE = 18
        const val PREVIEW_CELL = 8
    }

    /** Client-side grid for optimistic rendering. */
    var clientGrid: UpgradeGrid = loadGridFromStack()
        private set

    /** Current rotation for placement preview (0-3). Resets when the carried upgrade type changes. */
    var currentRotation: Int = 0
    private var rotationForType: UpgradeType? = null

    fun getRotationFor(type: UpgradeType): Int {
        if (type != rotationForType) {
            currentRotation = 0
            rotationForType = type
        }
        return currentRotation
    }

    private fun loadGridFromStack(): UpgradeGrid {
        return backpackStackGetter().get(AllDataComponents.UPGRADE_GRID.get())?.copy() ?: UpgradeGrid()
    }

    fun resyncFromStack() {
        clientGrid = loadGridFromStack()
    }

    private fun cellAt(mouseX: Double, mouseY: Double): Pair<Int, Int>? {
        if (mouseX < x || mouseY < y) return null
        val col = ((mouseX - x) / CELL_SIZE).toInt()
        val row = ((mouseY - y) / CELL_SIZE).toInt()
        if (col < 0 || col >= UpgradeGrid.COLS || row < 0 || row >= UpgradeGrid.ROWS) return null
        return col to row
    }

    // ── rendering ──

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val grid = clientGrid

        // 1. Slot backgrounds
        for (row in 0 until UpgradeGrid.ROWS) {
            for (col in 0 until UpgradeGrid.COLS) {
                AllGuiTextures.JEI_SLOT.render(guiGraphics, x + col * CELL_SIZE, y + row * CELL_SIZE)
            }
        }

        // 2. Placed upgrade item icons + outlines
        for (placement in grid.placements) {
            val shape = placement.type.shape.rotated(placement.rotation)
            val itemStack = getItemForUpgrade(placement.type)
            val cellSet = shape.cells.map { (dx, dy) -> (placement.x + dx) to (placement.y + dy) }.toSet()
            val outlineColor = getUpgradeColor(placement.type)

            for ((dx, dy) in shape.cells) {
                val gx = placement.x + dx
                val gy = placement.y + dy
                val cx = x + gx * CELL_SIZE
                val cy = y + gy * CELL_SIZE

                // Item icon
                guiGraphics.renderItem(itemStack, cx + 1, cy + 1)

                // Outline edges — draw a 2px border on sides not adjacent to same placement
                val borderW = 2
                // Top
                if ((gx to (gy - 1)) !in cellSet)
                    guiGraphics.fill(cx, cy, cx + CELL_SIZE, cy + borderW, outlineColor)
                // Bottom
                if ((gx to (gy + 1)) !in cellSet)
                    guiGraphics.fill(cx, cy + CELL_SIZE - borderW, cx + CELL_SIZE, cy + CELL_SIZE, outlineColor)
                // Left
                if (((gx - 1) to gy) !in cellSet)
                    guiGraphics.fill(cx, cy, cx + borderW, cy + CELL_SIZE, outlineColor)
                // Right
                if (((gx + 1) to gy) !in cellSet)
                    guiGraphics.fill(cx + CELL_SIZE - borderW, cy, cx + CELL_SIZE, cy + CELL_SIZE, outlineColor)
            }
        }

        // 3. Hover overlays
        val cell = cellAt(mouseX.toDouble(), mouseY.toDouble())
        val carried = menu.carried

        if (cell != null && !carried.isEmpty && carried.item is BeeUpgradeItem) {
            val type = (carried.item as BeeUpgradeItem).upgradeType
            val rot = getRotationFor(type)
            val shape = type.shape.rotated(rot)
            val canPlace = grid.canPlace(type, cell.first, cell.second, rot)
            val color = if (canPlace) 0x6000FF00.toInt() else 0x60FF0000.toInt()
            for ((dx, dy) in shape.cells) {
                val px = cell.first + dx
                val py = cell.second + dy
                if (px in 0 until UpgradeGrid.COLS && py in 0 until UpgradeGrid.ROWS) {
                    val cx = x + px * CELL_SIZE
                    val cy = y + py * CELL_SIZE
                    guiGraphics.fill(cx + 1, cy + 1, cx + CELL_SIZE - 1, cy + CELL_SIZE - 1, color)
                }
            }
        } else if (cell != null && carried.isEmpty) {
            val occupant = grid.occupied[cell.second][cell.first]
            if (occupant != null) {
                val placement = grid.placements.find { p ->
                    val s = p.type.shape.rotated(p.rotation)
                    s.cells.any { (dx, dy) -> p.x + dx == cell.first && p.y + dy == cell.second }
                }
                if (placement != null) {
                    val shape = placement.type.shape.rotated(placement.rotation)
                    val gold = 0x60FFD700.toInt()
                    for ((dx, dy) in shape.cells) {
                        val cx = x + (placement.x + dx) * CELL_SIZE
                        val cy = y + (placement.y + dy) * CELL_SIZE
                        guiGraphics.fill(cx + 1, cy + 1, cx + CELL_SIZE - 1, cy + CELL_SIZE - 1, gold)
                    }
                }
            }
        }
    }

    /** Renders a small shape preview beside the grid. */
    fun renderShapePreview(guiGraphics: GuiGraphics, previewX: Int, previewY: Int) {
        val carried = menu.carried
        if (carried.isEmpty || carried.item !is BeeUpgradeItem) return

        val type = (carried.item as BeeUpgradeItem).upgradeType
        val rot = getRotationFor(type)
        val shape = type.shape.rotated(rot)
        val color = getUpgradeColor(type)

        guiGraphics.drawString(
            Minecraft.getInstance().font,
            Component.translatable("gui.cbbees.grid.shape_preview"),
            previewX, previewY - 10, 0xFFFFFF, false
        )

        for ((dx, dy) in shape.cells) {
            val cx = previewX + dx * PREVIEW_CELL
            val cy = previewY + dy * PREVIEW_CELL
            guiGraphics.fill(cx, cy, cx + PREVIEW_CELL - 1, cy + PREVIEW_CELL - 1, color)
        }

        val hintY = previewY + shape.height() * PREVIEW_CELL + 4
        guiGraphics.drawString(
            Minecraft.getInstance().font,
            Component.translatable("gui.cbbees.grid.rotate_hint"),
            previewX, hintY, 0xAAAAAA, false
        )
    }

    /** Renders the base shape for an upgrade the mouse is hovering over in a slot. */
    fun renderSlotShapeHint(guiGraphics: GuiGraphics, type: UpgradeType, hintX: Int, hintY: Int) {
        val color = getUpgradeColor(type)
        for ((dx, dy) in type.shape.cells) {
            val cx = hintX + dx * PREVIEW_CELL
            val cy = hintY + dy * PREVIEW_CELL
            guiGraphics.fill(cx, cy, cx + PREVIEW_CELL - 1, cy + PREVIEW_CELL - 1, color)
        }
    }

    // ── mouse interaction ──

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val cell = cellAt(mouseX, mouseY) ?: return false

        if (button == 0) {
            val carried = menu.carried
            if (!carried.isEmpty && carried.item is BeeUpgradeItem) {
                val type = (carried.item as BeeUpgradeItem).upgradeType
                val rot = getRotationFor(type)
                if (clientGrid.canPlace(type, cell.first, cell.second, rot)) {
                    clientGrid.place(type, cell.first, cell.second, rot)
                    carried.shrink(1)
                    PacketDistributor.sendToServer(GridPlaceUpgradePacket(cell.first, cell.second, rot))
                }
                return true
            } else if (carried.isEmpty && clientGrid.occupied[cell.second][cell.first] != null) {
                val removed = clientGrid.removeAt(cell.first, cell.second)
                if (removed != null) {
                    menu.setCarried(getItemForUpgrade(removed.type))
                    PacketDistributor.sendToServer(GridRemoveUpgradePacket(cell.first, cell.second))
                }
                return true
            }
        }

        if (button == 1 && !menu.carried.isEmpty && menu.carried.item is BeeUpgradeItem) {
            currentRotation = (currentRotation + 1) % 4
            return true
        }

        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val carried = menu.carried
        if (!carried.isEmpty && carried.item is BeeUpgradeItem) {
            currentRotation = if (scrollY > 0) (currentRotation + 1) % 4 else (currentRotation + 3) % 4
            return true
        }
        return false
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput)
    }

    // ── util ──

    private fun getItemForUpgrade(type: UpgradeType): ItemStack = when (type) {
        UpgradeType.RAPID_WINGS -> ItemStack(AllItems.RAPID_WINGS.get())
        UpgradeType.SWARM_INTELLIGENCE -> ItemStack(AllItems.SWARM_INTELLIGENCE.get())
        UpgradeType.HONEY_EFFICIENCY -> ItemStack(AllItems.HONEY_EFFICIENCY.get())
        UpgradeType.SOFT_TOUCH -> ItemStack(AllItems.SOFT_TOUCH.get())
        UpgradeType.DROP_ITEMS -> ItemStack(AllItems.DROP_ITEMS.get())
        UpgradeType.HONEY_TANK -> ItemStack(AllItems.HONEY_TANK.get())
        UpgradeType.DRONE_VIEW -> ItemStack(AllItems.DRONE_VIEW.get())
        UpgradeType.DRONE_RANGE -> ItemStack(AllItems.DRONE_RANGE.get())
        UpgradeType.INVENTORY_ACCESS -> ItemStack(AllItems.INVENTORY_ACCESS.get())
    }

    private fun getUpgradeColor(type: UpgradeType): Int = when (type) {
        UpgradeType.RAPID_WINGS -> 0xFFFF6600.toInt()
        UpgradeType.SWARM_INTELLIGENCE -> 0xFF00AAFF.toInt()
        UpgradeType.HONEY_EFFICIENCY -> 0xFFFFDD00.toInt()
        UpgradeType.SOFT_TOUCH -> 0xFF00FF88.toInt()
        UpgradeType.DROP_ITEMS -> 0xFFFF4444.toInt()
        UpgradeType.HONEY_TANK -> 0xFFD97F00.toInt()
        UpgradeType.DRONE_VIEW -> 0xFF9933FF.toInt()
        UpgradeType.DRONE_RANGE -> 0xFF00CCCC.toInt()
        UpgradeType.INVENTORY_ACCESS -> 0xFFCC8844.toInt()
    }
}
