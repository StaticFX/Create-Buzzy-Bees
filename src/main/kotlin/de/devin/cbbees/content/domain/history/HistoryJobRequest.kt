package de.devin.cbbees.content.domain.history

import de.devin.cbbees.content.domain.action.impl.PlaceBeltAction
import de.devin.cbbees.content.domain.action.impl.PlaceFluidAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.BeeJobRequest
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.schematics.BlockPlacementClassifier
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

class HistoryJobRequest(
    private val entry: HistoryEntry,
    private val direction: HistoryDirection,
) : BeeJobRequest() {

    private lateinit var edits: Map<BlockPos, BlockChange>

    override fun jobType() = when (direction) {
        HistoryDirection.UNDO -> JobType.Undo
        HistoryDirection.REDO -> JobType.Redo
    }

    override fun completionKey() = direction.startedKey

    override fun createUniquenessKey(player: ServerPlayer) =
        SchematicJobKey(player.uuid, "history:${entry.jobId}", 0, 0, 0)

    override fun validate(player: ServerPlayer): Boolean {
        val level = player.level()
        if (level.dimension() != entry.dimension) {
            player.displayClientMessage(Component.translatable("cbbees.history.wrong_dimension"), true)
            return false
        }

        edits = reverseEdits(entry) { level.getBlockState(it) }.filterKeys { level.getBlockEntity(it) !is BeeHive }
        val skipped = entry.changes.size - edits.size
        if (edits.isEmpty()) {
            player.displayClientMessage(Component.translatable("cbbees.history.all_changed"), true)
            return false
        }
        if (skipped > 0) {
            player.displayClientMessage(Component.translatable("cbbees.history.skipped", skipped), true)
        }
        return true
    }

    override fun startHistory(job: BeeJob, player: ServerPlayer) {
        val history = JobHistory.of(player)
        val reverse = HistoryEntry(job.jobId, entry.jobType, job.dimensionKey)
        when (direction) {
            HistoryDirection.UNDO -> history.pushRedo(reverse)
            HistoryDirection.REDO -> history.pushUndo(reverse, clearRedo = false)
        }
    }

    override fun generateTasks(
        player: ServerPlayer,
        job: BeeJob,
        server: MinecraftServer,
        onComplete: (List<TaskBatch>, BlockPos) -> Unit
    ) {
        onComplete(reverseBatches(player.level(), edits, job), edits.keys.first())
    }

    companion object {
        private const val PLACEMENT_PHASE = 4

        fun reverseBatches(level: Level, edits: Map<BlockPos, BlockChange>, job: BeeJob): List<TaskBatch> {
            val maxY = edits.keys.maxOf { it.y }
            val removals = edits.keys.filterNot { level.getBlockState(it).isAir }
                .map { SchematicCreateBridge.removalBatch(it, level.getBlockState(it), maxY, job) }
            val restores = edits.filterValues { it.undo !is RemoveBlockAction }
                .entries.distinctBy { (pos, change) -> (change.undo as? PlaceBeltAction)?.pos ?: pos }
                .map { (pos, change) -> restoreBatch(pos, change, job) }
            return removals + restores
        }

        private fun restoreBatch(pos: BlockPos, change: BlockChange, job: BeeJob): TaskBatch {
            val task = BeeTask(change.undo, job, BlockPlacementClassifier.calculatePriority(pos, change.before.state))
            val phase = if (change.undo is PlaceFluidAction) BlockPlacementClassifier.FLUID_PHASE else PLACEMENT_PHASE
            return TaskBatch(listOf(task), job, pos, phase)
        }

        fun reverseEdits(entry: HistoryEntry, currentState: (BlockPos) -> BlockState): Map<BlockPos, BlockChange> =
            entry.changes.filter { (pos, change) -> currentState(pos) == change.after.state }
    }
}
