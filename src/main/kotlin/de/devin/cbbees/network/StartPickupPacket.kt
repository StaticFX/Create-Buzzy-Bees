package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobCalculationProgress
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Client → server packet to start item pickup within a selected area.
 * Scans for loose [net.minecraft.world.entity.item.ItemEntity] objects and
 * dispatches bumble bees to collect them.
 */
class StartPickupPacket(
    val pos1: BlockPos,
    val pos2: BlockPos,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<StartPickupPacket>(CreateBuzzyBeez.asResource("start_pickup"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StartPickupPacket> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StartPickupPacket::pos1,
            BlockPos.STREAM_CODEC, StartPickupPacket::pos2,
            ::StartPickupPacket
        )

        fun handle(payload: StartPickupPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork

                val jobId = UUID.randomUUID()
                val job = BeeJob(jobId, BlockPos.ZERO, player.level()).apply {
                    ownerId = player.uuid
                    uniquenessKey = SchematicJobKey(
                        player.uuid, "pickup_area",
                        payload.pos1.x, payload.pos1.y, payload.pos1.z,
                    )
                }

                val server = player.server ?: return@enqueueWork

                val bridge = SchematicCreateBridge(player.level())
                val result = bridge.generatePickupBatches(payload.pos1, payload.pos2, job)

                // Item scan is instant — just show the completion toast with item count
                val tracker = JobCalculationProgress.newTracker(
                    jobId, player.uuid, "cbbees.progress.scanning_items", result.totalItems.coerceAtLeast(1), server,
                )
                tracker.start()

                if (result.batches.isNotEmpty()) {
                    job.centerPos = BlockPos(
                        (payload.pos1.x + payload.pos2.x) / 2,
                        (payload.pos1.y + payload.pos2.y) / 2,
                        (payload.pos1.z + payload.pos2.z) / 2,
                    )
                    job.addBatches(result.batches)

                    ServerBeeNetworkManager.findPortableHive(player.uuid)?.let {
                        ServerBeeNetworkManager.reconnectPortableHive(it)
                    }

                    GlobalJobPool.dispatchNewJob(job)
                    HiveJobsSyncPacket.sendPlayerSnapshotTo(player)
                    tracker.complete("cbbees.pickup.started", result.batches.size)
                } else {
                    tracker.fail()
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<StartPickupPacket> = TYPE
}
