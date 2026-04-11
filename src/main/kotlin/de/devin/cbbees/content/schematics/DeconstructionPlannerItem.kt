package de.devin.cbbees.content.schematics

import net.minecraft.world.item.Item

/**
 * Deconstruction Planner - Tool for selecting areas to be dismantled by construction bees.
 *
 * This item works similarly to Create's Schematic and Quill:
 * 1. Hold the item in your main hand
 * 2. Right-click to set the first corner of the selection
 * 3. Right-click again to set the second corner
 * 4. Use scroll wheel (with CTRL) to resize the selection
 * 5. Right-click a third time to open the deconstruction prompt
 * 6. Shift+Right-click to cancel the selection
 *
 * The actual selection logic is handled by [de.devin.cbbees.content.schematics.client.DeconstructionHandler]
 * on the client side. This item just needs to exist for the handler to detect when it's held.
 *
 * The selection is rendered with a red outline (in contrast to Create's blue schematic outline)
 * to clearly indicate that this is a deconstruction/removal operation.
 */
class DeconstructionPlannerItem(properties: Properties) : Item(properties)
