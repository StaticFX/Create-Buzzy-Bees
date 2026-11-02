package de.devin.cbbees.content.planner.client

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object AreaSelection {
    var firstPos: BlockPos? = null
        set(value) {
            field = value
            dimension = Minecraft.getInstance().level?.dimension()
        }
    var secondPos: BlockPos? = null

    private var dimension: ResourceKey<Level>? = null

    var selectedPos: BlockPos? = null

    var range = 10

    fun discard() {
        firstPos = null
        secondPos = null
    }

    fun discardIfLeft(level: Level) {
        if (firstPos != null && dimension != level.dimension()) discard()
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
