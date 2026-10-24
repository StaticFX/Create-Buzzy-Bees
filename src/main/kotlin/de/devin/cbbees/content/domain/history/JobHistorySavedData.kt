package de.devin.cbbees.content.domain.history

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

class JobHistorySavedData : SavedData() {

    private val histories = mutableMapOf<UUID, PlayerJobHistory>()

    fun history(playerId: UUID): PlayerJobHistory = histories.getOrPut(playerId) { PlayerJobHistory(::setDirty) }

    fun entryFor(ownerId: UUID, jobId: UUID): HistoryEntry? =
        histories[ownerId]?.entries()?.firstOrNull { it.jobId == jobId }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        histories.forEach { (playerId, history) -> tag.put(playerId.toString(), history.save(registries)) }
        return tag
    }

    companion object {
        private const val DATA_NAME = "cbbees_job_history"

        private val FACTORY = Factory(::JobHistorySavedData, { tag, registries ->
            JobHistorySavedData().apply {
                tag.allKeys.forEach { key ->
                    history(UUID.fromString(key)).load(tag.getCompound(key), registries)
                }
            }
        })

        fun get(server: MinecraftServer): JobHistorySavedData =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, DATA_NAME)
    }
}
