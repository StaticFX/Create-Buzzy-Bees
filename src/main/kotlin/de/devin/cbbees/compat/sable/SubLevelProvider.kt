package de.devin.cbbees.compat.sable

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID

interface SubLevelProvider {
    fun subLevelId(level: Level, pos: BlockPos): UUID?
    fun toWorld(level: Level, pos: Vec3): Vec3
    fun worldDistanceSqr(level: Level, a: Vec3, b: Vec3): Double
}

object NoSubLevels : SubLevelProvider {
    override fun subLevelId(level: Level, pos: BlockPos): UUID? = null
    override fun toWorld(level: Level, pos: Vec3): Vec3 = pos
    override fun worldDistanceSqr(level: Level, a: Vec3, b: Vec3): Double = a.distanceToSqr(b)
}
