package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.client.BeeClientTracker
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Removes a bee from the client immediately (bee entered hive, dropped as item, etc.).
 * Sent as a targeted notification — no polling or staleness timeout needed.
 */
class BeeRemovePacket(val beeId: UUID) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<BeeRemovePacket>(CreateBuzzyBeez.asResource("bee_remove"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BeeRemovePacket> =
            object : StreamCodec<RegistryFriendlyByteBuf, BeeRemovePacket> {
                override fun decode(buf: RegistryFriendlyByteBuf) = BeeRemovePacket(buf.readUUID())
                override fun encode(buf: RegistryFriendlyByteBuf, packet: BeeRemovePacket) { buf.writeUUID(packet.beeId) }
            }

        fun handle(packet: BeeRemovePacket, context: IPayloadContext) {
            context.enqueueWork { BeeClientTracker.removeFlightData(packet.beeId) }
        }
    }

    override fun type(): CustomPacketPayload.Type<BeeRemovePacket> = TYPE
}
