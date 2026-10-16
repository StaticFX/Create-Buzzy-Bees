package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.client.BeeClientTracker
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Batched server → client update of the item each bee is carrying, sent when it changes.
 */
class BeeCarriedItemPacket(
    val entries: List<Entry>,
) : CustomPacketPayload {

    data class Entry(val beeId: UUID, val item: ItemStack)

    companion object {
        val TYPE = CustomPacketPayload.Type<BeeCarriedItemPacket>(
            CreateBuzzyBeez.asResource("bee_carried_item")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BeeCarriedItemPacket> =
            object : StreamCodec<RegistryFriendlyByteBuf, BeeCarriedItemPacket> {
                override fun decode(buf: RegistryFriendlyByteBuf) = BeeCarriedItemPacket(
                    entries = (0 until buf.readVarInt()).map {
                        Entry(buf.readUUID(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf))
                    }
                )

                override fun encode(buf: RegistryFriendlyByteBuf, packet: BeeCarriedItemPacket) {
                    buf.writeVarInt(packet.entries.size)
                    packet.entries.forEach { entry ->
                        buf.writeUUID(entry.beeId)
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.item)
                    }
                }
            }

        fun handle(packet: BeeCarriedItemPacket, context: IPayloadContext) {
            context.enqueueWork {
                packet.entries.forEach { entry ->
                    BeeClientTracker.setCarriedItem(entry.beeId, entry.item)
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<BeeCarriedItemPacket> = TYPE
}
