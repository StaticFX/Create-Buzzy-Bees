package de.devin.cbbees.content.domain

import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.world.item.ItemStack

object StuckReasonResolver {
    fun firstReasonOrNull(network: BeeNetwork, job: BeeJob): String? {
        if (job.batches.any { !network.isInRange(it.targetPosition) })
            return "cbbees.stall.out_of_range"

        // Collect all missing items across all pending batches
        val allMissing = mutableMapOf<String, Int>()
        job.batches.filter { it.status == TaskStatus.PENDING }.forEach { b ->
            b.tasks.map { it.action }
                .filterIsInstance<ItemConsumingAction>()
                .flatMap { it.requiredItems }
                .filter { req -> network.findAvailableProvider(req) == null }
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

        val totalBees = network.hives.sumOf { it.getAvailableBeeCount() + it.getActiveBeeCount() }
        if (totalBees <= 0) return "cbbees.stall.no_bees"

        return null
    }
}
