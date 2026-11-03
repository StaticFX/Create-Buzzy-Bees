package de.devin.cbbees.gametest.flight

import de.devin.cbbees.content.bee.flight.checkpoint.ActionThrottle
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper

object ActionThrottleTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"
    private const val MS = 1_000_000L
    private const val MIN = 5 * MS
    private const val TARGET = 30 * MS

    private fun next(budget: Long, tick: Long, saturated: Boolean = true) =
        ActionThrottle.nextBudget(budget, tick, saturated, MIN, TARGET)

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun budget_growsIntoSpareTickTimeWhileBeesWait(helper: GameTestHelper) = helper.unitTest {
        assertTrue(next(MIN, 8 * MS) > MIN, "spare time and waiting bees grow the budget")
        assertEquals(MIN, next(MIN, 8 * MS, saturated = false), "no growth while the budget isn't used up")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun budget_shrinksWhenTheServerIsOverTarget(helper: GameTestHelper) = helper.unitTest {
        assertTrue(next(20 * MS, 40 * MS) < 20 * MS, "an overloaded server shrinks the budget")
        assertEquals(MIN, next(MIN, 49 * MS), "never below the configured minimum")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun budget_settlesBelowTheTarget(helper: GameTestHelper) = helper.unitTest {
        // A server whose tick time is the base load plus whatever the budget lets bees spend
        var budget = MIN
        repeat(5_000) { budget = next(budget, 6 * MS + budget) }
        val tick = 6 * MS + budget
        assertTrue(tick in 28 * MS..TARGET, "tick time settles just under the target, got ${tick / MS} ms")
    }
}
