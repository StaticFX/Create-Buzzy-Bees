package de.devin.cbbees.content.domain.action.impl

import de.devin.cbbees.content.bee.server.BeeWorker
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Deposits the bee's inventory at a logistics port or the owner player.
 * Appended as the last task in removal batches to return picked-up items.
 */
class DropOffItemsAction(initialPos: BlockPos) : BeeAction {

    private var _pos: BlockPos = initialPos
    override val pos: BlockPos get() = _pos

    private var cachedWorker: BeeWorker? = null

    override fun onActivate(worker: BeeWorker) {
        cachedWorker = worker
        val port = worker.network()?.findDropOff(ItemStack.EMPTY)
        // Just set the target — actual item handling happens in execute()
        _pos = port?.pos ?: worker.blockPosition()
    }

    override fun execute(level: Level, worker: BeeWorker, context: BeeContext): Boolean {
        val contents = worker.getInventoryContents()
        if (contents.isEmpty()) return true

        // Try each item individually — different items might go to different ports
        contents.forEach { item ->
            val port = worker.network()?.findDropOff(item)
            if (port != null) {
                val remainder = port.addItemStack(item.copy())
                worker.removeFromInventory(item, item.count)
                if (!remainder.isEmpty) {
                    // Port full — drop only the remainder that didn't fit
                    level.addFreshEntity(ItemEntity(level, port.pos.x + 0.5, port.pos.y + 0.5, port.pos.z + 0.5, remainder))
                }
            } else {
                // No port accepts this item — drop on ground as last resort
                worker.removeFromInventory(item, item.count)
                level.addFreshEntity(ItemEntity(level, worker.getWorkerX(), worker.getWorkerY(), worker.getWorkerZ(), item.copy()))
            }
        }
        return true
    }

    override fun shouldReturnAfter(context: BeeContext) = false
    override fun getDescription() = "Dropping off items at (${pos.x}, ${pos.y}, ${pos.z})"
}
