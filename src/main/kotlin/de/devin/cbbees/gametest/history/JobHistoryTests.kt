package de.devin.cbbees.gametest.history

import de.devin.cbbees.content.domain.action.impl.PlaceBlockAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.history.BlockSnapshot
import de.devin.cbbees.content.domain.history.HistoryEntry
import de.devin.cbbees.content.domain.history.HistoryJobRequest
import de.devin.cbbees.content.domain.history.JobHistorySavedData
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.gametest.GameTestSetup
import de.devin.cbbees.gametest.dsl.beeTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import java.util.UUID

object JobHistoryTests {

    private const val CONSTRUCTION = "cbbees:gametest/construction/cbbees_construction"
    private const val EMPTY = "cbbees:gametest/empty3x3"

    private val DIRT = BlockSnapshot(Blocks.DIRT.defaultBlockState(), null)
    private val STONE = BlockSnapshot(Blocks.STONE.defaultBlockState(), null)
    private val AIR = BlockSnapshot(Blocks.AIR.defaultBlockState(), null)

    @GameTest(template = CONSTRUCTION, timeoutTicks = 12000, setupTicks = 20)
    @JvmStatic
    fun undoConstruction_restoresReplacedBlockAndClearsTheRest(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {}
        val jobPool = setupNetwork(scan)
        val extractVault = scan.ports.first { it.isValidForPickup() }.blockPos.below()
        insertItems(extractVault, Items.DIRT, 3)
        insertItems(extractVault, Items.COBBLESTONE, 1)

        val targets = (1..3).map { helper.absolutePos(BlockPos(it, 1, 2)) }
        level.setBlockAndUpdate(targets[0], Blocks.COBBLESTONE.defaultBlockState())
        targets.drop(1).forEach { level.setBlockAndUpdate(it, Blocks.AIR.defaultBlockState()) }

        val owner = UUID.randomUUID()
        val history = JobHistorySavedData.get(level.server).history(owner)
        val job = construct(targets.map {
            GameTestSetup.PlacementTask(it, Blocks.DIRT.defaultBlockState(), listOf(ItemStack(Items.DIRT)))
        })
        job.ownerId = owner
        history.pushUndo(HistoryEntry(job.jobId, job.jobType, job.dimensionKey), clearRedo = true)

        helper.startSequence()
            .thenWaitUntil {
                jobPool.tick(level.gameTime)
                targets.forEach { helper.assertTrue(level.getBlockState(it).`is`(Blocks.DIRT), "Expected dirt at $it") }
            }
            .thenExecute {
                val entry = history.popUndo() ?: throw GameTestAssertException("Construction was not recorded")
                helper.assertTrue(entry.changes.size == 3, "Expected 3 recorded changes, got ${entry.changes.size}")
                helper.assertTrue(entry.changes[targets[0]]?.before?.state?.`is`(Blocks.COBBLESTONE) == true, "Replaced block not captured")

                val undo = BeeJob(UUID.randomUUID(), targets[1], level, JobType.Undo, owner)
                val edits = HistoryJobRequest.reverseEdits(entry) { level.getBlockState(it) }
                undo.addBatches(HistoryJobRequest.reverseBatches(level, edits, undo))
                history.pushRedo(HistoryEntry(undo.jobId, entry.jobType, undo.dimensionKey))
                jobPool.dispatchNewJob(undo)
            }
            .thenWaitUntil {
                jobPool.tick(level.gameTime)
                helper.assertTrue(level.getBlockState(targets[0]).`is`(Blocks.COBBLESTONE), "Cobblestone should be back, found ${level.getBlockState(targets[0])}")
                targets.drop(1).forEach { helper.assertTrue(level.getBlockState(it).isAir, "Expected air at $it") }
            }
            .thenExecute {
                val redo = history.popRedo() ?: throw GameTestAssertException("Undo was not recorded for redo")
                helper.assertTrue(redo.changes.size == 3, "Redo entry should hold 3 changes, got ${redo.changes.size}")
                helper.assertTrue(redo.changes.values.all { it.after.state.`is`(Blocks.DIRT).not() }, "Redo should lead back to the pre-construction states")
            }
            .thenSucceed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun reverseEdits_skipsBlocksChangedSince(helper: GameTestHelper) {
        val kept = helper.absolutePos(BlockPos(0, 1, 0))
        val changed = helper.absolutePos(BlockPos(1, 1, 0))
        val entry = entry().apply {
            record(kept, AIR, DIRT)
            record(changed, AIR, DIRT)
        }
        helper.setBlock(BlockPos(0, 1, 0), Blocks.DIRT)
        helper.setBlock(BlockPos(1, 1, 0), Blocks.STONE)

        val edits = HistoryJobRequest.reverseEdits(entry) { helper.level.getBlockState(it) }
        helper.assertTrue(edits.keys == setOf(kept), "Only the untouched block should be reverted, got ${edits.keys}")
        helper.assertTrue(edits[kept]?.undo is RemoveBlockAction, "Undo should remove the placed block")
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun record_mergesChangesPerPosition(helper: GameTestHelper) {
        val pos = BlockPos(5, 5, 5)
        val entry = entry().apply {
            record(pos, STONE, AIR)
            record(pos, AIR, DIRT)
        }
        helper.assertTrue(entry.changes[pos]?.before == STONE && entry.changes[pos]?.after == DIRT, "Expected stone -> dirt")

        entry.record(pos, DIRT, STONE)
        helper.assertTrue(pos !in entry.changes, "Returning to the original state should drop the change")
        helper.succeed()
    }

    @GameTest(template = EMPTY)
    @JvmStatic
    fun entry_survivesSaveAndLoad(helper: GameTestHelper) {
        val pos = BlockPos(1, 2, 3)
        val entry = entry().apply { record(pos, STONE, DIRT) }
        val loaded = HistoryEntry.load(entry.save(helper.level.registryAccess()), helper.level.registryAccess())
            ?: throw GameTestAssertException("Entry failed to load")
        helper.assertTrue(loaded.jobId == entry.jobId && loaded.jobType == JobType.Construction, "Header mismatch")
        val change = loaded.changes[pos] ?: throw GameTestAssertException("Change missing after load")
        helper.assertTrue(change.before == STONE && change.after == DIRT, "Snapshots mismatch: $change")
        helper.assertTrue((change.undo as? PlaceBlockAction)?.blockState == STONE.state, "Undo should place stone back, got ${change.undo}")
        helper.succeed()
    }

    private fun entry() = HistoryEntry(UUID.randomUUID(), JobType.Construction, Level.OVERWORLD)
}
