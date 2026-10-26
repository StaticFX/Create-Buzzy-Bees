package de.devin.cbbees.content.logistics.storage

import com.simibubi.create.AllBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.IItemHandler

/** A creative crate hands out any item in any amount. */
class CreativeCrateStorage(handler: IItemHandler) : ItemHandlerStorage(handler) {

    override fun count(stack: ItemStack): Int = Int.MAX_VALUE

    override fun extract(stack: ItemStack): Boolean = true

    companion object : PortStorageProvider {
        override fun find(level: Level, pos: BlockPos, side: Direction): PortStorage? {
            if (!AllBlocks.CREATIVE_CRATE.has(level.getBlockState(pos))) return null
            return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)?.let(::CreativeCrateStorage)
        }
    }
}
