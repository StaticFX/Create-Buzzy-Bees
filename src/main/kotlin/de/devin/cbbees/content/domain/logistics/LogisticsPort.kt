package de.devin.cbbees.content.domain.logistics

import de.devin.cbbees.content.logistics.ports.PortType
import net.minecraft.world.item.ItemStack

/**
 * Port used by construction bees to pick up and drop off materials.
 *
 * Extends [ReservablePort] for shared item handling and reservation support.
 */
interface LogisticsPort : ReservablePort {

    fun getPortType(): PortType

    fun getFilter(): ItemStack

    fun isValidForPickup(): Boolean

    fun isValidForDropOff(): Boolean

    /**
     * Tests whether the given [stack] passes this port's item filter.
     * Returns true if no filter is set or if the item matches the filter.
     */
    fun testFilter(stack: ItemStack): Boolean = true
}
