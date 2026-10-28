package de.devin.cbbees.gametest.built

import de.devin.cbbees.content.domain.built.BuiltSchematic
import de.devin.cbbees.content.domain.built.BuiltSchematics
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.job.SchematicPlacement
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskStatus
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import java.util.UUID

object BuiltSchematicTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private fun constructionJob(helper: GameTestHelper, type: JobType = JobType.Construction): BeeJob =
        BeeJob(UUID.randomUUID(), helper.absolutePos(BlockPos(1, 1, 1)), helper.level, type, UUID.randomUUID()).apply {
            schematicPlacement = SchematicPlacement("houses/hut.nbt", centerPos)
        }

    private fun BeeJob.place(pos: BlockPos, completed: Boolean) {
        val state = Blocks.STONE.defaultBlockState()
        addTask(BeeTask.place(pos, state, emptyList(), job = this).apply { if (completed) status = TaskStatus.COMPLETED })
        if (completed) level.setBlockAndUpdate(pos, state)
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun record_storesOnlyCompletedPlacements(helper: GameTestHelper) = helper.unitTest {
        val placed = helper.absolutePos(BlockPos(0, 1, 0))
        val pending = helper.absolutePos(BlockPos(2, 1, 0))
        val job = constructionJob(helper).apply {
            place(placed, completed = true)
            place(pending, completed = false)
        }

        BuiltSchematics.record(job)
        val build = BuiltSchematics.data(helper.level.server).remove(job.jobId)
        assertNotNull(build, "build record")
        assertEquals(setOf(placed), build!!.blocks.keys, "recorded positions")
        assertEquals("hut", build.name, "build name")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun record_ignoresOtherJobTypes(helper: GameTestHelper) = helper.unitTest {
        val job = constructionJob(helper, JobType.Deconstruction).apply { place(helper.absolutePos(BlockPos(0, 1, 0)), completed = true) }

        BuiltSchematics.record(job)
        assertEquals(null, BuiltSchematics.data(helper.level.server).remove(job.jobId), "no record for deconstruction")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun remainingBlocks_skipsChangedBlocksAndSurvivesSaving(helper: GameTestHelper) = helper.unitTest {
        val kept = helper.absolutePos(BlockPos(0, 1, 0))
        val replaced = helper.absolutePos(BlockPos(1, 1, 0))
        listOf(kept, replaced).forEach { helper.level.setBlockAndUpdate(it, Blocks.STONE.defaultBlockState()) }
        val build = BuiltSchematic(
            UUID.randomUUID(), UUID.randomUUID(), helper.level.dimension(), "hut",
            mapOf(kept to Blocks.STONE.defaultBlockState(), replaced to Blocks.STONE.defaultBlockState()),
        )
        helper.level.setBlockAndUpdate(replaced, Blocks.GLASS.defaultBlockState())

        assertEquals(listOf(kept), build.remainingBlocks(helper.level), "only unchanged blocks remain")

        val loaded = BuiltSchematic.load(build.save(), helper.level.registryAccess())
        assertEquals(build.blocks, loaded?.blocks, "blocks after save/load")
        assertEquals(build.bounds, loaded?.bounds, "bounds after save/load")
    }
}
