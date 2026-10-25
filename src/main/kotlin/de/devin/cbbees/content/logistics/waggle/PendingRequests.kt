package de.devin.cbbees.content.logistics.waggle

import com.simibubi.create.content.logistics.BigItemStack
import net.minecraft.world.item.ItemStack

class PendingRequests {

    private class Entry(val stack: ItemStack, val count: Int, val until: Long)

    private val entries = mutableListOf<Entry>()

    fun record(order: List<BigItemStack>, until: Long) {
        order.forEach { entries += Entry(it.stack.copyWithCount(1), it.count, until) }
    }

    fun outstanding(stack: ItemStack, now: Long): Int {
        entries.removeIf { it.until <= now }
        return entries.filter { ItemStack.isSameItemSameComponents(it.stack, stack) }.sumOf { it.count }
    }

    fun plan(missing: List<BigItemStack>, stock: (ItemStack) -> Int, now: Long): List<BigItemStack> =
        missing.mapNotNull { entry ->
            val count = minOf(entry.count - outstanding(entry.stack, now), stock(entry.stack))
            if (count > 0) BigItemStack(entry.stack.copyWithCount(1), count) else null
        }

    fun clear() = entries.clear()
}
