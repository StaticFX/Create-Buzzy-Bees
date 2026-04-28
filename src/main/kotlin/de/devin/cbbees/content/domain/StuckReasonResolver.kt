package de.devin.cbbees.content.domain

import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.world.item.ItemStack

object StuckReasonResolver {
    fun firstReasonOrNull(network: BeeNetwork, job: BeeJob): String? {
        val pendingBatches = job.batches.filter { it.status == TaskStatus.PENDING }
        if (pendingBatches.isEmpty()) return null

        if (pendingBatches.any { !network.isInRange(it.targetPosition) })
            return "cbbees.stall.out_of_range"

        // Collect all missing items across all pending batches.
        // A provider only counts as available if at least one hive can reach it.
        val allMissing = mutableMapOf<String, Int>()
        pendingBatches.forEach { b ->
            b.tasks.map { it.action }
                .filterIsInstance<ItemConsumingAction>()
                .flatMap { it.requiredItems }
                .filter { req -> !isProviderReachable(network, req) }
                .forEach { stack ->
                    val name = stack.hoverName.string
                    allMissing[name] = (allMissing[name] ?: 0) + stack.count
                }
        }
        if (allMissing.isNotEmpty()) {
            val entries = allMissing.entries.toList()
            val shown = entries.take(5).joinToString(", ") { "${it.value}x ${it.key}" }
            val suffix = if (entries.size > 5) " +${entries.size - 5} more" else ""
            return "Missing: $shown$suffix"
        }

        if (pendingBatches.any { it.beeType == BeeType.TRANSPORT }
            && network.findDropOff(ItemStack.EMPTY) == null) {
            return "cbbees.stall.no_drop_off_port"
        }

        val needsTransport = pendingBatches.any { it.beeType == BeeType.TRANSPORT }
        val needsConstruction = pendingBatches.any { it.beeType == BeeType.CONSTRUCTION }

        if (needsTransport && network.hives.none { it.hasBeeOfType(BeeType.TRANSPORT) })
            return "cbbees.stall.no_bumble_bees"
        if (needsConstruction && network.hives.none { it.hasBeeOfType(BeeType.CONSTRUCTION) })
            return "cbbees.stall.no_bees"

        val totalBees = network.hives.sumOf { it.getAvailableBeeCount() + it.getActiveBeeCount() }
        if (totalBees <= 0) return "cbbees.stall.no_bees"

        return null
    }

    /**
     * Checks if a provider with the given item exists AND is reachable by at least one hive.
     * Mirrors the logic in [FlightPlanComputer.findBestProvider] which rejects providers
     * that are out of work range or belong to a different player's portable beehive.
     */
    private fun isProviderReachable(network: BeeNetwork, req: ItemStack): Boolean {
        return network.findAvailableProvider(req) != null
    }
}
