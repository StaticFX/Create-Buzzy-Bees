package de.devin.cbbees.content.domain.network

import com.simibubi.create.content.logistics.BigItemStack
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Materials a bee network has already asked for, by whoever asked (Waggle Ports, autocrafting),
 * so the same missing item is not requested twice.
 */
class PendingRequests {

    private class Entry(val source: UUID, val stack: ItemStack, val count: Int, val until: Long)

    private val entries = mutableListOf<Entry>()

    fun record(source: UUID, order: List<BigItemStack>, until: Long) {
        order.forEach { entries += Entry(source, it.stack.copyWithCount(1), it.count, until) }
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

    fun clear(source: UUID) = entries.removeIf { it.source == source }
}
