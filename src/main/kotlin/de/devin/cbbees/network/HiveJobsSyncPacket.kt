package de.devin.cbbees.network

import com.simibubi.create.AllBlocks
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.beehive.client.ClientJobCache
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.StuckReasonResolver
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.action.impl.PlaceBeltAction
import de.devin.cbbees.content.domain.action.impl.PlaceBlockAction
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.ClientBatchInfo
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.content.domain.job.ClientNetworkInfo
import de.devin.cbbees.content.domain.job.HiveSnapshot
import de.devin.cbbees.content.domain.job.JobStatus
import de.devin.cbbees.content.domain.job.SchematicPlacement
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TaskStatus
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
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
        private val BLOCK_STATE_CODEC: StreamCodec<RegistryFriendlyByteBuf, BlockState> = StreamCodec.of(
            { buf, s -> buf.writeNbt(NbtUtils.writeBlockState(s)) },
            { buf ->
                val tag = buf.readNbt() as? CompoundTag ?: CompoundTag()
                NbtUtils.readBlockState(buf.registryAccess().lookupOrThrow(Registries.BLOCK), tag)
            }
        )

        private val GHOST_BLOCK_ENTRY_CODEC: StreamCodec<RegistryFriendlyByteBuf, Pair<BlockPos, BlockState>> =
            StreamCodec.of(
                { buf, entry ->
                    BlockPos.STREAM_CODEC.encode(buf, entry.first)
                    BLOCK_STATE_CODEC.encode(buf, entry.second)
                },
                { buf ->
                    val pos = BlockPos.STREAM_CODEC.decode(buf)
                    val state = BLOCK_STATE_CODEC.decode(buf)
                    pos to state
                }
            )

        private val GHOST_BLOCKS_CODEC: StreamCodec<RegistryFriendlyByteBuf, Map<BlockPos, BlockState>> =
            StreamCodec.of(
                { buf, map ->
                    buf.writeVarInt(map.size)
                    map.forEach { (pos, state) ->
                        GHOST_BLOCK_ENTRY_CODEC.encode(buf, pos to state)
                    }
                },
                { buf ->
                    val size = buf.readVarInt()
                    val map = mutableMapOf<BlockPos, BlockState>()
                    repeat(size) {
                        val (pos, state) = GHOST_BLOCK_ENTRY_CODEC.decode(buf)
                        map[pos] = state
                    }
                    map
                }
            )

        private val BATCH_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClientBatchInfo> = StreamCodec.of(
            { buf, b ->
                STRING_CODEC.encode(buf, b.status)
                BlockPos.STREAM_CODEC.encode(buf, b.target)
                ITEM_LIST_CODEC.encode(buf, b.required)
                UUID_LIST_CODEC.encode(buf, b.assignedBeeIds)
                GHOST_BLOCKS_CODEC.encode(buf, b.ghostBlocks)
            },
            { buf ->
                val status = STRING_CODEC.decode(buf)
                val target = BlockPos.STREAM_CODEC.decode(buf)
                val required = ITEM_LIST_CODEC.decode(buf)
                val assignedBeeIds = UUID_LIST_CODEC.decode(buf)
                val ghostBlocks = GHOST_BLOCKS_CODEC.decode(buf)
                ClientBatchInfo(status, target, required, assignedBeeIds, ghostBlocks)
            }
        )

        private val BATCH_LIST_CODEC: StreamCodec<RegistryFriendlyByteBuf, List<ClientBatchInfo>> =
            ByteBufCodecs.collection({ mutableListOf() }, BATCH_CODEC)

        private val JOB_CODEC: StreamCodec<RegistryFriendlyByteBuf, ClientJobInfo> = StreamCodec.of(
            { buf, j ->
                UUID_CODEC.encode(buf, j.jobId)
                STRING_CODEC.encode(buf, j.name)
                STRING_CODEC.encode(buf, j.status)
                buf.writeVarInt(j.completed)
                buf.writeVarInt(j.total)
                buf.writeNullable(j.reason) { b, s -> STRING_CODEC.encode(b, s) }
                BATCH_LIST_CODEC.encode(buf, j.batches)
                buf.writeNullable(j.schematicPlacement) { b, sp ->
                    STRING_CODEC.encode(b, sp.file)
                    BlockPos.STREAM_CODEC.encode(b, sp.anchor)
                    b.writeEnum(sp.rotation)
                    b.writeEnum(sp.mirror)
                }
            },
            { buf ->
                val id = UUID_CODEC.decode(buf)
                val name = STRING_CODEC.decode(buf)
                val status = STRING_CODEC.decode(buf)
                val completed = buf.readVarInt()
                val total = buf.readVarInt()
                val reason = buf.readNullable { b -> STRING_CODEC.decode(b) }
                val batches = BATCH_LIST_CODEC.decode(buf)
                val placement = buf.readNullable { b ->
                    SchematicPlacement(
                        file = STRING_CODEC.decode(b),
                        anchor = BlockPos.STREAM_CODEC.decode(b),
                        rotation = b.readEnum(net.minecraft.world.level.block.Rotation::class.java),
                        mirror = b.readEnum(net.minecraft.world.level.block.Mirror::class.java)
                    )
                }
                ClientJobInfo(id, name, status, completed, total, reason, batches, placement)
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

        /**
         * Collect all ghost block positions and states from a batch.
         * Handles both regular PlaceBlockAction and PlaceBeltAction (belt chain + shafts).
         */
        private fun collectGhostBlocks(batch: TaskBatch): Map<BlockPos, BlockState> {
            if (batch.status == TaskStatus.COMPLETED) return emptyMap()

            val ghosts = mutableMapOf<BlockPos, BlockState>()
            for (task in batch.tasks) {
                when (val action = task.action) {
                    is PlaceBlockAction -> ghosts[action.pos] = action.blockState
                    is PlaceBeltAction -> {
                        action.chain.forEachIndexed { index, pos ->
                            // Don't overwrite shaft states already set by PlaceBlockAction
                            if (!ghosts.containsKey(pos)) {
                                val state = action.chainStates.getOrNull(index)
                                    ?: AllBlocks.BELT.defaultState
                                ghosts[pos] = state
                            }
                        }
                    }
                }
            }
            return ghosts
        }

        fun handle(payload: HiveJobsSyncPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                ClientJobCache.update(payload.hivePos, payload.snapshot)
            }
        }

        fun sendSnapshotTo(player: ServerPlayer, hivePos: BlockPos) {
            val level = player.level() as? ServerLevel ?: return
            val be = level.getBlockEntity(hivePos) as? MechanicalBeehiveBlockEntity ?: return
            val net = ServerBeeNetworkManager.getNetworkFor(be) ?: return

            val hiveList = net.hives
            val statsActive = hiveList.sumOf { it.getActiveBeeCount() }
            val statsStored = hiveList.sumOf { it.getAvailableBeeCount() }
            val statsMax = hiveList.sumOf { it.getBeeContext().maxActiveBees }

            val jobs = GlobalJobPool.getAllJobs()
                .filter { job ->
                    job.status != JobStatus.COMPLETED && job.status != JobStatus.CANCELLED && (
                            job.batches.any { it.assignedNetworkId == net.id } ||
                                    net.isInRange(job.centerPos)
                            )
                }
                .map { job ->
                    val completed = job.tasks.count { it.status == TaskStatus.COMPLETED }
                    val total = job.tasks.size

                    val reason = StuckReasonResolver.firstReasonOrNull(net, job)

                    // For jobs with schematic placement, skip per-batch detail entirely —
                    // the client renders ghosts from the schematic file. Only send a single
                    // summary batch to keep the packet small.
                    val hasPlacement = job.schematicPlacement != null
                    val batches = if (hasPlacement) {
                        // Single summary entry — no per-batch overhead for large schematics
                        listOf(ClientBatchInfo(
                            status = "SUMMARY",
                            target = job.centerPos,
                            required = emptyList(),
                            assignedBeeIds = emptyList(),
                            ghostBlocks = emptyMap()
                        ))
                    } else {
                        job.batches.map { b ->
                            ClientBatchInfo(
                                status = b.status.name,
                                target = b.targetPosition,
                                required = emptyList(),
                                assignedBeeIds = emptyList(),
                                ghostBlocks = collectGhostBlocks(b)
                            )
                        }
                    }
                    ClientJobInfo(
                        jobId = job.jobId,
                        name = job.jobId.toString().substring(0, 6).uppercase(),
                        status = job.status.name,
                        completed = completed,
                        total = total,
                        reason = reason,
                        batches = batches,
                        schematicPlacement = job.schematicPlacement
                    )
                }

            val ni = ClientNetworkInfo(net.name, statsActive, statsStored, statsMax)
            PacketDistributor.sendToPlayer(player, HiveJobsSyncPacket(hivePos, HiveSnapshot(ni, jobs)))
        }

        fun sendPlayerSnapshotTo(player: ServerPlayer) {
            val jobs = GlobalJobPool.getAllJobs().filter { it.ownerId == player.uuid }
                .filter { it.status != JobStatus.COMPLETED && it.status != JobStatus.CANCELLED }

            val clientJobs = jobs.map { job ->
                    val completed = job.tasks.count { it.status == TaskStatus.COMPLETED }
                    val batches = job.batches.map { b ->
                        ClientBatchInfo(
                            b.status.name, b.targetPosition, emptyList(), emptyList(),
                            ghostBlocks = emptyMap()
                        )
                    }
                    ClientJobInfo(
                        job.jobId,
                        job.jobId.toString().substring(0, 6).uppercase(),
                        job.status.name,
                        completed,
                        job.tasks.size,
                        null,
                        batches,
                        schematicPlacement = job.schematicPlacement
                    )
                }
            val snapshot = HiveSnapshot(ClientNetworkInfo("Personal", 0, 0, 0), clientJobs)
            PacketDistributor.sendToPlayer(player, HiveJobsSyncPacket(BlockPos.ZERO, snapshot))
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
