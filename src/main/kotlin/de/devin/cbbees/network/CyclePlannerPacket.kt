package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.planner.MasterPlannerItem
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client → server: switch the Master-Planner in the main hand to the next or previous planner.
 */
class CyclePlannerPacket(val forward: Boolean) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<CyclePlannerPacket>(CreateBuzzyBeez.asResource("cycle_planner"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CyclePlannerPacket> =
            ByteBufCodecs.BOOL.map(::CyclePlannerPacket, CyclePlannerPacket::forward).cast()

        fun handle(payload: CyclePlannerPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val stack = player.mainHandItem
                if (stack.item !is MasterPlannerItem) return@enqueueWork

                val planner = MasterPlannerItem.cycle(stack, payload.forward)
                player.displayClientMessage(
                    Component.translatable("tooltip.cbbees.master_planner.mode", planner.item.description), true
                )
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
