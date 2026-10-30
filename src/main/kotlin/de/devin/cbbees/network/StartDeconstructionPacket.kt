package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.domain.job.BeeJob
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
 * Client → server packet to start deconstruction of blocks within a selected area.
 */
class StartDeconstructionPacket(
    val pos1: BlockPos,
    val pos2: BlockPos
) : BeeJobPacket() {

    companion object {
        val TYPE =
            CustomPacketPayload.Type<StartDeconstructionPacket>(CreateBuzzyBeez.asResource("start_deconstruction"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StartDeconstructionPacket> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StartDeconstructionPacket::pos1,
            BlockPos.STREAM_CODEC, StartDeconstructionPacket::pos2,
            ::StartDeconstructionPacket
        )

        fun handle(payload: StartDeconstructionPacket, context: IPayloadContext) = handlePacket(payload, context)
    }

    override fun jobType() = JobType.Deconstruction
    override fun completionKey() = "cbbees.deconstruction.started"

    override fun createUniquenessKey(player: ServerPlayer) =
        SchematicJobKey(player.uuid, "deconstruct_area", pos1.x, pos1.y, pos1.z)

    override fun generateTasks(
        player: ServerPlayer,
        job: BeeJob,
        server: MinecraftServer,
        onComplete: (List<TaskBatch>, BlockPos) -> Unit
    ) {
        CreateBuzzyBeez.LOGGER.info("Received deconstruction request from ${player.name.string} for area $pos1 to $pos2")
        val bridge = SchematicCreateBridge(player.level())
        val budgetMs = CBBeesConfig.taskGenerationBudgetMs.get()
        bridge.generateRemovalTasksAsync(pos1, pos2, job, server, budgetMs) { batches ->
            val center = BlockPos(
                (pos1.x + pos2.x) / 2,
                (pos1.y + pos2.y) / 2,
                (pos1.z + pos2.z) / 2
            )
            onComplete(batches, center)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
