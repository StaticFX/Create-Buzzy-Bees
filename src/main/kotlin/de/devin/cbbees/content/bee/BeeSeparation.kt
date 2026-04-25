package de.devin.cbbees.content.bee

import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.Vec3

/**
 * Provides visual spread for mechanical bees so they don't all fly on the exact same path.
 *
 * Instead of a runtime separation force (which fights pathfinding and causes bees to
 * block each other), each bee gets a small deterministic positional offset based on its
 * entity ID. This offset is applied once per tick as a gentle nudge, spreading bees out
 * without interfering with navigation.
 */
object BeeSeparation {

    /**
     * Applies a small per-bee flight offset so bees in the same area spread out visually.
     *
     * The offset is deterministic per entity (based on ID), so each bee consistently
     * drifts to a slightly different position without oscillation or path-fighting.
     * Only applies a gentle horizontal nudge — no vertical component to avoid
     * interfering with altitude-based pathfinding.
     */
    fun applyFlightOffset(bee: Mob) {
        val id = bee.id
        // Spread bees across a small area using their entity ID
        val angle = (id * 2654435761L and 0xFFFF).toDouble() / 0xFFFF * Math.PI * 2
        val offsetX = Math.cos(angle) * 0.006
        val offsetZ = Math.sin(angle) * 0.006

        bee.deltaMovement = bee.deltaMovement.add(offsetX, 0.0, offsetZ)
    }

    /**
     * Returns a small random offset for spawning bees around a hive.
     */
    fun spawnOffset(random: net.minecraft.util.RandomSource): Vec3 {
        return Vec3(
            (random.nextDouble() - 0.5) * 0.8,
            random.nextDouble() * 0.3,
            (random.nextDouble() - 0.5) * 0.8
        )
    }
}
