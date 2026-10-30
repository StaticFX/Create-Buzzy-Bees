package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.schematics.share.SharedSchematics
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

class ShareSchematicPacket(val filename: String) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<ShareSchematicPacket>(CreateBuzzyBeez.asResource("share_schematic"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ShareSchematicPacket> = StreamCodec.of(
            { buf, packet -> buf.writeUtf(packet.filename) },
            { buf -> ShareSchematicPacket(buf.readUtf()) }
        )

        fun handle(payload: ShareSchematicPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                SharedSchematics.share(player, payload.filename)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<ShareSchematicPacket> = TYPE
}
