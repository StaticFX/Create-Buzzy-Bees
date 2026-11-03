package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.flight.CheckpointAction
import de.devin.cbbees.content.bee.flight.FlightPlanComputer
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.domain.network.BeeNetwork
import java.util.UUID
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack

class GatherFromPort(val items: List<ItemStack>, val providerId: UUID) : CheckpointAction, ReservesStock {

    override fun reserve(beeId: UUID, network: BeeNetwork, tick: Long) {
        network.ports.firstOrNull { it.id == providerId }?.reserve(beeId, items, tick)
    }

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        val network = bee.network()
        if (network == null) {
            CreateBuzzyBeez.debug { "[GatherFromPort] Bee ${bee.id.toString().substring(0, 6)}: no network" }
            return bee.abandonTask()
        }

        val targetPort = network.ports.firstOrNull { it.id == providerId }
        CreateBuzzyBeez.debug { "[GatherFromPort] Bee ${bee.id.toString().substring(0, 6)} arrived, gathering ${items.size} item type(s) from ${targetPort?.javaClass?.simpleName ?: "fallback"}" }
        val nextStop = bee.currentTask?.let { it.getRemainingTasks().firstOrNull()?.targetPos ?: it.targetPosition }
        var allGathered = true
        items.forEach { item ->
            if (bee.isInventoryFull()) {
                CreateBuzzyBeez.debug { "[GatherFromPort] Bee inventory full" }
                return@forEach
            }
            // Use the planned provider if it still has the item, otherwise fall back to network scan
            val provider = if (targetPort != null && targetPort.hasItemStack(item)) {
                targetPort
            } else {
                network.findAvailableProvider(item.copyWithCount(1), bee.id, bee.blockPosition(), nextStop)
            }

            if (provider == null) {
                CreateBuzzyBeez.debug { "[GatherFromPort] No reachable provider for ${item.hoverName.string}" }
                allGathered = false
                return@forEach
            }

            if (provider.hasItemStack(item) && provider.removeItemStack(item)) {
                val remainder = bee.addToInventory(item.copy())
                if (!remainder.isEmpty) provider.addItemStack(remainder)
                bee.consumeSpring(CBBeesConfig.springDrainPickup.get())
                CreateBuzzyBeez.debug { "[GatherFromPort] Gathered ${item.hoverName.string} x${item.count} from ${provider.javaClass.simpleName}" }
            } else {
                CreateBuzzyBeez.debug { "[GatherFromPort] Provider ${provider.javaClass.simpleName} hasItemStack=false for ${item.hoverName.string}" }
                allGathered = false
            }
        }

        // Release the reservation regardless of outcome — items are now in the bee's inventory or gone
        targetPort?.releaseReservation(bee.id)

        if (allGathered) {
            CreateBuzzyBeez.debug { "[GatherFromPort] All items gathered successfully" }
            return true
        }

        // Items unavailable — try to replan from current position with a different provider.
        val newPlan = FlightPlanComputer.replanFrom(bee, bee.currentTask, bee.network(), level)
        if (newPlan != null) {
            CreateBuzzyBeez.debug { "[GatherFromPort] Replanning flight for bee ${bee.id.toString().substring(0, 6)}" }
            ServerBeeManager.reserveStock(bee, newPlan, network, gameTime)
            bee.flightPlan = newPlan
            bee.currentCheckpointIndex = 0
            ServerBeeManager.broadcastFlightPlan(bee, newPlan, clientStartIndex = 0)
            return true // advance past this checkpoint — the new plan starts fresh
        }

        CreateBuzzyBeez.debug { "[GatherFromPort] Gather FAILED, no alternative provider — releasing batch" }
        return bee.abandonTask()
    }
}
