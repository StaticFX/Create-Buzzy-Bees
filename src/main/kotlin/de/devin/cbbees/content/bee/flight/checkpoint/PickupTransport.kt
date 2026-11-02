package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.flight.CheckpointAction
import de.devin.cbbees.content.bee.flight.FlightPlanComputer
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.domain.logistics.TransportPort
import de.devin.cbbees.content.domain.task.TransportTask
import net.minecraft.server.level.ServerLevel

/**
 * Bumble bee: pick up items from the source transport port.
 *
 * Never stalls: if nothing could be picked up (source emptied, port removed, network gone),
 * the bee skips the deposit and is re-planned straight back to its hive, freeing its hive slot.
 *
 * @param task the transport task defining source position and items
 * @see CheckpointAction
 */
class PickupTransport(private val task: TransportTask) : CheckpointAction {

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        val port = bee.network()?.transportPortsByPos?.get(task.sourcePos)?.takeIf { it.isValidProvider() }
        port?.releaseReservation(bee.id)

        if (port == null || !pickUp(bee, port)) {
            val homePlan = FlightPlanComputer.forReturnHome(bee, level)
            bee.flightPlan = homePlan
            bee.currentCheckpointIndex = 0
            ServerBeeManager.broadcastFlightPlan(bee, homePlan, clientStartIndex = 0)
        }
        return true
    }

    private fun pickUp(bee: ServerBeeData, port: TransportPort): Boolean {
        var pickedUp = false
        task.items.forEach { item ->
            if (bee.isInventoryFull()) return@forEach
            if (port.hasItemStack(item) && port.removeItemStack(item)) {
                val rem = bee.addToInventory(item.copy())
                if (!rem.isEmpty) port.addItemStack(rem)
                pickedUp = true
                bee.consumeSpring(CBBeesConfig.springDrainPickup.get())
            }
        }
        return pickedUp
    }
}
