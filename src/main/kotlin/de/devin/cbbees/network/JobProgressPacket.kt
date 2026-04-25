package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.job.JobCalculationProgress
import de.devin.cbbees.content.domain.job.client.JobProgressClient
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Server → client packet broadcasting the latest progress snapshot for a job's
 * calculation phase (schematic build/removal task generation).
 *
 * Sent on each completed calculation tick, on start, and on completion/failure.
 * Cached server-side via [JobCalculationProgress] so that re-joining players are
 * immediately resynced to the latest known state.
 *
 * @see JobCalculationProgress
 * @see JobProgressClient
 */
class JobProgressPacket(
    val jobId: UUID,
    val phase: JobCalculationProgress.Phase,
    val labelKey: String,
    val processedBlocks: Int,
    val expectedBlocks: Int,
    /** Translation key for the completion message (only meaningful when phase is COMPLETED). */
    val resultKey: String = "",
    /** Argument for the completion translation (e.g. task count). */
    val resultCount: Int = 0,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<JobProgressPacket>(CreateBuzzyBeez.asResource("job_progress"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, JobProgressPacket> =
            object : StreamCodec<RegistryFriendlyByteBuf, JobProgressPacket> {
                override fun decode(buf: RegistryFriendlyByteBuf) = JobProgressPacket(
                    jobId = buf.readUUID(),
                    phase = JobCalculationProgress.Phase.entries[buf.readByte().toInt()],
                    labelKey = buf.readUtf(),
                    processedBlocks = buf.readVarInt(),
                    expectedBlocks = buf.readVarInt(),
                    resultKey = buf.readUtf(),
                    resultCount = buf.readVarInt(),
                )

                override fun encode(buf: RegistryFriendlyByteBuf, packet: JobProgressPacket) {
                    buf.writeUUID(packet.jobId)
                    buf.writeByte(packet.phase.ordinal)
                    buf.writeUtf(packet.labelKey)
                    buf.writeVarInt(packet.processedBlocks)
                    buf.writeVarInt(packet.expectedBlocks)
                    buf.writeUtf(packet.resultKey)
                    buf.writeVarInt(packet.resultCount)
                }
            }

        fun handle(packet: JobProgressPacket, context: IPayloadContext) {
            context.enqueueWork { JobProgressClient.apply(packet) }
        }
    }

    override fun type(): CustomPacketPayload.Type<JobProgressPacket> = TYPE
}
