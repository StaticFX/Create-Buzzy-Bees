package de.devin.cbbees.content.domain.action.impl

import de.devin.cbbees.content.bee.server.BeeWorker
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.Level
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidUtil
import net.neoforged.neoforge.fluids.capability.templates.FluidTank

/**
 * Places a fluid source block, or fills a waterloggable block, using the fluid the bee carries.
 */
class PlaceFluidAction(
    override val pos: BlockPos,
    val fluid: FluidStack,
) : BeeAction {

    override fun execute(level: Level, worker: BeeWorker, context: BeeContext): Boolean {
        val existing = level.getFluidState(pos)
        if (existing.isSource && existing.type.isSame(fluid.fluid)) return true

        val carried = worker.carriedFluid
        if (!FluidStack.isSameFluidSameComponents(carried, fluid) || carried.amount < fluid.amount) return false

        val tank = FluidTank(ServerBeeData.FLUID_CAPACITY).apply { this.fluid = carried.copy() }
        if (!FluidUtil.tryPlaceFluid(null, level, InteractionHand.MAIN_HAND, pos, tank, fluid)) return false

        worker.carriedFluid = tank.fluid
        return true
    }

    override fun getDescription(): String =
        "Placing ${fluid.hoverName.string} at (${pos.x}, ${pos.y}, ${pos.z})"

    fun save(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        tag.putInt("X", pos.x)
        tag.putInt("Y", pos.y)
        tag.putInt("Z", pos.z)
        tag.put("Fluid", fluid.save(registries))
        return tag
    }

    companion object {
        fun load(tag: CompoundTag, registries: HolderLookup.Provider): PlaceFluidAction {
            val pos = BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"))
            return PlaceFluidAction(pos, FluidStack.parseOptional(registries, tag.getCompound("Fluid")))
        }
    }
}
