package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.zapper.SchematicZapperItem
import de.devin.cbbees.content.zapper.shape.ShapeSettings
import de.devin.cbbees.content.zapper.shape.ZapperShapes
import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.neoforged.neoforge.network.handling.IPayloadContext

class ConfigureZapperShapePacket(val hand: InteractionHand, val settings: ShapeSettings) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<ConfigureZapperShapePacket>(CreateBuzzyBeez.asResource("configure_zapper_shape"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ConfigureZapperShapePacket> = StreamCodec.composite(
            ByteBufCodecs.idMapper({ InteractionHand.entries[it] }, InteractionHand::ordinal), ConfigureZapperShapePacket::hand,
            ShapeSettings.STREAM_CODEC.cast(), ConfigureZapperShapePacket::settings,
            ::ConfigureZapperShapePacket
        )

        fun handle(payload: ConfigureZapperShapePacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val stack = player.getItemInHand(payload.hand)
                if (stack.item !is SchematicZapperItem) return@enqueueWork
                stack.set(AllDataComponents.ZAPPER_SHAPE.get(), payload.settings.clampedTo(ZapperShapes.byId(payload.settings.shape)))
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
