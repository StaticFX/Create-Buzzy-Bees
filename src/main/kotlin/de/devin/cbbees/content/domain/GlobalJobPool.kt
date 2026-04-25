package de.devin.cbbees.content.domain

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.domain.beehive.BeeHive
import java.util.UUID
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobStatus
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData
import de.devin.cbbees.util.ServerSide

/**
 * Global Bee Job distribution pool.
 *
 */
@ServerSide
object GlobalJobPool : SavedData(), JobPool {
    private val jobBacklog = mutableListOf<BeeJob>()
    private var redispatchCounter = 0
    private var watchdogCounter = 0
    private val REDISPATCH_INTERVAL: Int get() = CBBeesConfig.redispatchInterval.get()
    private const val WATCHDOG_INTERVAL = 20
    private const val STALE_BATCH_TICKS = 600L

    fun clear() {
        jobBacklog.clear()
        redispatchCounter = 0
        watchdogCounter = 0
    }

    override fun tick(gameTime: Long) {
        if (jobBacklog.removeIf {
            it.status == JobStatus.COMPLETED || it.status == JobStatus.CANCELLED
        }) {
            this.setDirty()
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

        var dispatched = 0
        for (job in jobBacklog) {
            if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) continue

            for (batch in job.batches) {
                if (dispatched >= maxDispatchesPerCycle) return
                if (batch.status != TaskStatus.PENDING) continue
                if (!batch.canRetry()) continue
                if (!batch.isCooldownElapsed(gameTime)) continue
                if (!job.isPhaseReady(batch.phase)) continue

                val inWorldNetworks = allNetworks.filter { network ->
                    val firstComp = network.components.firstOrNull()
                    firstComp != null && firstComp.world == job.level
                }
                if (inWorldNetworks.isEmpty()) continue

                val inRangeNetworks = inWorldNetworks.filter { it.isInRange(batch.targetPosition) }
                if (inRangeNetworks.isEmpty()) continue

                val withBees = inRangeNetworks.filter { network ->
                    network.hives.any { it.getAvailableBeeCount() > 0 }
                }
                if (withBees.isEmpty()) continue

                val withPorts = if (batch.beeType == BeeType.TRANSPORT) {
                    withBees.filter { it.findDropOff(net.minecraft.world.item.ItemStack.EMPTY) != null }
                } else withBees
                if (withPorts.isEmpty()) continue

                val targetNetwork = withPorts.minByOrNull { network ->
                    network.hives.minOfOrNull { it.pos.distSqr(batch.targetPosition) } ?: Double.MAX_VALUE
                } ?: continue

                batch.assignedNetworkId = targetNetwork.id

                val missingMaterials = batch.tasks.map { it.action }
                    .filterIsInstance<de.devin.cbbees.content.domain.action.ItemConsumingAction>()
                    .flatMap { it.requiredItems }
                    .any { req -> targetNetwork.findAvailableProvider(req) == null }
                if (missingMaterials) continue
                targetNetwork.dispatchBatch(batch)
                dispatched++
            }
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

        var bestAssigned: TaskBatch? = null
        var bestAssignedDist = Double.MAX_VALUE
        for (job in jobBacklog) {
            if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) continue
            for (batch in job.batches) {
                if (!isDispatchable(batch)) continue
                if (batch.assignedNetworkId != networkId) continue
                val dist = batch.targetPosition.distSqr(hivePos)
                if (dist < bestAssignedDist) {
                    bestAssignedDist = dist
                    bestAssigned = batch
                }
            }
        }

        if (bestAssigned != null) {
            bestAssigned.status = TaskStatus.PICKED
            return bestAssigned
        }

        var bestJob: BeeJob? = null
        var bestJobDist = Double.MAX_VALUE
        for (job in jobBacklog) {
            if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) continue
            if (!network.isInRange(job.centerPos)) continue
            val hasDispatchable = job.batches.any { isDispatchable(it) && (it.assignedNetworkId == null || it.assignedNetworkId == networkId) }
            if (!hasDispatchable) continue
            val dist = job.centerPos.distSqr(hivePos)
            if (dist < bestJobDist) {
                bestJobDist = dist
                bestJob = job
            }
        }

        val job = bestJob ?: return null
        val batch = job.batches.firstOrNull { isDispatchable(it) && (it.assignedNetworkId == null || it.assignedNetworkId == networkId) }
            ?: return null

        if (!network.isInRange(batch.targetPosition)) return null

        batch.assignedNetworkId = networkId
        batch.status = TaskStatus.PICKED
        return batch
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
        this.setDirty()

        // Dispatch immediately so bees start flying without waiting for the next
        // 10-tick redispatch cycle
        redispatchPendingBatches(0L)

        CreateBuzzyBeez.LOGGER.debug("[JobPool] Registered job with ${job.batches.size} batches")
    }

    override fun save(
        tag: CompoundTag,
        registries: HolderLookup.Provider
    ): CompoundTag {
        return tag
    }
}