package de.devin.cbbees.content.schematics

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

class CopyPlannerItem(properties: Properties) : Item(properties) {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (player.isShiftKeyDown && ConstructionPlannerItem.reset(stack, player)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
        return super.use(level, player, hand)
    }

    override fun isFoil(stack: ItemStack): Boolean = ConstructionPlannerItem.hasSchematic(stack)

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, context, tooltip, flag)
        val key = if (ConstructionPlannerItem.hasSchematic(stack)) "filled" else "empty"
        tooltip.add(Component.translatable("tooltip.cbbees.copy_planner.$key").withStyle(ChatFormatting.GRAY))
    }
}
