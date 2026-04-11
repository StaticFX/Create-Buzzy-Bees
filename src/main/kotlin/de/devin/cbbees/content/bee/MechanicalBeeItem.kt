package de.devin.cbbees.content.bee

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

/**
 * Constructor Bee Item - Represents a bee that can be stored in the Constructor Backpack.
 *
 * Features:
 * - Stackable up to 64
 * - Consumed from backpack when deployed for construction
 * - Returned to backpack when task is complete
 * - If backpack is full, drops on the ground
 */
class MechanicalBeeItem(properties: Properties) : Item(properties) {

    companion object {
        /** Maximum stack size for bees */
        const val MAX_STACK_SIZE = 64
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)

        // Show description
        tooltipComponents.add(
            Component.translatable("tooltip.cbbees.bee.description")
                .withStyle(ChatFormatting.GRAY)
        )

        // Show behavior info
        tooltipComponents.add(
            Component.translatable("tooltip.cbbees.bee.consumed_on_deploy")
                .withStyle(ChatFormatting.DARK_GRAY)
        )
    }
}
