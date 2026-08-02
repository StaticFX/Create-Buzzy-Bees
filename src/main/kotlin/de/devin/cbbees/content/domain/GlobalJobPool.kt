package de.devin.cbbees.content.domain

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.compat.sable.SableRenderSupport
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobStatus
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.core.BlockPos
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
    private var lastRedispatchLogTick = 0L
    private val REDISPATCH_INTERVAL: Int get() = CBBeesConfig.redispatchInterval.get()
    private const val WATCHDOG_INTERVAL = 20
    private const val STALE_BATCH_TICKS = 600L
    private const val LOG_INTERVAL_TICKS = 100L // ~5 seconds

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
        CreateBuzzyBeez.LOGGER.debug("[JobPool] Saved ${jobList.size} jobs to disk")
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
            redispatchPendingBatches(gameTime)
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

            for (batch in job.batches) {
                if (batch.status != TaskStatus.IN_PROGRESS && batch.status != TaskStatus.PICKED) continue
                if (batch.startedAtTick == 0L) continue

                val elapsed = gameTime - batch.startedAtTick
                if (elapsed < STALE_BATCH_TICKS) continue

                val beeId = batch.assignedBeeId
                val serverLevel = job.level as? net.minecraft.server.level.ServerLevel
                val beeAlive = if (beeId != null && serverLevel != null) {
                    serverLevel.getEntity(beeId)?.isAlive == true
                } else false

                if (!beeAlive) {
                    batch.release(gameTick = gameTime)
                    healedCount++
                }
            }
        }

        cleanupOrphanedBees(gameTime)

        if (healedCount > 0) {
            CreateBuzzyBeez.LOGGER.debug("[Watchdog] Healed $healedCount stale batches")
        }
    }

    /**
     * Scans all hives and removes active bee entries for entities that no longer exist.
     * This prevents hives from thinking they have active bees when the entities are gone.
     */
    private fun cleanupOrphanedBees(gameTime: Long) {
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

    /**
     * Scans for PENDING batches and dispatches them to networks that have available bees.
     * Handles both:
     * - Retrying failed/released batches
     * - Assigning work to newly available bees in hives
     */
    private val maxDispatchesPerCycle: Int get() = CBBeesConfig.maxCheckpointsPerTick.get()

    private fun redispatchPendingBatches(gameTime: Long) {
        val allNetworks = ServerBeeNetworkManager.getNetworks()
        if (allNetworks.isEmpty()) return

        val log = CreateBuzzyBeez.LOGGER
        val verbose = gameTime - lastRedispatchLogTick >= LOG_INTERVAL_TICKS

        // Throttled summary counters
        var noRetry = 0; var noWorld = 0; var noRange = 0; var noBees = 0; var noMaterials = 0; var noCapacity = 0
        var totalPending = 0

        var dispatched = 0
        for (job in jobBacklog) {
            if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) continue

            for (batch in job.batches) {
                if (dispatched >= maxDispatchesPerCycle) return
                if (batch.status != TaskStatus.PENDING) continue
                totalPending++
                if (!batch.canRetry()) { noRetry++; continue }
                if (!batch.isCooldownElapsed(gameTime)) continue
                if (!job.isPhaseReady(batch.phase)) continue

                val inWorldNetworks = allNetworks.filter { network ->
                    val firstComp = network.components.firstOrNull()
                    firstComp != null && firstComp.world == job.level
                }
                if (inWorldNetworks.isEmpty()) { noWorld++; continue }

                val inRangeNetworks = inWorldNetworks.filter { it.isBatchInRange(batch) }
                if (inRangeNetworks.isEmpty()) { noRange++; continue }

                val withBees = inRangeNetworks.filter { network ->
                    network.hives.any { hive ->
                        hive.hasBeeOfType(batch.beeType) &&
                                hive.getAvailableBeeCount() > 0 &&
                                hive.getActiveBeeCount() < hive.getBeeContext().maxActiveBees
                    }
                }
                if (withBees.isEmpty()) { noBees++; continue }

                val withPorts = if (batch.beeType == BeeType.TRANSPORT) {
                    withBees.filter { it.findDropOff(net.minecraft.world.item.ItemStack.EMPTY) != null }
                } else withBees
                if (withPorts.isEmpty()) continue

                val targetNetwork = if (batch.job.jobType == JobType.Pickup) {
                    // Prefer a network with a currently eligible hive in the exact
                    // same logical coordinate space as the loose item entities.
                    withPorts.minWithOrNull(
                        compareBy<BeeNetwork>(
                            { network ->
                                if (network.hives.any { hive ->
                                        isEligibleSameSpacePickupHive(hive, batch)
                                    }
                                ) 0 else 1
                            },
                            { network ->
                                network.hives.minOfOrNull { hive ->
                                    SableRenderSupport.dispatchDistanceSquared(
                                        hive.world,
                                        hive.pos,
                                        batch.targetPosition
                                    )
                                } ?: Double.MAX_VALUE
                            }
                        )
                    )
                } else {
                    withPorts.minByOrNull { network ->
                        network.hives.minOfOrNull { it.pos.distSqr(batch.targetPosition) }
                            ?: Double.MAX_VALUE
                    }
                } ?: continue

                if (!hasMaterialsAvailable(batch, targetNetwork)) { noMaterials++; continue }

                batch.assignedNetworkId = targetNetwork.id
                if (targetNetwork.dispatchBatch(batch)) {
                    dispatched++
                } else {
                    noCapacity++
                    batch.assignedNetworkId = null
                }
            }
        }

        if (verbose && totalPending > 0) {
            lastRedispatchLogTick = gameTime
            log.debug("[Redispatch] pending=$totalPending, dispatched=$dispatched | blocked: noRetry=$noRetry, noWorld=$noWorld, noRange=$noRange, noBees=$noBees, noMaterials=$noMaterials, noCapacity=$noCapacity | networks=${allNetworks.size}")
        }
    }

    val workers: Set<BeeHive> get() = ServerBeeNetworkManager.getNetworks().flatMap { it.hives }.toSet()

    override fun getAllJobs(): List<BeeJob> = jobBacklog

    fun workBacklog(beeHive: BeeHive): TaskBatch? {
        val network = beeHive.network()
        val gameTime = (beeHive.world as? net.minecraft.server.level.ServerLevel)?.gameTime ?: 0L
        val networkId = network.id
        val hivePos = beeHive.pos

        fun isDispatchable(batch: TaskBatch): Boolean =
            batch.status == TaskStatus.PENDING && batch.canRetry() && batch.isCooldownElapsed(gameTime)
                    && batch.job.isPhaseReady(batch.phase)
                    && !shouldDeferPickupToSameSpaceHive(batch, beeHive)

        fun distanceToBatch(batch: TaskBatch): Double =
            if (batch.job.jobType == JobType.Pickup) {
                SableRenderSupport.dispatchDistanceSquared(
                    beeHive.world,
                    beeHive.pos,
                    batch.targetPosition
                )
            } else {
                batch.targetPosition.distSqr(hivePos)
            }

        var bestAssigned: TaskBatch? = null
        var bestAssignedDist = Double.MAX_VALUE
        for (job in jobBacklog) {
            if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) continue
            for (batch in job.batches) {
                if (!isDispatchable(batch)) continue
                if (batch.assignedNetworkId != networkId) continue
                val dist = distanceToBatch(batch)
                if (dist < bestAssignedDist) {
                    bestAssignedDist = dist
                    bestAssigned = batch
                }
            }
        }

        if (bestAssigned != null && hasMaterialsAvailable(bestAssigned, network)) {
            bestAssigned.status = TaskStatus.PICKED
            return bestAssigned
        }

        var bestJob: BeeJob? = null
        var bestJobDist = Double.MAX_VALUE
        for (job in jobBacklog) {
            if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) continue

            val reachableBatches = job.batches.filter {
                isDispatchable(it) &&
                        (it.assignedNetworkId == null || it.assignedNetworkId == networkId) &&
                        network.isBatchInRange(it)
            }
            if (reachableBatches.isEmpty()) continue

            val dist = if (job.jobType == JobType.Pickup) {
                reachableBatches.minOf { distanceToBatch(it) }
            } else {
                job.centerPos.distSqr(hivePos)
            }
            if (dist < bestJobDist) {
                bestJobDist = dist
                bestJob = job
            }
        }

        val job = bestJob ?: return null
        val batch = job.batches.firstOrNull {
            isDispatchable(it) &&
                    (it.assignedNetworkId == null || it.assignedNetworkId == networkId) &&
                    network.isBatchInRange(it)
        } ?: return null

        if (!network.isBatchInRange(batch)) return null
        if (!hasMaterialsAvailable(batch, network)) return null

        batch.assignedNetworkId = networkId
        batch.status = TaskStatus.PICKED
        return batch
    }

    /**
     * Coordinate-space anchor used only for pickup hive preference. A deployer
     * job uses the deployer's logical position; direct planner jobs fall back
     * to the item target position.
     */
    private fun pickupRoutingAnchor(batch: TaskBatch): BlockPos =
        batch.job.dispatchOrigin ?: batch.targetPosition

    /** A currently usable pickup hive in the preferred world/Sable coordinate space. */
    private fun isEligibleSameSpacePickupHive(hive: BeeHive, batch: TaskBatch): Boolean {
        return hive.world == batch.job.level &&
                SableRenderSupport.isSameCoordinateSpace(
                    hive.world,
                    hive.pos,
                    pickupRoutingAnchor(batch)
                ) &&
                SableRenderSupport.isWithinHorizontalWorkRange(
                    hive.world,
                    hive.pos,
                    batch.targetPosition,
                    hive.getWorkRange()
                ) &&
                hive.hasBeeOfType(batch.beeType) &&
                hive.getAvailableBeeCount() > 0 &&
                hive.getActiveBeeCount() < hive.getBeeContext().maxActiveBees
    }

    /**
     * Do not let a world hive steal a Sable pickup while a usable hive inside the
     * target sub-level is available. Cross-space pickup remains the fallback.
     */
    private fun shouldDeferPickupToSameSpaceHive(batch: TaskBatch, currentHive: BeeHive): Boolean {
        if (batch.job.jobType != JobType.Pickup) return false
        if (SableRenderSupport.isSameCoordinateSpace(
                currentHive.world,
                currentHive.pos,
                pickupRoutingAnchor(batch)
            )
        ) return false

        return ServerBeeNetworkManager.getNetworks()
            .asSequence()
            .flatMap { it.hives.asSequence() }
            .any { hive -> isEligibleSameSpacePickupHive(hive, batch) }
    }

    private fun hasMaterialsAvailable(batch: TaskBatch, network: BeeNetwork): Boolean {
        return batch.tasks.map { it.action }
            .filterIsInstance<ItemConsumingAction>()
            .flatMap { it.requiredItems }
            .all { req -> network.findAvailableProvider(req) != null }
    }

    /**
     * Registers a new job for processing. The job is added to the backlog immediately
     * but batch-to-network assignment is deferred to [redispatchPendingBatches] which
     * runs every few ticks. This avoids a synchronous O(batches × networks × hives)
     * spike when placing large schematics.
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
        redispatchPendingBatches(0L)

        CreateBuzzyBeez.LOGGER.debug("[JobPool] Registered job with ${job.batches.size} batches")
    }
}
