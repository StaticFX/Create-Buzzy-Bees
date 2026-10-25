package de.devin.cbbees.content.planner

import com.simibubi.create.AllItems as CreateItems
import de.devin.cbbees.items.AllItems
import net.minecraft.world.item.ItemStack

object AllPlanners {

    val CONSTRUCTION = Planner("construction", AllItems.CONSTRUCTION_PLANNER, placesSchematic = true)
    val DECONSTRUCTION = Planner("deconstruction", AllItems.DECONSTRUCTION_PLANNER)
    val PICKUP = Planner("pickup", AllItems.PICKUP_PLANNER)
    val COPY = Planner("copy", AllItems.COPY_PLANNER, placesSchematic = true, keepsSchematic = true)
    val SCHEMATIC_AND_QUILL = Planner("schematic_and_quill", CreateItems.SCHEMATIC_AND_QUILL)

    /** All planners in the order the Master-Planner cycles through them. */
    val ALL = listOf(CONSTRUCTION, DECONSTRUCTION, PICKUP, COPY, SCHEMATIC_AND_QUILL)

    fun byId(id: String): Planner? = ALL.firstOrNull { it.id == id }

    fun of(stack: ItemStack): Planner? = ALL.firstOrNull { it.isIn(stack) }

    @JvmStatic
    fun schematicPlannerOf(stack: ItemStack): Planner? = ALL.firstOrNull { it.placesSchematic && it.isIn(stack) }
}
