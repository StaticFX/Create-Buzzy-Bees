package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.compat.sable.SableRenderSupport
import de.devin.cbbees.content.deployer.DeployMode
import de.devin.cbbees.content.deployer.SchematicDeployerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client -> Server packet sent when the player changes deploy mode,
 * relative offset, or rotation/mirror overrides in the
 * Schematic Deployer GUI.
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

        val STREAM_CODEC:
            StreamCodec<RegistryFriendlyByteBuf, DeployerSettingsPacket> =
            StreamCodec.of(
                { buf, packet ->
                    buf.writeBlockPos(packet.deployerPos)
                    buf.writeEnum(packet.mode)
                    buf.writeBlockPos(packet.relativeOffset)
                    buf.writeEnum(packet.relativeRotation)
                    buf.writeEnum(packet.relativeMirror)
                },
                { buf ->
                    DeployerSettingsPacket(
                        deployerPos = buf.readBlockPos(),
                        mode = buf.readEnum(DeployMode::class.java),
                        relativeOffset = buf.readBlockPos(),
                        relativeRotation = buf.readEnum(Rotation::class.java),
                        relativeMirror = buf.readEnum(Mirror::class.java)
                    )
                }
            )

        fun handle(
            payload: DeployerSettingsPacket,
            context: IPayloadContext
        ) {
            context.enqueueWork {
                val player =
                    context.player() as? ServerPlayer
                        ?: return@enqueueWork

                val level = player.serverLevel()

                /*
                 * distSqr() and distanceToSqr() return squared distance.
                 *
                 * The old check used:
                 *
                 *     distanceSquared > 64.0
                 *
                 * which only allowed approximately 8 actual blocks.
                 *
                 * Sable sublevels may also use a different coordinate space,
                 * so use Sable's sublevel-aware distance calculation when
                 * available, with normal Minecraft distance as fallback.
                 */
                val deployerCenter =
                    Vec3.atCenterOf(payload.deployerPos)

                val distanceSquared =
                    SableRenderSupport.distanceSquaredWithSubLevels(
                        level,
                        player.position(),
                        deployerCenter
                    ) ?: player.position()
                        .distanceToSqr(deployerCenter)

                val maximumDistance = 64.0
                val maximumDistanceSquared =
                    maximumDistance * maximumDistance

                if (distanceSquared > maximumDistanceSquared) {
                    return@enqueueWork
                }

                val deployer =
                    level.getBlockEntity(payload.deployerPos)
                        as? SchematicDeployerBlockEntity
                        ?: return@enqueueWork

                deployer.deployMode = payload.mode
                deployer.relativeOffset = payload.relativeOffset
                deployer.relativeRotation = payload.relativeRotation
                deployer.relativeMirror = payload.relativeMirror

                deployer.setChanged()
                deployer.sendData()
            }
        }
    }

    override fun type():
        CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}