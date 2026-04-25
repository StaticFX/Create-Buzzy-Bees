package de.devin.cbbees.content.bee.brain.behavior

import de.devin.cbbees.content.bee.BeePathNavigation
import de.devin.cbbees.content.bee.MechanicalBeelike
import de.devin.cbbees.content.bee.debug.BeeDebug
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.phys.Vec3

/**
 * Detects when a bee is stuck by checking whether it makes progress toward
 * its walk target. Wiggling around without getting closer counts as stuck.
 *
 * Every [CHECK_INTERVAL] ticks, we snapshot the distance to the target.
 * If the bee hasn't closed at least [MIN_PROGRESS] blocks in that window,
 * we increment a fail counter. [MAX_FAILS] consecutive failures triggers
 * a teleport to the target.
 *
 * Works with both [de.devin.cbbees.content.bee.MechanicalBeeEntity] and
 * [de.devin.cbbees.content.bee.MechanicalBumbleBeeEntity].
 */
class StuckSafetyBehavior : Behavior<PathfinderMob>(
    mapOf(
        MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_PRESENT,
    )
) {
    companion object {
        private const val CHECK_INTERVAL = 20
        private const val MIN_PROGRESS = 1.5
        private const val MAX_FAILS = 3
    }

    private var lastDistanceToTarget = Double.MAX_VALUE
    private var ticksSinceCheck = 0
    private var failedChecks = 0
    private var lastTargetPos: Vec3 = Vec3.ZERO

    override fun checkExtraStartConditions(level: ServerLevel, owner: PathfinderMob): Boolean {
        val walkTarget = owner.brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) ?: return false
        val targetPos = Vec3.atCenterOf(walkTarget.target.currentBlockPosition())

        if (targetPos.distanceToSqr(lastTargetPos) > 1.0) {
            lastTargetPos = targetPos
            lastDistanceToTarget = owner.position().distanceTo(targetPos)
            ticksSinceCheck = 0
            failedChecks = 0
            return false
        }

        ticksSinceCheck++
        if (ticksSinceCheck < CHECK_INTERVAL) return false

        val currentDist = owner.position().distanceTo(targetPos)
        val progress = lastDistanceToTarget - currentDist

        if (progress < MIN_PROGRESS) {
            failedChecks++
            // Try rerouting before escalating to teleport
            if (failedChecks < MAX_FAILS) {
                owner.navigation.recomputePath()
            }
        } else {
            failedChecks = 0
        }

        lastDistanceToTarget = currentDist
        ticksSinceCheck = 0

        return failedChecks >= MAX_FAILS
    }

    override fun start(level: ServerLevel, entity: PathfinderMob, gameTime: Long) {
        val walkTarget = entity.brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) ?: return
        val targetPos = walkTarget.target.currentBlockPosition()

        val bee = entity as? MechanicalBeelike
        if (bee != null) {
            BeeDebug.log(bee, "Stuck! Teleporting to target at $targetPos")
        }

        val safeY = findSafeY(level, targetPos)
        entity.teleportTo(targetPos.x + 0.5, safeY, targetPos.z + 0.5)

        failedChecks = 0
        ticksSinceCheck = 0
        lastDistanceToTarget = Double.MAX_VALUE
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        entity.brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
    }

    /**
     * Finds a Y coordinate above [targetPos] that isn't inside a solid block.
     * Scans up to 4 blocks above the target; falls back to the bee-safe default of +1.
     */
    private fun findSafeY(level: ServerLevel, targetPos: BlockPos): Double {
        for (dy in 1..4) {
            val checkPos = targetPos.above(dy)
            if (!level.getBlockState(checkPos).isSuffocating(level, checkPos)) {
                return checkPos.y.toDouble()
            }
        }
        return targetPos.y + 1.0
    }
}
