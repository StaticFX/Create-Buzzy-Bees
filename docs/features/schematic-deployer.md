# Schematic Deployer

The Schematic Deployer is a block that automatically executes programmed schematics — construction, deconstruction, or pickup jobs — without player intervention.

## Usage

1. Use a **Construction Planner**, **Deconstruction Planner**, or **Pickup Planner** to select a task
2. Press the **program keybind** (default: P) instead of starting the job
3. This creates a **Programmed Schematic** item
4. Place the Programmed Schematic into the Schematic Deployer
5. Power the deployer with a redstone pulse to execute

## Self-Replicating Builds

When a construction schematic contains a Schematic Deployer block, the deployer automatically programs the new deployer with the same schematic (offset to the new position). This enables self-replicating build patterns.

## Programs

The deployer supports all three program types:

- **Construction** — places blocks from a schematic file
- **Deconstruction** — removes blocks in an area
- **Pickup** — collects loose items in an area

Each program is stored as a data component on the Programmed Schematic item and can be relocated (offset) when the deployer is placed at a different position.
