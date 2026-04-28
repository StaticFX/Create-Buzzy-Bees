package de.devin.cbbees.content.domain

import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.world.item.ItemStack

object StuckReasonResolver {

    fun firstReasonOrNull(network: BeeNetwork, job: BeeJob): String? {
        if (hasActiveBees(job)) return null

        val pendingBatches = job.batches.filter { it.status == TaskStatus.PENDING }
        if (pendingBatches.isEmpty()) return null

        return checkOutOfRange(network, pendingBatches)
            ?: checkNoFuel(network, pendingBatches)
            ?: checkNoBees(network, pendingBatches)
            ?: checkMissingItems(network, pendingBatches)
            ?: checkNoDropOff(network, pendingBatches)
    }

    private fun hasActiveBees(job: BeeJob): Boolean {
        return job.batches.any { it.status == TaskStatus.IN_PROGRESS || it.status == TaskStatus.PICKED }
    }

    private fun checkOutOfRange(network: BeeNetwork, batches: List<TaskBatch>): String? {
        if (batches.any { !network.isInRange(it.targetPosition) })
            return "cbbees.stall.out_of_range"
        return null
    }

    private fun checkNoFuel(network: BeeNetwork, batches: List<TaskBatch>): String? {
        // Check if any hive that could actually dispatch these batches has fuel
        val hivesInRange = network.hives.filter { hive ->
            batches.any { hive.isInWorkRange(it.targetPosition) }
        }
        if (hivesInRange.isEmpty()) return null

        // If any non-portable hive can handle the work, fuel isn't the bottleneck
        if (hivesInRange.any { it !is PortableBeeHive }) return null

        // All in-range hives are portable — check if any has fuel
        val portableInRange = hivesInRange.filterIsInstance<PortableBeeHive>()
        if (portableInRange.any { it.hasHoney(1) }) return null

        return "cbbees.stall.no_fuel"
    }

    private fun checkNoBees(network: BeeNetwork, batches: List<TaskBatch>): String? {
        val totalBees = network.hives.sumOf { it.getAvailableBeeCount() + it.getActiveBeeCount() }
        if (totalBees <= 0) return "cbbees.stall.no_bees"

        val needsTransport = batches.any { it.beeType == BeeType.TRANSPORT }
        val needsConstruction = batches.any { it.beeType == BeeType.CONSTRUCTION }

        if (needsTransport && network.hives.none { it.hasBeeOfType(BeeType.TRANSPORT) })
            return "cbbees.stall.no_bumble_bees"
        if (needsConstruction && network.hives.none { it.hasBeeOfType(BeeType.CONSTRUCTION) })
            return "cbbees.stall.no_bees"

        return null
    }

    private fun checkMissingItems(network: BeeNetwork, batches: List<TaskBatch>): String? {
        val allMissing = mutableMapOf<String, Int>()
        batches.forEach { b ->
            b.tasks.map { it.action }
                .filterIsInstance<ItemConsumingAction>()
                .flatMap { it.requiredItems }
                .filter { req -> network.findAvailableProvider(req) == null }
                .forEach { stack ->
                    val name = stack.hoverName.string
                    allMissing[name] = (allMissing[name] ?: 0) + stack.count
                }
        }
        if (allMissing.isEmpty()) return null
        val entries = allMissing.entries.toList()
        val shown = entries.take(5).joinToString(", ") { "${it.value}x ${it.key}" }
        val suffix = if (entries.size > 5) " +${entries.size - 5} more" else ""
        return "Missing: $shown$suffix"
    }

    private fun checkNoDropOff(network: BeeNetwork, batches: List<TaskBatch>): String? {
        if (batches.any { it.beeType == BeeType.TRANSPORT }
            && network.findDropOff(ItemStack.EMPTY) == null) {
            return "cbbees.stall.no_drop_off_port"
        }
        return null
    }
}
