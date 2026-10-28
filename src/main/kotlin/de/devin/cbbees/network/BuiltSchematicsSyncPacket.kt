package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.built.BuiltSchematics
import de.devin.cbbees.content.domain.built.client.ClientBuiltSchematic
import de.devin.cbbees.content.domain.built.client.ClientBuiltSchematics
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

class BuiltSchematicsSyncPacket(val builds: List<ClientBuiltSchematic>) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<BuiltSchematicsSyncPacket>(CreateBuzzyBeez.asResource("built_schematics_sync"))

        private val NAME_CODEC = ByteBufCodecs.stringUtf8(256)

        private val BUILD_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClientBuiltSchematic> = StreamCodec.of(
            { buf, build ->
                UUIDUtil.STREAM_CODEC.encode(buf, build.id)
                NAME_CODEC.encode(buf, build.name)
                UUIDUtil.STREAM_CODEC.encode(buf, build.ownerId)
                buf.writeBoolean(build.ownerName != null)
                build.ownerName?.let { NAME_CODEC.encode(buf, it) }
                BlockPos.STREAM_CODEC.encode(buf, BlockPos(build.bounds.minX(), build.bounds.minY(), build.bounds.minZ()))
                BlockPos.STREAM_CODEC.encode(buf, BlockPos(build.bounds.maxX(), build.bounds.maxY(), build.bounds.maxZ()))
            },
            { buf ->
                val id = UUIDUtil.STREAM_CODEC.decode(buf)
                val name = NAME_CODEC.decode(buf)
                val ownerId = UUIDUtil.STREAM_CODEC.decode(buf)
                val ownerName = if (buf.readBoolean()) NAME_CODEC.decode(buf) else null
                val bounds = BoundingBox.fromCorners(BlockPos.STREAM_CODEC.decode(buf), BlockPos.STREAM_CODEC.decode(buf))
                ClientBuiltSchematic(id, name, ownerId, ownerName, bounds)
            }
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BuiltSchematicsSyncPacket> =
            BUILD_CODEC.apply(ByteBufCodecs.list()).map(::BuiltSchematicsSyncPacket, BuiltSchematicsSyncPacket::builds)

        fun handle(payload: BuiltSchematicsSyncPacket, ctx: IPayloadContext) {
            ctx.enqueueWork { ClientBuiltSchematics.update(payload.builds) }
        }

        fun sendTo(player: ServerPlayer) {
            val builds = BuiltSchematics.nearby(player).map {
                ClientBuiltSchematic(it.id, it.name, it.ownerId, HiveJobsSyncPacket.playerName(player.server, it.ownerId), it.bounds)
            }
            PacketDistributor.sendToPlayer(player, BuiltSchematicsSyncPacket(builds))
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
