package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.domain.built.BuiltSchematics
import de.devin.cbbees.content.domain.built.BuiltSchematics.isNear
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobCalculationProgress
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

class RemoveBuildPacket(val buildId: UUID) : BeeJobPacket() {

    private var positions: List<BlockPos> = emptyList()
    private var center: BlockPos = BlockPos.ZERO

    companion object {
        val TYPE = CustomPacketPayload.Type<RemoveBuildPacket>(CreateBuzzyBeez.asResource("remove_build"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RemoveBuildPacket> =
            UUIDUtil.STREAM_CODEC.map(::RemoveBuildPacket, RemoveBuildPacket::buildId).cast()

        fun handle(payload: RemoveBuildPacket, context: IPayloadContext) = handlePacket(payload, context)
    }

    override fun jobType() = JobType.Deconstruction
    override fun progressKey() = "cbbees.progress.processing_area"
    override fun completionKey() = "cbbees.deconstruction.started"

    override fun createUniquenessKey(player: ServerPlayer) =
        SchematicJobKey(player.uuid, "remove_build:$buildId", center.x, center.y, center.z)

    override fun estimateWork(player: ServerPlayer) = positions.size

    override fun validate(player: ServerPlayer): Boolean {
        val data = BuiltSchematics.data(player.server)
        val build = data[buildId] ?: return false
        if (build.ownerId != player.uuid) {
            player.displayClientMessage(Component.translatable("cbbees.remove_build.not_owner"), true)
            return false
        }
        if (build.dimension != player.level().dimension() || !build.isNear(player.position())) return false

        positions = build.remainingBlocks(player.level())
        if (positions.isEmpty()) {
            data.remove(buildId)
            BuiltSchematicsSyncPacket.sendTo(player)
            player.displayClientMessage(Component.translatable("cbbees.remove_build.nothing_left"), true)
            return false
        }
        center = build.bounds.center
        return true
    }

    override fun beforeGenerate(job: BeeJob, player: ServerPlayer) {
        BuiltSchematics.data(player.server).remove(buildId)
        BuiltSchematicsSyncPacket.sendTo(player)
    }

    override fun generateTasks(
        player: ServerPlayer,
        job: BeeJob,
        server: MinecraftServer,
        tracker: JobCalculationProgress.Tracker,
        onComplete: (List<TaskBatch>, BlockPos) -> Unit
    ) {
        SchematicCreateBridge(player.level()).generateRemovalTasksAsync(
            positions, job, server, CBBeesConfig.taskGenerationBlocksPerTick.get(), tracker
        ) { batches -> onComplete(batches, center) }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
