package de.devin.cbbees.content.domain.action.impl

import de.devin.cbbees.content.bee.server.BeeWorker
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
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
        val port = worker.network()?.findDropOff(ItemStack.EMPTY, worker.hiveId)
        _pos = port?.pos ?: worker.blockPosition()
    }

    override fun execute(level: Level, worker: BeeWorker, context: BeeContext): Boolean {
        val contents = worker.getInventoryContents()
        if (contents.isEmpty()) return true

        contents.forEach { item ->
            val port = worker.network()?.findDropOff(item, worker.hiveId)
            if (port != null) {
                val remainder = port.addItemStack(item.copy())
                val remainingCount = if (remainder.isEmpty) 0 else remainder.count
                val deposited = item.count - remainingCount
                if (deposited > 0) {
                    worker.removeFromInventory(item.copyWithCount(deposited), deposited)
                }
                // If the port is full, keep the remainder inside the bee instead of dropping it.
            }
            // No loose ItemEntity fallback. Some modded construction items are unsafe when dropped.
        }
        return true
    }

    override fun shouldReturnAfter(context: BeeContext) = false
    override fun getDescription() = "Dropping off items at (${pos.x}, ${pos.y}, ${pos.z})"

    fun save(): CompoundTag {
        val tag = CompoundTag()
        tag.putInt("X", _pos.x)
        tag.putInt("Y", _pos.y)
        tag.putInt("Z", _pos.z)
        return tag
    }

    companion object {
        fun load(tag: CompoundTag): DropOffItemsAction {
            return DropOffItemsAction(BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")))
        }
    }
}
