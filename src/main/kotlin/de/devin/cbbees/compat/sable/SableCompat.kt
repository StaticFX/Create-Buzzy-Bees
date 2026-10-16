package de.devin.cbbees.compat.sable

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID

object SableCompat {

    private const val COMPANION_CLASS = "dev.ryanhcode.sable.companion.SableCompanion"

    val isLoaded: Boolean = isPresent(COMPANION_CLASS)

    val provider: SubLevelProvider = if (isLoaded) CompanionSubLevels else NoSubLevels

    val flight: FlightSpace = if (isLoaded) SubLevelFlightSpace else FlatFlightSpace

    val render: RenderSpace = if (isLoaded) SubLevelRenderSpace else FlatRenderSpace

    private fun isPresent(className: String) = runCatching {
        Class.forName(className, false, SableCompat::class.java.classLoader)
    }.isSuccess
}

fun Level.subLevelId(pos: BlockPos): UUID? = SableCompat.provider.subLevelId(this, pos)

fun Level.toWorld(pos: Vec3): Vec3 = SableCompat.provider.toWorld(this, pos)

fun Level.toWorld(pos: BlockPos): Vec3 = toWorld(Vec3.atCenterOf(pos))

fun Level.worldDistanceSqr(a: Vec3, b: Vec3): Double = SableCompat.provider.worldDistanceSqr(this, a, b)

fun Level.worldDistanceSqr(a: BlockPos, b: BlockPos): Double =
    worldDistanceSqr(Vec3.atCenterOf(a), Vec3.atCenterOf(b))
