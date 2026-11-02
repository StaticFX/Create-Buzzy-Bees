package de.devin.cbbees.content.schematics.client

import com.simibubi.create.AllDataComponents
import com.simibubi.create.foundation.gui.AllIcons
import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.content.planner.client.PlannerTool
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.icons.AllCCRIcons
import de.devin.cbbees.network.ProgramSchematicPacket
import de.devin.cbbees.network.UnselectSchematicPacket
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.neoforged.neoforge.network.PacketDistributor

@ClientSide
abstract class PlacementTool(key: String, icon: AllIcons, color: Int) : PlannerTool(key, icon, color) {
    abstract fun use(player: LocalPlayer, planner: ItemStack): Boolean
}

@ClientSide
object PlacementTools {

    val CONSTRUCT = object : PlacementTool("construct", AllCCRIcons.START_CONSTRUCTION, 0x88FF88) {
        override fun use(player: LocalPlayer, planner: ItemStack): Boolean {
            ConstructPrompt.construct(planner)
            return true
        }
    }

    val UNSELECT = object : PlacementTool("unselect", AllIcons.I_TRASH, 0xFFAA88) {
        override fun use(player: LocalPlayer, planner: ItemStack): Boolean {
            PacketDistributor.sendToServer(UnselectSchematicPacket.INSTANCE)
            ConstructionPlannerItem.clearSchematic(planner)
            player.displayClientMessage(Component.translatable("gui.cbbees.tool.unselect.done").withStyle { it.withColor(color) }, true)
            return true
        }
    }

    val PROGRAM = object : PlacementTool("program", AllCCRIcons.PROGRAM, 0x88CCFF) {
        override fun use(player: LocalPlayer, planner: ItemStack): Boolean {
            val file = planner.get(AllDataComponents.SCHEMATIC_FILE) ?: return false
            val owner = planner.get(AllDataComponents.SCHEMATIC_OWNER) ?: return false
            val program = SchematicProgram.Construction(
                file,
                planner.getOrDefault(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO),
                planner.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE),
                planner.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE),
                owner,
            )
            PacketDistributor.sendToServer(ProgramSchematicPacket(program))
            player.displayClientMessage(Component.translatable("cbbees.schematic.programmed").withStyle { it.withColor(color) }, true)
            return true
        }
    }

    @JvmStatic
    val all = listOf(CONSTRUCT, UNSELECT, PROGRAM)

    @JvmStatic
    var active: PlacementTool? = null
}
