package de.devin.cbbees.network

import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.schematics.SchematicPrinter
import com.simibubi.create.foundation.utility.BlockHelper
import com.simibubi.create.infrastructure.config.AllConfigs
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.domain.history.BlockSnapshot
import de.devin.cbbees.content.domain.history.HistoryEntry
import de.devin.cbbees.content.domain.history.JobHistory
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
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

class PrintSchematicPacket(
    val anchor: BlockPos,
    val rotation: Rotation,
    val mirror: Mirror,
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PrintSchematicPacket>(CreateBuzzyBeez.asResource("print_schematic"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, PrintSchematicPacket> = StreamCodec.of(
            { buf, pkt ->
                buf.writeBlockPos(pkt.anchor)
                buf.writeEnum(pkt.rotation)
                buf.writeEnum(pkt.mirror)
            },
            { buf -> PrintSchematicPacket(buf.readBlockPos(), buf.readEnum(Rotation::class.java), buf.readEnum(Mirror::class.java)) }
        )

        fun handle(payload: PrintSchematicPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                payload.execute(player)
            }
        }
    }

    fun execute(player: ServerPlayer): Boolean {
        if (!player.isCreative) return false
        val stack = ConstructionPlannerItem.findPlanner(player)
        val file = stack.get(AllDataComponents.SCHEMATIC_FILE) ?: return false
        stack.get(AllDataComponents.SCHEMATIC_OWNER)?.let { ensureSchematicUploaded(it, file) }

        stack.set(AllDataComponents.SCHEMATIC_ANCHOR, anchor)
        stack.set(AllDataComponents.SCHEMATIC_ROTATION, rotation)
        stack.set(AllDataComponents.SCHEMATIC_MIRROR, mirror)
        stack.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)

        val level = player.serverLevel()
        val printer = SchematicPrinter()
        printer.loadSchematic(stack, level, !player.canUseGameMasterBlocks())
        if (!printer.isLoaded || printer.isErrored) {
            player.displayClientMessage(Component.translatable("cbbees.construction.load_failed"), true)
            return false
        }

        val entry = HistoryEntry(UUID.randomUUID(), JobType.Construction, level.dimension())
        val includeAir = AllConfigs.server().schematics.creativePrintIncludesAir.get()
        while (printer.advanceCurrentPos()) {
            if (!printer.shouldPlaceCurrent(level)) continue
            printer.handleCurrentTarget({ pos, state, blockEntity ->
                if (state.isAir && !includeAir) return@handleCurrentTarget
                val before = BlockSnapshot.capture(level, pos)
                BlockHelper.placeSchematicBlock(level, state, pos, null, BlockHelper.prepareBlockEntityData(level, state, blockEntity))
                entry.record(pos, before, BlockSnapshot.capture(level, pos))
            }, { _, entity -> level.addFreshEntity(entity) })
        }

        if (entry.changes.isNotEmpty()) JobHistory.of(player).pushUndo(entry, clearRedo = true)
        ConstructionPlannerItem.releaseSchematic(stack)
        return true
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
