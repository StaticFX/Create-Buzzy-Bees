package de.devin.cbbees.util

import net.neoforged.neoforge.fluids.FluidStack

data class FluidStackKey(val stack: FluidStack) {
    override fun equals(other: Any?): Boolean {
        if (other !is FluidStackKey) return false
        return FluidStack.isSameFluidSameComponents(stack, other.stack)
    }

    override fun hashCode(): Int = FluidStack.hashFluidAndComponents(stack)
}
