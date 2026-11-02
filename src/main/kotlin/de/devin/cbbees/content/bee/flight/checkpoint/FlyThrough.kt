package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.content.bee.flight.CheckpointAction
import de.devin.cbbees.content.bee.server.ServerBeeData
import net.minecraft.server.level.ServerLevel

/**
 * Fly-through waypoint — always advances immediately.
 * Used for spawn points or intermediate routing waypoints.
 *
 * @see CheckpointAction
 */
object FlyThrough : CheckpointAction {
    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long) = true
}
