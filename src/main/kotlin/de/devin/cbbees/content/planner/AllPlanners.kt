package de.devin.cbbees.content.planner

import de.devin.cbbees.items.AllItems

object AllPlanners {

    val CONSTRUCTION = Planner("construction", AllItems.CONSTRUCTION_PLANNER)
    val DECONSTRUCTION = Planner("deconstruction", AllItems.DECONSTRUCTION_PLANNER, selectsArea = true)
    val PICKUP = Planner("pickup", AllItems.PICKUP_PLANNER, selectsArea = true)

    /** All planners in the order the Master-Planner cycles through them. */
    val ALL = listOf(CONSTRUCTION, DECONSTRUCTION, PICKUP)

    fun byId(id: String): Planner? = ALL.firstOrNull { it.id == id }
}
