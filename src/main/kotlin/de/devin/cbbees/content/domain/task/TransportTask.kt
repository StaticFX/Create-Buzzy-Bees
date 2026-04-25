package de.devin.cbbees.content.domain.task

import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack

/**
 * Represents a logistics transport task for BumbleBees.
 *
 * @property sourcePos The EXTRACT port position to pick up items from.
 * @property targetPos The INSERT port position to deliver items to.
 * @property items The items to transport.
 * @property returningOverflow When true, the bee is returning overflow items to the
 *   original provider after the requester couldn't accept them all.
 */
data class TransportTask(
    val sourcePos: BlockPos,
    val targetPos: BlockPos,
    val items: List<ItemStack>,
    val returningOverflow: Boolean = false
)
