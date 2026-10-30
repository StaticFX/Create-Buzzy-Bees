package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.client.BeeClientTracker
import de.devin.cbbees.content.bee.flight.ClientBeeFlightData
import de.devin.cbbees.content.bee.flight.ClientCheckpoint
import de.devin.cbbees.content.bee.server.BeeType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Sends a bee's full flight plan to the client — **once per bee per mission**.
 *
 * Includes [elapsedTicks] so the client can fast-forward to match the server's
 * current position along the checkpoint path. This prevents the visual desync
 * caused by async plan computation + network latency.
 *
 * @see ClientBeeFlightData
 * @see de.devin.cbbees.content.bee.flight.FlightPlan
 */
class BeeFlightPlanPacket(
    val beeId: UUID,
    val type: BeeType,
    val speed: Float,
    val checkpoints: List<ClientCheckpoint>,
    val startIndex: Int,
    /** How many ticks the server has been running this plan. Client fast-forwards by this amount. */
    val elapsedTicks: Long = 0,
    val carriedItem: ItemStack = ItemStack.EMPTY,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<BeeFlightPlanPacket>(CreateBuzzyBeez.asResource("bee_flight_plan"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BeeFlightPlanPacket> =
            object : StreamCodec<RegistryFriendlyByteBuf, BeeFlightPlanPacket> {
                override fun decode(buf: RegistryFriendlyByteBuf) = BeeFlightPlanPacket(
                    beeId = buf.readUUID(),
                    type = BeeType.entries[buf.readByte().toInt()],
                    speed = buf.readFloat(),
                    checkpoints = (0 until buf.readVarInt()).map {
                        ClientCheckpoint(
                            pos = buf.readBlockPos(),
                            pauseTicks = buf.readVarInt(),
                            awaitConfirm = buf.readBoolean(),
                            travelTicks = buf.readVarLong(),
                            workTicks = buf.readVarInt(),
                        )
                    },
                    startIndex = buf.readVarInt(),
                    elapsedTicks = buf.readVarLong(),
                    carriedItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                )

                override fun encode(buf: RegistryFriendlyByteBuf, packet: BeeFlightPlanPacket) {
                    buf.writeUUID(packet.beeId)
                    buf.writeByte(packet.type.ordinal)
                    buf.writeFloat(packet.speed)
                    buf.writeVarInt(packet.checkpoints.size)
                    packet.checkpoints.forEach { cp ->
                        buf.writeBlockPos(cp.pos)
                        buf.writeVarInt(cp.pauseTicks)
                        buf.writeBoolean(cp.awaitConfirm)
                        buf.writeVarLong(cp.travelTicks)
                        buf.writeVarInt(cp.workTicks)
                    }
                    buf.writeVarInt(packet.startIndex)
                    buf.writeVarLong(packet.elapsedTicks)
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.carriedItem)
                }
            }

        fun handle(packet: BeeFlightPlanPacket, context: IPayloadContext) {
            context.enqueueWork {
                BeeClientTracker.applyFlightPlan(
                    ClientBeeFlightData(
                        id = packet.beeId,
                        type = packet.type,
                        speed = packet.speed,
                        checkpoints = packet.checkpoints,
                        startIndex = packet.startIndex,
                        elapsedNanoOffset = packet.elapsedTicks * 50_000_000L, // ticks → nanos
                        carriedItem = packet.carriedItem,
                    )
                )
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<BeeFlightPlanPacket> = TYPE
}
