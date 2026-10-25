package de.devin.cbbees.content.planner.client

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object AreaSelection {
    var firstPos: BlockPos? = null
    var secondPos: BlockPos? = null

    var selectedPos: BlockPos? = null

    var range = 10

    fun discard() {
        firstPos = null
        secondPos = null
    }

    fun getSelectionBox(): AABB? {
        val first = firstPos
        val second = secondPos
        val selected = selectedPos

        return when {
            second != null && first != null ->
                AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(second)).expandTowards(1.0, 1.0, 1.0)
            first != null && selected != null ->
                AABB(Vec3.atLowerCornerOf(first), Vec3.atLowerCornerOf(selected)).expandTowards(1.0, 1.0, 1.0)
            first != null -> AABB(first)
            selected != null -> AABB(selected)
            else -> null
        }
    }

    fun isComplete(): Boolean = firstPos != null && secondPos != null
}
