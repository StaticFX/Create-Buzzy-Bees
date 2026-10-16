package de.devin.cbbees.compat.sable

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

object SubLevelFlightSpace : FlightSpace {

    override fun crossings(level: Level, positions: List<BlockPos>): Set<Int> {
        val spaces = positions.map { CompanionSubLevels.subLevelId(level, it) }
        return (1 until spaces.size).filter { spaces[it - 1] != spaces[it] }.map { it - 1 }.toSet()
    }

    override fun pose(from: BlockPos, to: BlockPos, t: Double, offset: Vec3, partialTick: Float): FlightPose {
        val start = Vec3.atCenterOf(from)
        val end = Vec3.atCenterOf(to)
        val fromFrame = CompanionSubLevels.clientFrame(from, partialTick)
        val toFrame = if (to == from) fromFrame else CompanionSubLevels.clientFrame(to, partialTick)

        if (fromFrame?.id == toFrame?.id) {
            val local = start.lerp(end, t).add(offset)
            return FlightPose(fromFrame?.project(local) ?: local, fromFrame?.rotation)
        }

        val worldStart = fromFrame?.project(start) ?: start
        val worldEnd = toFrame?.project(end) ?: end
        return FlightPose(worldStart.lerp(worldEnd, t).add(offset), null)
    }

    override fun heading(from: BlockPos, to: BlockPos): Vec3 {
        val start = Vec3.atCenterOf(from)
        val end = Vec3.atCenterOf(to)
        val fromFrame = CompanionSubLevels.clientFrame(from, 1f)
        val toFrame = CompanionSubLevels.clientFrame(to, 1f)
        if (fromFrame?.id == toFrame?.id) return end.subtract(start)
        return (toFrame?.project(end) ?: end).subtract(fromFrame?.project(start) ?: start)
    }
}
