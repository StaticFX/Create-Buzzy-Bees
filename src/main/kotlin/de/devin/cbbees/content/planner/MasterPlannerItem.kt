package de.devin.cbbees.content.planner

import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

/**
 * One item that acts as any of [AllPlanners.ALL]. The current planner is stored on the stack and
 * switched with Shift+Scroll; item behavior is handed to that planner's item.
 */
class MasterPlannerItem(properties: Properties) : Item(properties) {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> =
        currentPlanner(player.getItemInHand(hand)).item.use(level, player, hand)

    override fun isFoil(stack: ItemStack): Boolean = currentPlanner(stack).item.isFoil(stack)

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        val planner = currentPlanner(stack)
        tooltip.add(
            Component.translatable("tooltip.cbbees.master_planner.mode", planner.item.description)
                .withStyle(ChatFormatting.GOLD)
        )
        tooltip.add(Component.translatable("tooltip.cbbees.master_planner.cycle").withStyle(ChatFormatting.GRAY))
        planner.item.appendHoverText(stack, context, tooltip, flag)
    }

    companion object {

        /** The planner [stack] acts as, or null if it isn't a Master-Planner. */
        fun getPlanner(stack: ItemStack): Planner? =
            if (stack.item is MasterPlannerItem) currentPlanner(stack) else null

        fun currentPlanner(stack: ItemStack): Planner =
            stack.get(AllDataComponents.PLANNER_MODE.get())?.let(AllPlanners::byId) ?: AllPlanners.ALL.first()

        fun select(stack: ItemStack, planner: Planner) {
            stack.set(AllDataComponents.PLANNER_MODE.get(), planner.id)
        }

        /** Switches [stack] to the next planner, or the previous one when [forward] is false. */
        fun cycle(stack: ItemStack, forward: Boolean): Planner {
            val planners = AllPlanners.ALL
            val step = if (forward) 1 else -1
            val next = planners[(planners.indexOf(currentPlanner(stack)) + step).mod(planners.size)]
            select(stack, next)
            return next
        }
    }
}
