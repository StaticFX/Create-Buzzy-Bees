package de.devin.cbbees.api.schematic

import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState

/**
 * Custom mirror and rotation for blocks whose state vanilla can't transform; return null to skip.
 * @see de.devin.cbbees.compat.refinedstorage.RefinedStorageSchematics
 */
interface BlockTransform {
    fun mirror(state: BlockState, mirror: Mirror): BlockState?
    fun rotate(state: BlockState, rotation: Rotation): BlockState?
}
