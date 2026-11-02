package de.devin.cbbees.content.bee

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
