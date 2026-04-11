package de.devin.cbbees.content.domain.action

import de.devin.cbbees.content.bee.server.BeeWorker
import net.minecraft.world.item.ItemStack

/**
 * Actions that consume items from the bee's inventory (placing blocks, belts, etc.).
 */
interface ItemConsumingAction {
    val requiredItems: List<ItemStack>

    fun hasItems(worker: BeeWorker): Boolean =
        requiredItems.all { req ->
            worker.getInventoryContents()
                .filter { ItemStack.isSameItemSameComponents(it, req) }
                .sumOf { it.count } >= req.count
        }

    fun consumeItems(worker: BeeWorker): Boolean {
        if (!hasItems(worker)) return false
        requiredItems.forEach { req -> worker.removeFromInventory(req, req.count) }
        return true
    }
}
