package de.devin.cbbees.api.schematic

import de.devin.cbbees.content.domain.action.BeeAction
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * What bees place instead of a schematic block that can't be placed as it is, such as a part of a formed multiblock.
 * [finish] runs once every block of the schematic is placed, e.g. to form the multiblock again.
 */
class Substitute(val state: BlockState, val finish: BeeAction? = null)

/** Replaces schematic blocks bees can't place directly; return null to leave the block alone. */
fun interface SchematicSubstitute {
    fun substitute(pos: BlockPos, state: BlockState, blockEntity: BlockEntity?): Substitute?
}
