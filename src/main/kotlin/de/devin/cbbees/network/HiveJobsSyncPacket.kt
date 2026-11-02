package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.beehive.client.ClientJobCache
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.StuckReasonResolver
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.content.domain.job.ClientNetworkInfo
import de.devin.cbbees.content.domain.job.HiveSnapshot
import de.devin.cbbees.content.domain.job.JobStatus
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.job.SchematicPlacement
import de.devin.cbbees.content.domain.job.toClientInfo
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.server.MinecraftServer
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

class HiveJobsSyncPacket(
    val hivePos: BlockPos,
    val snapshot: HiveSnapshot
) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<HiveJobsSyncPacket>(CreateBuzzyBeez.asResource("hive_jobs_sync"))

        private val UUID_CODEC = net.minecraft.core.UUIDUtil.STREAM_CODEC
        private val UUID_LIST_CODEC: StreamCodec<RegistryFriendlyByteBuf, List<UUID>> =
            ByteBufCodecs.collection({ mutableListOf() }, UUID_CODEC)
        private val ITEM_LIST_CODEC: StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> =
            ByteBufCodecs.collection({ mutableListOf() }, ItemStackStreamCodec.CODEC)
        private val STRING_CODEC = ByteBufCodecs.stringUtf8(256)
        private val BOUNDS_CODEC: StreamCodec<ByteBuf, BoundingBox> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, { BlockPos(it.minX(), it.minY(), it.minZ()) },
            BlockPos.STREAM_CODEC, { BlockPos(it.maxX(), it.maxY(), it.maxZ()) },
            BoundingBox::fromCorners,
        )

        private val JOB_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClientJobInfo> = StreamCodec.of(
            { buf, j ->
                UUID_CODEC.encode(buf, j.jobId)
                STRING_CODEC.encode(buf, j.name)
                STRING_CODEC.encode(buf, j.status)
                buf.writeVarInt(j.completed)
                buf.writeVarInt(j.total)
                buf.writeNullable(j.reason?.take(250)) { b, s -> STRING_CODEC.encode(b, s) }
                UUID_LIST_CODEC.encode(buf, j.beeIds)
                ITEM_LIST_CODEC.encode(buf, j.materials)
                buf.writeNullable(j.bounds) { b, box -> BOUNDS_CODEC.encode(b, box) }
                buf.writeNullable(j.schematicPlacement) { b, sp ->
                    STRING_CODEC.encode(b, sp.file)
                    BlockPos.STREAM_CODEC.encode(b, sp.anchor)
                    b.writeEnum(sp.rotation)
                    b.writeEnum(sp.mirror)
                }
                buf.writeVarInt(JobType.ordinalOf(j.jobType))
                buf.writeNullable(j.ownerId) { b, id -> UUID_CODEC.encode(b, id) }
                buf.writeNullable(j.ownerName) { b, s -> STRING_CODEC.encode(b, s) }
            },
            { buf ->
                val id = UUID_CODEC.decode(buf)
                val name = STRING_CODEC.decode(buf)
                val status = STRING_CODEC.decode(buf)
                val completed = buf.readVarInt()
                val total = buf.readVarInt()
                val reason = buf.readNullable { b -> STRING_CODEC.decode(b) }
                val beeIds = UUID_LIST_CODEC.decode(buf)
                val materials = ITEM_LIST_CODEC.decode(buf)
                val bounds = buf.readNullable { b -> BOUNDS_CODEC.decode(b) }
                val placement = buf.readNullable { b ->
                    SchematicPlacement(
                        file = STRING_CODEC.decode(b),
                        anchor = BlockPos.STREAM_CODEC.decode(b),
                        rotation = b.readEnum(net.minecraft.world.level.block.Rotation::class.java),
                        mirror = b.readEnum(net.minecraft.world.level.block.Mirror::class.java)
                    )
                }
                val jobType = JobType.fromOrdinal(buf.readVarInt())
                val ownerId = buf.readNullable { b -> UUID_CODEC.decode(b) }
                val ownerName = buf.readNullable { b -> STRING_CODEC.decode(b) }
                ClientJobInfo(id, name, status, completed, total, reason, beeIds, materials, bounds, placement, jobType, ownerId, ownerName)
            }
        )

        private val JOB_LIST_CODEC: StreamCodec<RegistryFriendlyByteBuf, List<ClientJobInfo>> =
            ByteBufCodecs.collection({ mutableListOf() }, JOB_CODEC)

        private val NETWORK_INFO_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClientNetworkInfo> = StreamCodec.of(
            { buf, ni ->
                STRING_CODEC.encode(buf, ni.name)
                buf.writeVarInt(ni.activeBees)
                buf.writeVarInt(ni.storedBees)
                buf.writeVarInt(ni.maxBees)
            },
            { buf ->
                val name = STRING_CODEC.decode(buf)
                val active = buf.readVarInt()
                val stored = buf.readVarInt()
                val max = buf.readVarInt()
                ClientNetworkInfo(name, active, stored, max)
            }
        )

        private val SNAPSHOT_CODEC: StreamCodec<RegistryFriendlyByteBuf, HiveSnapshot> = StreamCodec.of(
            { buf, s ->
                NETWORK_INFO_CODEC.encode(buf, s.networkInfo)
                JOB_LIST_CODEC.encode(buf, s.jobs)
            },
            { buf ->
                val ni = NETWORK_INFO_CODEC.decode(buf)
                val jobs = JOB_LIST_CODEC.decode(buf)
                HiveSnapshot(ni, jobs)
            }
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, HiveJobsSyncPacket> = StreamCodec.of(
            { buf, p ->
                BlockPos.STREAM_CODEC.encode(buf, p.hivePos)
                SNAPSHOT_CODEC.encode(buf, p.snapshot)
            },
            { buf ->
                val pos = BlockPos.STREAM_CODEC.decode(buf)
                val snapshot = SNAPSHOT_CODEC.decode(buf)
                HiveJobsSyncPacket(pos, snapshot)
            }
        )

        fun handle(payload: HiveJobsSyncPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                ClientJobCache.update(payload.hivePos, payload.snapshot)
            }
        }

        private const val VISIBLE_RANGE = 128.0

        /** Other players' jobs in the same dimension close enough to see their bounding box. */
        private fun isNearby(job: BeeJob, player: ServerPlayer): Boolean =
            job.level == player.level() && job.centerPos.closerToCenterThan(player.position(), VISIBLE_RANGE)

        fun playerName(server: MinecraftServer, playerId: UUID): String? =
            server.playerList.getPlayer(playerId)?.gameProfile?.name
                ?: server.profileCache?.get(playerId)?.map { it.name }?.orElse(null)

        fun sendPlayerSnapshotTo(player: ServerPlayer) = sendPlayerSnapshots(player.server, listOf(player))

        /**
         * Sends each player their own jobs plus other players' jobs nearby. Each job's
         * [ClientJobInfo], including its stuck reason, is built once and shared.
         */
        fun sendPlayerSnapshots(server: MinecraftServer, players: List<ServerPlayer> = server.playerList.players) {
            val jobs = GlobalJobPool.getAllJobs()
                .filter { it.status != JobStatus.COMPLETED && it.status != JobStatus.CANCELLED }
            val infos = mutableMapOf<UUID, ClientJobInfo>()
            fun infoOf(job: BeeJob) = infos.getOrPut(job.jobId) {
                val network = ServerBeeNetworkManager.findNetworkForJob(job)
                val reason = if (network != null) StuckReasonResolver.firstReasonOrNull(network, job) else "cbbees.stall.no_network"
                job.toClientInfo(server, reason)
            }

            for (player in players) {
                val clientJobs = jobs.filter { it.ownerId == player.uuid || isNearby(it, player) }.map(::infoOf)
                val snapshot = HiveSnapshot(personalNetworkInfo(player), clientJobs)
                PacketDistributor.sendToPlayer(player, HiveJobsSyncPacket(BlockPos.ZERO, snapshot))
            }
        }

        private fun personalNetworkInfo(player: ServerPlayer): ClientNetworkInfo {
            val network = ServerBeeNetworkManager.findPortableHive(player.uuid)?.let(ServerBeeNetworkManager::getNetworkFor)
                ?: return ClientNetworkInfo("Personal", 0, 0, 0)
            val hives = network.hives
            return ClientNetworkInfo(
                network.name,
                hives.sumOf { it.getActiveBeeCount() },
                hives.sumOf { it.getAvailableBeeCount() },
                hives.sumOf { it.getBeeContext().maxActiveBees },
            )
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
