# Deconstruction Planner

The Deconstruction Planner lets you select an area for bees to tear down.

## Usage

1. Hold the **Deconstruction Planner**
2. **Right-click** to set the first corner
3. **Right-click** again to set the second corner
4. **Ctrl+Scroll** to resize the selection by dragging faces
5. Press the **start keybind** (default: R) to begin deconstruction

### Free-Aim Mode

Hold **Ctrl** to switch to free-aim mode — the selection point snaps to a fixed range in front of the camera instead of requiring a block target. Use **Scroll** to adjust the range.

### Cancel

**Shift+Right-click** to cancel the selection.

## Multi-Phase Deconstruction

Bees deconstruct in phases to avoid structural issues:

1. **Phase 0** — Brittle blocks, tunnel components
2. **Phase 1** — Belts, supports, shafts
3. **Phase 2** — Normal blocks (top-down)

Each phase completes before the next begins.

## Item Collection

If enabled in config (`beePickupItems`), bees automatically pick up drops after breaking blocks and deliver them to the nearest drop-off logistics port.
