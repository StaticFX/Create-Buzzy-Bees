package de.devin.cbbees.gametest.flight

import de.devin.cbbees.content.bee.flight.Checkpoint
import de.devin.cbbees.content.bee.flight.FlightPlan
import de.devin.cbbees.content.bee.flight.FlyThrough
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.gametest.dsl.assertLongArrayEquals
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import java.util.UUID

object FlightPlanTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    // ── travelTicks ──

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun travelTicks_samePos_returnsOne(helper: GameTestHelper) = helper.unitTest {
        val pos = BlockPos(0, 0, 0)
        assertEquals(1L, FlightPlan.travelTicks(pos, pos, 1.0f), "same position")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun travelTicks_straightLine_correctDistance(helper: GameTestHelper) = helper.unitTest {
        assertEquals(10L, FlightPlan.travelTicks(BlockPos(0, 0, 0), BlockPos(10, 0, 0), 1.0f), "10 blocks")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun travelTicks_speedMultiplier(helper: GameTestHelper) = helper.unitTest {
        assertEquals(5L, FlightPlan.travelTicks(BlockPos(0, 0, 0), BlockPos(10, 0, 0), 2.0f), "speed 2.0")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun travelTicks_diagonal_usesEuclidean(helper: GameTestHelper) = helper.unitTest {
        assertEquals(5L, FlightPlan.travelTicks(BlockPos(0, 0, 0), BlockPos(3, 4, 0), 1.0f), "3-4-5 triangle")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun travelTicks_fractionalSpeed_truncates(helper: GameTestHelper) = helper.unitTest {
        assertEquals(3L, FlightPlan.travelTicks(BlockPos(0, 0, 0), BlockPos(10, 0, 0), 3.0f), "truncated")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun travelTicks_veryHighSpeed_clampsToOne(helper: GameTestHelper) = helper.unitTest {
        assertEquals(1L, FlightPlan.travelTicks(BlockPos(0, 0, 0), BlockPos(1, 0, 0), 100.0f), "clamped")
    }

    // ── estimatedDurationTicks ──

    private fun plan(vararg checkpoints: Checkpoint) = FlightPlan(
        beeId = UUID.randomUUID(), type = BeeType.CONSTRUCTION, speed = 1.0f,
        checkpoints = checkpoints.toList(),
    )

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun estimatedDuration_linearPath(helper: GameTestHelper) = helper.unitTest {
        val p = plan(
            Checkpoint(BlockPos(0, 0, 0), FlyThrough),
            Checkpoint(BlockPos(10, 0, 0), FlyThrough),
            Checkpoint(BlockPos(20, 0, 0), FlyThrough),
        )
        assertEquals(20L, p.estimatedDurationTicks, "linear 20 blocks")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun estimatedDuration_includesPauseTicks(helper: GameTestHelper) = helper.unitTest {
        val p = plan(
            Checkpoint(BlockPos(0, 0, 0), FlyThrough, clientPauseTicks = 5),
            Checkpoint(BlockPos(10, 0, 0), FlyThrough, clientPauseTicks = 3),
            Checkpoint(BlockPos(20, 0, 0), FlyThrough),
        )
        assertEquals(28L, p.estimatedDurationTicks, "with pauses")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun estimatedDuration_singleCheckpoint_isZero(helper: GameTestHelper) = helper.unitTest {
        val p = plan(Checkpoint(BlockPos(0, 0, 0), FlyThrough))
        assertEquals(0L, p.estimatedDurationTicks, "single checkpoint")
    }

    // ── computeArrivalTicks ──

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun arrivalTicks_firstCheckpointIsZero(helper: GameTestHelper) = helper.unitTest {
        val p = plan(
            Checkpoint(BlockPos(0, 0, 0), FlyThrough),
            Checkpoint(BlockPos(10, 0, 0), FlyThrough),
        )
        assertEquals(0L, p.computeArrivalTicks()[0], "first checkpoint")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun arrivalTicks_cumulativeWithPauses(helper: GameTestHelper) = helper.unitTest {
        val p = plan(
            Checkpoint(BlockPos(0, 0, 0), FlyThrough, clientPauseTicks = 5),
            Checkpoint(BlockPos(10, 0, 0), FlyThrough, clientPauseTicks = 3),
            Checkpoint(BlockPos(20, 0, 0), FlyThrough),
        )
        assertLongArrayEquals(longArrayOf(0L, 15L, 28L), p.computeArrivalTicks(), "arrival ticks")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun arrivalTicks_matchesDuration(helper: GameTestHelper) = helper.unitTest {
        val p = FlightPlan(
            beeId = UUID.randomUUID(), type = BeeType.CONSTRUCTION, speed = 0.5f,
            checkpoints = listOf(
                Checkpoint(BlockPos(0, 0, 0), FlyThrough, clientPauseTicks = 2),
                Checkpoint(BlockPos(5, 0, 0), FlyThrough, clientPauseTicks = 4),
                Checkpoint(BlockPos(5, 10, 0), FlyThrough),
            ),
        )
        assertEquals(p.estimatedDurationTicks, p.computeArrivalTicks().last(), "last arrival = duration")
    }
}
