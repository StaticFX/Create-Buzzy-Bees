package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.schematics.client.SharedSchematicChat
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

class SharedSchematicPacket(
    val id: String,
    val filename: String,
    val save: Boolean,
    val totalSize: Int,
    val data: ByteArray,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<SharedSchematicPacket>(CreateBuzzyBeez.asResource("shared_schematic"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SharedSchematicPacket> = StreamCodec.of(
            { buf, packet ->
                buf.writeUtf(packet.id)
                buf.writeUtf(packet.filename)
                buf.writeBoolean(packet.save)
                buf.writeVarInt(packet.totalSize)
                buf.writeByteArray(packet.data)
            },
            { buf -> SharedSchematicPacket(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readByteArray()) }
        )

        fun handle(payload: SharedSchematicPacket, ctx: IPayloadContext) {
            ctx.enqueueWork { SharedSchematicChat.receive(payload) }
        }
    }

    override fun type(): CustomPacketPayload.Type<SharedSchematicPacket> = TYPE
}
