package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobCalculationProgress
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import java.util.*
import kotlin.math.abs
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Packet sent from client to server to start deconstruction of blocks within a selected area.
 *
 * When received, the server will:
 * 1. Validate the selection positions
 * 2. Generate removal tasks for all blocks within the selected area
 * 3. Spawn bees to perform the deconstruction
 *
 * @param pos1 First corner of the selection area
 * @param pos2 Second corner of the selection area
 */
class StartDeconstructionPacket(
    val pos1: BlockPos,
    val pos2: BlockPos
) : CustomPacketPayload {

    companion object {
        val TYPE =
            CustomPacketPayload.Type<StartDeconstructionPacket>(CreateBuzzyBeez.asResource("start_deconstruction"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StartDeconstructionPacket> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StartDeconstructionPacket::pos1,
            BlockPos.STREAM_CODEC, StartDeconstructionPacket::pos2,
            ::StartDeconstructionPacket
        )

        fun handle(payload: StartDeconstructionPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork

                CreateBuzzyBeez.LOGGER.info("Received deconstruction request from ${player.name.string} for area ${payload.pos1} to ${payload.pos2}")

                val jobId = UUID.randomUUID()
                val job = BeeJob(jobId, BlockPos.ZERO, player.level()).apply {
                    ownerId = player.uuid
                    uniquenessKey =
                        SchematicJobKey(player.uuid, "deconstruct_area", payload.pos1.x, payload.pos1.y, payload.pos1.z)
                }

                val server = player.server ?: return@enqueueWork
                val expectedBlocks = volumeOf(payload.pos1, payload.pos2)
                val blocksPerTick = CBBeesConfig.taskGenerationBlocksPerTick.get()
                val tracker = JobCalculationProgress.newTracker(
                    jobId, player.uuid, "cbbees.progress.processing_area", expectedBlocks, server,
                )
                tracker.start()

                val bridge = SchematicCreateBridge(player.level())
                bridge.generateRemovalTasksAsync(
                    payload.pos1, payload.pos2, job, server, blocksPerTick, tracker,
                ) { tasks ->
                    if (tasks.isNotEmpty()) {
                        job.centerPos = BlockPos(
                            (payload.pos1.x + payload.pos2.x) / 2,
                            (payload.pos1.y + payload.pos2.y) / 2,
                            (payload.pos1.z + payload.pos2.z) / 2
                        )
                        job.addBatches(tasks)

                        ServerBeeNetworkManager.findPortableHive(player.uuid)?.let {
                            ServerBeeNetworkManager.reconnectPortableHive(it)
                        }

                        GlobalJobPool.dispatchNewJob(job)
                        tracker.complete("cbbees.deconstruction.started", tasks.size)
                    } else {
                        tracker.fail()
                    }
                }
            }
        }

        private fun volumeOf(a: BlockPos, b: BlockPos): Int =
            (abs(a.x - b.x) + 1) * (abs(a.y - b.y) + 1) * (abs(a.z - b.z) + 1)
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
