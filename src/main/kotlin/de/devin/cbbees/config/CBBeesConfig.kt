package de.devin.cbbees.config

import net.neoforged.neoforge.common.ModConfigSpec

object CBBeesConfig {
    val SPEC: ModConfigSpec

    // Features
    val enableExternalBrowser: ModConfigSpec.BooleanValue

    val maxBeesPerHive: ModConfigSpec.IntValue
    val minActiveBeesAtRpm: ModConfigSpec.IntValue
    val beePickupItems: ModConfigSpec.BooleanValue

    // Beehive RPM scaling
    val hiveBaseRange: ModConfigSpec.IntValue
    val hiveRangePerRpm: ModConfigSpec.DoubleValue
    val hiveRpmSpeedDivisor: ModConfigSpec.DoubleValue
    val hiveRpmBeeDivisor: ModConfigSpec.DoubleValue

    // Bee defaults
    val defaultMaxActiveBees: ModConfigSpec.IntValue
    val defaultWorkRange: ModConfigSpec.DoubleValue

    // Upgrade values
    val rapidWingsSpeedBonus: ModConfigSpec.DoubleValue
    val swarmIntelligenceBeeBonus: ModConfigSpec.IntValue
    val honeyEfficiencyBreakSpeedReduction: ModConfigSpec.DoubleValue
    val honeyEfficiencyCarryBonus: ModConfigSpec.IntValue
    val honeyEfficiencyFuelReduction: ModConfigSpec.DoubleValue
    val honeyTankCapacityBonus: ModConfigSpec.IntValue

    // Drone view settings
    val droneBaseRange: ModConfigSpec.DoubleValue
    val droneRangeBonus: ModConfigSpec.DoubleValue
    val droneMoveSpeed: ModConfigSpec.DoubleValue

    // Spring (clockwork) settings
    val springDrainPlace: ModConfigSpec.DoubleValue
    val springDrainBreak: ModConfigSpec.DoubleValue
    val springDrainFlight: ModConfigSpec.DoubleValue
    val springDrainPickup: ModConfigSpec.DoubleValue
    val springDrainDeposit: ModConfigSpec.DoubleValue
    val springRechargeTicks: ModConfigSpec.IntValue

    // Performance settings
    val maxBlockOperationsPerTick: ModConfigSpec.IntValue
    val taskGenerationBlocksPerTick: ModConfigSpec.IntValue
    val maxCheckpointsPerTick: ModConfigSpec.IntValue
    val redispatchInterval: ModConfigSpec.IntValue

    // Honey fuel settings
    val portableHoneyPerRewind: ModConfigSpec.IntValue
    val portableMaxHoney: ModConfigSpec.IntValue
    val honeyBottleFuelValue: ModConfigSpec.IntValue
    val honeycombFuelValue: ModConfigSpec.IntValue
    val honeyBlockFuelValue: ModConfigSpec.IntValue

    init {
        val builder = ModConfigSpec.Builder()

        builder.comment("Feature Toggles")
            .push("features")

        enableExternalBrowser = builder
            .comment("Enable the 'Browse Online' button and createmod.com connectivity. Requires restart.")
            .define("enableExternalBrowser", true)

        builder.pop()

        builder.comment("Mechanical Beehive Settings")
            .push("beehive")

        maxBeesPerHive = builder
            .comment("Hard cap on active bees per hive, after RPM and upgrade scaling. Even with max RPM and upgrades, a hive will never exceed this.")
            .defineInRange("maxBeesPerHive", 16, 1, 64)

        minActiveBeesAtRpm = builder
            .comment("Minimum active bees when the hive has any RPM. Ensures even slow shafts deploy at least this many bees.")
            .defineInRange("minActiveBeesAtRpm", 1, 0, 64)

        hiveBaseRange = builder
            .comment("Base work range (blocks) of a beehive at minimum RPM, before RPM scaling is applied.")
            .defineInRange("hiveBaseRange", 1, 0, 128)

        hiveRangePerRpm = builder
            .comment("Work range added per RPM. Formula: range = baseRange + RPM * this. Example: 64 RPM * 0.25 = 16 + 1 base = 17 blocks.")
            .defineInRange("hiveRangePerRpm", 0.25, 0.01, 10.0)

        hiveRpmSpeedDivisor = builder
            .comment("RPM divisor for bee flight speed and spring efficiency. Formula: multiplier = 1 + RPM / this. Higher = slower scaling.")
            .defineInRange("hiveRpmSpeedDivisor", 256.0, 1.0, 1024.0)

        hiveRpmBeeDivisor = builder
            .comment("RPM divisor for max active bees. Formula: extra bees = RPM / this. Example: 64 RPM / 8 = 8 extra bees.")
            .defineInRange("hiveRpmBeeDivisor", 8.0, 1.0, 256.0)

        builder.pop()

        builder.comment("Bee Behavior Settings")
            .push("behavior")

        beePickupItems = builder
            .comment("Whether bees pick up item drops when breaking blocks")
            .define("beePickupItems", true)

        defaultMaxActiveBees = builder
            .comment("Default max active bees before RPM and upgrade scaling. This is the base value that RPM bonuses are added to.")
            .defineInRange("defaultMaxActiveBees", 4, 1, 64)

        defaultWorkRange = builder
            .comment("Default work range (blocks) for portable beehives and other non-hive bee sources.")
            .defineInRange("defaultWorkRange", 32.0, 1.0, 256.0)

        builder.pop()

        builder.comment("Upgrade Effect Settings — controls how strong each upgrade is")
            .push("upgrades")

        rapidWingsSpeedBonus = builder
            .comment("Speed multiplier bonus per Rapid Wings upgrade (default 0.25 = +25% per upgrade)")
            .defineInRange("rapidWingsSpeedBonus", 0.25, 0.01, 2.0)

        swarmIntelligenceBeeBonus = builder
            .comment("Extra concurrent bees per Swarm Intelligence upgrade")
            .defineInRange("swarmIntelligenceBeeBonus", 8, 1, 16)

        honeyEfficiencyBreakSpeedReduction = builder
            .comment("Break speed multiplier reduction per Honey Efficiency upgrade (0.25 = 25% faster)")
            .defineInRange("honeyEfficiencyBreakSpeedReduction", 0.25, 0.01, 1.0)

        honeyEfficiencyCarryBonus = builder
            .comment("Extra carry capacity per Honey Efficiency upgrade")
            .defineInRange("honeyEfficiencyCarryBonus", 2, 1, 16)

        honeyEfficiencyFuelReduction = builder
            .comment("Fuel consumption reduction per Honey Efficiency upgrade (0.15 = 15% less fuel)")
            .defineInRange("honeyEfficiencyFuelReduction", 0.15, 0.01, 1.0)

        honeyTankCapacityBonus = builder
            .comment("Extra honey capacity per Honey Tank upgrade")
            .defineInRange("honeyTankCapacityBonus", 200, 50, 5000)

        builder.pop()

        builder.comment("Drone View Settings — controls drone camera behavior")
            .push("drone_view")

        droneBaseRange = builder
            .comment("Base range (blocks) the drone can move from the player without upgrades")
            .defineInRange("droneBaseRange", 32.0, 8.0, 256.0)

        droneRangeBonus = builder
            .comment("Extra range (blocks) per Drone Range upgrade")
            .defineInRange("droneRangeBonus", 16.0, 4.0, 128.0)

        droneMoveSpeed = builder
            .comment("Drone movement speed in blocks per tick when controlled by WASD")
            .defineInRange("droneMoveSpeed", 1.5, 0.1, 5.0)

        builder.pop()

        builder.comment("Clockwork Spring Settings — controls per-action energy drain on bees")
            .push("spring")

        springDrainPlace = builder
            .comment("Spring tension drained per block placement (~50 placements per full spring)")
            .defineInRange("springDrainPlace", 0.02, 0.0, 1.0)

        springDrainBreak = builder
            .comment("Spring tension drained per block break (~66 breaks per full spring)")
            .defineInRange("springDrainBreak", 0.015, 0.0, 1.0)

        springDrainFlight = builder
            .comment("Spring tension drained per tick of flight (~10000 ticks per full spring)")
            .defineInRange("springDrainFlight", 0.0001, 0.0, 1.0)

        springDrainPickup = builder
            .comment("Spring tension drained per item pickup from a logistics port (construction and transport bees).")
            .defineInRange("springDrainPickup", 0.01, 0.0, 1.0)

        springDrainDeposit = builder
            .comment("Spring tension drained per item deposit to a logistics port (construction and transport bees).")
            .defineInRange("springDrainDeposit", 0.01, 0.0, 1.0)

        springRechargeTicks = builder
            .comment("Base ticks to recharge a fully depleted spring at the hive (scales with RPM)")
            .defineInRange("springRechargeTicks", 200, 20, 2000)

        builder.pop()

        builder.comment("Honey Fuel Settings — controls fuel consumption for portable beehive")
            .push("honey_fuel")

        portableHoneyPerRewind = builder
            .comment("Honey consumed per full spring rewind in a portable beehive")
            .defineInRange("portableHoneyPerRewind", 6, 1, 1000)

        portableMaxHoney = builder
            .comment("Maximum honey stored in a portable beehive")
            .defineInRange("portableMaxHoney", 400, 100, 10000)

        honeyBottleFuelValue = builder
            .comment("Honey fuel value per honey bottle")
            .defineInRange("honeyBottleFuelValue", 100, 1, 1000)

        honeycombFuelValue = builder
            .comment("Honey fuel value per honeycomb")
            .defineInRange("honeycombFuelValue", 60, 1, 1000)

        honeyBlockFuelValue = builder
            .comment("Honey fuel value per honey block")
            .defineInRange("honeyBlockFuelValue", 400, 1, 10000)

        builder.pop()

        builder.comment("Performance Tuning")
            .push("performance")

        maxBlockOperationsPerTick = builder
            .comment("Maximum number of block place/break operations all bees can perform per server tick. Lower values reduce TPS impact with many bees.")
            .defineInRange("maxBlockOperationsPerTick", 20, 1, 200)

        taskGenerationBlocksPerTick = builder
            .comment("Maximum schematic blocks scanned per server tick when generating build/removal tasks. Lower values reduce server hitches on large schematics; higher values finish calculation faster.")
            .defineInRange("taskGenerationBlocksPerTick", 500, 50, 10000)

        maxCheckpointsPerTick = builder
            .comment("Maximum bee checkpoint actions (block place/break arrivals) processed per server tick. Higher values let more bees work simultaneously but increase per-tick server load.")
            .defineInRange("maxCheckpointsPerTick", 30, 1, 500)

        redispatchInterval = builder
            .comment("How many GlobalJobPool tick cycles between redispatch attempts for pending batches. GlobalJobPool ticks every 10 server ticks, so interval of 4 = every 40 ticks (2 seconds). Lower = faster bee deployment but more server work.")
            .defineInRange("redispatchInterval", 4, 1, 20)

        builder.pop()

        SPEC = builder.build()
    }
}
