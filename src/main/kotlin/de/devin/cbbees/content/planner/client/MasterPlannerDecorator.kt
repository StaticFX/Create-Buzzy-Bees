package de.devin.cbbees.content.planner.client

import de.devin.cbbees.content.planner.MasterPlannerItem
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.client.IItemDecorator

/**
 * Draws the planner a Master-Planner currently acts as, small in the corner of its slot.
 */
@ClientSide
object MasterPlannerDecorator : IItemDecorator {

    override fun render(guiGraphics: GuiGraphics, font: Font, stack: ItemStack, xOffset: Int, yOffset: Int): Boolean {
        val planner = MasterPlannerItem.getPlanner(stack) ?: return false

        val poseStack = guiGraphics.pose()
        poseStack.pushPose()
        poseStack.translate(xOffset.toFloat(), yOffset + 8f, 100f)
        poseStack.scale(0.5f, 0.5f, 0.5f)
        guiGraphics.renderItem(ItemStack(planner.item), 0, 0)
        poseStack.popPose()
        return false
    }
}
