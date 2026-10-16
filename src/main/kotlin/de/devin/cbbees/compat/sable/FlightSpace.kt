package de.devin.cbbees.compat.sable

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

interface FlightSpace {
    fun crossings(level: Level, positions: List<BlockPos>): Set<Int>
    fun pose(from: BlockPos, to: BlockPos, t: Double, offset: Vec3, partialTick: Float): FlightPose
    fun heading(from: BlockPos, to: BlockPos): Vec3
}

data class FlightPose(val position: Vec3, val rotation: Quaternionf?)

object FlatFlightSpace : FlightSpace {
    override fun crossings(level: Level, positions: List<BlockPos>): Set<Int> = emptySet()

    override fun pose(from: BlockPos, to: BlockPos, t: Double, offset: Vec3, partialTick: Float) =
        FlightPose(Vec3.atCenterOf(from).lerp(Vec3.atCenterOf(to), t).add(offset), null)

    override fun heading(from: BlockPos, to: BlockPos): Vec3 = Vec3.atCenterOf(to).subtract(Vec3.atCenterOf(from))
}
