# Configuration

All config options are in the server config file (`serverconfig/cbbees-server.toml`).

## Beehive Settings

| Option | Default | Description |
|--------|---------|-------------|
| `maxBeesPerHive` | 16 | Hard cap on active bees per hive |
| `minActiveBeesAtRpm` | 1 | Minimum bees when hive has any RPM |
| `hiveBaseRange` | 1 | Base work range (blocks) at minimum RPM |
| `hiveRangePerRpm` | 0.25 | Range added per RPM |
| `hiveRpmSpeedDivisor` | 256 | RPM divisor for speed scaling |
| `hiveRpmBeeDivisor` | 8 | RPM divisor for bee count scaling |

## Bee Behavior

| Option | Default | Description |
|--------|---------|-------------|
| `beePickupItems` | true | Whether bees pick up drops after breaking blocks |
| `defaultMaxActiveBees` | 4 | Base max active bees before RPM scaling |
| `defaultWorkRange` | 32 | Default range for portable beehives |

## Clockwork Spring

| Option | Default | Description |
|--------|---------|-------------|
| `springDrainPlace` | 0.02 | Spring drain per block placement |
| `springDrainBreak` | 0.015 | Spring drain per block break |
| `springDrainFlight` | 0.0001 | Spring drain per tick of flight |
| `springDrainPickup` | 0.01 | Spring drain per item pickup |
| `springDrainDeposit` | 0.01 | Spring drain per item deposit |
| `springRechargeTicks` | 200 | Base recharge ticks (scales with RPM) |

## Honey Fuel

| Option | Default | Description |
|--------|---------|-------------|
| `portableHoneyPerRewind` | 6 | Honey consumed per spring rewind |
| `portableMaxHoney` | 400 | Maximum honey in portable beehive |
| `honeyBottleFuelValue` | 100 | Fuel per honey bottle |
| `honeycombFuelValue` | 60 | Fuel per honeycomb |
| `honeyBlockFuelValue` | 400 | Fuel per honey block |

## Upgrade Effects

| Option | Default | Description |
|--------|---------|-------------|
| `rapidWingsSpeedBonus` | 0.25 | Speed bonus per Rapid Wings upgrade |
| `swarmIntelligenceBeeBonus` | 8 | Extra bees per Swarm Intelligence |
| `honeyEfficiencyFuelReduction` | 0.15 | Fuel reduction per Honey Efficiency |
| `honeyTankCapacityBonus` | 200 | Extra capacity per Honey Tank |

## Performance

| Option | Default | Description |
|--------|---------|-------------|
| `maxBlockOperationsPerTick` | 20 | Max block operations per server tick |
| `taskGenerationBlocksPerTick` | 500 | Schematic blocks scanned per tick |
| `maxCheckpointsPerTick` | 30 | Max bee checkpoint actions per tick |
| `redispatchInterval` | 4 | Redispatch cycles between attempts |
