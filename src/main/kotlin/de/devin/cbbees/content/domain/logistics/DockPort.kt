package de.devin.cbbees.content.domain.logistics

import de.devin.cbbees.content.domain.network.INetworkComponent
import net.minecraft.core.BlockPos
import net.neoforged.neoforge.fluids.FluidStack

/**
 * Port used by construction bees to pick up and drop off fluids from an attached tank.
 */
interface DockPort : INetworkComponent {

    /** Position where a bee hovers to pick up or drop off fluid. */
    fun gatherPos(): BlockPos = pos.above()

    fun priority(): Int

    fun isValidForPickup(): Boolean

    fun isValidForDropOff(): Boolean

    /** Tests whether [fluid] passes this port's fluid filter. */
    fun testFilter(fluid: FluidStack): Boolean = true

    /** Whether the attached tank can supply all of [fluid]. */
    fun hasFluid(fluid: FluidStack): Boolean

    /** Drains exactly [fluid] from the attached tank. Returns false and drains nothing if it can't. */
    fun drainFluid(fluid: FluidStack): Boolean

    /** Whether the attached tank can take all of [fluid]. */
    fun canFill(fluid: FluidStack): Boolean

    /** Fills the attached tank with [fluid]. Returns the amount accepted. */
    fun fillFluid(fluid: FluidStack): Int

    override fun isAnchor(): Boolean = false

    override fun getNetworkingRange(): Double = 0.0

    override fun isInWorkRange(pos: BlockPos): Boolean = false
}
