package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.task.TransportTask
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack

/**
 * BumbleBee behavior: deposit carried items at the target (INSERT) port.
 *
 * Runs when the bee has items and is at the target port.
 * After depositing, clears the transport task so the bee returns to the hive.
 */
class DepositAtTargetBehavior : Behavior<MechanicalBumbleBeeEntity>(
    mapOf(
        BeeMemoryModules.TRANSPORT_TASK.get() to MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
    ),
    1
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBumbleBeeEntity): Boolean {
        if (owner.springTension <= 0f) return false
        if (owner.isInventoryEmpty()) return false

        val task = owner.brain.getMemory(BeeMemoryModules.TRANSPORT_TASK.get()).get()
        return owner.blockPosition().closerThan(task.targetPos, owner.workRange)
    }

    override fun start(level: ServerLevel, entity: MechanicalBumbleBeeEntity, gameTime: Long) {
        val task = entity.brain.getMemory(BeeMemoryModules.TRANSPORT_TASK.get()).get()
        val targetPos = task.targetPos
        val isReturning = task.returningOverflow

        val network = entity.network()
        // When returning overflow, targetPos is the original provider
        val port = if (isReturning) {
            network?.transportPortsByPos?.get(targetPos)?.takeIf { it.isValidProvider() }
        } else {
            network?.transportPortsByPos?.get(targetPos)?.takeIf { it.isValidRequester() }
        }

        val items = entity.getInventoryContents().map { it.copy() }
        val overflow = mutableListOf<ItemStack>()

        if (port != null) {
            for (item in items) {
                val remainder = port.addItemStack(item)
                entity.removeFromInventory(item, item.count)
                entity.consumeSpring(CBBeesConfig.springDrainDeposit.get())

                val deposited = item.count - (if (remainder.isEmpty) 0 else remainder.count)
                if (deposited > 0) {
                    BeeDebug.logForEntity(entity, "Bumble", "Deposited ${deposited}x ${item.item} at $targetPos")
                }
                if (!remainder.isEmpty) {
                    overflow.add(remainder)
                }
            }
        } else {
            BeeDebug.logForEntity(entity, "Bumble", "Port gone at $targetPos")
            for (item in items) {
                overflow.add(item)
                entity.removeFromInventory(item, item.count)
            }
        }

        if (overflow.isNotEmpty() && !isReturning) {
            // Return overflow to the original source
            BeeDebug.logForEntity(entity, "Bumble", "Returning ${overflow.sumOf { it.count }} overflow item(s) to source at ${task.sourcePos}")
            for (item in overflow) {
                val rem = entity.addToInventory(item)
                if (!rem.isEmpty) {
                    // Bee inventory can't hold it all — drop excess
                    level.addFreshEntity(ItemEntity(level, entity.x, entity.y, entity.z, rem))
                }
            }
            entity.brain.setMemory(
                BeeMemoryModules.TRANSPORT_TASK.get(),
                TransportTask(
                    sourcePos = task.targetPos,
                    targetPos = task.sourcePos,
                    items = overflow,
                    returningOverflow = true
                )
            )
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        } else {
            // Drop any remaining overflow as last resort (already tried returning, or was a return trip)
            if (overflow.isNotEmpty()) {
                BeeDebug.logForEntity(entity, "Bumble", "Cannot return overflow — dropping ${overflow.sumOf { it.count }} item(s)")
                for (item in overflow) {
                    level.addFreshEntity(ItemEntity(level, entity.x, entity.y, entity.z, item))
                }
            }
            entity.brain.eraseMemory(BeeMemoryModules.TRANSPORT_TASK.get())
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        }
    }
}
