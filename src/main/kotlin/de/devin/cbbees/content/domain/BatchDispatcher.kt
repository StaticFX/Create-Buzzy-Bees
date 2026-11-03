package de.devin.cbbees.content.domain

import de.devin.cbbees.compat.sable.worldDistanceSqr
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.action.impl.PlaceFluidAction
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobStatus
import de.devin.cbbees.content.domain.job.Stall
import de.devin.cbbees.content.domain.job.StallReason
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.logistics.MaterialRequests
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.util.ItemStackKey
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.fluids.FluidStack

/**
 * Decides which batch a bee works on next.
 *
 * Bees finishing a flight and hives with free bees both ask [next], so there is one rule
 * for what gets built next: the nearest job first, and within a job the earliest pending
 * batch in phase and build order that the network can actually do. Each job keeps its
 * pending batches sorted, so the first batch looked at is usually the one taken.
 *
 * While skipping batches, the hive cycle in [dispatch] records why on the job
 * ([BeeJob.stall]), so the client sees the same reason dispatch acted on.
 *
 * Batches no network in their level can reach are parked ([BeeJob.park]): they no longer hold up
 * later phases or crowd out reachable batches, and [unparkReachable] puts them back once a hive
 * covers them. The job can't finish while any are parked.
 */
object BatchDispatcher {

    /** Batches looked at per job and search, so a job blocked on one item can't make dispatch scan everything. */
    private const val MAX_CANDIDATES = 256

    private var woken = false

    /** Asks for a dispatch on the current tick instead of the next cycle, e.g. because a job's next phase opened. */
    fun wake() {
        woken = true
    }

    /** Whether [wake] was called since the last time this returned true. */
    fun consumeWake(): Boolean = woken.also { woken = false }

    private var rangesChanged = false

    /** A hive's range changed or it came online: re-check parked batches and dispatch right away. */
    fun rangesChanged() {
        rangesChanged = true
        wake()
    }

    /** Whether [rangesChanged] was called since the last time this returned true. */
    fun consumeRangesChanged(): Boolean = rangesChanged.also { rangesChanged = false }

    /**
     * Sends bees out of every hive in [networks] that has one free, up to [limit] in total.
     *
     * @return how many bees were sent out
     */
    fun dispatch(jobs: List<BeeJob>, networks: List<BeeNetwork>, limit: Int): Int {
        val open = jobs.filter { it.status == JobStatus.IN_PROGRESS && (it.pendingBatches.isNotEmpty() || it.parkedBatches.isNotEmpty()) }
        if (open.isEmpty()) return 0

        val byLevel = networks.groupBy { it.level }
        var dispatched = 0
        for (network in networks) {
            val level = network.level ?: continue
            val levelNetworks = byLevel[level].orEmpty()
            for (hive in network.hives) {
                while (dispatched < limit && hive.hasFreeBee()) {
                    val batch = search(open, network, hive.pos, level, levelNetworks) { hive.canTake(network, it) } ?: break
                    val previousNetwork = batch.assignedNetworkId
                    batch.assignedNetworkId = network.id
                    if (!hive.acceptBatch(batch)) {
                        batch.assignedNetworkId = previousNetwork
                        break
                    }
                    dispatched++
                }
            }
        }

        // Jobs no hive could look at; anything else keeps the reason from its last search
        open.filter { it.workingBatches.isEmpty() }.forEach { job ->
            val inLevel = networks.filter { it.level == job.level }
            when {
                inLevel.isEmpty() -> job.stall = Stall(StallReason.NO_NETWORK)
                inLevel.all { network -> network.hives.all { it.getAvailableBeeCount() + it.getActiveBeeCount() == 0 } } ->
                    job.stall = Stall(StallReason.NO_BEES)
                job.pendingBatches.isEmpty() -> job.stall = Stall(StallReason.OUT_OF_RANGE)
            }
        }
        return dispatched
    }

    /** Next batch a bee of [network] at [from] should work on, limited to the batches it [canTake]. */
    fun next(jobs: List<BeeJob>, network: BeeNetwork, from: BlockPos, level: Level, canTake: (TaskBatch) -> Boolean): TaskBatch? =
        search(jobs.filter { it.status == JobStatus.IN_PROGRESS }, network, from, level, levelNetworks = null, canTake)

    /** Puts parked batches back in line once any network in their level reaches them. */
    fun unparkReachable(jobs: List<BeeJob>, networks: List<BeeNetwork>) {
        val byLevel = networks.groupBy { it.level }
        for (job in jobs) {
            if (job.parkedBatches.isEmpty()) continue
            val inLevel = byLevel[job.level].orEmpty()
            job.parkedBatches.filter { batch -> inLevel.any { it.isInRange(batch.targetPosition) } }.forEach(job::unpark)
        }
    }

    /**
     * With [levelNetworks] (the hive cycle), records why jobs yield nothing and parks batches
     * none of those networks can reach; bees asking on their way home only skip such batches.
     */
    private fun search(
        jobs: List<BeeJob>,
        network: BeeNetwork,
        from: BlockPos,
        level: Level,
        levelNetworks: List<BeeNetwork>?,
        canTake: (TaskBatch) -> Boolean,
    ): TaskBatch? {
        val materials = MaterialCheck(network)
        return jobs.asSequence()
            .filter { it.level == level && it.pendingBatches.isNotEmpty() }
            .sortedBy { level.worldDistanceSqr(it.centerPos, from) }
            .firstNotNullOfOrNull { job ->
                val blockers = Blockers()
                nextIn(job, network, materials, blockers, canTake).also {
                    if (levelNetworks != null) {
                        blockers.unreachable.filter { batch -> levelNetworks.none { it.isInRange(batch.targetPosition) } }.forEach(job::park)
                    }
                    if (blockers.isMissingMaterials()) MaterialRequests.report(network, job)
                    if (it == null && levelNetworks != null) job.stall = blockers.stall()
                }
            }
    }

    private fun nextIn(job: BeeJob, network: BeeNetwork, materials: MaterialCheck, blockers: Blockers, canTake: (TaskBatch) -> Boolean): TaskBatch? {
        val openPhase = job.lowestOpenPhase()
        val gameTime = job.level.gameTime
        for (batch in job.pendingBatches.asSequence().take(MAX_CANDIDATES)) {
            // Sorted by phase, so everything after this waits for an earlier phase too
            if (batch.phase > openPhase) break
            if (!batch.canRetry() || !batch.isCooldownElapsed(gameTime)) continue
            if (batch.assignedNetworkId.let { it != null && it != network.id }) continue
            when {
                !network.isInRange(batch.targetPosition) -> {
                    blockers.outOfRange = true
                    blockers.unreachable += batch
                }
                batch.beeType == BeeType.TRANSPORT && network.findDropOff(ItemStack.EMPTY) == null -> blockers.noDropOff = true
                !canTake(batch) -> blockers.hive = blockers.hive ?: hiveBlocker(network, batch)
                !materials.availableFor(batch, blockers) -> {}
                else -> return batch
            }
        }
        return null
    }

    /** A reason only when no hive of the network could ever take the batch, not when they're just busy. */
    private fun hiveBlocker(network: BeeNetwork, batch: TaskBatch): StallReason? {
        val reaching = network.hives.filter { network.canReach(it, batch.targetPosition) }
        return when {
            network.hives.none { it.hasBeeOfType(batch.beeType) } -> if (batch.beeType == BeeType.TRANSPORT) StallReason.NO_BUMBLE_BEES else StallReason.NO_BEES
            reaching.isNotEmpty() && reaching.all { it is PortableBeeHive && !it.hasFuel() } -> StallReason.NO_FUEL
            else -> null
        }
    }

    private fun BeeHive.hasFreeBee(): Boolean =
        getAvailableBeeCount() > 0 && getActiveBeeCount() < getBeeContext().maxActiveBees

    private fun BeeHive.canTake(network: BeeNetwork, batch: TaskBatch): Boolean =
        hasBeeOfType(batch.beeType) && network.canReach(this, batch.targetPosition) && (this !is PortableBeeHive || hasFuel())

    /** Asks the network's storages once per item and amount for the whole search. */
    private class MaterialCheck(private val network: BeeNetwork) {
        private val items = HashMap<Pair<ItemStackKey, Int>, Boolean>()

        fun availableFor(batch: TaskBatch, blockers: Blockers): Boolean {
            var available = true
            batch.tasks.forEach { task ->
                (task.action as? ItemConsumingAction)?.requiredItems?.forEach { stack ->
                    if (!items.getOrPut(ItemStackKey(stack) to stack.count) { network.findAvailableProvider(stack) != null }) {
                        blockers.missingItems.merge(ItemStackKey(stack), stack.count, Int::plus)
                        available = false
                    }
                }
                (task.action as? PlaceFluidAction)?.fluid?.let { fluid ->
                    if (network.findFluidProvider(fluid) == null) {
                        blockers.missingFluids.merge(fluid.fluid, fluid.amount, Int::plus)
                        available = false
                    }
                }
            }
            return available
        }
    }

    /** What kept a job's batches from being taken during one search. */
    private class Blockers {
        var outOfRange = false
        /** Batches out of this network's range; parked after the search if no other network reaches them either. */
        val unreachable = mutableListOf<TaskBatch>()
        var hive: StallReason? = null
        var noDropOff = false
        val missingItems = LinkedHashMap<ItemStackKey, Int>()
        val missingFluids = LinkedHashMap<Fluid, Int>()

        fun isMissingMaterials() = missingItems.isNotEmpty() || missingFluids.isNotEmpty()

        fun stall(): Stall? {
            val hive = hive
            return when {
                outOfRange -> Stall(StallReason.OUT_OF_RANGE)
                hive != null -> Stall(hive)
                isMissingMaterials() -> Stall.missing(
                    missingItems.map { (key, count) -> Component.literal("${count}x ").append(key.stack.hoverName) } +
                        missingFluids.map { (fluid, amount) -> Component.literal("${amount}mB ").append(FluidStack(fluid, amount).hoverName) }
                )
                noDropOff -> Stall(StallReason.NO_DROP_OFF)
                else -> null
            }
        }
    }
}
