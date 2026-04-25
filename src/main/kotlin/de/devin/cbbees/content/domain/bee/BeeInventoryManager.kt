package de.devin.cbbees.content.domain.bee

import de.devin.cbbees.content.bee.CompositeMaterialSource
import de.devin.cbbees.content.bee.MaterialSource
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.PlayerMaterialSource
import de.devin.cbbees.content.domain.network.NetworkMaterialSource
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Handles inventory operations for construction bees, including item collection
 * from player inventory and wireless storages.
 */
class BeeInventoryManager(private val bee: MechanicalBeeEntity) {

    /**
     * Picks up items for the current task into the bee's inventory.
     */
    fun pickUpItems(required: List<ItemStack>, context: BeeContext): Boolean {
        val ownerPlayer = bee.getOwnerPlayer() ?: return false
        if (ownerPlayer.isCreative) return true

        val source = getMaterialSource(ownerPlayer, context)
        bee.inventory.clearContent()

        for (req in required) {
            if (req.isEmpty) continue
            if (bee.isInventoryFull()) break

            val extracted = source.extractItems(req, req.count)
            if (!extracted.isEmpty) {
                bee.addToInventory(extracted)
            }
        }

        val totalRequired = required.sumOf { it.count }
        var totalCarried = 0
        for (i in 0 until bee.inventory.containerSize) {
            totalCarried += bee.inventory.getItem(i).count
        }
        return totalCarried >= totalRequired
    }

    /**
     * Deposits carried items back into player or wireless inventory.
     */
    fun depositItems(context: BeeContext) {
        val ownerPlayer = bee.getOwnerPlayer() ?: return
        if (ownerPlayer.isCreative) {
            bee.inventory.clearContent()
            return
        }

        val source = getMaterialSource(ownerPlayer, context)

        for (i in 0 until bee.inventory.containerSize) {
            val stack = bee.inventory.getItem(i)
            if (stack.isEmpty) continue
            val remaining = source.insertItems(stack)
            bee.inventory.setItem(i, remaining)
        }
    }

    /**
     * Gets a material source that checks all available inventories.
     */
    private fun getMaterialSource(ownerPlayer: ServerPlayer, context: BeeContext): MaterialSource {
        val sources = mutableListOf<MaterialSource>()

        // 1. Player inventory
        sources.add(PlayerMaterialSource(ownerPlayer))

        // 2. Network inventory
        bee.network()?.let {
            sources.add(NetworkMaterialSource(it, bee.level()))
        }

        return CompositeMaterialSource(sources)
    }
}
