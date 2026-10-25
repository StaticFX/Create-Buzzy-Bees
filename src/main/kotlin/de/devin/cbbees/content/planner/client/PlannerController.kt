package de.devin.cbbees.content.planner.client

import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack

interface PlannerController {

    fun handles(stack: ItemStack): Boolean = true

    fun tick() {}

    fun onDeactivated(next: PlannerController?) {}

    fun onScroll(delta: Double): Boolean = false

    fun onMouseButton(button: Int, action: Int): Boolean = false

    fun onKey(key: Int, action: Int) {}

    fun renderHud(graphics: GuiGraphics, deltaTracker: DeltaTracker) {}
}
