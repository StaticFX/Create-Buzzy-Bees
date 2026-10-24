package de.devin.cbbees.content.domain.history

import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.job.JobStatus
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object JobHistory {

    fun of(player: ServerPlayer): PlayerJobHistory = JobHistorySavedData.get(player.server).history(player.uuid)

    fun undo(player: ServerPlayer) = step(player, HistoryDirection.UNDO)

    fun redo(player: ServerPlayer) = step(player, HistoryDirection.REDO)

    private fun step(player: ServerPlayer, direction: HistoryDirection) {
        val history = of(player)
        while (true) {
            val entry = when (direction) {
                HistoryDirection.UNDO -> history.popUndo()
                HistoryDirection.REDO -> history.popRedo()
            }
            if (entry == null) {
                player.displayClientMessage(Component.translatable(direction.nothingKey), true)
                return
            }

            val liveJob = GlobalJobPool.getAllJobs().firstOrNull { it.jobId == entry.jobId && it.status == JobStatus.IN_PROGRESS }
            liveJob?.cancel()

            if (entry.changes.isNotEmpty()) {
                HistoryJobRequest(entry, direction).submit(player)
                return
            }
            if (liveJob != null) {
                player.displayClientMessage(Component.translatable("cbbees.history.cancelled", Component.translatable(entry.jobType.translationKey)), true)
                return
            }
        }
    }
}

enum class HistoryDirection(val nothingKey: String, val startedKey: String) {
    UNDO("cbbees.history.nothing_to_undo", "cbbees.history.undo_started"),
    REDO("cbbees.history.nothing_to_redo", "cbbees.history.redo_started"),
}
