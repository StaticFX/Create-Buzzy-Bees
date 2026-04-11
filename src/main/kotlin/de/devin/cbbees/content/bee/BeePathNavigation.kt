package de.devin.cbbees.content.bee

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.level.Level
import net.minecraft.world.level.pathfinder.Node
import net.minecraft.world.level.pathfinder.Path

/**
 * Lightweight flight navigation for mechanical bees that bypasses A* pathfinding.
 *
 * Instead of running a 3D A* search, creates straight-line paths and follows them
 * with direct MoveControl targeting. Overrides [tick] completely to avoid vanilla
 * path-following logic that causes oscillation with simple paths.
 *
 * Obstacle avoidance: every [CHECK_INTERVAL] ticks, checks a few blocks ahead
 * and routes vertically over obstructions.
 */
class BeePathNavigation(mob: PathfinderMob, level: Level) : FlyingPathNavigation(mob, level) {

    companion object {
        private const val CHECK_INTERVAL = 5
        private const val ARRIVAL_DISTANCE_SQ = 2.25 // 1.5 blocks
    }

    private var obstructionCheckTick = 0

    /**
     * Creates a straight-line path bypassing A*.
     */
    override fun createPath(target: BlockPos, distance: Int): Path? {
        val startPos = mob.blockPosition()
        val startNode = Node(startPos.x, startPos.y, startPos.z).apply { walkedDistance = 0f; costMalus = 0f }
        val endNode = Node(target.x, target.y, target.z).apply {
            walkedDistance = startPos.distManhattan(target).toFloat(); costMalus = 0f
        }
        return Path(listOf(startNode, endNode), target, true)
    }

    override fun createPath(entity: Entity, distance: Int): Path? {
        return createPath(entity.blockPosition(), distance)
    }

    /**
     * Completely replaces vanilla path-following. Sets MoveControl target directly
     * and handles node advancement + obstruction avoidance.
     */
    override fun tick() {
        val currentPath = this.path
        if (currentPath == null || isDone) return

        // Advance past nodes the bee has already reached
        val nodeIndex = currentPath.nextNodeIndex
        if (nodeIndex >= currentPath.nodeCount) {
            stop()
            return
        }

        val targetNode = currentPath.getNode(nodeIndex)
        val tx = targetNode.x + 0.5
        val ty = targetNode.y.toDouble()
        val tz = targetNode.z + 0.5

        val dx = tx - mob.x
        val dy = ty - mob.y
        val dz = tz - mob.z
        val distSq = dx * dx + dy * dy + dz * dz

        if (distSq < ARRIVAL_DISTANCE_SQ) {
            // Arrived at this node — advance
            currentPath.advance()
            if (currentPath.nextNodeIndex >= currentPath.nodeCount) {
                stop()
                return
            }
            // Target next node
            val next = currentPath.getNode(currentPath.nextNodeIndex)
            mob.moveControl.setWantedPosition(next.x + 0.5, next.y.toDouble(), next.z + 0.5, 1.0)
        } else {
            // Still moving toward current node
            mob.moveControl.setWantedPosition(tx, ty, tz, 1.0)
        }

        // Lightweight obstruction check
        obstructionCheckTick++
        if (obstructionCheckTick >= CHECK_INTERVAL) {
            obstructionCheckTick = 0
            checkAndAvoidObstruction()
        }
    }

    /**
     * Checks a few blocks in the bee's flight direction for solid obstructions.
     * If found, creates a new path that routes above the obstruction.
     */
    private fun checkAndAvoidObstruction() {
        val currentPath = this.path ?: return
        if (currentPath.nextNodeIndex >= currentPath.nodeCount) return

        val targetNode = currentPath.getNode(currentPath.nextNodeIndex)
        val targetPos = BlockPos(targetNode.x, targetNode.y, targetNode.z)
        val beePos = mob.blockPosition()

        // Check if the block at the target is solid
        if (!level.getBlockState(targetPos).getCollisionShape(level, targetPos).isEmpty) {
            // Find clear altitude above
            var clearY = beePos.y + 2
            for (dy in 2..8) {
                val checkPos = beePos.above(dy)
                if (level.getBlockState(checkPos).getCollisionShape(level, checkPos).isEmpty) {
                    clearY = checkPos.y
                    break
                }
            }

            // Get the final target
            val endNode = currentPath.endNode ?: return
            val finalTarget = BlockPos(endNode.x, endNode.y, endNode.z)

            // Create a 3-node path: current → above → final target
            val n0 = Node(beePos.x, beePos.y, beePos.z).apply { walkedDistance = 0f }
            val n1 = Node(beePos.x, clearY, beePos.z).apply { walkedDistance = (clearY - beePos.y).toFloat() }
            val n2 = Node(finalTarget.x, finalTarget.y, finalTarget.z).apply {
                walkedDistance = n1.walkedDistance + beePos.distManhattan(finalTarget).toFloat()
            }
            this.path = Path(listOf(n0, n1, n2), finalTarget, true)
        }
    }
}
