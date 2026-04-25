package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.neoforged.neoforge.items.ItemHandlerHelper

/**
 * BumbleBee behavior: fly to the source (EXTRACT) port and pick up items.
 *
 * Only runs when the bee has a transport task and hasn't picked up items yet
 * (inventory is empty).
 */
class PickUpFromSourceBehavior : Behavior<MechanicalBumbleBeeEntity>(
    mapOf(
        BeeMemoryModules.TRANSPORT_TASK.get() to MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
    ),
    1
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBumbleBeeEntity): Boolean {
        if (owner.springTension <= 0f) return false
        // Only pick up if inventory is empty (haven't collected yet)
        return owner.isInventoryEmpty()
    }

    override fun start(level: ServerLevel, entity: MechanicalBumbleBeeEntity, gameTime: Long) {
        val task = entity.brain.getMemory(BeeMemoryModules.TRANSPORT_TASK.get()).get()
        val sourcePos = task.sourcePos

        if (entity.blockPosition().closerThan(sourcePos, entity.workRange)) {
            // At the source port — extract items
            val network = entity.network() ?: run {
                BeeDebug.logForEntity(entity, "Bumble", "No network — clearing task")
                entity.brain.eraseMemory(BeeMemoryModules.TRANSPORT_TASK.get())
                return
            }

            val port = network.transportPortsByPos[sourcePos]?.takeIf { it.isValidProvider() }
            if (port == null) {
                BeeDebug.logForEntity(entity, "Bumble", "Source port gone at $sourcePos — clearing task")
                entity.brain.eraseMemory(BeeMemoryModules.TRANSPORT_TASK.get())
                return
            }

            // Release reservation now that we're at the port
            port.releaseReservation(entity.uuid)

            // Pre-check: how much the target can accept (best-effort, handles most overflow)
            val targetPort = network.transportPortsByPos[task.targetPos]?.takeIf { it.isValidRequester() }
            val targetHandler = targetPort?.getItemHandler(targetPort.world)

            var pickedUp = false
            for (item in task.items) {
                if (entity.isInventoryFull()) break

                // Determine how many items the target can accept
                var toExtract = item
                if (targetHandler != null) {
                    val simulated = ItemHandlerHelper.insertItemStacked(targetHandler, item.copy(), true)
                    val canAccept = item.count - simulated.count
                    if (canAccept <= 0) {
                        BeeDebug.logForEntity(entity, "Bumble", "Target full for ${item.item}, skipping")
                        continue
                    }
                    if (canAccept < item.count) {
                        toExtract = item.copyWithCount(canAccept)
                        BeeDebug.logForEntity(entity, "Bumble", "Target can accept ${canAccept}/${item.count} ${item.item}")
                    }
                }

                if (port.hasItemStack(toExtract) && port.removeItemStack(toExtract)) {
                    val remainder = entity.addToInventory(toExtract.copy())
                    if (!remainder.isEmpty) {
                        port.addItemStack(remainder)
                    }
                    pickedUp = true
                    entity.consumeSpring(CBBeesConfig.springDrainPickup.get())
                    BeeDebug.logForEntity(
                        entity,
                        "Bumble",
                        "Picked up ${toExtract.count}x ${toExtract.item} from source at $sourcePos"
                    )
                }
            }

            if (!pickedUp) {
                BeeDebug.logForEntity(entity, "Bumble", "No items available at source — clearing task")
                entity.brain.eraseMemory(BeeMemoryModules.TRANSPORT_TASK.get())
            }
        } else {
            BeeDebug.logForEntity(entity, "Bumble", "Flying to source port at $sourcePos")
            entity.brain.setMemory(
                MemoryModuleType.WALK_TARGET,
                WalkTarget(sourcePos, 1.0f, 1)
            )
        }
    }
}
