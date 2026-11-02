package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.flight.CheckpointAction
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.domain.task.TransportTask
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity

/**
 * Bumble bee: deposit items at the target transport port.
 *
 * @param task the transport task defining target position
 * @see CheckpointAction
 */
class DepositTransport(private val task: TransportTask) : CheckpointAction {

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        val network = bee.network()
        val port = network?.transportPortsByPos?.get(task.targetPos)?.takeIf { it.isValidRequester() }

        bee.getInventoryContents().forEach { item ->
            if (port != null) {
                val remainder = port.addItemStack(item.copy())
                bee.removeFromInventory(item, item.count)
                bee.consumeSpring(CBBeesConfig.springDrainDeposit.get())
                if (!remainder.isEmpty) {
                    level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, remainder))
                }
            } else {
                bee.removeFromInventory(item, item.count)
                level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, item.copy()))
            }
        }
        return true
    }
}
