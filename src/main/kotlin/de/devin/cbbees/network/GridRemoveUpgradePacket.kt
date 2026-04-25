package de.devin.cbbees.network

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.backpack.BeehiveContainer
import de.devin.cbbees.content.upgrades.UpgradeGrid
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * Client → Server: remove an upgrade from the backpack grid and return it to the player.
 */
class GridRemoveUpgradePacket(
    val gridX: Int,
    val gridY: Int
) : CustomPacketPayload {

    companion object {
        val TYPE = CustomPacketPayload.Type<GridRemoveUpgradePacket>(
            CreateBuzzyBeez.asResource("grid_remove_upgrade")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, GridRemoveUpgradePacket> = StreamCodec.of(
            { buf, p ->
                buf.writeVarInt(p.gridX)
                buf.writeVarInt(p.gridY)
            },
            { buf ->
                GridRemoveUpgradePacket(buf.readVarInt(), buf.readVarInt())
            }
        )

        fun handle(payload: GridRemoveUpgradePacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                val menu = player.containerMenu as? BeehiveContainer ?: return@enqueueWork

                // Only allow removal when cursor is empty
                if (!menu.carried.isEmpty) return@enqueueWork

                val backpackStack = menu.backpackStack
                val grid = backpackStack.get(AllDataComponents.UPGRADE_GRID.get())?.copy() ?: return@enqueueWork

                val removed = grid.removeAt(payload.gridX, payload.gridY) ?: return@enqueueWork

                // Put the upgrade item on the cursor
                val returnStack = when (removed.type) {
                    de.devin.cbbees.content.upgrades.UpgradeType.RAPID_WINGS -> ItemStack(AllItems.RAPID_WINGS.get())
                    de.devin.cbbees.content.upgrades.UpgradeType.SWARM_INTELLIGENCE -> ItemStack(AllItems.SWARM_INTELLIGENCE.get())
                    de.devin.cbbees.content.upgrades.UpgradeType.HONEY_EFFICIENCY -> ItemStack(AllItems.HONEY_EFFICIENCY.get())
                    de.devin.cbbees.content.upgrades.UpgradeType.SOFT_TOUCH -> ItemStack(AllItems.SOFT_TOUCH.get())
                    de.devin.cbbees.content.upgrades.UpgradeType.DROP_ITEMS -> ItemStack(AllItems.DROP_ITEMS.get())
                    de.devin.cbbees.content.upgrades.UpgradeType.HONEY_TANK -> ItemStack(AllItems.HONEY_TANK.get())
                    de.devin.cbbees.content.upgrades.UpgradeType.DRONE_VIEW -> ItemStack(AllItems.DRONE_VIEW.get())
                    de.devin.cbbees.content.upgrades.UpgradeType.DRONE_RANGE -> ItemStack(AllItems.DRONE_RANGE.get())
                }

                menu.setCarried(returnStack)
                backpackStack.set(AllDataComponents.UPGRADE_GRID.get(), grid)
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
