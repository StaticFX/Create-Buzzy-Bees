package de.devin.cbbees.gametest.planner

import de.devin.cbbees.content.planner.AllPlanners
import de.devin.cbbees.content.planner.MasterPlannerItem
import de.devin.cbbees.items.AllItems
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper

object MasterPlannerTests {

    private const val TEMPLATE = "cbbees:gametest/construction/cbbees_construction"

    @GameTest(template = TEMPLATE)
    @JvmStatic
    fun masterPlanner_cyclesThroughAllPlanners(helper: GameTestHelper) {
        val stack = AllItems.MASTER_PLANNER.asStack()
        helper.assertTrue(MasterPlannerItem.currentPlanner(stack) == AllPlanners.CONSTRUCTION, "Should start as the Construction Planner")

        val visited = AllPlanners.ALL.map { MasterPlannerItem.cycle(stack, forward = true) }
        helper.assertTrue(visited == AllPlanners.ALL.drop(1) + AllPlanners.ALL.first(), "Forward cycle order was $visited")

        helper.assertTrue(MasterPlannerItem.cycle(stack, forward = false) == AllPlanners.ALL.last(), "Backward cycle should wrap around")
        helper.succeed()
    }

    @GameTest(template = TEMPLATE)
    @JvmStatic
    fun masterPlanner_actsOnlyAsCurrentPlanner(helper: GameTestHelper) {
        val stack = AllItems.MASTER_PLANNER.asStack()
        MasterPlannerItem.cycle(stack, forward = true)

        helper.assertTrue(AllPlanners.DECONSTRUCTION.isIn(stack), "Should act as the Deconstruction Planner")
        helper.assertFalse(AllPlanners.CONSTRUCTION.isIn(stack), "Should not act as the Construction Planner")
        helper.assertFalse(AllPlanners.PICKUP.isIn(stack), "Should not act as the Pickup Planner")
        helper.assertTrue(AllPlanners.PICKUP.isIn(AllItems.PICKUP_PLANNER.asStack()), "Standalone planners still count")
        helper.succeed()
    }
}
