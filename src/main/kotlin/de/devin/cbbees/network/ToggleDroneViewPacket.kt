package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.drone.DroneViewManager
import de.devin.cbbees.util.ServerSide
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client -> Server: toggle drone view on/off.
 */
class ToggleDroneViewPacket : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<ToggleDroneViewPacket>(
            CreateBuzzyBeez.asResource("toggle_drone_view")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ToggleDroneViewPacket> = StreamCodec.of(
            { _, _ -> /* no data */ },
            { _ -> ToggleDroneViewPacket() }
        )

        @ServerSide
        fun handle(payload: ToggleDroneViewPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                DroneViewManager.toggleDrone(player)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
