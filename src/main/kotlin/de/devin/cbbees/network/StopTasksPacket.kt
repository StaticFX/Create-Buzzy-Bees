package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.GlobalJobPool
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Packet sent from client to server to stop all ongoing bee tasks for the player.
 * Used when the player wants to cancel construction or deconstruction.
 */
class StopTasksPacket private constructor() : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<StopTasksPacket>(CreateBuzzyBeez.asResource("stop_tasks"))

        @JvmStatic
        val INSTANCE = StopTasksPacket()
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StopTasksPacket> = StreamCodec.unit(INSTANCE)

        fun handle(payload: StopTasksPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                GlobalJobPool.getAllJobs().filter { it.ownerId == player.uuid }.forEach { it.cancel() }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
