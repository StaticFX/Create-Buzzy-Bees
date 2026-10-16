package de.devin.cbbees.content.domain.job

import com.simibubi.create.content.schematics.cannon.MaterialChecklist
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.world.item.ItemStack

object MissingMaterials {

    private val FINISHED = setOf(TaskStatus.COMPLETED, TaskStatus.CANCELLED)

    fun checklistFor(job: BeeJob, network: BeeNetwork?): MaterialChecklist {
        val checklist = MaterialChecklist()

        job.tasks.asSequence()
            .filter { it.status !in FINISHED }
            .mapNotNull { it.action as? ItemConsumingAction }
            .flatMap { it.requiredItems }
            .filterNot(ItemStack::isEmpty)
            .forEach { checklist.required.mergeInt(it.item, it.count, Integer::sum) }

        network?.ports.orEmpty()
            .filter { it.isValidForPickup() }
            .distinct()
            .mapNotNull { it.getItemHandler(it.world) }
            .forEach { handler -> (0 until handler.slots).forEach { checklist.collect(handler.getStackInSlot(it)) } }

        return checklist
    }

    fun MaterialChecklist.missingCount(): Int =
        required.keys.count { getRequiredAmount(it) > gathered.getInt(it) }
}
