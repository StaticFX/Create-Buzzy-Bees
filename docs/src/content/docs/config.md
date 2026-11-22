---
title: Config
description: Every server and client option with its default and range.
---

Create Buzzy Beez has two config files. Edit them by hand or use the in-game mod config screen.

| File | Where | Applies to |
|---|---|---|
| `cbbees-server.toml` | `<world>/serverconfig/` | Gameplay and balance. Synced to every player on the server. |
| `cbbees-client.toml` | `config/` | Rendering on your own client only. |

To ship server defaults with a modpack, put a `cbbees-server.toml` into `defaultconfigs/`. New worlds copy it.

## Server

### `[features]`

Feature Toggles

| Key | Default | Range | What it does |
|---|---|---|---|
| `enableExternalBrowser` | `true` | true / false | Enable the 'Browse Online' button and createmod.com connectivity. Requires restart. |

### `[beehive]`

Mechanical Beehive Settings

| Key | Default | Range | What it does |
|---|---|---|---|
| `maxBeesPerHive` | `64` | 1 – 64 | Hard cap on active bees per hive, after RPM and upgrade scaling. Even with max RPM and upgrades, a hive will never exceed this. |
| `minActiveBeesAtRpm` | `1` | 0 – 64 | Minimum active bees when the hive has any RPM. Ensures even slow shafts deploy at least this many bees. |
| `hiveBaseRange` | `10` | 0 – 128 | Base work range (blocks) of a beehive at minimum RPM, before RPM scaling is applied. |
| `hiveRangePerRpm` | `1.0` | 0.01 – 10.0 | Work range added per RPM. Formula: range = baseRange + RPM * this. Example: 64 RPM * 0.25 = 16 + 10 base = 26 blocks. |
| `hiveRpmSpeedDivisor` | `64.0` | 1.0 – 1024.0 | RPM divisor for bee flight speed and spring efficiency. Formula: multiplier = 1 + RPM / this. Higher = slower scaling. |
| `hiveRpmBeeDivisor` | `1.0` | 1.0 – 256.0 | RPM divisor for max active bees. Formula: extra bees = RPM / this. Example: 64 RPM / 8 = 8 extra bees. |

### `[behavior]`

Bee Behavior Settings

| Key | Default | Range | What it does |
|---|---|---|---|
| `beePickupItems` | `true` | true / false | Whether bees pick up item drops when breaking blocks |
| `beeInstantBreaking` | `true` | true / false | Whether bees break blocks instantly. When false, bees take as long as the best diamond tool would for each block, so obsidian takes much longer than dirt. |
| `defaultMaxActiveBees` | `4` | 1 – 64 | Default max active bees before RPM and upgrade scaling. This is the base value that RPM bonuses are added to. |
| `defaultWorkRange` | `32.0` | 1.0 – 256.0 | Default work range (blocks) for portable beehives and other non-hive bee sources. |

### `[upgrades]`

Upgrade Effect Settings: controls how strong each upgrade is

| Key | Default | Range | What it does |
|---|---|---|---|
| `rapidWingsSpeedBonus` | `0.25` | 0.01 – 2.0 | Speed multiplier bonus per Rapid Wings upgrade (default 0.25 = +25% per upgrade) |
| `swarmIntelligenceBeeBonus` | `8` | 1 – 16 | Extra concurrent bees per Swarm Intelligence upgrade |
| `honeyEfficiencyBreakSpeedReduction` | `0.25` | 0.01 – 1.0 | Break speed multiplier reduction per Honey Efficiency upgrade (0.25 = 25% faster) |
| `honeyEfficiencyCarryBonus` | `2` | 1 – 16 | Extra carry capacity per Honey Efficiency upgrade |
| `honeyEfficiencyFuelReduction` | `0.15` | 0.01 – 1.0 | Fuel consumption reduction per Honey Efficiency upgrade (0.15 = 15% less fuel) |
| `honeyTankCapacityBonus` | `200` | 50 – 5000 | Extra honey capacity per Honey Tank upgrade |

### `[drone_view]`

Drone View Settings: controls drone camera behavior

| Key | Default | Range | What it does |
|---|---|---|---|
| `droneBaseRange` | `32.0` | 8.0 – 256.0 | Base range (blocks) the drone can move from the player without upgrades |
| `droneRangeBonus` | `16.0` | 4.0 – 128.0 | Extra range (blocks) per Drone Range upgrade |
| `droneMoveSpeed` | `1.5` | 0.1 – 5.0 | Drone movement speed in blocks per tick when controlled by WASD |

### `[zapper]`

Schematic Zapper Settings: limits for zapper shapes

| Key | Default | Range | What it does |
|---|---|---|---|
| `zapperMaxRadius` | `32` | 1 – 128 | Largest radius a round zapper shape (ellipsoid, dome, cylinder, capsule, cone, torus) can have |
| `zapperMaxSize` | `64` | 1 – 256 | Largest edge length of a cuboid and largest height or length of other zapper shapes |
| `zapperMaxBlocksPerShot` | `50000` | 1 – 1000000 | Most blocks a single zapper shot may change; larger shots are refused |

### `[waggle_port]`

Waggle Port Settings: requesting missing materials from Create's logistics network

| Key | Default | Range | What it does |
|---|---|---|---|
| `waggleRequestInterval` | `40` | 10 – 1200 | Ticks between checks for materials that bee jobs are missing |
| `waggleRequestTimeout` | `1200` | 100 – 24000 | Ticks a request counts as on its way; after that, anything still missing is requested again |

### `[autocrafting]`

Autocrafting Settings: pickup ports on an AE2 or Refined Storage network craft what bee jobs are missing

| Key | Default | Range | What it does |
|---|---|---|---|
| `autocraftMissing` | `true` | true / false | Whether pickup ports ask their storage network to craft missing materials |
| `autocraftInterval` | `100` | 20 – 1200 | Ticks between checks for materials that bee jobs are missing |
| `autocraftRetryDelay` | `1200` | 100 – 24000 | Ticks to wait before asking again for an item the network could not craft |

### `[spring]`

Clockwork Spring Settings: controls per-action energy drain on bees

| Key | Default | Range | What it does |
|---|---|---|---|
| `springDrainPlace` | `0.02` | 0.0 – 1.0 | Spring tension drained per block placement (~50 placements per full spring) |
| `springDrainBreak` | `0.015` | 0.0 – 1.0 | Spring tension drained per block break (~66 breaks per full spring) |
| `springDrainFlight` | `0.0001` | 0.0 – 1.0 | Spring tension drained per tick of flight (~10000 ticks per full spring) |
| `springDrainPickup` | `0.01` | 0.0 – 1.0 | Spring tension drained per item pickup from a logistics port (construction and transport bees). |
| `springDrainDeposit` | `0.01` | 0.0 – 1.0 | Spring tension drained per item deposit to a logistics port (construction and transport bees). |
| `springRechargeTicks` | `200` | 20 – 2000 | Base ticks to recharge a fully depleted spring at the hive (scales with RPM) |

### `[honey_fuel]`

Honey Fuel Settings: controls fuel consumption for portable beehive

| Key | Default | Range | What it does |
|---|---|---|---|
| `portableHoneyPerRewind` | `6` | 1 – 1000 | Honey consumed per full spring rewind in a portable beehive |
| `portableMaxHoney` | `400` | 100 – 10000 | Maximum honey stored in a portable beehive |
| `honeyBottleFuelValue` | `100` | 1 – 1000 | Honey fuel value per honey bottle |
| `honeycombFuelValue` | `60` | 1 – 1000 | Honey fuel value per honeycomb |
| `honeyBlockFuelValue` | `400` | 1 – 10000 | Honey fuel value per honey block |

### `[performance]`

Performance Tuning

| Key | Default | Range | What it does |
|---|---|---|---|
| `blockOperationBudgetMs` | `5.0` | 0.5 – 40.0 | Milliseconds per server tick all bees together may spend placing and breaking blocks. Cheap blocks fit many per tick, heavy ones fewer. Lower values reduce TPS impact with many bees. With adaptiveBlockBudget on, this is the minimum. |
| `adaptiveBlockBudget` | `true` | true / false | Let bees use spare tick time for placing and breaking blocks: the budget grows while bees wait for it and the server's average tick time is below targetTickMs, and shrinks when it goes above. |
| `targetTickMs` | `30.0` | 10.0 – 45.0 | Average server tick time (out of 50 ms) the adaptive block budget stays below. Lower leaves more room for other mods and players. |
| `taskGenerationBudgetMs` | `15.0` | 1.0 – 40.0 | Milliseconds per server tick spent scanning schematics and areas when planning build/removal jobs. Higher values finish planning faster; lower values reduce TPS impact. |
| `checkpointBudgetMs` | `3.0` | 0.5 – 20.0 | Milliseconds per server tick all bees together may spend at their other stops: gathering, picking their next task, drop-off and entering the hive. Bees that don't fit wait for the next tick, first in line. Block placing and breaking is limited by blockOperationBudgetMs instead. |
| `redispatchInterval` | `4` | 1 – 20 | How many GlobalJobPool tick cycles between redispatch attempts for pending batches. GlobalJobPool ticks every 10 server ticks, so interval of 4 = every 40 ticks (2 seconds). Lower = faster bee deployment but more server work. |
| `maxDispatchesPerCycle` | `64` | 1 – 1000 | Maximum bees sent out from hives per redispatch attempt, across all networks. Bees that finish a task pick up the next one on their own; this limits how fast idle hives start working. |

### `[debug]`

Debugging

| Key | Default | Range | What it does |
|---|---|---|---|
| `debugLogging` | `false` | true / false | Log detailed bee, job and network activity. Only useful when tracking down a problem; very noisy with many bees. |

## Client

### `[rendering]`

Rendering & Performance Toggles

| Key | Default | Range | What it does |
|---|---|---|---|
| `showConstructionGhosts` | `true` | true / false | Show ghost blocks for active construction jobs |
| `showSchematicPreview` | `true` | true / false | Show ghost block preview while browsing schematics |
| `showBeehiveRange` | `true` | true / false | Show beehive range overlay when looking at a hive |
| `ghostBlockOpacity` | `0.5` | 0.05 – 1.0 | Opacity for all ghost block rendering (0.05 = nearly invisible, 1.0 = fully opaque) |
| `renderGhostBlockEntities` | `false` | true / false | Render block entities (chests, signs, gearboxes, etc.) in ghost previews. Disable for better performance on large schematics. |
| `maxGhostBlockEntities` | `256` | 1 – 1024 | Maximum number of block entities to render per ghost preview, including belts. Lower values improve performance. |
| `beeRenderDistance` | `64` | 16 – 256 | Maximum distance (blocks) at which bees are rendered. Lower for better FPS with many bees. |
| `beeItemRenderDistance` | `24` | 8 – 64 | Maximum distance (blocks) at which carried items on bumble bees are rendered. |
| `maxRenderedBees` | `512` | 0 – 8192 | Maximum number of bees rendered at once. When more are in view, only the nearest ones are drawn. Lower for better FPS with many bees. |

### `[planners]`

Planners

| Key | Default | Range | What it does |
|---|---|---|---|
| `keepSelectionBetweenPlanners` | `true` | true / false | Keep the area selection when you switch to another item or planner and back |

