package de.devin.cbbees.content.bee.server

import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * Minimal interface for bee-world interaction, used by [de.devin.cbbees.content.domain.action.BeeAction].
 *
 * Implemented by both [ServerBeeData] (non-entity system) and [de.devin.cbbees.content.bee.MechanicalBeeEntity]
 * (legacy migration). Actions only need inventory access, position, and spring management.
 */
interface BeeWorker {
    val uuid: UUID
    val networkId: UUID
    fun blockPosition(): BlockPos
    fun level(): Level

    fun addToInventory(stack: ItemStack): ItemStack
    fun removeFromInventory(stack: ItemStack, count: Int)
    fun getInventoryContents(): List<ItemStack>
    fun isInventoryFull(): Boolean
    fun isInventoryEmpty(): Boolean

    fun consumeSpring(baseDrain: Double): Boolean
    fun getBeeContext(): BeeContext

    /** Returns the owner player (for portable beehive bees), or null. */
    fun getOwnerPlayer(): Player?

    /** Look up this bee's network. */
    fun network(): de.devin.cbbees.content.domain.network.BeeNetwork?

    /** Drop all inventory contents as items on the ground. */
    fun dropInventory() {
        val level = level()
        getInventoryContents().forEach { item ->
            removeFromInventory(item, item.count)
            level.addFreshEntity(net.minecraft.world.entity.item.ItemEntity(level, getWorkerX(), getWorkerY(), getWorkerZ(), item.copy()))
        }
    }

    fun getWorkerX(): Double
    fun getWorkerY(): Double
    fun getWorkerZ(): Double
}
