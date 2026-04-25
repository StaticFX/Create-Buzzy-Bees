package de.devin.cbbees.content.schematics

import com.simibubi.create.AllDataComponents
import de.devin.cbbees.content.schematics.client.ConstructionPlannerHandler
import de.devin.cbbees.items.AllItems
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

/**
 * Construction Planner - a reusable tool for selecting and deploying schematics.
 *
 * When right-clicked with no schematic loaded, enters groups or selects schematics
 * from the inline HUD. Shift+right-click on a schematic instantly starts construction
 * at the crosshair position without the Create overlay.
 *
 * After construction starts, the schematic data is cleared and the planner
 * is ready for reuse.
 */
class ConstructionPlannerItem(properties: Properties) : Item(properties) {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)

        if (level.isClientSide) {
            if (!hasSchematic(stack)) {
                handleClientUse(player)
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    @OnlyIn(Dist.CLIENT)
    private fun handleClientUse(player: Player) {
        if (player.isShiftKeyDown) {
            // Shift+RMB: instant construction at crosshair
            ConstructionPlannerHandler.instantConstruct()
        } else {
            // Normal RMB: enter group or select schematic
            ConstructionPlannerHandler.confirmSelection()
        }
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltip: MutableList<Component>,
        flag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltip, flag)
        val file = stack.get(AllDataComponents.SCHEMATIC_FILE)
        if (file != null) {
            tooltip.add(Component.literal(file).withStyle(ChatFormatting.GOLD))
        } else {
            tooltip.add(
                Component.translatable("tooltip.cbbees.construction_planner.empty")
                    .withStyle(ChatFormatting.GRAY)
            )
        }
    }

    override fun isFoil(stack: ItemStack): Boolean {
        return hasSchematic(stack)
    }

    companion object {
        fun hasSchematic(stack: ItemStack): Boolean {
            return stack.has(AllDataComponents.SCHEMATIC_FILE)
        }

        /**
         * Finds a Construction Planner in the player's inventory.
         * Checks main hand first, then searches the full inventory.
         * Returns [ItemStack.EMPTY] if not found.
         */
        fun findPlanner(player: Player): ItemStack {
            val mainHand = player.mainHandItem
            if (AllItems.CONSTRUCTION_PLANNER.isIn(mainHand)) return mainHand
            val inv = player.inventory
            for (i in 0 until inv.containerSize) {
                val stack = inv.getItem(i)
                if (AllItems.CONSTRUCTION_PLANNER.isIn(stack)) return stack
            }
            return ItemStack.EMPTY
        }

        fun clearSchematic(stack: ItemStack) {
            stack.remove(AllDataComponents.SCHEMATIC_FILE)
            stack.remove(AllDataComponents.SCHEMATIC_OWNER)
            stack.remove(AllDataComponents.SCHEMATIC_DEPLOYED)
            stack.remove(AllDataComponents.SCHEMATIC_ANCHOR)
            stack.remove(AllDataComponents.SCHEMATIC_ROTATION)
            stack.remove(AllDataComponents.SCHEMATIC_MIRROR)
            stack.remove(AllDataComponents.SCHEMATIC_BOUNDS)
        }
    }
}
