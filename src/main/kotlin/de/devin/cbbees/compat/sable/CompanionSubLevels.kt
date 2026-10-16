package de.devin.cbbees.compat.sable

import dev.ryanhcode.sable.companion.SableCompanion
import dev.ryanhcode.sable.companion.math.Pose3dc
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Vector3d
import java.util.UUID

object CompanionSubLevels : SubLevelProvider {

    private val companion: SableCompanion get() = SableCompanion.INSTANCE

    override fun subLevelId(level: Level, pos: BlockPos): UUID? =
        companion.getContaining(level, pos)?.uniqueId

    override fun toWorld(level: Level, pos: Vec3): Vec3 =
        companion.projectOutOfSubLevel(level, pos as Position)

    override fun worldDistanceSqr(level: Level, a: Vec3, b: Vec3): Double =
        companion.distanceSquaredWithSubLevels(level, a, b)

    fun clientFrame(pos: BlockPos, partialTick: Float): SubLevelFrame? =
        companion.getContainingClient(pos)?.let { frameOf(it.uniqueId, it.renderPose(partialTick)) }

    private fun frameOf(id: UUID, pose: Pose3dc) = SubLevelFrame(
        id,
        Vector3d(pose.position()),
        Quaterniond(pose.orientation()),
        Vector3d(pose.rotationPoint()),
        Vector3d(pose.scale())
    )
}
