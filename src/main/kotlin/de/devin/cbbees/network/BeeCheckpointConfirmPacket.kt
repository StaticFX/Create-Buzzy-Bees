package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.client.BeeClientTracker
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Batched server → client notification that one or more bee checkpoint actions completed
 * this tick. Sent once per tick after [de.devin.cbbees.content.bee.server.ServerBeeManager.tickAll]
 * finishes processing all checkpoint arrivals.
 *
 * Batching avoids per-checkpoint packet overhead: with 30 confirmations/tick, this is
 * one ~500-byte packet instead of 30 individual ~30-byte packets (saving TCP framing,
 * NeoForge dispatch, and `enqueueWork` per entry).
 *
 * @see de.devin.cbbees.content.bee.flight.ClientBeeFlightData.confirmCheckpoint
 */
class BeeCheckpointConfirmPacket(
    val entries: List<Entry>,
) : CustomPacketPayload {

    data class Entry(val beeId: UUID, val checkpointIndex: Int)

    companion object {
        val TYPE = CustomPacketPayload.Type<BeeCheckpointConfirmPacket>(
            CreateBuzzyBeez.asResource("bee_checkpoint_confirm")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BeeCheckpointConfirmPacket> =
            object : StreamCodec<RegistryFriendlyByteBuf, BeeCheckpointConfirmPacket> {
                override fun decode(buf: RegistryFriendlyByteBuf) = BeeCheckpointConfirmPacket(
                    entries = (0 until buf.readVarInt()).map {
                        Entry(buf.readUUID(), buf.readVarInt())
                    }
                )

                override fun encode(buf: RegistryFriendlyByteBuf, packet: BeeCheckpointConfirmPacket) {
                    buf.writeVarInt(packet.entries.size)
                    packet.entries.forEach { entry ->
                        buf.writeUUID(entry.beeId)
                        buf.writeVarInt(entry.checkpointIndex)
                    }
                }
            }

        fun handle(packet: BeeCheckpointConfirmPacket, context: IPayloadContext) {
            context.enqueueWork {
                packet.entries.forEach { entry ->
                    BeeClientTracker.confirmCheckpoint(entry.beeId, entry.checkpointIndex)
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<BeeCheckpointConfirmPacket> = TYPE
}
