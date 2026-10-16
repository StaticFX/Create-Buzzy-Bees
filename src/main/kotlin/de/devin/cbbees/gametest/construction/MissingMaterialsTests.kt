package de.devin.cbbees.gametest.construction

import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.job.MissingMaterials
import de.devin.cbbees.content.domain.job.MissingMaterials.missingCount
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import java.util.UUID

object MissingMaterialsTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private fun GameTestHelper.jobWith(vararg placements: Pair<ItemStack, TaskStatus>): BeeJob {
        val job = BeeJob(UUID.randomUUID(), BlockPos.ZERO, level, JobType.Construction)
        placements.forEachIndexed { index, (stack, status) ->
            val pos = BlockPos(index, 0, 0)
            val task = BeeTask.place(pos, Blocks.STONE.defaultBlockState(), listOf(stack), job = job)
            task.status = status
            job.batches.add(TaskBatch(listOf(task), job, pos))
        }
        return job
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun partlyBuilt_countsOnlyUnfinishedTasks(helper: GameTestHelper) = helper.unitTest {
        val job = helper.jobWith(
            ItemStack(Items.STONE, 2) to TaskStatus.PENDING,
            ItemStack(Items.STONE, 3) to TaskStatus.IN_PROGRESS,
            ItemStack(Items.STONE, 5) to TaskStatus.COMPLETED,
            ItemStack(Items.DIRT, 4) to TaskStatus.PENDING,
            ItemStack(Items.GLASS, 1) to TaskStatus.CANCELLED,
        )

        val checklist = MissingMaterials.checklistFor(job, null)

        assertEquals(5, checklist.getRequiredAmount(Items.STONE), "stone")
        assertEquals(4, checklist.getRequiredAmount(Items.DIRT), "dirt")
        assertFalse(checklist.required.containsKey(Items.GLASS), "cancelled glass is not required")
        assertEquals(2, checklist.missingCount(), "missing item types")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun finishedJob_requiresNothing(helper: GameTestHelper) = helper.unitTest {
        val job = helper.jobWith(ItemStack(Items.STONE, 2) to TaskStatus.COMPLETED)

        assertTrue(MissingMaterials.checklistFor(job, null).required.isEmpty(), "nothing required")
    }
}
