package de.devin.cbbees.util

import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.items.IItemHandler

/**
 * Extracts exactly [stack]'s count of matching items, spread across as many slots as needed.
 *
 * All-or-nothing: the extraction is simulated first, and nothing is removed unless the
 * full amount is available. This keeps callers from handing out more items than were taken.
 *
 * @return true if the full amount was extracted
 */
fun IItemHandler.extractExactly(stack: ItemStack): Boolean {
    val matchingSlots = (0 until slots).filter { slot ->
        getStackInSlot(slot).let { !it.isEmpty && ItemStack.isSameItemSameComponents(it, stack) }
    }

    var available = 0
    for (slot in matchingSlots) {
        available += extractItem(slot, stack.count - available, true).count
        if (available >= stack.count) break
    }
    if (available < stack.count) return false

    var remaining = stack.count
    for (slot in matchingSlots) {
        remaining -= extractItem(slot, remaining, false).count
        if (remaining <= 0) break
    }
    return true
}
