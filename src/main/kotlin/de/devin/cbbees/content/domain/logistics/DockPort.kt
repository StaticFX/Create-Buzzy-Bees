package de.devin.cbbees.content.domain.logistics

import net.neoforged.neoforge.fluids.FluidStack
import java.util.UUID

/**
 * Port used by construction bees to pick up and drop off fluids from an attached tank.
 */
interface DockPort : NetworkPort, Reservable<FluidStack> {

    fun isValidForPickup(): Boolean

    fun isValidForDropOff(): Boolean

    /** Tests whether [fluid] passes this port's fluid filter. */
    fun testFilter(fluid: FluidStack): Boolean = true

    /** Whether the attached tank can supply all of [fluid]. */
    fun hasFluid(fluid: FluidStack): Boolean

    fun hasAvailableFluid(fluid: FluidStack, excludeBeeId: UUID? = null): Boolean = hasFluid(fluid)

    /** Drains exactly [fluid] from the attached tank. Returns false and drains nothing if it can't. */
    fun drainFluid(fluid: FluidStack): Boolean

    /** Whether the attached tank can take all of [fluid]. */
    fun canFill(fluid: FluidStack): Boolean

    /** Fills the attached tank with [fluid]. Returns the amount accepted. */
    fun fillFluid(fluid: FluidStack): Int
}
