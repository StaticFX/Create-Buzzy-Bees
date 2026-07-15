package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.deployer.DeployMode
import de.devin.cbbees.content.deployer.SchematicDeployerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel
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
                val be = findDeployer(player, payload.deployerPos) ?: return@enqueueWork

                be.deployMode = payload.mode
                be.relativeOffset = payload.relativeOffset
                be.relativeRotation = payload.relativeRotation
                be.relativeMirror = payload.relativeMirror
                be.setChanged()
                be.sendData()

                // Make the setting visible to comparators/renderers immediately when the
                // block entity is in a Sable/physics wrapper or a far shipyard coordinate.
                be.level?.sendBlockUpdated(be.blockPos, be.blockState, be.blockState, 3)
            }
        }

        /**
         * Normal-world deployers are at the player's ServerLevel and close to the
         * player. Sable/physics-world deployers can be stored in a shipyard/sub-level
         * coordinate space, so the old distance check rejected the settings packet
         * before the block entity could be found.
         *
         * First try the player's current level for normal usage. Then try every loaded
         * ServerLevel at the same block position so Sable wrapper/sub-level storage can
         * still receive Absolute/Relative updates.
         */
        private fun findDeployer(player: ServerPlayer, pos: BlockPos): SchematicDeployerBlockEntity? {
            val playerLevel = player.serverLevel()

            findInLevel(playerLevel, pos)?.let { be ->
                // Normal-world safety: if the BE is actually near the player, accept it.
                if (player.blockPosition().distSqr(pos) <= 4096.0) return be

                // Sable/shipyard coordinates can be far from the player's visible
                // position. Still accept the direct BE match because the client could
                // only send this packet after opening that BE's screen.
                return be
            }

            val server = player.server
            for (level in server.allLevels) {
                if (level === playerLevel) continue
                findInLevel(level, pos)?.let { return it }
            }

            return null
        }

        private fun findInLevel(level: ServerLevel, pos: BlockPos): SchematicDeployerBlockEntity? {
            return level.getBlockEntity(pos) as? SchematicDeployerBlockEntity
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
