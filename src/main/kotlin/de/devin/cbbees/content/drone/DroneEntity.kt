package de.devin.cbbees.content.drone

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import java.util.UUID
import kotlin.math.sqrt

/**
 * Invisible camera the player looks through while drone view is active.
 */
class DroneEntity(entityType: EntityType<out DroneEntity>, level: Level) : Entity(entityType, level) {

    companion object {
        const val ALTITUDE: Double = 25.0
        const val MAX_SPEED: Double = 2.0
    }

    var ownerId: UUID? = null
    var maxRange: Double = 32.0
    private var offsetX: Double = 0.0
    private var offsetZ: Double = 0.0

    init {
        isNoGravity = true
        noPhysics = true
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return

        val owner = ownerId?.let(level()::getPlayerByUUID) ?: run {
            discard()
            return
        }

        val targetX = owner.x + offsetX
        val targetZ = owner.z + offsetZ
        val target = BlockPos.containing(targetX, 0.0, targetZ)
        val groundY = level().getHeight(Heightmap.Types.MOTION_BLOCKING, target.x, target.z).toDouble()
        val targetY = (groundY + ALTITUDE).coerceAtMost(level().maxBuildHeight.toDouble() - 1.0)

        moveTo(targetX, targetY, targetZ, 0f, 90f)
    }

    fun moveBy(dx: Double, dz: Double) {
        offsetX += dx
        offsetZ += dz

        val distance = sqrt(offsetX * offsetX + offsetZ * offsetZ)
        if (distance > maxRange) {
            offsetX = offsetX / distance * maxRange
            offsetZ = offsetZ / distance * maxRange
        }
    }

    // The client predicts drone movement itself, so server position updates are ignored.
    override fun lerpTo(x: Double, y: Double, z: Double, yRot: Float, xRot: Float, steps: Int) {}

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {}

    override fun readAdditionalSaveData(compound: CompoundTag) {}

    override fun addAdditionalSaveData(compound: CompoundTag) {}
}
