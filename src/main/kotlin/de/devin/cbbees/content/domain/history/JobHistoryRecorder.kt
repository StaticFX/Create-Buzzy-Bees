package de.devin.cbbees.content.domain.history

import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.domain.job.BeeJob
import net.minecraft.server.level.ServerLevel

object JobHistoryRecorder {

    fun execute(level: ServerLevel, job: BeeJob, action: BeeAction, execute: () -> Boolean): Boolean {
        val ownerId = job.ownerId ?: return execute()
        val data = JobHistorySavedData.get(level.server)
        val entry = data.entryFor(ownerId, job.jobId) ?: return execute()

        val before = action.changedPositions.associateWith { BlockSnapshot.capture(level, it) }
        val undos = before.mapNotNull { (pos, snapshot) -> action.inversion(pos, snapshot)?.let { pos to it } }
        val done = execute()
        if (done && undos.isNotEmpty()) {
            undos.forEach { (pos, undo) -> entry.record(pos, before.getValue(pos), BlockSnapshot.capture(level, pos), undo) }
            data.setDirty()
        }
        return done
    }
}
