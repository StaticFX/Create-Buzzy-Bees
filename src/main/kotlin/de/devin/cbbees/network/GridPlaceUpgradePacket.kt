package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.backpack.BeehiveContainer
import de.devin.cbbees.content.upgrades.BeeUpgradeItem
import de.devin.cbbees.content.upgrades.UpgradeGrid
import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client → Server: place an upgrade from the cursor (carried item) onto the backpack grid.
 */
class GridPlaceUpgradePacket(
    val gridX: Int,
    val gridY: Int,
    val rotation: Int
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<GridPlaceUpgradePacket>(
            CreateBuzzyBeez.asResource("grid_place_upgrade")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, GridPlaceUpgradePacket> = StreamCodec.of(
            { buf, p ->
                buf.writeVarInt(p.gridX)
                buf.writeVarInt(p.gridY)
                buf.writeVarInt(p.rotation)
            },
            { buf ->
                GridPlaceUpgradePacket(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
                )
            }
        )

        fun handle(payload: GridPlaceUpgradePacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                val menu = player.containerMenu as? BeehiveContainer ?: return@enqueueWork

                // The upgrade is on the cursor (carried item)
                val carried = menu.carried
                val upgradeItem = carried.item as? BeeUpgradeItem ?: return@enqueueWork

                val backpackStack = menu.backpackStack
                val grid = backpackStack.get(AllDataComponents.UPGRADE_GRID.get())?.copy() ?: UpgradeGrid()

                if (grid.canPlace(upgradeItem.upgradeType, payload.gridX, payload.gridY, payload.rotation)) {
                    grid.place(upgradeItem.upgradeType, payload.gridX, payload.gridY, payload.rotation)
                    carried.shrink(1)
                    backpackStack.set(AllDataComponents.UPGRADE_GRID.get(), grid)
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
