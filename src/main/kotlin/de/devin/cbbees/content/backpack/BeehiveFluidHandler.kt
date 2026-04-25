package de.devin.cbbees.content.backpack

import com.simibubi.create.AllFluids
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem

/**
 * Fluid handler that allows the Portable Beehive to be filled with honey via Create's Spout.
 *
 * Converts honey fluid (mB) to internal honey fuel units.
 * 1 mB of honey = 1 honey fuel unit.
 */
class BeehiveFluidHandler(private val stack: ItemStack) : IFluidHandlerItem {

    override fun getTanks(): Int = 1

    override fun getFluidInTank(tank: Int): FluidStack {
        val fuel = stack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0)
        if (fuel <= 0) return FluidStack.EMPTY
        return FluidStack(AllFluids.HONEY.get().source, fuel)
    }

    override fun getTankCapacity(tank: Int): Int {
        val beehiveItem = stack.item as? PortableBeehiveItem ?: return CBBeesConfig.portableMaxHoney.get()
        return beehiveItem.getMaxHoney(stack)
    }

    override fun isFluidValid(tank: Int, fluidStack: FluidStack): Boolean {
        return fluidStack.fluid.isSame(AllFluids.HONEY.get().source)
    }

    override fun fill(resource: FluidStack, action: net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction): Int {
        if (!isFluidValid(0, resource)) return 0

        val current = stack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0)
        val max = getTankCapacity(0)
        val space = max - current
        if (space <= 0) return 0

        val toFill = minOf(space, resource.amount)
        if (action.execute()) {
            stack.set(AllDataComponents.HONEY_FUEL.get(), current + toFill)
        }
        return toFill
    }

    override fun drain(resource: FluidStack, action: net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction): FluidStack {
        if (!resource.fluid.isSame(AllFluids.HONEY.get().source)) return FluidStack.EMPTY
        return drain(resource.amount, action)
    }

    override fun drain(maxDrain: Int, action: net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction): FluidStack {
        val current = stack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0)
        if (current <= 0) return FluidStack.EMPTY

        val drained = minOf(current, maxDrain)
        if (action.execute()) {
            stack.set(AllDataComponents.HONEY_FUEL.get(), current - drained)
        }
        return FluidStack(AllFluids.HONEY.get().source, drained)
    }

    override fun getContainer(): ItemStack = stack
}
