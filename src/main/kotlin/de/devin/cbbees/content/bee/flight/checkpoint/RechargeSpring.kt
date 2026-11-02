package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.content.bee.flight.CheckpointAction
import de.devin.cbbees.content.bee.server.ServerBeeData
import net.minecraft.server.level.ServerLevel

class RechargeSpring : CheckpointAction {
    private var finishTick: Long = -1

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        if (finishTick < 0) {
            val hive = bee.hiveInstance ?: return true // skip if no hive
            val ctx = bee.getBeeContext()
            finishTick = gameTime + hive.rechargeSpring(ctx)
        }
        if (gameTime >= finishTick) {
            bee.springTension = 1.0f
            finishTick = -1
            return true
        }
        return false
    }
}
