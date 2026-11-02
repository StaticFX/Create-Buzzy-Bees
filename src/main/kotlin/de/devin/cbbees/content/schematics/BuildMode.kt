package de.devin.cbbees.content.schematics

import net.minecraft.world.level.block.state.BlockState

data class BuildMode(
    val id: String,
    val replacesBlocks: Boolean,
    val clearsEmptySpots: Boolean,
)

object BuildModes {
    val REPLACE = BuildMode("replace", replacesBlocks = true, clearsEmptySpots = false)
    val KEEP = BuildMode("keep", replacesBlocks = false, clearsEmptySpots = false)
    val CLEAR = BuildMode("clear", replacesBlocks = true, clearsEmptySpots = true)

    val all = listOf(REPLACE, KEEP, CLEAR)

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: REPLACE

    fun isOccupied(state: BlockState) = !state.isAir && !state.canBeReplaced()
}
