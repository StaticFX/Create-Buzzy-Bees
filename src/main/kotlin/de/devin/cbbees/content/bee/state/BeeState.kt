package de.devin.cbbees.content.bee.state

/**
 * States for construction bees (MechanicalBeeEntity).
 * Replaces the Brain/Behavior system with O(1) state dispatch.
 */
enum class ConstructionBeeState {
    /** Flying to a logistics port to gather required items. */
    GATHERING,
    /** Flying to the task's target block position. */
    FLYING_TO_TASK,
    /** At the target position, executing the action (place/break). */
    EXECUTING,
    /** Flying back to the hive (no more tasks or spring empty). */
    FLYING_HOME,
    /** At the hive, entering to return the bee item. */
    ENTERING_HIVE,
    /** At the hive, recharging spring tension. */
    RECHARGING,
    /** Flying to a port to drop off excess items. */
    DROPPING_ITEMS,
    /** No hive found, waiting for adoption or drop. */
    ORPHANED,
    /** Portable beehive removed, returning to owner player. */
    RETURNING_TO_OWNER
}

/**
 * States for transport bees (MechanicalBumbleBeeEntity).
 */
enum class TransportBeeState {
    /** Flying to the source port to pick up items. */
    FLYING_TO_SOURCE,
    /** At source port, picking up items. */
    PICKING_UP,
    /** Flying to the target port to deliver items. */
    FLYING_TO_TARGET,
    /** At target port, depositing items. */
    DEPOSITING,
    /** Flying back to the hive. */
    FLYING_HOME,
    /** At the hive, entering. */
    ENTERING_HIVE,
    /** Recharging spring. */
    RECHARGING,
    /** No hive found. */
    ORPHANED
}

/**
 * Tracks stuck-detection state for a bee.
 */
class StuckCheckData {
    var lastDistanceToTarget: Double = Double.MAX_VALUE
    var ticksSinceCheck: Int = 0
    var failedChecks: Int = 0
    var lastTargetX: Double = 0.0
    var lastTargetY: Double = 0.0
    var lastTargetZ: Double = 0.0

    fun reset() {
        lastDistanceToTarget = Double.MAX_VALUE
        ticksSinceCheck = 0
        failedChecks = 0
    }
}
