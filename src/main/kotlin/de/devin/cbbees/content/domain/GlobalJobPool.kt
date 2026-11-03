package de.devin.cbbees.content.domain

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobStatus
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import de.devin.cbbees.util.ServerSide

/**
 * Global Bee Job distribution pool.
 *
 * Jobs are persisted to the overworld via [JobPoolSavedData] so they survive server restarts.
 * Bees are ephemeral — on reload, all in-progress batches reset to PENDING and the
 * redispatch cycle re-assigns them to available hives.
 */
@ServerSide
object GlobalJobPool : JobPool {
    private val jobBacklog = mutableListOf<BeeJob>()
    private var redispatchCounter = 0
    private var watchdogCounter = 0
    private val REDISPATCH_INTERVAL: Int get() = CBBeesConfig.redispatchInterval.get()
    private const val WATCHDOG_INTERVAL = 20
    private const val STALE_BATCH_TICKS = 600L

    /** Reference to the SavedData wrapper for dirty-marking. */
    var savedData: SavedData? = null
        internal set

    /** Deferred load state — set by [JobPoolSavedData] factory, consumed by [ensureLoaded]. */
    @JvmField var pendingLoadTag: CompoundTag? = null
    @JvmField var pendingLoadRegistries: HolderLookup.Provider? = null

    /** Tracks the current server instance to detect world transitions. */
    private var loadedForServer: MinecraftServer? = null

    /**
     * Called every server tick. On the first tick of a new server lifecycle,
     * clears stale in-memory state and loads persisted jobs from disk.
     */
    fun ensureLoaded(server: MinecraftServer) {
        if (loadedForServer === server) return
        loadedForServer = server
        // Clear any stale in-memory state from a previous world
        jobBacklog.clear()
        redispatchCounter = 0
        watchdogCounter = 0
        savedData = null
        pendingLoadTag = null
        pendingLoadRegistries = null
        // Register with data storage — triggers load from disk if data exists
        JobPoolSavedData.register(server)
    }

    /**
     * Prepares job state for persistence before the server saves.
     * Resets all in-progress/picked batches to PENDING since bees are ephemeral
     * and won't exist after reload.
     */
    fun prepareForSave() {
        for (job in jobBacklog) {
            if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) continue
            for (batch in job.batches) {
                if (batch.status == TaskStatus.IN_PROGRESS || batch.status == TaskStatus.PICKED) {
                    batch.assignedBeeId = null
                    batch.assignedNetworkId = null
                    batch.restoreState(
                        status = TaskStatus.PENDING,
                        retryCount = batch.retryCount,
                        lastReleasedTick = batch.lastReleasedTick,
                        startedAtTick = 0L,
                        currentIndex = 0
                    )
                    batch.tasks.forEach { task ->
                        if (task.status == TaskStatus.IN_PROGRESS || task.status == TaskStatus.PICKED) {
                            task.release()
                        }
                    }
                }
            }
        }
        savedData?.setDirty()
    }

    // ── Serialization ──

    fun saveJobs(tag: CompoundTag, registries: HolderLookup.Provider) {
        tag.putInt("Version", 1)
        val jobList = ListTag()
        for (job in jobBacklog) {
            if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) continue
            jobList.add(job.save(registries))
        }
        tag.put("Jobs", jobList)
        CreateBuzzyBeez.debug { "[JobPool] Saved ${jobList.size} jobs to disk" }
    }

    fun loadJobs(tag: CompoundTag, registries: HolderLookup.Provider, server: MinecraftServer) {
        jobBacklog.clear()
        val jobList = tag.getList("Jobs", Tag.TAG_COMPOUND.toInt())
        var loaded = 0
        for (i in 0 until jobList.size) {
            val job = BeeJob.load(jobList.getCompound(i), registries, server) ?: continue
            // Reset any in-progress batches since bees don't persist
            for (batch in job.batches) {
                if (batch.status == TaskStatus.IN_PROGRESS || batch.status == TaskStatus.PICKED) {
                    batch.assignedBeeId = null
                    batch.assignedNetworkId = null
                    batch.restoreState(
                        status = TaskStatus.PENDING,
                        retryCount = batch.retryCount,
                        lastReleasedTick = batch.lastReleasedTick,
                        startedAtTick = 0L,
                        currentIndex = 0
                    )
                    batch.tasks.forEach { task ->
                        if (task.status == TaskStatus.IN_PROGRESS || task.status == TaskStatus.PICKED) {
                            task.release()
                        }
                    }
                }
            }
            // Ensure job status is compatible with reload
            if (job.status != JobStatus.COMPLETED && job.status != JobStatus.CANCELLED) {
                job.status = JobStatus.IN_PROGRESS
            }
            jobBacklog.add(job)
            loaded++
        }
        CreateBuzzyBeez.LOGGER.info("[JobPool] Loaded $loaded jobs from disk")
    }

    // ── Tick ──

    override fun tick(gameTime: Long) {
        if (jobBacklog.removeIf {
            it.status == JobStatus.COMPLETED || it.status == JobStatus.CANCELLED
        }) {
            savedData?.setDirty()
        }

        redispatchCounter++
        if (redispatchCounter >= REDISPATCH_INTERVAL) {
            redispatchCounter = 0
            redispatchPendingBatches()
        }

        watchdogCounter++
        if (watchdogCounter >= WATCHDOG_INTERVAL) {
            watchdogCounter = 0
            healStaleBatches(gameTime)
        }
    }

    /**
     * Self-healing watchdog: detects batches stuck in IN_PROGRESS or PICKED state
     * where the assigned bee no longer exists, and releases them for retry.
     */
    private fun healStaleBatches(gameTime: Long) {
        var healedCount = 0

        for (job in jobBacklog) {
            if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) continue

            for (batch in job.workingBatches.toList()) {
                if (batch.startedAtTick == 0L) continue

                val elapsed = gameTime - batch.startedAtTick
                if (elapsed < STALE_BATCH_TICKS) continue

                val beeAlive = batch.assignedBeeId?.let(ServerBeeManager::getBee) != null

                if (!beeAlive) {
                    batch.release(gameTick = gameTime)
                    healedCount++
                }
            }
        }

        cleanupOrphanedBees()
        BatchDispatcher.unparkReachable(jobBacklog, ServerBeeNetworkManager.getNetworks())

        if (healedCount > 0) {
            CreateBuzzyBeez.debug { "[Watchdog] Healed $healedCount stale batches" }
        }
    }

    /**
     * Scans all hives and removes active bee entries for entities that no longer exist.
     * This prevents hives from thinking they have active bees when the entities are gone.
     */
    private fun cleanupOrphanedBees() {
        for (network in ServerBeeNetworkManager.getNetworks()) {
            for (hive in network.hives) {
                if (hive is de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity) {
                    hive.cleanupOrphanedBees()
                } else if (hive is de.devin.cbbees.content.domain.beehive.PortableBeeHive) {
                    hive.cleanupOrphanedBees()
                }
            }
        }
    }

    private val maxDispatchesPerCycle: Int get() = CBBeesConfig.maxDispatchesPerCycle.get()

    /** Sends bees out of hives that have one free; bees already out pick their next batch themselves. */
    private fun redispatchPendingBatches() {
        val dispatched = BatchDispatcher.dispatch(jobBacklog, ServerBeeNetworkManager.getNetworks(), maxDispatchesPerCycle)
        if (dispatched > 0) CreateBuzzyBeez.debug { "[Redispatch] Sent out $dispatched bees" }
    }

    /** Runs a dispatch right away when something [BatchDispatcher.wake]d it; called every server tick. */
    fun dispatchIfWoken() {
        if (!BatchDispatcher.consumeWake()) return
        if (BatchDispatcher.consumeRangesChanged()) BatchDispatcher.unparkReachable(jobBacklog, ServerBeeNetworkManager.getNetworks())
        redispatchPendingBatches()
    }

    override fun getAllJobs(): List<BeeJob> = jobBacklog

    /**
     * Registers a new job for processing and sends out any free bees right away.
     */
    override fun dispatchNewJob(job: BeeJob) {
        if (job.uniquenessKey != null && jobBacklog.any {
            it.uniquenessKey == job.uniquenessKey &&
            it.status != JobStatus.COMPLETED &&
            it.status != JobStatus.CANCELLED
        }) return

        if (!jobBacklog.contains(job)) jobBacklog.add(job)
        savedData?.setDirty()

        // Dispatch immediately so bees start flying without waiting for the next
        // 10-tick redispatch cycle
        redispatchPendingBatches()

        CreateBuzzyBeez.debug { "[JobPool] Registered job with ${job.batches.size} batches" }
    }
}
