package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager

/** Releases the bee's batch without retry and signals [ServerBeeManager] to return the bee to its hive. */
internal fun ServerBeeData.abandonTask(): Boolean {
    currentTask?.releaseWithoutRetry()
    springTension = -9999f
    return false
}
