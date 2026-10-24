package de.devin.cbbees.content.domain.history

import com.simibubi.create.content.schematics.requirement.ItemRequirement
import com.simibubi.create.foundation.utility.BlockHelper
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.domain.action.impl.PlaceBlockAction
import de.devin.cbbees.content.domain.action.impl.PlaceFluidAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.fluids.FluidStack

data class BlockSnapshot(val state: BlockState, val blockEntityTag: CompoundTag?) {

    fun restoreAt(pos: BlockPos): BeeAction = when {
        state.isAir -> RemoveBlockAction(pos)
        state.block is LiquidBlock ->
            if (state.fluidState.isSource) PlaceFluidAction(pos, FluidStack(state.fluidState.type, ServerBeeData.FLUID_CAPACITY))
            else RemoveBlockAction(pos)
        else -> PlaceBlockAction(pos, state, blockEntityTag?.copy(), ItemRequirement.of(state, null).requiredItems.map { it.stack.copy() })
    }

    fun save(): CompoundTag = CompoundTag().apply {
        put("State", NbtUtils.writeBlockState(state))
        blockEntityTag?.let { put("Tag", it.copy()) }
    }

    companion object {
        fun capture(level: Level, pos: BlockPos): BlockSnapshot {
            val state = level.getBlockState(pos)
            return BlockSnapshot(state, BlockHelper.prepareBlockEntityData(level, state, level.getBlockEntity(pos)))
        }

        fun load(tag: CompoundTag, registries: HolderLookup.Provider) = BlockSnapshot(
            NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("State")),
            tag.getCompound("Tag").takeIf { tag.contains("Tag") }
        )
    }
}
