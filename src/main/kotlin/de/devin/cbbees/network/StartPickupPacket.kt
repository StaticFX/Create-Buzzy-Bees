package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobCalculationProgress
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client → server packet to start item pickup within a selected area.
 * Scans for loose item entities and dispatches bumble bees to collect them.
 */
class StartPickupPacket(
    val pos1: BlockPos,
    val pos2: BlockPos,
) : BeeJobPacket() {

    companion object {
        val TYPE = CustomPacketPayload.Type<StartPickupPacket>(CreateBuzzyBeez.asResource("start_pickup"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StartPickupPacket> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StartPickupPacket::pos1,
            BlockPos.STREAM_CODEC, StartPickupPacket::pos2,
            ::StartPickupPacket
        )

        fun handle(payload: StartPickupPacket, context: IPayloadContext) = handlePacket(payload, context)
    }

    override fun jobType() = JobType.Pickup
    override fun progressKey() = "cbbees.progress.scanning_items"
    override fun completionKey() = "cbbees.pickup.started"

    override fun createUniquenessKey(player: ServerPlayer) =
        SchematicJobKey(player.uuid, "pickup_area", pos1.x, pos1.y, pos1.z)

    override fun estimateWork(player: ServerPlayer): Int {
        // Item scan is instant — estimate from results at generation time
        return 1
    }

    override fun generateTasks(
        player: ServerPlayer,
        job: BeeJob,
        server: MinecraftServer,
        tracker: JobCalculationProgress.Tracker,
        onComplete: (List<TaskBatch>, BlockPos) -> Unit
    ) {
        val bridge = SchematicCreateBridge(player.level())
        val result = bridge.generatePickupBatches(pos1, pos2, job)
        val center = BlockPos(
            (pos1.x + pos2.x) / 2,
            (pos1.y + pos2.y) / 2,
            (pos1.z + pos2.z) / 2,
        )
        onComplete(result.batches, center)
    }

    override fun type(): CustomPacketPayload.Type<StartPickupPacket> = TYPE
}
