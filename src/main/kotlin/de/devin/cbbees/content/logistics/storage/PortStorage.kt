package de.devin.cbbees.content.logistics.storage

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * The item storage a port is attached to: a plain inventory, or a whole storage network.
 */
interface PortStorage {

    fun stacks(): List<ItemStack>

    fun count(stack: ItemStack): Int

    /** Extracts exactly [stack]'s count, or nothing at all. */
    fun extract(stack: ItemStack): Boolean

    /** @return the part of [stack] that did not fit */
    fun insert(stack: ItemStack, simulate: Boolean = false): ItemStack

    fun hasSpace(): Boolean

    val canCraft: Boolean get() = false

    fun isCrafting(stack: ItemStack): Boolean = false

    fun craft(stack: ItemStack, amount: Int): CraftResult = CraftResult.UNAVAILABLE
}

enum class CraftResult {
    CRAFTING,

    /** The storage is still working out whether it can craft the item. */
    PLANNING,
    UNAVAILABLE
}

fun interface PortStorageProvider {
    fun find(level: Level, pos: BlockPos, side: Direction): PortStorage?
}

fun Long.toIntClamped(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
