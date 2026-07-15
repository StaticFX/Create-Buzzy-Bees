package de.devin.cbbees.content.bee

import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.player.Player
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.neoforged.neoforge.items.ItemHandlerHelper

/**
 * Abstraction for finding and extracting materials from various sources.
 */
interface MaterialSource {
    /**
     * Tries to find and extract the required item from this source.
     * @param required The item stack representing the type of item needed.
     * @param amount The maximum amount to extract.
     * @return The extracted item stack.
     */
    fun extractItems(required: ItemStack, amount: Int): ItemStack

    /**
     * Tries to insert items into this source.
     * @param stack The item stack to insert.
     * @return The remaining items that could not be inserted.
     */
    fun insertItems(stack: ItemStack): ItemStack
}

/**
 * Sources materials from a player's inventory.
 */
class PlayerMaterialSource(private val player: Player) : MaterialSource {
    override fun extractItems(required: ItemStack, amount: Int): ItemStack {
        for (i in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(i)
            if (ItemStack.isSameItemSameComponents(stack, required)) {
                return player.inventory.removeItem(i, amount)
            }
        }
        return ItemStack.EMPTY
    }

    override fun insertItems(stack: ItemStack): ItemStack {
        val copy = stack.copy()
        if (player.inventory.add(copy)) {
            return ItemStack.EMPTY
        }
        return copy
    }
}

/**
 * Sources materials from external inventories via Wireless Link.
 */
class WirelessMaterialSource(private val level: Level, private val positions: List<BlockPos>) : MaterialSource {
    override fun extractItems(required: ItemStack, amount: Int): ItemStack {
        for (pos in positions) {
            val handler = de.devin.cbbees.compat.NeoForgeCapabilityCompat.getUnsidedItemHandler(level, pos)
            if (handler != null) {
                for (slot in 0 until handler.slots) {
                    val stack = handler.getStackInSlot(slot)
                    if (ItemStack.isSameItemSameComponents(stack, required)) {
                        return handler.extractItem(slot, amount, false)
                    }
                }
            }
        }
        return ItemStack.EMPTY
    }

    override fun insertItems(stack: ItemStack): ItemStack {
        var current = stack
        for (pos in positions) {
            val handler = de.devin.cbbees.compat.NeoForgeCapabilityCompat.getUnsidedItemHandler(level, pos)
            if (handler != null) {
                current = ItemHandlerHelper.insertItem(handler, current, false)
                if (current.isEmpty) return ItemStack.EMPTY
            }
        }
        return current
    }
}

/**
 * Combines multiple material sources, checking them in order.
 */
class CompositeMaterialSource(private val sources: List<MaterialSource>) : MaterialSource {
    override fun extractItems(required: ItemStack, amount: Int): ItemStack {
        for (source in sources) {
            val items = source.extractItems(required, amount)
            if (!items.isEmpty) return items
        }
        return ItemStack.EMPTY
    }

    override fun insertItems(stack: ItemStack): ItemStack {
        var current = stack
        for (source in sources) {
            current = source.insertItems(current)
            if (current.isEmpty) return ItemStack.EMPTY
        }
        return current
    }
}

/**
 * Helper object for generating adjacent block positions.
 */
object AdjacentPositions {
    /**
     * Gets all positions adjacent to a center position within a given range.
     * For range=1, this returns all 26 neighboring blocks (3x3x3 cube minus center).
     * 
     * @param center The center position.
     * @param range The range in blocks (default 1).
     * @return List of all adjacent positions.
     */
    fun getAdjacentPositions(center: BlockPos, range: Int = 1): List<BlockPos> {
        val positions = mutableListOf<BlockPos>()
        for (x in -range..range) {
            for (y in -range..range) {
                for (z in -range..range) {
                    if (x != 0 || y != 0 || z != 0) {
                        positions.add(center.offset(x, y, z))
                    }
                }
            }
        }
        return positions
    }
    
    /**
     * Gets the 6 directly adjacent positions (faces only, no diagonals).
     */
    fun getDirectlyAdjacentPositions(center: BlockPos): List<BlockPos> {
        return listOf(
            center.above(),
            center.below(),
            center.north(),
            center.south(),
            center.east(),
            center.west()
        )
    }
}

/**
 * Caches inventory handlers for adjacent blocks to avoid scanning every tick.
 * Rescans periodically to detect new or removed inventories.
 * 
 * @param center The center position to scan around.
 * @param level The level to scan in.
 * @param range The range in blocks (default 1 for 26 neighbors).
 * @param rescanInterval How often to rescan in game ticks (default 20 = 1 second).
 */
class AdjacentInventoryCache(
    private val center: BlockPos,
    private val level: Level,
    private val range: Int = 1,
    private val rescanInterval: Long = 20
) {
    private var cachedPositions: List<BlockPos>? = null
    private var lastScanTick: Long = 0
    
    /**
     * Gets positions of blocks with inventory capabilities.
     * Results are cached and refreshed periodically.
     */
    fun getInventoryPositions(): List<BlockPos> {
        val currentTick = level.gameTime
        if (cachedPositions == null || currentTick - lastScanTick > rescanInterval) {
            cachedPositions = scanForInventories()
            lastScanTick = currentTick
        }
        return cachedPositions ?: emptyList()
    }
    
    private fun scanForInventories(): List<BlockPos> {
        val positions = AdjacentPositions.getAdjacentPositions(center, range)
        return positions.filter { pos ->
            de.devin.cbbees.compat.NeoForgeCapabilityCompat.getUnsidedItemHandler(level, pos) != null
        }
    }
    
    /**
     * Creates a WirelessMaterialSource using the cached inventory positions.
     */
    fun createMaterialSource(): WirelessMaterialSource {
        return WirelessMaterialSource(level, getInventoryPositions())
    }
    
    /**
     * Forces a rescan on the next access.
     */
    fun invalidateCache() {
        cachedPositions = null
    }
}
