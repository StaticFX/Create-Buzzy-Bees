package de.devin.cbbees.content.schematics

import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState

interface BlockTransform {
    fun mirror(state: BlockState, mirror: Mirror): BlockState?
    fun rotate(state: BlockState, rotation: Rotation): BlockState?
}

object BlockTransforms {

    private val transforms = mutableListOf<BlockTransform>()

    fun register(transform: BlockTransform) {
        transforms += transform
    }

    @JvmStatic
    fun mirror(state: BlockState, mirror: Mirror): BlockState? = transforms.firstNotNullOfOrNull { it.mirror(state, mirror) }

    @JvmStatic
    fun rotate(state: BlockState, rotation: Rotation): BlockState? = transforms.firstNotNullOfOrNull { it.rotate(state, rotation) }
}
