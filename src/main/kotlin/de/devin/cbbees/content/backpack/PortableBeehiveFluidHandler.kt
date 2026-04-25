package de.devin.cbbees.content.backpack

import com.simibubi.create.AllFluids
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.Tags
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem

/**
 * Fluid handler that allows the portable beehive to be filled with honey
 * via Create's Spout (or any other fluid transfer system).
 *
 * Conversion: 250mB honey (1 bottle) = [CBBeesConfig.honeyBottleFuelValue] fuel units.
 */
class PortableBeehiveFluidHandler(private var stack: ItemStack) : IFluidHandlerItem {

    companion object {
        /** mB of honey per bottle — matches Create's filling recipe. */
        const val MB_PER_BOTTLE = 250
    }

    private fun currentFuel(): Int = stack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0)
    private fun maxFuel(): Int = CBBeesConfig.portableMaxHoney.get()
    private fun fuelPerBottle(): Int = CBBeesConfig.honeyBottleFuelValue.get()

    /** How many mB the tank can still accept, based on remaining fuel capacity. */
    private fun remainingCapacityMb(): Int {
        val fuelSpace = maxFuel() - currentFuel()
        if (fuelSpace <= 0) return 0
        // Convert fuel space back to mB: fuelSpace / fuelPerBottle * MB_PER_BOTTLE
        return (fuelSpace.toLong() * MB_PER_BOTTLE / fuelPerBottle()).toInt()
    }

    /** Total tank capacity in mB. */
    private fun capacityMb(): Int {
        return (maxFuel().toLong() * MB_PER_BOTTLE / fuelPerBottle()).toInt()
    }

    /** Current fill level in mB (for display purposes). */
    private fun currentMb(): Int {
        return (currentFuel().toLong() * MB_PER_BOTTLE / fuelPerBottle()).toInt()
    }

    private fun isHoney(fluid: FluidStack): Boolean {
        return fluid.fluid.`is`(Tags.Fluids.HONEY)
    }

    override fun getTanks(): Int = 1

    override fun getFluidInTank(tank: Int): FluidStack {
        val mb = currentMb()
        if (mb <= 0) return FluidStack.EMPTY
        return FluidStack(AllFluids.HONEY.get().source, mb)
    }

    override fun getTankCapacity(tank: Int): Int = capacityMb()

    override fun isFluidValid(tank: Int, stack: FluidStack): Boolean = isHoney(stack)

    override fun fill(resource: FluidStack, action: FluidAction): Int {
        if (resource.isEmpty || !isHoney(resource)) return 0

        val spaceInMb = remainingCapacityMb()
        if (spaceInMb <= 0) return 0

        val toFillMb = minOf(resource.amount, spaceInMb)
        if (toFillMb <= 0) return 0

        if (action.execute()) {
            // Convert mB to fuel: toFillMb / MB_PER_BOTTLE * fuelPerBottle
            val fuelGain = (toFillMb.toLong() * fuelPerBottle() / MB_PER_BOTTLE).toInt()
            val newFuel = minOf(currentFuel() + fuelGain, maxFuel())
            stack.set(AllDataComponents.HONEY_FUEL.get(), newFuel)
        }

        return toFillMb
    }

    override fun drain(resource: FluidStack, action: FluidAction): FluidStack = FluidStack.EMPTY

    override fun drain(maxDrain: Int, action: FluidAction): FluidStack = FluidStack.EMPTY

    override fun getContainer(): ItemStack = stack
}
