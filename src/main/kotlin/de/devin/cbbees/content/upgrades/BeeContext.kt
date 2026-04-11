package de.devin.cbbees.content.upgrades

import de.devin.cbbees.config.CBBeesConfig

/**
 * Data class representing the calculated stats for construction bees
 * based on the installed upgrades in a backpack.
 */
data class BeeContext(
    var speedMultiplier: Double = 1.0,
    var carryCapacity: Int = 1,
    var workRange: Double = CBBeesConfig.defaultWorkRange.get(),
    var maxActiveBees: Int = CBBeesConfig.defaultMaxActiveBees.get(),
    var silkTouchEnabled: Boolean = false,
    var dropItemsEnabled: Boolean = false,
    var breakSpeedMultiplier: Double = 1.0,
    /** Maximum number of bees this source can contribute to a single job */
    var maxContributedBees: Int = CBBeesConfig.defaultMaxActiveBees.get(),
    var fuelConsumptionMultiplier: Double = 1.0,
    /** Higher RPM → tighter wound spring → less drain per action */
    var springEfficiency: Double = 1.0,
    /** Bonus honey capacity from upgrades */
    var honeyCapacityBonus: Int = 0,
    /** Whether the drone view ability is available */
    var droneViewAvailable: Boolean = false,
    /** Maximum range the drone can fly from the player */
    var droneRange: Double = CBBeesConfig.droneBaseRange.get()
)
