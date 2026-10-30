package de.devin.cbbees.gametest.flight

import de.devin.cbbees.content.bee.flight.Checkpoint
import de.devin.cbbees.content.bee.flight.ExecuteBeeAction
import de.devin.cbbees.content.bee.flight.FlightPlan
import de.devin.cbbees.content.bee.flight.FlyThrough
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.task.BeeTask
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

    private fun plan(vararg checkpoints: Checkpoint) =
        FlightPlan.of(UUID.randomUUID(), BeeType.CONSTRUCTION, 1.0f, checkpoints.toList(), null)

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
        val p = FlightPlan.of(
            UUID.randomUUID(), BeeType.CONSTRUCTION, 0.5f,
            listOf(
                Checkpoint(BlockPos(0, 0, 0), FlyThrough, clientPauseTicks = 2),
                Checkpoint(BlockPos(5, 0, 0), FlyThrough, clientPauseTicks = 4),
                Checkpoint(BlockPos(5, 10, 0), FlyThrough),
            ),
            null
        )
        assertEquals(p.estimatedDurationTicks, p.computeArrivalTicks().last(), "last arrival = duration")
    }

    private fun actionPlan(helper: GameTestHelper, vararg targets: BlockPos): FlightPlan {
        val job = BeeJob(UUID.randomUUID(), BlockPos.ZERO, helper.level, JobType.Construction)
        val actions = targets.map { target ->
            val action = RemoveBlockAction(target)
            Checkpoint(target, ExecuteBeeAction(action, BeeTask(action, job)), workTicks = 5)
        }
        return plan(Checkpoint(BlockPos(0, 0, 0), FlyThrough), *actions.toTypedArray())
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun workTicks_comeBeforeTheAction(helper: GameTestHelper) = helper.unitTest {
        val p = actionPlan(helper, BlockPos(10, 0, 0))
        assertEquals(15L, p.ticksToReach(1), "travel plus work")
        assertEquals(p.estimatedDurationTicks, p.computeArrivalTicks().last(), "duration includes the work")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun scheduling_freeSlot_keepsThePlan(helper: GameTestHelper) = helper.unitTest {
        val p = actionPlan(helper, BlockPos(10, 0, 0), BlockPos(20, 0, 0))
        assertEquals(p, p.scheduled(100) { it }, "nothing changes when every slot is free")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun scheduling_lateSlot_slowsTheFlightThenWaits(helper: GameTestHelper) = helper.unitTest {
        val p = actionPlan(helper, BlockPos(10, 0, 0), BlockPos(12, 0, 0))
        val slots = listOf(140L, 160L)
        var call = 0
        val scheduled = p.scheduled(100) { maxOf(it, slots[call++]) }
        assertEquals(20L, scheduled.segmentTicks[1], "a 25 tick delay slows the 10 tick flight to at most half speed")
        assertEquals(15, scheduled.checkpoints[0].clientPauseTicks, "the rest is waited before leaving")
        assertEquals(40L, scheduled.computeArrivalTicks()[1], "first action lands on its slot")
        assertEquals(60L, scheduled.computeArrivalTicks()[2], "second action lands on its slot")
    }
}
