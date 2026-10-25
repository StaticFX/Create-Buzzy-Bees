package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.planner.AllPlanners
import de.devin.cbbees.content.schematics.PlannerClipboard
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

class CopyAreaPacket(
    val first: BlockPos,
    val second: BlockPos,
    val cut: Boolean,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<CopyAreaPacket>(CreateBuzzyBeez.asResource("copy_area"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CopyAreaPacket> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CopyAreaPacket::first,
            BlockPos.STREAM_CODEC, CopyAreaPacket::second,
            ByteBufCodecs.BOOL, CopyAreaPacket::cut,
            ::CopyAreaPacket
        )

        fun handle(payload: CopyAreaPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                payload.execute(player)
            }
        }
    }

    fun execute(player: ServerPlayer): Boolean {
        val stack = player.mainHandItem
        if (!AllPlanners.COPY.isIn(stack)) return false

        val result = PlannerClipboard.save(player, first, second)
        if (result == null) {
            player.displayClientMessage(Component.translatable("cbbees.copy.failed"), true)
            return false
        }
        PlannerClipboard.fill(stack, player, result.origin)

        val bounds = result.bounds
        val key = if (cut) "cbbees.copy.cut" else "cbbees.copy.copied"
        player.displayClientMessage(Component.translatable(key, bounds.x, bounds.y, bounds.z), true)
        if (cut) StartDeconstructionPacket(first, second).submit(player)
        return true
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
