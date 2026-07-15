package de.devin.cbbees.compat

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3dc
import java.lang.reflect.Method

/**
 * Optional Sable Companion bridge.
 *
 * This file intentionally uses reflection instead of a compile-time dependency.
 * When Sable/Sable Companion is not present, every method safely returns null.
 */
object SableCompanionCompat {
    private val companionClass: Class<*>? by lazy {
        try {
            Class.forName("dev.ryanhcode.sable.companion.SableCompanion")
        } catch (_: Throwable) {
            null
        }
    }

    private val instance: Any? by lazy {
        try {
            companionClass?.getField("INSTANCE")?.get(null)
        } catch (_: Throwable) {
            null
        }
    }

    private val getContainingMethod: Method? by lazy {
        try {
            companionClass?.methods?.firstOrNull { method ->
                method.name == "getContaining" &&
                    method.parameterTypes.size == 2 &&
                    Level::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    Vec3i::class.java.isAssignableFrom(method.parameterTypes[1])
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getSubLevel(level: Level, pos: BlockPos): Any? {
        val inst = instance ?: return null
        val method = getContainingMethod ?: return null
        return try {
            method.invoke(inst, level, pos)
        } catch (_: Throwable) {
            null
        }
    }

    private fun getRenderPose(subLevel: Any, partialTicks: Float): Any? {
        return try {
            subLevel.javaClass.methods.firstOrNull { method ->
                method.name == "renderPose" && method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == java.lang.Float.TYPE
            }?.invoke(subLevel, partialTicks)
                ?: subLevel.javaClass.methods.firstOrNull { method ->
                    method.name == "renderPose" && method.parameterTypes.isEmpty()
                }?.invoke(subLevel)
                ?: subLevel.javaClass.methods.firstOrNull { method ->
                    method.name == "logicalPose" && method.parameterTypes.isEmpty()
                }?.invoke(subLevel)
        } catch (_: Throwable) {
            null
        }
    }

    private fun bakePoseMatrix(pose: Any): Matrix4f? {
        return try {
            val matrix = Matrix4d()
            val method = pose.javaClass.methods.firstOrNull { m ->
                m.name == "bakeIntoMatrix" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0].name == "org.joml.Matrix4d"
            } ?: return null
            val result = method.invoke(pose, matrix) as? Matrix4d ?: matrix
            Matrix4f(result)
        } catch (_: Throwable) {
            null
        }
    }

    fun getRenderMatrix(level: Level, pos: BlockPos, partialTicks: Float): Matrix4f? {
        val subLevel = getSubLevel(level, pos) ?: return null
        val pose = getRenderPose(subLevel, partialTicks) ?: return null
        return bakePoseMatrix(pose)
    }

    fun applyRenderTransform(poseStack: PoseStack, level: Level, pos: BlockPos, partialTicks: Float): Boolean {
        val matrix = getRenderMatrix(level, pos, partialTicks) ?: return false
        poseStack.mulPose(matrix)
        return true
    }

    fun projectPosition(level: Level, pos: Vec3, partialTicks: Float): Vec3? {
        return projectPosition(level, pos, BlockPos.containing(pos), partialTicks)
    }

    /**
     * Projects [pos] through the Sable render pose found from [samplePos].
     *
     * This is important for fake/client-only bee rendering: the bee flies through
     * air, so asking Sable what contains the bee position can flicker between a
     * valid sub-level and null. The construction frame/ghost is block based and
     * already has a stable sample position, so bee render can reuse that context.
     */
    fun projectPosition(level: Level, pos: Vec3, samplePos: BlockPos, partialTicks: Float): Vec3? {
        val subLevel = getSubLevel(level, samplePos) ?: return null
        val pose = getRenderPose(subLevel, partialTicks) ?: return null
        return try {
            val src = Vector3d(pos.x, pos.y, pos.z)
            val dest = Vector3d()
            val method = pose.javaClass.methods.firstOrNull { m ->
                m.name == "transformPosition" && m.parameterTypes.size == 2 &&
                    Vector3dc::class.java.isAssignableFrom(m.parameterTypes[0]) &&
                    Vector3d::class.java.isAssignableFrom(m.parameterTypes[1])
            } ?: return null
            method.invoke(pose, src, dest)
            Vec3(dest.x, dest.y, dest.z)
        } catch (_: Throwable) {
            null
        }
    }

    fun projectAabb(level: Level, bounds: AABB, partialTicks: Float): AABB? {
        val samplePos = BlockPos.containing(
            (bounds.minX + bounds.maxX) * 0.5,
            (bounds.minY + bounds.maxY) * 0.5,
            (bounds.minZ + bounds.maxZ) * 0.5
        )
        return projectAabb(level, bounds, samplePos, partialTicks)
    }

    fun projectAabb(level: Level, bounds: AABB, samplePos: BlockPos, partialTicks: Float): AABB? {
        val subLevel = getSubLevel(level, samplePos) ?: return null
        val pose = getRenderPose(subLevel, partialTicks) ?: return null

        try {
            val method = pose.javaClass.methods.firstOrNull { m ->
                m.name == "transformPosition" && m.parameterTypes.size == 2 &&
                    Vector3dc::class.java.isAssignableFrom(m.parameterTypes[0]) &&
                    Vector3d::class.java.isAssignableFrom(m.parameterTypes[1])
            } ?: return null

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
                        method.invoke(pose, Vector3d(x, y, z), dest)
                        minX = minOf(minX, dest.x)
                        minY = minOf(minY, dest.y)
                        minZ = minOf(minZ, dest.z)
                        maxX = maxOf(maxX, dest.x)
                        maxY = maxOf(maxY, dest.y)
                        maxZ = maxOf(maxZ, dest.z)
                    }
                }
            }

            return AABB(minX, minY, minZ, maxX, maxY, maxZ)
        } catch (_: Throwable) {
            return null
        }
    }
}
