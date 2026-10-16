package de.devin.cbbees.compat.sable

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Quaternionf
import org.joml.Vector3d
import java.util.UUID

class SubLevelFrame(
    val id: UUID,
    private val position: Vector3d,
    private val orientation: Quaterniond,
    private val rotationPoint: Vector3d,
    private val scale: Vector3d
) {
    val rotation: Quaternionf get() = Quaternionf(orientation)

    fun project(local: Vec3): Vec3 {
        val world = orientation.transform(Vector3d(local.x, local.y, local.z).sub(rotationPoint).mul(scale)).add(position)
        return Vec3(world.x, world.y, world.z)
    }

    fun unproject(world: Vec3): Vec3 {
        val local = orientation.transformInverse(Vector3d(world.x, world.y, world.z).sub(position)).div(scale).add(rotationPoint)
        return Vec3(local.x, local.y, local.z)
    }

    fun applyTo(poseStack: PoseStack, camera: Vec3): Vec3 {
        poseStack.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z)
        poseStack.mulPose(rotation)
        poseStack.scale(scale.x.toFloat(), scale.y.toFloat(), scale.z.toFloat())
        return Vec3(rotationPoint.x, rotationPoint.y, rotationPoint.z)
    }
}
