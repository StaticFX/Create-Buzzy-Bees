package de.devin.cbbees.gametest.job

import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.job.Unreachable
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import java.util.UUID

object UnreachableTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private fun parkedJob(helper: GameTestHelper, positions: List<BlockPos>): BeeJob {
        val job = BeeJob(UUID.randomUUID(), BlockPos.ZERO, helper.level, JobType.Deconstruction)
        val batches = positions.map { TaskBatch(listOf(BeeTask(RemoveBlockAction(it), job)), job, it) }
        job.addBatches(batches)
        batches.forEach(job::park)
        return job
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun reachableJob_hasNothingToShow(helper: GameTestHelper) = helper.unitTest {
        assertEquals(null, Unreachable.of(parkedJob(helper, emptyList()), emptyList()), "nothing parked")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun separateClusters_getTheirOwnBoxes(helper: GameTestHelper) = helper.unitTest {
        val near = (0 until 10).map { BlockPos(it, 64, 0) }
        val far = (0 until 10).map { BlockPos(1000 + it, 64, 1000) }
        val unreachable = Unreachable.of(parkedJob(helper, near + far), emptyList())!!
        assertEquals(2, unreachable.areas.size, "one box per cluster")
        assertEquals(20, unreachable.blocks, "blocks out of reach")
        assertEquals(null, unreachable.shortfall, "no hive to measure against")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun scatteredBlocks_mergeIntoFewBoxesCoveringAll(helper: GameTestHelper) = helper.unitTest {
        val positions = (0 until 50).flatMap { x -> (0 until 50).map { z -> BlockPos(x * 20, 64, z * 20) } }
        val areas = Unreachable.of(parkedJob(helper, positions), emptyList())!!.areas
        assertTrue(areas.size <= Unreachable.MAX_AREAS, "at most ${Unreachable.MAX_AREAS} boxes, got ${areas.size}")
        assertTrue(positions.all { pos -> areas.any { it.isInside(pos) } }, "every block is inside a box")
    }
}
