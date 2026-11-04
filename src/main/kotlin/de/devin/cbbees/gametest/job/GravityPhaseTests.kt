package de.devin.cbbees.gametest.job

import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.schematics.BlockPlacementClassifier
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

object GravityPhaseTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private fun job(helper: GameTestHelper) = BeeJob(UUID.randomUUID(), BlockPos.ZERO, helper.level, JobType.Construction)

    private fun place(job: BeeJob, pos: BlockPos, state: BlockState) =
        TaskBatch(listOf(BeeTask.place(pos, state, emptyList(), job = job)), job, pos, BlockPlacementClassifier.PLACEMENT_PHASE)

    private fun remove(job: BeeJob, pos: BlockPos, state: BlockState) = SchematicCreateBridge.removalBatch(pos, state, pos.y, job)

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun stackedSand_isPlacedBottomUpAfterItsSupport(helper: GameTestHelper) = helper.unitTest {
        val job = job(helper)
        val stone = place(job, BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState())
        val sand = (1..3).map { place(job, BlockPos(0, it, 0), Blocks.SAND.defaultBlockState()) }
        val loose = place(job, BlockPos(5, 1, 0), Blocks.GRAVEL.defaultBlockState())
        job.addBatches(sand.reversed() + stone + loose)

        assertTrue(stone.phase < sand[0].phase, "the support goes first")
        assertTrue(sand[0].phase < sand[1].phase && sand[1].phase < sand[2].phase, "each layer waits for the one below")
        assertEquals(sand[0].phase, loose.phase, "an unstacked gravity block is the first layer")
        assertTrue(sand[2].phase < BlockPlacementClassifier.FLUID_PLACEMENT_PHASE, "fluids still come last")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun stackedSand_isRemovedTopDownBeforeItsSupport(helper: GameTestHelper) = helper.unitTest {
        val job = job(helper)
        val stone = remove(job, BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState())
        val sand = (1..3).map { remove(job, BlockPos(0, it, 0), Blocks.SAND.defaultBlockState()) }
        val torch = remove(job, BlockPos(0, 4, 0), Blocks.TORCH.defaultBlockState())
        job.addBatches(listOf(stone, torch) + sand)

        assertTrue(torch.phase < sand[2].phase, "what sits on the sand goes first")
        assertTrue(sand[2].phase < sand[1].phase && sand[1].phase < sand[0].phase, "each layer waits for the one above")
        assertTrue(sand[0].phase < stone.phase, "the support goes last")
        assertTrue(stone.phase < BlockPlacementClassifier.PLACEMENT_PHASE, "removal still ends before placement")
    }
}
