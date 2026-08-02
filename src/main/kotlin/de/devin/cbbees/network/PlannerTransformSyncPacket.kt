package de.devin.cbbees.network

import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.schematics.SchematicInstances
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.items.AllItems
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Synchronizes the Construction Planner's live Create placement transform.
 *
 * Create's own SchematicSyncPacket rejects non-Create schematic items on the
 * server. The planner is accepted client-side through a mixin, so it needs its
 * own narrow sync packet for anchor, rotation, mirror, and deployed state.
 */
class PlannerTransformSyncPacket(
    val slot: Int,
    val anchor: BlockPos,
    val rotation: Rotation,
    val mirror: Mirror,
    val deployed: Boolean
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<PlannerTransformSyncPacket>(
            CreateBuzzyBeez.asResource("planner_transform_sync")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, PlannerTransformSyncPacket> =
            StreamCodec.of(
                { buf, packet ->
                    buf.writeVarInt(packet.slot)
                    buf.writeBlockPos(packet.anchor)
                    buf.writeEnum(packet.rotation)
                    buf.writeEnum(packet.mirror)
                    buf.writeBoolean(packet.deployed)
                },
                { buf ->
                    PlannerTransformSyncPacket(
                        buf.readVarInt(),
                        buf.readBlockPos(),
                        buf.readEnum(Rotation::class.java),
                        buf.readEnum(Mirror::class.java),
                        buf.readBoolean()
                    )
                }
            )

        fun handle(payload: PlannerTransformSyncPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val inventory = player.inventory

                val stack = when {
                    payload.slot == -1 -> player.mainHandItem
                    payload.slot in 0 until inventory.containerSize -> inventory.getItem(payload.slot)
                    else -> return@enqueueWork
                }

                if (!AllItems.CONSTRUCTION_PLANNER.isIn(stack)) return@enqueueWork
                if (!stack.has(AllDataComponents.SCHEMATIC_FILE)) return@enqueueWork

                stack.set(AllDataComponents.SCHEMATIC_ANCHOR, payload.anchor)
                stack.set(AllDataComponents.SCHEMATIC_ROTATION, payload.rotation)
                stack.set(AllDataComponents.SCHEMATIC_MIRROR, payload.mirror)
                stack.set(AllDataComponents.SCHEMATIC_DEPLOYED, payload.deployed)
                SchematicInstances.clearHash(stack)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
