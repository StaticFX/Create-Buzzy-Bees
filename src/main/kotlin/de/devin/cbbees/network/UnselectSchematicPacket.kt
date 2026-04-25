package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client → Server packet that clears the schematic data from the Construction
 * Planner held in the player's main hand, returning it to the "empty" state
 * so the player can pick a new schematic.
 */
class UnselectSchematicPacket private constructor() : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<UnselectSchematicPacket>(
            CreateBuzzyBeez.asResource("unselect_schematic")
        )

        val INSTANCE = UnselectSchematicPacket()

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, UnselectSchematicPacket> =
            StreamCodec.unit(INSTANCE)

        fun handle(payload: UnselectSchematicPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                val stack = ConstructionPlannerItem.findPlanner(player)
                if (stack.isEmpty) return@enqueueWork
                ConstructionPlannerItem.clearSchematic(stack)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
