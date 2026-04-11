package de.devin.cbbees.content.domain.action.impl

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.server.BeeWorker
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Scans a small radius around the target position for loose [ItemEntity] objects
 * and picks them up into the bee's inventory. Used by the Pickup Schematic program.
 *
 * On arrival the bee collects as many items as its inventory allows in a single pass.
 * Items that don't fit remain on the ground for a future pickup batch.
 */
class PickupItemsAction(private val targetPos: BlockPos) : BeeAction {

    override val pos: BlockPos get() = targetPos

    override fun onActivate(worker: BeeWorker) {
        // Target position already set
    }

    override fun execute(level: Level, worker: BeeWorker, context: BeeContext): Boolean {
        val center = Vec3.atCenterOf(targetPos)
        val scanBox = AABB.ofSize(center, 3.0, 3.0, 3.0)
        val items = level.getEntitiesOfClass(ItemEntity::class.java, scanBox) { it.isAlive }

        for (entity in items) {
            if (worker.isInventoryFull()) break
            val stack = entity.item.copy()
            val remainder = worker.addToInventory(stack)
            if (remainder.isEmpty) {
                entity.discard()
            } else {
                entity.item = remainder
            }
            worker.consumeSpring(CBBeesConfig.springDrainPickup.get())
        }
        return true
    }

    override fun getDescription() = "Picking up items at (${targetPos.x}, ${targetPos.y}, ${targetPos.z})"
}
