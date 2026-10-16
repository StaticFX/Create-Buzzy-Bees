package de.devin.cbbees.network

import com.simibubi.create.AllBlocks
import com.simibubi.create.AllSoundEvents
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.job.MissingMaterials
import de.devin.cbbees.content.domain.job.MissingMaterials.missingCount
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import net.minecraft.core.UUIDUtil
import net.minecraft.core.component.DataComponents
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

class WriteMissingToClipboardPacket(val jobId: UUID, val hand: InteractionHand) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<WriteMissingToClipboardPacket>(
            CreateBuzzyBeez.asResource("write_missing_to_clipboard")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, WriteMissingToClipboardPacket> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WriteMissingToClipboardPacket::jobId,
            NeoForgeStreamCodecs.enumCodec(InteractionHand::class.java), WriteMissingToClipboardPacket::hand,
            ::WriteMissingToClipboardPacket
        )

        fun handle(payload: WriteMissingToClipboardPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                payload.write(player)
            }
        }
    }

    private fun write(player: ServerPlayer) {
        val held = player.getItemInHand(hand)
        if (!AllBlocks.CLIPBOARD.isIn(held)) return

        val job = GlobalJobPool.getAllJobs().firstOrNull { it.jobId == jobId && it.level == player.level() } ?: return
        val checklist = MissingMaterials.checklistFor(job, ServerBeeNetworkManager.findNetworkForJob(job))
        if (checklist.required.isEmpty()) {
            player.displayClientMessage(Component.translatable("cbbees.clipboard.nothing_missing"), true)
            return
        }

        val clipboard = checklist.createWrittenClipboard()
        job.schematicPlacement?.let { placement ->
            clipboard.set(
                DataComponents.CUSTOM_NAME,
                Component.literal(placement.file.removeSuffix(".nbt")).withStyle { it.withItalic(false) }
            )
        }

        if (held.count == 1) {
            player.setItemInHand(hand, clipboard)
        } else {
            held.shrink(1)
            if (!player.inventory.add(clipboard)) player.drop(clipboard, false)
        }

        AllSoundEvents.CLIPBOARD_CHECKMARK.playOnServer(player.level(), player.blockPosition())
        player.displayClientMessage(
            Component.translatable("cbbees.clipboard.written", checklist.missingCount()),
            true
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
