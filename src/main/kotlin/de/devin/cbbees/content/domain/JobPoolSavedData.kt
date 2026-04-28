package de.devin.cbbees.content.domain

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData

/**
 * SavedData bridge that persists [GlobalJobPool]'s job backlog to the overworld data storage.
 *
 * Since [GlobalJobPool] is a Kotlin object singleton (and NeoForge's [computeIfAbsent] creates
 * new instances), this thin wrapper delegates save/load to the singleton.
 */
class JobPoolSavedData private constructor() : SavedData() {

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        GlobalJobPool.saveJobs(tag, registries)
        return tag
    }

    companion object {
        private const val DATA_NAME = "cbbees_jobs"

        private val FACTORY = Factory(
            { JobPoolSavedData() },
            { tag, registries ->
                val data = JobPoolSavedData()
                GlobalJobPool.pendingLoadTag = tag
                GlobalJobPool.pendingLoadRegistries = registries
                data
            }
        )

        /**
         * Registers with the overworld data storage. If saved data exists on disk,
         * the factory's load function captures the tag for deferred loading
         * (since we need a [MinecraftServer] reference to resolve dimension keys).
         */
        fun register(server: MinecraftServer): JobPoolSavedData {
            val data = server.overworld().dataStorage.computeIfAbsent(FACTORY, DATA_NAME)
            GlobalJobPool.savedData = data

            // Now that we have the server, process any deferred load
            val pendingTag = GlobalJobPool.pendingLoadTag
            val pendingRegistries = GlobalJobPool.pendingLoadRegistries
            if (pendingTag != null && pendingRegistries != null) {
                GlobalJobPool.loadJobs(pendingTag, pendingRegistries, server)
                GlobalJobPool.pendingLoadTag = null
                GlobalJobPool.pendingLoadRegistries = null
            }
            return data
        }
    }
}
