package de.devin.cbbees.content.planner

import com.simibubi.create.AllDataComponents
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

object SchematicAndQuillPlanner {

    @JvmStatic
    fun isHeld(stack: ItemStack): Boolean = AllPlanners.SCHEMATIC_AND_QUILL.isIn(stack)

    @JvmStatic
    fun convertSaved(player: ServerPlayer, schematic: ItemStack, origin: BlockPos): Boolean {
        val stack = player.mainHandItem
        if (stack.item !is MasterPlannerItem) return false
        val file = schematic.get(AllDataComponents.SCHEMATIC_FILE) ?: return false
        MasterPlannerItem.select(stack, AllPlanners.CONSTRUCTION)
        ConstructionPlannerItem.deploy(stack, file, player.gameProfile.name, origin, player.level())
        return true
    }
}
