package de.devin.cbbees.content.domain.logistics

import de.devin.cbbees.util.ItemStackKey
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Manages item reservations for a port.
 *
 * Both [de.devin.cbbees.content.logistics.ports.LogisticPortBlockEntity] and
 * [de.devin.cbbees.content.logistics.transport.TransportPortBlockEntity] use identical
 * reservation tracking. This class extracts that shared logic.
 *
 * Maintains a pre-aggregated count map for O(1) lookups in the common case.
 */
class PortReservationManager {

    private data class Reservation(val items: List<ItemStack>, val tick: Long)

    private val reservations = mutableMapOf<UUID, Reservation>()

    // Pre-aggregated total reserved counts by item type, maintained incrementally
    private val aggregatedCounts = mutableMapOf<ItemStackKey, Int>()

    val hasReservations: Boolean get() = reservations.isNotEmpty()

    /**
     * Returns the total reserved count of [stack], optionally excluding one bee's reservation.
     */
    fun getReservedCount(stack: ItemStack, excludeBeeId: UUID? = null): Int {
        if (reservations.isEmpty()) return 0
        val key = ItemStackKey(stack)
        val total = aggregatedCounts[key] ?: return 0
        if (excludeBeeId == null) return total
        // Subtract the excluded bee's contribution
        val excluded = reservations[excludeBeeId]?.items
            ?.filter { ItemStack.isSameItemSameComponents(it, stack) }
            ?.sumOf { it.count } ?: 0
        return total - excluded
    }

    fun reserve(beeId: UUID, items: List<ItemStack>, tick: Long) {
        // Remove old reservation counts if overwriting
        reservations[beeId]?.let { old -> removeFromAggregated(old.items) }
        reservations[beeId] = Reservation(items, tick)
        addToAggregated(items)
    }

    fun release(beeId: UUID): Boolean {
        val removed = reservations.remove(beeId) ?: return false
        removeFromAggregated(removed.items)
        return true
    }

    fun cleanup(currentTick: Long, maxAge: Long = 600): Boolean {
        val sizeBefore = reservations.size
        val iter = reservations.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (currentTick - entry.value.tick > maxAge) {
                removeFromAggregated(entry.value.items)
                iter.remove()
            }
        }
        return reservations.size != sizeBefore
    }

    fun clear(): Boolean {
        val had = reservations.isNotEmpty()
        reservations.clear()
        aggregatedCounts.clear()
        return had
    }

    private fun addToAggregated(items: List<ItemStack>) {
        for (item in items) {
            val key = ItemStackKey(item)
            aggregatedCounts[key] = (aggregatedCounts[key] ?: 0) + item.count
        }
    }

    private fun removeFromAggregated(items: List<ItemStack>) {
        for (item in items) {
            val key = ItemStackKey(item)
            val newCount = (aggregatedCounts[key] ?: 0) - item.count
            if (newCount <= 0) {
                aggregatedCounts.remove(key)
            } else {
                aggregatedCounts[key] = newCount
            }
        }
    }
}
