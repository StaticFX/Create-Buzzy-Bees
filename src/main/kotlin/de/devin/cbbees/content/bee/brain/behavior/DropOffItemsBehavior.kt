package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.action.impl.DropOffItemsAction
import de.devin.cbbees.util.ItemStackKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack

/**
 * Brain behavior that drops off excess items (picked up during block removal)
 * at a logistics port. If no port is available, drops items on the ground.
 *
 * Runs at higher priority than MoveToTask so the bee deposits items
 * before continuing to the next task.
 */
class DropOffItemsBehavior : Behavior<MechanicalBeeEntity>(
    mapOf(
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT
    ),
    1
) {

    override fun checkExtraStartConditions(level: ServerLevel, owner: MechanicalBeeEntity): Boolean {
        // Don't interfere when the current task is a DropOffItemsAction —
        // it has its own player-inventory fallback logic.
        val batch = owner.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).orElse(null)
        if (batch?.getCurrentTask()?.action is DropOffItemsAction) return false

        val excess = getExcessItems(owner)
        if (excess.isEmpty()) return false
        BeeDebug.log(owner, "DropOff: ${excess.size} excess item type(s) in inventory")
        return true
    }

    override fun start(level: ServerLevel, entity: MechanicalBeeEntity, gameTime: Long) {
        val excess = getExcessItems(entity)
        if (excess.isEmpty()) return

        val network = entity.network()
        val dropOffPort = network?.findDropOff(excess.first(), entity.homeId)

        if (dropOffPort == null) {
            // Try giving items to the owner player (portable beehive bees)
            val owner = entity.getOwnerPlayer()
            if (owner != null) {
                BeeDebug.log(entity, "DropOff: no port, giving ${excess.size} stack(s) to player ${owner.name.string}")
                for (item in excess) {
                    entity.removeFromInventory(item, item.count)
                    if (!owner.inventory.add(item.copy())) {
                        val drop = ItemEntity(level, owner.x, owner.y, owner.z, item.copy())
                        level.addFreshEntity(drop)
                    }
                }
            } else {
                BeeDebug.log(entity, "DropOff: no port available, dropping items on ground")
                dropItems(entity, excess)
            }
            return
        }

        val workRange = entity.workRange
        if (entity.blockPosition().closerThan(dropOffPort.pos, workRange)) {
            // At the port — deposit items
            for (item in excess) {
                val remainder = dropOffPort.addItemStack(item.copy())
                if (!remainder.isEmpty) {
                    val itemEntity = ItemEntity(
                        level,
                        dropOffPort.pos.x + 0.5,
                        dropOffPort.pos.y + 0.5,
                        dropOffPort.pos.z + 0.5,
                        remainder
                    )
                    level.addFreshEntity(itemEntity)
                }
                entity.removeFromInventory(item, item.count)
                BeeDebug.log(entity, "Deposited ${item.count}x ${item.item} at port ${dropOffPort.pos}")
            }
        } else {
            BeeDebug.log(entity, "Flying to drop-off port at ${dropOffPort.pos}")
            entity.brain.setMemory(
                MemoryModuleType.WALK_TARGET,
                WalkTarget(dropOffPort.pos, 1.0f, 1)
            )
        }
    }

    /**
     * Returns items in the bee's inventory that are NOT needed by remaining tasks.
     */
    private fun getExcessItems(bee: MechanicalBeeEntity): List<ItemStack> {
        val contents = bee.getInventoryContents()
        if (contents.isEmpty()) return emptyList()

        val batch = bee.brain.getMemory(BeeMemoryModules.CURRENT_TASK.get()).orElse(null)
            ?: return contents.map { it.copy() }

        // Tally items needed by remaining tasks
        val needed = mutableMapOf<ItemStackKey, Int>()
        for (task in batch.getRemainingTasks()) {
            val action = task.action
            if (action is ItemConsumingAction) {
                for (req in action.requiredItems) {
                    val key = ItemStackKey(req)
                    needed[key] = (needed[key] ?: 0) + req.count
                }
            }
        }

        val excess = mutableListOf<ItemStack>()
        for (carried in contents) {
            val key = ItemStackKey(carried)
            val neededCount = needed[key] ?: 0
            if (neededCount <= 0) {
                excess.add(carried.copy())
            } else {
                val surplus = carried.count - neededCount
                needed[key] = maxOf(0, neededCount - carried.count)
                if (surplus > 0) {
                    excess.add(carried.copyWithCount(surplus))
                }
            }
        }

        return excess
    }

    private fun dropItems(bee: MechanicalBeeEntity, items: List<ItemStack>) {
        for (item in items) {
            bee.removeFromInventory(item, item.count)
            val itemEntity = ItemEntity(bee.level(), bee.x, bee.y, bee.z, item.copy())
            bee.level().addFreshEntity(itemEntity)
        }
    }
}
