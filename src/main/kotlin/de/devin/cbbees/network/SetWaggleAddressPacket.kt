package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.logistics.waggle.WagglePortBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

class SetWaggleAddressPacket(val pos: BlockPos, val address: String) : CustomPacketPayload {

    companion object {
        private const val MAX_DISTANCE_SQR = 64.0

        val TYPE = CustomPacketPayload.Type<SetWaggleAddressPacket>(CreateBuzzyBeez.asResource("set_waggle_address"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SetWaggleAddressPacket> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetWaggleAddressPacket::pos,
            ByteBufCodecs.STRING_UTF8, SetWaggleAddressPacket::address,
            ::SetWaggleAddressPacket
        )

        fun handle(payload: SetWaggleAddressPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                if (player.distanceToSqr(payload.pos.center) > MAX_DISTANCE_SQR) return@enqueueWork
                val port = player.level().getBlockEntity(payload.pos) as? WagglePortBlockEntity ?: return@enqueueWork
                if (!port.link.mayInteractMessage(player)) return@enqueueWork
                port.address = payload.address
                port.notifyUpdate()
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
