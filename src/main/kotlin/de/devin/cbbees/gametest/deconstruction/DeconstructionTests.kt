package de.devin.cbbees.gametest.deconstruction

import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.gametest.dsl.beeTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import java.util.UUID

object DeconstructionTests {

    private const val TEMPLATE = "cbbees:gametest/cbbees_deconstruct"

    @GameTest(template = TEMPLATE, timeoutTicks = 6000, setupTicks = 20)
    @JvmStatic
    fun deconstruct_dirtBlocks_endsUpInVault(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {
            target(Blocks.DIRT)
            expect(Items.DIRT, count = 27)
        }
        setupNetwork(scan)
        deconstruct()

        awaitSuccess {
            assertTargetsDeconstructed()
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 6000, setupTicks = 20)
    @JvmStatic
    fun deconstruct_unreachableEarlierPhase_doesNotBlockTheRest(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {
            target(Blocks.DIRT)
            expect(Items.DIRT, count = 27)
        }
        setupNetwork(scan, beeCount = 1)
        val job = BeeJob(UUID.randomUUID(), scan.targetPositions.first(), level, JobType.Deconstruction)
        val farAway = helper.absolutePos(BlockPos(10_000, 0, 10_000))
        job.addBatches(listOf(TaskBatch(listOf(BeeTask.remove(pos = farAway, job = job)), job, farAway, phase = -1)))
        job.addBatches(scan.targetPositions.map { SchematicCreateBridge.removalBatch(it, level.getBlockState(it), it.y, job) })
        jobPool.dispatchNewJob(job)

        awaitSuccess {
            assertTargetsDeconstructed()
        }
    }
}
