package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.deployer.DeployMode
import de.devin.cbbees.content.deployer.SchematicDeployerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client -> Server packet sent when the player changes deploy mode, relative offset,
 * or rotation/mirror overrides in the Schematic Deployer GUI.
 */
class DeployerSettingsPacket(
    val deployerPos: BlockPos,
    val mode: DeployMode,
    val relativeOffset: BlockPos,
    val relativeRotation: Rotation,
    val relativeMirror: Mirror
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<DeployerSettingsPacket>(
            CreateBuzzyBeez.asResource("deployer_settings")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, DeployerSettingsPacket> = StreamCodec.of(
            { buf, pkt ->
                buf.writeBlockPos(pkt.deployerPos)
                buf.writeEnum(pkt.mode)
                buf.writeBlockPos(pkt.relativeOffset)
                buf.writeEnum(pkt.relativeRotation)
                buf.writeEnum(pkt.relativeMirror)
            },
            { buf ->
                DeployerSettingsPacket(
                    buf.readBlockPos(),
                    buf.readEnum(DeployMode::class.java),
                    buf.readBlockPos(),
                    buf.readEnum(Rotation::class.java),
                    buf.readEnum(Mirror::class.java)
                )
            }
        )

        fun handle(payload: DeployerSettingsPacket, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val level = player.serverLevel()

                if (player.blockPosition().distSqr(payload.deployerPos) > 64.0) return@enqueueWork

                val be = level.getBlockEntity(payload.deployerPos) as? SchematicDeployerBlockEntity
                    ?: return@enqueueWork

                be.deployMode = payload.mode
                be.relativeOffset = payload.relativeOffset
                be.relativeRotation = payload.relativeRotation
                be.relativeMirror = payload.relativeMirror
                be.setChanged()
                be.sendData()
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
