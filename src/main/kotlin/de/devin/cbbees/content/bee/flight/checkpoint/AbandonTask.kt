package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.content.bee.flight.FlightPlanComputer
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager
import net.minecraft.server.level.ServerLevel


internal fun ServerBeeData.abandonTask(level: ServerLevel, gameTime: Long, useRetry: Boolean = false): Boolean {
    currentTask?.let {
        if (useRetry) it.release(
            resetNetwork = false,
            gameTick = gameTime
        ) else it.releaseWithoutRetry()
    }
    currentTask = null
    if (hiveInstance == null) {
        springTension = -9999f
        return false
    }
    ServerBeeManager.redirect(this, FlightPlanComputer.forReturnHome(this, level), gameTime)
    return true
}
