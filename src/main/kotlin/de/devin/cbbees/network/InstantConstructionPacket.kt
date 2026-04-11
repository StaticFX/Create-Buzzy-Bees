package de.devin.cbbees.network

import com.simibubi.create.AllDataComponents
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobCalculationProgress
import de.devin.cbbees.content.domain.job.SchematicPlacement
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import de.devin.cbbees.util.ServerSide
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Client → Server packet that combines schematic selection + instant construction.
 * Used for shift+RMB in the Construction Planner HUD — selects the schematic and
 * immediately starts construction at the specified position without the Create overlay.
 */
class InstantConstructionPacket(
    val schematicName: String,
    val anchor: BlockPos,
    val rotation: Rotation,
    val mirror: Mirror
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<InstantConstructionPacket>(
            CreateBuzzyBeez.asResource("instant_construction")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, InstantConstructionPacket> = StreamCodec.of(
            { buf, pkt ->
                buf.writeUtf(pkt.schematicName)
                buf.writeBlockPos(pkt.anchor)
                buf.writeEnum(pkt.rotation)
                buf.writeEnum(pkt.mirror)
            },
            { buf ->
                InstantConstructionPacket(
                    buf.readUtf(),
                    buf.readBlockPos(),
                    buf.readEnum(Rotation::class.java),
                    buf.readEnum(Mirror::class.java)
                )
            }
        )

        @ServerSide
        fun handle(payload: InstantConstructionPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val mainHand = ConstructionPlannerItem.findPlanner(player)

                if (mainHand.isEmpty) {
                    player.displayClientMessage(
                        Component.translatable("cbbees.construction.requires_planner"), true
                    )
                    return@enqueueWork
                }

                // Sanitize filename
                val name = payload.schematicName
                if (name.contains("..") || name.contains("/") || name.contains("\\")) return@enqueueWork

                val owner = player.gameProfile.name

                // Ensure the schematic file exists in the server's uploaded directory
                ensureSchematicUploaded(owner, name)

                // Set all schematic data components for loading
                mainHand.set(AllDataComponents.SCHEMATIC_FILE, name)
                mainHand.set(AllDataComponents.SCHEMATIC_OWNER, owner)
                mainHand.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)
                mainHand.set(AllDataComponents.SCHEMATIC_ANCHOR, payload.anchor)
                mainHand.set(AllDataComponents.SCHEMATIC_ROTATION, payload.rotation)
                mainHand.set(AllDataComponents.SCHEMATIC_MIRROR, payload.mirror)

                // Try to write bounds
                try {
                    com.simibubi.create.content.schematics.SchematicItem.writeSize(player.level(), mainHand)
                } catch (_: Exception) {
                    // Bounds will be inferred from schematic
                }

                // Load and build
                val bridge = SchematicCreateBridge(player.level())
                if (!bridge.loadSchematic(mainHand)) {
                    player.displayClientMessage(
                        Component.translatable("cbbees.construction.load_failed"), true
                    )
                    ConstructionPlannerItem.clearSchematic(mainHand)
                    return@enqueueWork
                }

                val jobId = UUID.randomUUID()
                val job = BeeJob(jobId, BlockPos.ZERO, player.level()).apply {
                    ownerId = player.uuid
                    uniquenessKey = SchematicJobKey(
                        player.uuid, name,
                        payload.anchor.x, payload.anchor.y, payload.anchor.z
                    )
                    schematicPlacement = SchematicPlacement(
                        file = name,
                        anchor = payload.anchor,
                        rotation = payload.rotation,
                        mirror = payload.mirror
                    )
                }

                val server = player.server ?: return@enqueueWork
                val bounds = mainHand.get(AllDataComponents.SCHEMATIC_BOUNDS)
                val expectedBlocks = bounds?.let { it.x * it.y * it.z } ?: 0
                val blocksPerTick = CBBeesConfig.taskGenerationBlocksPerTick.get()
                val tracker = JobCalculationProgress.newTracker(
                    jobId, player.uuid, "cbbees.progress.processing_schematic", expectedBlocks, server,
                )
                tracker.start()

                ConstructionPlannerItem.clearSchematic(mainHand)

                bridge.generateBuildTasksAsync(job, server, blocksPerTick, tracker) { batches ->
                    if (batches.isNotEmpty()) {
                        job.centerPos = bridge.getAnchor() ?: batches[0].targetPosition
                        job.batches.addAll(batches)

                        ServerBeeNetworkManager.findPortableHive(player.uuid)?.let {
                            ServerBeeNetworkManager.reconnectPortableHive(it)
                        }
                        GlobalJobPool.dispatchNewJob(job)

                        tracker.complete("cbbees.construction.started", batches.size)
                    } else {
                        tracker.fail()
                    }
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
