package de.devin.cbbees.content.schematics

import net.minecraft.world.item.Item

/**
 * Pickup Planner — tool for selecting areas to scan for loose items.
 *
 * Uses the same two-corner selection flow as the Deconstruction Planner:
 * 1. Hold the item in your main hand
 * 2. Right-click to set the first corner
 * 3. Right-click again to set the second corner
 * 4. Press the action key to dispatch bumble bees for item collection
 *
 * Client-side logic handled by [de.devin.cbbees.content.schematics.client.PickupHandler].
 */
class PickupPlannerItem(properties: Properties) : Item(properties)
