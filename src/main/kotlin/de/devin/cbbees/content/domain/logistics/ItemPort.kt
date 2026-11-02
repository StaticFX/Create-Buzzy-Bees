package de.devin.cbbees.content.domain.logistics

import de.devin.cbbees.content.logistics.storage.PortStorage
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * Common interface for network ports that hold item inventories.
 *
 * Both [LogisticsPort] (construction bee ports) and [TransportPort] (bumble bee cargo ports)
 * share these operations.
 */
interface ItemPort : NetworkPort, Reservable<ItemStack> {
    override val id: UUID
    override val world: Level
    override val pos: BlockPos

    fun storage(): PortStorage?

    fun walkTarget(): WalkTarget

    fun hasItemStack(stack: ItemStack): Boolean

    /**
     * Checks whether the port has enough unreserved stock of [stack],
     * optionally excluding a specific bee's reservation.
     */
    fun hasAvailableItemStack(stack: ItemStack, excludeBeeId: UUID? = null): Boolean = hasItemStack(stack)

    fun removeItemStack(stack: ItemStack): Boolean

    fun addItemStack(stack: ItemStack): ItemStack

    /**
     * Checks whether the attached inventory has any free space at all.
     */
    fun hasInventorySpace(): Boolean = storage()?.hasSpace() ?: false
}
