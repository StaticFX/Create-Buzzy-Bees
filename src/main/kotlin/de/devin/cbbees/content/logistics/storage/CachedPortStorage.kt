package de.devin.cbbees.content.logistics.storage

import de.devin.cbbees.util.ItemStackKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Remembers what a storage holds until something is inserted or extracted through it.
 *
 * Dispatch, flight planning and stuck detection ask the same ports about the same items many
 * times per tick. Counting means scanning every slot of a vault or querying a whole ME / RS
 * network, so each answer is computed once. Changes made through another port on the same
 * storage are only seen once the cache is rebuilt next tick (see [PortStorageCache]).
 */
class CachedPortStorage(private val storage: PortStorage) : PortStorage by storage {

    private val counts = HashMap<ItemStackKey, Int>()
    private var stacks: List<ItemStack>? = null
    private var hasSpace: Boolean? = null

    override fun stacks(): List<ItemStack> = stacks ?: storage.stacks().also { stacks = it }

    override fun count(stack: ItemStack): Int =
        counts.getOrPut(ItemStackKey(stack.copyWithCount(1))) { storage.count(stack) }

    override fun hasSpace(): Boolean = hasSpace ?: storage.hasSpace().also { hasSpace = it }

    override fun extract(stack: ItemStack): Boolean = storage.extract(stack).also { if (it) invalidate() }

    override fun insert(stack: ItemStack, simulate: Boolean): ItemStack =
        storage.insert(stack, simulate).also { if (!simulate) invalidate() }

    private fun invalidate() {
        counts.clear()
        stacks = null
        hasSpace = null
    }
}

/** Resolves a port's storage at most once per tick and caches what it holds for that tick. */
class PortStorageCache {
    private var tick = Long.MIN_VALUE
    private var storage: PortStorage? = null

    fun get(level: Level, resolve: () -> PortStorage?): PortStorage? {
        if (level.gameTime != tick) {
            tick = level.gameTime
            storage = resolve()?.let(::CachedPortStorage)
        }
        return storage
    }
}
