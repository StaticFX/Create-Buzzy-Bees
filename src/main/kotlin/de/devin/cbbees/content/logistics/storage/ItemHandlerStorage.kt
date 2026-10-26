package de.devin.cbbees.content.logistics.storage

import de.devin.cbbees.util.extractExactly
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.ItemHandlerHelper

open class ItemHandlerStorage(protected val handler: IItemHandler) : PortStorage {

    private fun slots() = (0 until handler.slots).asSequence().map(handler::getStackInSlot)

    override fun stacks(): List<ItemStack> = slots().filterNot(ItemStack::isEmpty).toList()

    override fun count(stack: ItemStack): Int =
        slots().filter { !it.isEmpty && ItemStack.isSameItemSameComponents(it, stack) }.sumOf { it.count }

    override fun extract(stack: ItemStack): Boolean = handler.extractExactly(stack)

    override fun insert(stack: ItemStack, simulate: Boolean): ItemStack =
        ItemHandlerHelper.insertItemStacked(handler, stack, simulate)

    override fun hasSpace(): Boolean = slots().any { it.isEmpty || it.count < it.maxStackSize }

    companion object : PortStorageProvider {
        override fun find(level: Level, pos: BlockPos, side: Direction): PortStorage? =
            level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)?.let(::ItemHandlerStorage)
    }
}
