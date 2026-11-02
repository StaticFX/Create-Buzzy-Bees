package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.flight.CheckpointAction
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.domain.network.BeeNetwork
import java.util.UUID
import net.minecraft.server.level.ServerLevel
import net.neoforged.neoforge.fluids.FluidStack

/**
 * Pick up fluid from the Dock Port chosen by the flight planner, or any other port that has it.
 *
 * @param fluid the fluid this bee needs to pick up
 * @param portId the UUID of the Dock Port selected by the flight planner
 * @see CheckpointAction
 */
class GatherFluidFromDock(val fluid: FluidStack, val portId: UUID) : CheckpointAction, ReservesStock {

    override fun reserve(beeId: UUID, network: BeeNetwork, tick: Long) {
        network.dockPorts.firstOrNull { it.id == portId }?.reserve(beeId, listOf(fluid), tick)
    }

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        val network = bee.network() ?: return bee.abandonTask()

        if (!FluidStack.isSameFluidSameComponents(bee.carriedFluid, fluid)) bee.returnCarriedFluid()

        val planned = network.dockPorts.firstOrNull { it.id == portId }
        val port = planned?.takeIf { it.hasFluid(fluid) } ?: network.findFluidProvider(fluid, bee.id)
        val drained = port != null && port.drainFluid(fluid)
        planned?.releaseReservation(bee.id)
        if (drained) {
            bee.carriedFluid = fluid.copyWithAmount(bee.carriedFluid.amount + fluid.amount)
            bee.consumeSpring(CBBeesConfig.springDrainPickup.get())
            return true
        }

        return bee.abandonTask()
    }
}
