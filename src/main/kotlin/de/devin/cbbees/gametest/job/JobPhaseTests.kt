package de.devin.cbbees.gametest.job

import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import java.util.UUID

object JobPhaseTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private fun job(helper: GameTestHelper) = BeeJob(UUID.randomUUID(), BlockPos.ZERO, helper.level, JobType.Construction)

    private fun batch(job: BeeJob, phase: Int, pos: BlockPos = BlockPos.ZERO): TaskBatch {
        val action = RemoveBlockAction(pos)
        return TaskBatch(listOf(BeeTask(action, job)), job, pos, phase)
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun laterPhases_waitForEarlierOnes(helper: GameTestHelper) = helper.unitTest {
        val job = job(helper)
        job.addBatches(listOf(batch(job, 0), batch(job, 1)))
        assertTrue(job.isPhaseReady(0), "the first phase can start")
        assertTrue(!job.isPhaseReady(1), "the second phase waits")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun addingAnEarlierPhase_blocksLaterOnesRightAway(helper: GameTestHelper) = helper.unitTest {
        val job = job(helper)
        job.addBatches(listOf(batch(job, 1)))
        assertTrue(job.isPhaseReady(1), "nothing earlier is open")
        job.addBatches(listOf(batch(job, 0)))
        assertTrue(!job.isPhaseReady(1), "the new earlier batch blocks it in the same tick")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun manyBatches_checkEveryBatchQuickly(helper: GameTestHelper) = helper.unitTest {
        val job = job(helper)
        job.addBatches((0 until 50_000).map { batch(job, it % 2, BlockPos(it, 0, 0)) })
        val ready = job.batches.count { job.isPhaseReady(it.phase) }
        assertEquals(25_000, ready, "only the first phase is ready")
    }
}
