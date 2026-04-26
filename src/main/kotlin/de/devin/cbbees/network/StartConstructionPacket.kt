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
 * Client → server packet that carries the client-side schematic placement data
 * (anchor, rotation, mirror) since Create's SchematicHandler only updates the
 * client ItemStack when the player deploys/moves/rotates the schematic.
 */
class StartConstructionPacket(
    val anchor: BlockPos,
    val rotation: Rotation,
    val mirror: Mirror
) : BeeJobPacket() {

    companion object {
        val TYPE = CustomPacketPayload.Type<StartConstructionPacket>(CreateBuzzyBeez.asResource("start_construction"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StartConstructionPacket> = StreamCodec.of(
            { buf, pkt ->
                buf.writeBlockPos(pkt.anchor)
                buf.writeEnum(pkt.rotation)
                buf.writeEnum(pkt.mirror)
            },
            { buf ->
                StartConstructionPacket(
                    buf.readBlockPos(),
                    buf.readEnum(Rotation::class.java),
                    buf.readEnum(Mirror::class.java)
                )
            }
        )

        @ServerSide
        fun handle(payload: StartConstructionPacket, context: IPayloadContext) = handlePacket(payload, context)
    }

    private lateinit var plannerStack: ItemStack
    private lateinit var bridge: SchematicCreateBridge
    private var schematicFile: String = ""

    override fun jobType() = JobType.Construction
    override fun progressKey() = "cbbees.progress.processing_schematic"
    override fun completionKey() = "cbbees.construction.started"

    override fun validate(player: ServerPlayer): Boolean {
        val mainHand = ConstructionPlannerItem.findPlanner(player)
        if (mainHand.isEmpty) {
            player.displayClientMessage(Component.translatable("cbbees.construction.requires_planner"), true)
            return false
        }

        if (!mainHand.has(AllDataComponents.SCHEMATIC_FILE)) {
            player.displayClientMessage(Component.translatable("cbbees.construction.no_schematic"), true)
            return false
        }

        // Sync placement data from client — Create's SchematicHandler only updates the client-side ItemStack
        mainHand.set(AllDataComponents.SCHEMATIC_ANCHOR, anchor)
        mainHand.set(AllDataComponents.SCHEMATIC_ROTATION, rotation)
        mainHand.set(AllDataComponents.SCHEMATIC_MIRROR, mirror)
        mainHand.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)

        val file = mainHand.get(AllDataComponents.SCHEMATIC_FILE)
        val owner = mainHand.get(AllDataComponents.SCHEMATIC_OWNER)
        if (file != null && owner != null) {
            ensureSchematicUploaded(owner, file)
        }

        val b = SchematicCreateBridge(player.level())
        if (!b.loadSchematic(mainHand)) {
            player.displayClientMessage(Component.translatable("cbbees.construction.load_failed"), true)
            return false
        }

        plannerStack = mainHand
        bridge = b
        schematicFile = file ?: ""
        return true
    }

    override fun createUniquenessKey(player: ServerPlayer): Any {
        return SchematicJobKey(player.uuid, schematicFile, anchor.x, anchor.y, anchor.z)
    }

    override fun estimateWork(player: ServerPlayer): Int {
        val bounds = plannerStack.get(AllDataComponents.SCHEMATIC_BOUNDS)
        return bounds?.let { it.x * it.y * it.z } ?: 0
    }

    override fun configureJob(job: BeeJob, player: ServerPlayer) {
        job.schematicPlacement = SchematicPlacement(
            file = schematicFile,
            anchor = anchor,
            rotation = plannerStack.getOrDefault(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE),
            mirror = plannerStack.getOrDefault(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE)
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
