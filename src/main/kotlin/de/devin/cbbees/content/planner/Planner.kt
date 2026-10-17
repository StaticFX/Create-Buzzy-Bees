package de.devin.cbbees.content.planner

import com.tterrag.registrate.util.entry.ItemEntry
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * A planning tool. Its own item counts as holding it, and so does a Master-Planner switched to it.
 *
 * @property selectsArea whether the planner works on a two-corner area selection
 */
class Planner(
    val id: String,
    private val entry: ItemEntry<out Item>,
    val selectsArea: Boolean = false,
) {
    val item: Item get() = entry.get()

    fun isIn(stack: ItemStack): Boolean = entry.isIn(stack) || MasterPlannerItem.getPlanner(stack) == this
}
