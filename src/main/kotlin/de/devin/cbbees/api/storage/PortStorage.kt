package de.devin.cbbees.api.storage

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/** The item storage behind a port: a plain inventory or a whole storage network. */
interface PortStorage {

    /** Every stack the storage holds. */
    fun stacks(): List<ItemStack>

    /** How many items matching [stack] are stored. */
    fun count(stack: ItemStack): Int

    /** Extracts exactly [stack]'s count, or nothing at all. */
    fun extract(stack: ItemStack): Boolean

    /** @return the part of [stack] that did not fit */
    fun insert(stack: ItemStack, simulate: Boolean = false): ItemStack

    /** Whether at least one more item would fit. */
    fun hasSpace(): Boolean

    /** Whether the storage can autocraft missing items. */
    val canCraft: Boolean get() = false

    /** Whether a craft for [stack] is already running. */
    fun isCrafting(stack: ItemStack): Boolean = false

    /** Requests [amount] of [stack] to be crafted. */
    fun craft(stack: ItemStack, amount: Int): CraftResult = CraftResult.UNAVAILABLE
}

/** Outcome of [PortStorage.craft]. */
enum class CraftResult {
    CRAFTING,

    /** The storage is still working out whether it can craft the item. */
    PLANNING,
    UNAVAILABLE
}

/** Finds the [PortStorage] a port facing [side] at [pos] is attached to, or null if it isn't yours. */
fun interface PortStorageProvider {
    fun find(level: Level, pos: BlockPos, side: Direction): PortStorage?
}
