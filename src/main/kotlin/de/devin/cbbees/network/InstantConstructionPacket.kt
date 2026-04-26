package de.devin.cbbees.network

import com.simibubi.create.AllDataComponents
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobCalculationProgress
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.job.SchematicPlacement
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.content.schematics.SchematicJobKey
import de.devin.cbbees.util.ServerSide
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client → server packet that combines schematic selection + instant construction.
 * Used for shift+RMB in the Construction Planner HUD — selects the schematic and
 * immediately starts construction at the specified position without the Create overlay.
 */
class InstantConstructionPacket(
    val schematicName: String,
    val anchor: BlockPos,
    val rotation: Rotation,
    val mirror: Mirror
) : BeeJobPacket() {

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
        fun handle(payload: InstantConstructionPacket, context: IPayloadContext) = handlePacket(payload, context)
    }

    private lateinit var plannerStack: ItemStack
    private lateinit var bridge: SchematicCreateBridge

    override fun jobType() = JobType.Construction
    override fun progressKey() = "cbbees.progress.processing_schematic"
    override fun completionKey() = "cbbees.construction.started"

    override fun validate(player: ServerPlayer): Boolean {
        val mainHand = ConstructionPlannerItem.findPlanner(player)
        if (mainHand.isEmpty) {
            player.displayClientMessage(Component.translatable("cbbees.construction.requires_planner"), true)
            return false
        }

        // Sanitize filename
        if (schematicName.contains("..") || schematicName.contains("/") || schematicName.contains("\\")) return false

        val owner = player.gameProfile.name
        ensureSchematicUploaded(owner, schematicName)

        // Set all schematic data components for loading
        mainHand.set(AllDataComponents.SCHEMATIC_FILE, schematicName)
        mainHand.set(AllDataComponents.SCHEMATIC_OWNER, owner)
        mainHand.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)
        mainHand.set(AllDataComponents.SCHEMATIC_ANCHOR, anchor)
        mainHand.set(AllDataComponents.SCHEMATIC_ROTATION, rotation)
        mainHand.set(AllDataComponents.SCHEMATIC_MIRROR, mirror)

        try {
            com.simibubi.create.content.schematics.SchematicItem.writeSize(player.level(), mainHand)
        } catch (_: Exception) {}

        val b = SchematicCreateBridge(player.level())
        if (!b.loadSchematic(mainHand)) {
            player.displayClientMessage(Component.translatable("cbbees.construction.load_failed"), true)
            ConstructionPlannerItem.clearSchematic(mainHand)
            return false
        }

        plannerStack = mainHand
        bridge = b
        return true
    }

    override fun createUniquenessKey(player: ServerPlayer): Any {
        return SchematicJobKey(player.uuid, schematicName, anchor.x, anchor.y, anchor.z)
    }

    override fun estimateWork(player: ServerPlayer): Int {
        val bounds = plannerStack.get(AllDataComponents.SCHEMATIC_BOUNDS)
        return bounds?.let { it.x * it.y * it.z } ?: 0
    }

    override fun configureJob(job: BeeJob, player: ServerPlayer) {
        job.schematicPlacement = SchematicPlacement(
            file = schematicName,
            anchor = anchor,
            rotation = rotation,
            mirror = mirror
        )
    }

    override fun beforeGenerate(job: BeeJob, player: ServerPlayer) {
        ConstructionPlannerItem.clearSchematic(plannerStack)
    }

    override fun generateTasks(
        player: ServerPlayer,
        job: BeeJob,
        server: MinecraftServer,
        tracker: JobCalculationProgress.Tracker,
        onComplete: (List<TaskBatch>, BlockPos) -> Unit
    ) {
        val blocksPerTick = CBBeesConfig.taskGenerationBlocksPerTick.get()
        bridge.generateBuildTasksAsync(job, server, blocksPerTick, tracker) { batches ->
            val center = bridge.getAnchor() ?: batches.firstOrNull()?.targetPosition ?: BlockPos.ZERO
            onComplete(batches, center)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
