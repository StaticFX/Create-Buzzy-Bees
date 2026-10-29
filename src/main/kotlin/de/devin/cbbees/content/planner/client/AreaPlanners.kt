package de.devin.cbbees.content.planner.client

import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.content.schematics.PlannerClipboard
import de.devin.cbbees.icons.AllCCRIcons
import de.devin.cbbees.network.CopyAreaPacket
import de.devin.cbbees.network.ProgramSchematicPacket
import de.devin.cbbees.network.StartDeconstructionPacket
import de.devin.cbbees.network.StartPickupPacket
import de.devin.cbbees.registry.AllKeys
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.PacketDistributor

object AreaPlanners {

    val DECONSTRUCTION = AreaPlannerController(listOf(
        AreaTool("deconstruct", AllCCRIcons.START_DECONSTRUCTION, 0xc56868) { a, b -> send(StartDeconstructionPacket(a, b)) },
        BuildSnapTool(),
        programTool { a, b -> SchematicProgram.Deconstruction(a, b) },
    ))

    val PICKUP = AreaPlannerController(listOf(
        AreaTool("pickup", AllIcons.I_TOOLBOX, 0x68c588) { a, b -> send(StartPickupPacket(a, b)) },
        programTool { a, b -> SchematicProgram.Pickup(a, b) },
    ))

    val COPY = AreaPlannerController(
        listOf(
            AreaTool("copy", AllCCRIcons.COPY, 0x4f8fd8) { a, b -> copy(a, b, cut = false) },
            AreaTool("cut", AllCCRIcons.CUT, 0xe0a050) { a, b -> copy(a, b, cut = true) },
        ),
        handles = { !ConstructionPlannerItem.hasSchematic(it) },
    )

    private fun programTool(program: (BlockPos, BlockPos) -> SchematicProgram) =
        AreaTool("program", AllCCRIcons.PROGRAM, 0x88CCFF, AllKeys.PROGRAM_ACTION) { a, b ->
            send(ProgramSchematicPacket(program(a, b)))
            Minecraft.getInstance().player?.displayClientMessage(
                Component.translatable("cbbees.schematic.programmed").withStyle { it.withColor(0x88CCFF) }, true
            )
        }

    private fun copy(first: BlockPos, second: BlockPos, cut: Boolean) {
        val level = Minecraft.getInstance().level ?: return
        PlannerClipboard.save(CreatePaths.SCHEMATICS_DIR, level, first, second)
        send(CopyAreaPacket(first, second, cut))
    }

    private fun send(packet: CustomPacketPayload) = PacketDistributor.sendToServer(packet)
}
