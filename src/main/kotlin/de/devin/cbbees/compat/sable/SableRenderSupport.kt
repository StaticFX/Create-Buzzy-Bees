package de.devin.cbbees.compat.sable

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.core.Vec3i
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3dc
import java.util.UUID

/**
 * Optional bridge for Sable coordinate-space checks and client rendering support.
 *
 * This file intentionally uses reflection only. Buzzy Bees can still run without Sable installed,
 * and the schematic/deployer/planner logic remains unchanged from 1.3.3.
 */
object SableRenderSupport {
    private val sableClass: Class<*>? by lazy {
        runCatching { Class.forName("dev.ryanhcode.sable.Sable") }.getOrNull()
    }

    private val helperInstance: Any? by lazy {
        runCatching { sableClass?.getField("HELPER")?.get(null) }.getOrNull()
    }

    private val helperClass: Class<*>? by lazy { helperInstance?.javaClass }

    private val projectOutOfSubLevelMethod by lazy {
        runCatching {
            helperClass?.getMethod(
                "projectOutOfSubLevel",
                Level::class.java,
                Position::class.java
            )
        }.getOrNull()
    }

    private val distanceSquaredWithSubLevelsMethod by lazy {
        runCatching {
            helperClass?.getMethod(
                "distanceSquaredWithSubLevels",
                Level::class.java,
                Position::class.java,
                Position::class.java
            )
        }.getOrNull()
    }

    private val getContainingBlockMethod by lazy {
        runCatching {
            helperClass?.getMethod(
                "getContaining",
                Level::class.java,
                Vec3i::class.java
            )
        }.getOrNull() ?: helperClass?.methods?.firstOrNull { method ->
            method.name == "getContaining" &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == Level::class.java &&
                    method.parameterTypes[1].isAssignableFrom(BlockPos::class.java)
        }
    }

    fun projectOutOfSubLevel(level: Level, pos: Position): Vec3? {
        val helper = helperInstance ?: return null
        val method = projectOutOfSubLevelMethod ?: return null
        return runCatching { method.invoke(helper, level, pos) as? Vec3 }.getOrNull()
    }

    fun distanceSquaredWithSubLevels(level: Level, posA: Position, posB: Position): Double? {
        val helper = helperInstance ?: return null
        val method = distanceSquaredWithSubLevelsMethod ?: return null
        return runCatching { method.invoke(helper, level, posA, posB) as? Double }.getOrNull()
    }

    /**
     * Returns the stable UUID of the Sable sub-level containing [pos], or null when
     * the position is in the normal world (or Sable is not installed).
     */
    fun subLevelId(level: Level, pos: BlockPos): UUID? {
        val containing = getContaining(level, pos) ?: return null
        return uniqueId(containing)
    }

    /**
     * True when both positions belong to the normal world or to the exact same
     * loaded Sable sub-level. A world position and a sub-level plot position are
     * never treated as the same coordinate space.
     */
    fun isSameCoordinateSpace(level: Level, a: BlockPos, b: BlockPos): Boolean {
        val aContainer = getContaining(level, a)
        val bContainer = getContaining(level, b)

        if (aContainer == null || bContainer == null) {
            return aContainer == null && bContainer == null
        }

        val aId = uniqueId(aContainer)
        val bId = uniqueId(bContainer)
        return if (aId != null && bId != null) aId == bId else aContainer === bContainer
    }

    /**
     * Preserves Buzzy Bees' horizontal square work-range rule after projecting
     * world and Sable positions into the same visible/global coordinate space.
     */
    fun isWithinHorizontalWorkRange(
        level: Level,
        origin: BlockPos,
        target: BlockPos,
        range: Double
    ): Boolean {
        val originGlobal = projectOutOfSubLevel(level, Vec3.atCenterOf(origin))
            ?: Vec3.atCenterOf(origin)
        val targetGlobal = projectOutOfSubLevel(level, Vec3.atCenterOf(target))
            ?: Vec3.atCenterOf(target)

        return kotlin.math.abs(targetGlobal.x - originGlobal.x) <= range &&
                kotlin.math.abs(targetGlobal.z - originGlobal.z) <= range
    }

    /** Sable-aware distance used only to order candidate hives and networks. */
    fun dispatchDistanceSquared(level: Level, a: BlockPos, b: BlockPos): Double {
        return distanceSquaredWithSubLevels(level, Vec3.atCenterOf(a), Vec3.atCenterOf(b))
            ?: a.distSqr(b)
    }

    fun applyRenderTransform(poseStack: PoseStack, level: Level, samplePos: BlockPos, partialTicks: Float): Boolean {
        val matrix = getRenderMatrix(level, samplePos, partialTicks) ?: return false
        poseStack.mulPose(matrix)
        return true
    }

    fun hasRenderTransform(level: Level, samplePos: BlockPos): Boolean =
        getRenderPose(level, samplePos, 0f) != null

    fun hasProjection(level: Level, samplePos: BlockPos): Boolean =
        projectOutOfSubLevel(level, Vec3.atLowerCornerOf(samplePos)) != null

    /**
     * Maps local schematic coordinates directly into the visible world using
     * the same Sable projection used for rendered bees.
     */
    fun applyProjectedLocalTransform(poseStack: PoseStack, level: Level, anchor: BlockPos): Boolean {
        val origin = Vec3.atLowerCornerOf(anchor)
        val projectedOrigin = projectOutOfSubLevel(level, origin) ?: return false
        val projectedX = projectOutOfSubLevel(level, origin.add(1.0, 0.0, 0.0)) ?: return false
        val projectedY = projectOutOfSubLevel(level, origin.add(0.0, 1.0, 0.0)) ?: return false
        val projectedZ = projectOutOfSubLevel(level, origin.add(0.0, 0.0, 1.0)) ?: return false

        val xAxis = projectedX.subtract(projectedOrigin)
        val yAxis = projectedY.subtract(projectedOrigin)
        val zAxis = projectedZ.subtract(projectedOrigin)
        val matrix = Matrix4f()
        matrix.m00(xAxis.x.toFloat()).m01(xAxis.y.toFloat()).m02(xAxis.z.toFloat())
        matrix.m10(yAxis.x.toFloat()).m11(yAxis.y.toFloat()).m12(yAxis.z.toFloat())
        matrix.m20(zAxis.x.toFloat()).m21(zAxis.y.toFloat()).m22(zAxis.z.toFloat())
        matrix.m30(projectedOrigin.x.toFloat())
            .m31(projectedOrigin.y.toFloat())
            .m32(projectedOrigin.z.toFloat())
        poseStack.mulPose(matrix)
        return true
    }

    /**
     * Projects an AABB with the exact anchor-basis transform used by
     * [applyProjectedLocalTransform]. Unlike projecting every corner through
     * Sable independently, this still works when an AABB edge lies exactly on
     * or slightly beyond a sub-level boundary.
     */
    fun projectAabbFromAnchor(level: Level, bounds: AABB, anchor: BlockPos): AABB? {
        val origin = Vec3.atLowerCornerOf(anchor)
        val projectedOrigin = projectOutOfSubLevel(level, origin) ?: return null
        val projectedX = projectOutOfSubLevel(level, origin.add(1.0, 0.0, 0.0)) ?: return null
        val projectedY = projectOutOfSubLevel(level, origin.add(0.0, 1.0, 0.0)) ?: return null
        val projectedZ = projectOutOfSubLevel(level, origin.add(0.0, 0.0, 1.0)) ?: return null

        val xAxis = projectedX.subtract(projectedOrigin)
        val yAxis = projectedY.subtract(projectedOrigin)
        val zAxis = projectedZ.subtract(projectedOrigin)

        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY

        for (x in listOf(bounds.minX, bounds.maxX)) {
            for (y in listOf(bounds.minY, bounds.maxY)) {
                for (z in listOf(bounds.minZ, bounds.maxZ)) {
                    val localX = x - anchor.x
                    val localY = y - anchor.y
                    val localZ = z - anchor.z
                    val projected = projectedOrigin
                        .add(xAxis.scale(localX))
                        .add(yAxis.scale(localY))
                        .add(zAxis.scale(localZ))

                    minX = minOf(minX, projected.x)
                    minY = minOf(minY, projected.y)
                    minZ = minOf(minZ, projected.z)
                    maxX = maxOf(maxX, projected.x)
                    maxY = maxOf(maxY, projected.y)
                    maxZ = maxOf(maxZ, projected.z)
                }
            }
        }

        return AABB(minX, minY, minZ, maxX, maxY, maxZ)
    }

    fun projectAabbOutOfSubLevel(level: Level, bounds: AABB): AABB? {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY

        for (x in listOf(bounds.minX, bounds.maxX)) {
            for (y in listOf(bounds.minY, bounds.maxY)) {
                for (z in listOf(bounds.minZ, bounds.maxZ)) {
                    val projected = projectOutOfSubLevel(level, Vec3(x, y, z)) ?: return null
                    minX = minOf(minX, projected.x)
                    minY = minOf(minY, projected.y)
                    minZ = minOf(minZ, projected.z)
                    maxX = maxOf(maxX, projected.x)
                    maxY = maxOf(maxY, projected.y)
                    maxZ = maxOf(maxZ, projected.z)
                }
            }
        }

        return AABB(minX, minY, minZ, maxX, maxY, maxZ)
    }

    fun projectAabb(level: Level, bounds: AABB, samplePos: BlockPos, partialTicks: Float): AABB? {
        val pose = getRenderPose(level, samplePos, partialTicks) ?: return null
        val transform = transformPositionMethod(pose) ?: return null

        return runCatching {
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var minZ = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            var maxZ = Double.NEGATIVE_INFINITY

            for (x in listOf(bounds.minX, bounds.maxX)) {
                for (y in listOf(bounds.minY, bounds.maxY)) {
                    for (z in listOf(bounds.minZ, bounds.maxZ)) {
                        val dest = Vector3d()
                        transform.invoke(pose, Vector3d(x, y, z), dest)
                        minX = minOf(minX, dest.x)
                        minY = minOf(minY, dest.y)
                        minZ = minOf(minZ, dest.z)
                        maxX = maxOf(maxX, dest.x)
                        maxY = maxOf(maxY, dest.y)
                        maxZ = maxOf(maxZ, dest.z)
                    }
                }
            }

            AABB(minX, minY, minZ, maxX, maxY, maxZ)
        }.getOrNull()
    }

    private fun getRenderMatrix(level: Level, samplePos: BlockPos, partialTicks: Float): Matrix4f? {
        val pose = getRenderPose(level, samplePos, partialTicks) ?: return null
        return runCatching {
            val matrix = Matrix4d()
            val method = pose.javaClass.methods.firstOrNull { m ->
                m.name == "bakeIntoMatrix" &&
                        m.parameterTypes.size == 1 &&
                        m.parameterTypes[0].name == "org.joml.Matrix4d"
            } ?: return null
            val result = method.invoke(pose, matrix) as? Matrix4d ?: matrix
            Matrix4f(result)
        }.getOrNull()
    }

    private fun uniqueId(containing: Any): UUID? {
        return runCatching {
            containing.javaClass.methods.firstOrNull { method ->
                method.name == "getUniqueId" && method.parameterTypes.isEmpty()
            }?.invoke(containing) as? UUID
        }.getOrNull()
    }

    private fun getContaining(level: Level, samplePos: BlockPos): Any? {
        val helper = helperInstance ?: return null
        val method = getContainingBlockMethod ?: return null
        return runCatching { method.invoke(helper, level, samplePos) }.getOrNull()
    }

    private fun getRenderPose(level: Level, samplePos: BlockPos, partialTicks: Float): Any? {
        val containing = getContaining(level, samplePos) ?: return null

        return runCatching {
            containing.javaClass.methods.firstOrNull { method ->
                method.name == "renderPose" &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == java.lang.Float.TYPE
            }?.invoke(containing, partialTicks)
                ?: containing.javaClass.methods.firstOrNull { method ->
                    method.name == "renderPose" && method.parameterTypes.isEmpty()
                }?.invoke(containing)
                ?: containing.javaClass.methods.firstOrNull { method ->
                    method.name == "logicalPose" && method.parameterTypes.isEmpty()
                }?.invoke(containing)
        }.getOrNull()
    }

    private fun transformPositionMethod(pose: Any) =
        pose.javaClass.methods.firstOrNull { method ->
            method.name == "transformPosition" &&
                    method.parameterTypes.size == 2 &&
                    Vector3dc::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    Vector3d::class.java.isAssignableFrom(method.parameterTypes[1])
        }
}
