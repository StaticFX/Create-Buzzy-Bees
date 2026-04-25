package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.drone.client.DroneViewClientState
import de.devin.cbbees.util.ClientSide
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Server -> Client: sync drone view state.
 * entityId = -1 means deactivate, otherwise the entity ID of the drone.
 * maxRange = maximum drone range from player (for HUD display).
 */
class DroneViewSyncPacket(val entityId: Int, val maxRange: Float) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<DroneViewSyncPacket>(
            CreateBuzzyBeez.asResource("drone_view_sync")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, DroneViewSyncPacket> = StreamCodec.of(
            { buf, pkt ->
                buf.writeVarInt(pkt.entityId)
                buf.writeFloat(pkt.maxRange)
            },
            { buf -> DroneViewSyncPacket(buf.readVarInt(), buf.readFloat()) }
        )

        @ClientSide
        fun handle(payload: DroneViewSyncPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                if (payload.entityId == -1) {
                    DroneViewClientState.deactivate()
                } else {
                    DroneViewClientState.activate(payload.entityId, payload.maxRange)
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
