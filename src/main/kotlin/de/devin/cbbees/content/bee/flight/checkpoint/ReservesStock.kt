package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.content.bee.flight.CheckpointAction
import de.devin.cbbees.content.domain.network.BeeNetwork
import java.util.UUID

/**
 * Pick up items from a specific logistics port. The [providerId] identifies the port
 * selected during flight planning, ensuring the bee gathers from the intended source.
 * Falls back to a network-wide scan if the original port is no longer available.
 *
 * @param items the items this bee needs to pick up
 * @param providerId the UUID of the logistics port selected by the flight planner
 * @see CheckpointAction
 */
fun interface ReservesStock {
    fun reserve(beeId: UUID, network: BeeNetwork, tick: Long)
}
