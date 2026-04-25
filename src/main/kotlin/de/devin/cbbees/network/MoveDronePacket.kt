package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.drone.DroneViewManager
import de.devin.cbbees.util.ServerSide
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client -> Server: move the drone by a delta offset.
 * dx/dz are world-space deltas clamped to max speed on the server.
 */
class MoveDronePacket(val dx: Float, val dz: Float) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<MoveDronePacket>(
            CreateBuzzyBeez.asResource("move_drone")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MoveDronePacket> = StreamCodec.of(
            { buf, pkt ->
                buf.writeFloat(pkt.dx)
                buf.writeFloat(pkt.dz)
            },
            { buf -> MoveDronePacket(buf.readFloat(), buf.readFloat()) }
        )

        @ServerSide
        fun handle(payload: MoveDronePacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork

                val droneUUID = DroneViewManager.getDroneUUID(player) ?: return@enqueueWork
                val entity = player.serverLevel().getEntity(droneUUID) as? MechanicalBeeEntity ?: return@enqueueWork
                if (!entity.isDrone) return@enqueueWork

                // Clamp magnitude to prevent speed hacking
                var dx = payload.dx.toDouble()
                var dz = payload.dz.toDouble()
                val mag = kotlin.math.sqrt(dx * dx + dz * dz)
                if (mag > MechanicalBeeEntity.DRONE_MAX_SPEED) {
                    dx = dx / mag * MechanicalBeeEntity.DRONE_MAX_SPEED
                    dz = dz / mag * MechanicalBeeEntity.DRONE_MAX_SPEED
                }

                entity.applyDroneMovement(dx, dz)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
