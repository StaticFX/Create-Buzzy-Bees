package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import de.devin.cbbees.content.drone.client.DroneViewClientState
import de.devin.cbbees.content.planner.AllPlanners
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.network.PrintSchematicPacket
import de.devin.cbbees.network.StopTasksPacket
import de.devin.cbbees.registry.AllKeys
import de.devin.cbbees.util.ClientSide
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor

@ClientSide
object PlannerToolInput {

    @JvmStatic
    fun rightClick(): Boolean {
        if (ConstructPrompt.isOpen) {
            ConstructPrompt.confirm()
            return true
        }

        val tool = PlacementTools.active ?: return false
        val player = Minecraft.getInstance().player ?: return false
        if (player.isShiftKeyDown) return false
        val planner = DroneViewClientState.findSchematicPlanner(player)
        if (planner.isEmpty || !tool.use(player, planner)) return false

        PlacementTools.active = null
        return true
    }

    @JvmStatic
    fun keyPressed(key: Int) {
        val player = Minecraft.getInstance().player ?: return
        if (AllKeys.START_ACTION.matches(key, 0)) {
            DroneViewClientState.findSchematicPlanner(player).takeUnless { it.isEmpty }?.let(ConstructPrompt::construct)
        }
        if (AllKeys.STOP_ACTION.matches(key, 0)) {
            PacketDistributor.sendToServer(StopTasksPacket.INSTANCE)
        }
    }

    @JvmStatic
    fun printInstantly(stack: ItemStack?): Boolean {
        if (stack == null || AllPlanners.schematicPlannerOf(stack) == null) return false
        val transform = CreateClient.SCHEMATIC_HANDLER.transformation
        val settings = transform.toSettings()
        PacketDistributor.sendToServer(PrintSchematicPacket(transform.anchor, settings.rotation, settings.mirror))
        ConstructionPlannerItem.releaseSchematic(stack)
        return true
    }
}
