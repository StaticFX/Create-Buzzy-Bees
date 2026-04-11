package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client -> Server packet sent when the player clicks "Program" in the
 * Construction or Deconstruction Planner HUD. Creates a [ProgrammedSchematicItem]
 * with the appropriate [SchematicProgram] data component and gives it to the player.
 */
class ProgramSchematicPacket(
    val program: SchematicProgram
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<ProgramSchematicPacket>(
            CreateBuzzyBeez.asResource("program_schematic")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ProgramSchematicPacket> = StreamCodec.of(
            { buf, pkt -> SchematicProgram.STREAM_CODEC.encode(buf, pkt.program) },
            { buf -> ProgramSchematicPacket(SchematicProgram.STREAM_CODEC.decode(buf)) }
        )

        fun handle(payload: ProgramSchematicPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork

                // Ensure schematic file is uploaded for construction programs
                val program = payload.program
                if (program is SchematicProgram.Construction) {
                    ensureSchematicUploaded(program.owner, program.schematicName)
                }

                // Create the programmed schematic item
                val stack = ItemStack(AllItems.PROGRAMMED_SCHEMATIC.get())
                stack.set(AllDataComponents.SCHEMATIC_PROGRAM, program)

                // Give to player or drop at feet
                if (!player.inventory.add(stack)) {
                    player.drop(stack, false)
                }

                player.displayClientMessage(
                    Component.translatable("cbbees.schematic.programmed"),
                    true
                )
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
