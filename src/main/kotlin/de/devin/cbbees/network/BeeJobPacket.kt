package de.devin.cbbees.network

import de.devin.cbbees.content.domain.job.BeeJobRequest
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

abstract class BeeJobPacket : BeeJobRequest(), CustomPacketPayload {

    companion object {
        /** Standard handle pattern — use in each packet's companion `handle` function. */
        fun <T : BeeJobPacket> handlePacket(payload: T, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                payload.submit(player)
            }
        }
    }
}
