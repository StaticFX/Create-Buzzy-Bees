package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.schematics.share.SharedSchematics
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

class RequestSharedSchematicPacket(val id: String) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<RequestSharedSchematicPacket>(CreateBuzzyBeez.asResource("request_shared_schematic"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestSharedSchematicPacket> = StreamCodec.of(
            { buf, packet -> buf.writeUtf(packet.id) },
            { buf -> RequestSharedSchematicPacket(buf.readUtf()) }
        )

        fun handle(payload: RequestSharedSchematicPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                SharedSchematics.requestSave(player, payload.id)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<RequestSharedSchematicPacket> = TYPE
}
