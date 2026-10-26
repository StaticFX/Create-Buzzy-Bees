package de.devin.cbbees.compat.ae2

import appeng.api.AECapabilities
import appeng.api.config.Actionable
import appeng.api.networking.IGridNode
import appeng.api.networking.security.IActionSource
import appeng.api.stacks.AEItemKey
import appeng.api.storage.MEStorage
import de.devin.cbbees.content.logistics.storage.CraftResult
import de.devin.cbbees.content.logistics.storage.PortStorage
import de.devin.cbbees.content.logistics.storage.PortStorageProvider
import de.devin.cbbees.content.logistics.storage.toIntClamped
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/** The ME network behind an ME Interface. */
class MEPortStorage(private val level: Level, private val storage: MEStorage, private val node: IGridNode?) : PortStorage {

    private val source = IActionSource.empty()

    override fun stacks(): List<ItemStack> = storage.availableStacks.mapNotNull { entry ->
        (entry.key as? AEItemKey)?.toStack(entry.longValue.toIntClamped())
    }

    override fun count(stack: ItemStack): Int {
        val key = AEItemKey.of(stack) ?: return 0
        return storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source).toIntClamped()
    }

    override fun extract(stack: ItemStack): Boolean {
        val key = AEItemKey.of(stack) ?: return false
        val amount = stack.count.toLong()
        if (storage.extract(key, amount, Actionable.SIMULATE, source) < amount) return false
        return storage.extract(key, amount, Actionable.MODULATE, source) == amount
    }

    override fun insert(stack: ItemStack, simulate: Boolean): ItemStack {
        val key = AEItemKey.of(stack) ?: return stack
        val inserted = storage.insert(key, stack.count.toLong(), Actionable.ofSimulate(simulate), source)
        return stack.copyWithCount(stack.count - inserted.toInt())
    }

    override fun hasSpace(): Boolean = true

    override val canCraft: Boolean get() = node != null

    override fun isCrafting(stack: ItemStack): Boolean {
        val key = AEItemKey.of(stack) ?: return false
        return node?.grid?.craftingService?.isRequesting(key) == true
    }

    override fun craft(stack: ItemStack, amount: Int): CraftResult {
        val node = node ?: return CraftResult.UNAVAILABLE
        val key = AEItemKey.of(stack) ?: return CraftResult.UNAVAILABLE
        if (!node.grid.craftingService.isCraftable(key)) return CraftResult.UNAVAILABLE
        return MECrafting.request(level, node, key, amount.toLong())
    }

    companion object : PortStorageProvider {
        override fun find(level: Level, pos: BlockPos, side: Direction): PortStorage? {
            val storage = level.getCapability(AECapabilities.ME_STORAGE, pos, side) ?: return null
            val node = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null)?.getGridNode(side)
            return MEPortStorage(level, storage, node)
        }
    }
}
