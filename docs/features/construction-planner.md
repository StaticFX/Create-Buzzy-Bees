# Construction Planner

The Construction Planner is the primary tool for deploying schematics with bees.

## Usage

### Inline HUD

Hold the Construction Planner to see the inline schematic selector above your hotbar:

1. **Alt+Scroll** to browse schematics and groups
2. **Right-click** to select a schematic or enter a group
3. **Backspace** to go back up in the group hierarchy

### Deploying a Schematic

After selecting a schematic:

1. Create's schematic overlay appears — position, rotate, and mirror the schematic
2. Select the **Construct** tool from the extended toolbar
3. **Right-click** to start construction

### Instant Construction

**Shift+Right-click** on a schematic in the HUD to skip the placement overlay and immediately start building at the crosshair position.

### Full-Screen Browser

Press the **schematic browser keybind** to open the full-screen browser with:

- Search across all schematics
- 3D isometric preview with rotation and zoom
- Material list showing required blocks
- Group management (rename, reassign)

## How It Works

1. The planner sends the schematic placement data to the server
2. The server loads the schematic and generates build tasks
3. Tasks are dispatched to the bee network
4. Bees gather materials from logistics ports and fly to placement positions
5. Progress is shown via a translucent bounding box outline

## Stuck Detection

If bees can't complete a job, the bounding box turns **red** and the stall reason is shown:

- **Missing materials** — required blocks aren't in any provider port
- **Out of range** — target area is outside the hive's work range
- **No bees available** — all bees are busy or none are stored
- **No logistics port** — no provider port with the needed items
