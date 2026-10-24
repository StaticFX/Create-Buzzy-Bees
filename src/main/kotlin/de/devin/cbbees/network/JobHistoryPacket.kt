package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.history.JobHistory
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

data class JobHistoryPacket(val redo: Boolean) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<JobHistoryPacket>(CreateBuzzyBeez.asResource("job_history"))

        val STREAM_CODEC: StreamCodec<ByteBuf, JobHistoryPacket> =
            ByteBufCodecs.BOOL.map(::JobHistoryPacket, JobHistoryPacket::redo)

        fun handle(payload: JobHistoryPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                if (payload.redo) JobHistory.redo(player) else JobHistory.undo(player)
            }
        }
    }
}
