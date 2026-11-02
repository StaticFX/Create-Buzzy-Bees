package de.devin.cbbees.content.domain.logistics

import de.devin.cbbees.util.FluidStackKey
import de.devin.cbbees.util.ItemStackKey
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.fluids.FluidStack
import java.util.UUID

class ReservationManager<T>(
    private val keyOf: (T) -> Any,
    private val amountOf: (T) -> Int,
) {

    private class Reservation<T>(val resources: List<T>, val tick: Long)

    private val reservations = mutableMapOf<UUID, Reservation<T>>()
    private val totals = mutableMapOf<Any, Int>()

    val hasReservations: Boolean get() = reservations.isNotEmpty()

    fun getReservedAmount(resource: T, excludeBeeId: UUID? = null): Int {
        if (reservations.isEmpty()) return 0
        val key = keyOf(resource)
        val total = totals[key] ?: return 0
        val excluded = excludeBeeId?.let(reservations::get)?.resources
            ?.filter { keyOf(it) == key }
            ?.sumOf(amountOf) ?: 0
        return total - excluded
    }

    fun reserve(beeId: UUID, resources: List<T>, tick: Long) {
        reservations[beeId]?.let { removeFromTotals(it.resources) }
        reservations[beeId] = Reservation(resources, tick)
        addToTotals(resources)
    }

    fun release(beeId: UUID): Boolean {
        val removed = reservations.remove(beeId) ?: return false
        removeFromTotals(removed.resources)
        return true
    }

    fun cleanup(currentTick: Long, maxAge: Long = 600): Boolean {
        val expired = reservations.filterValues { currentTick - it.tick > maxAge }.keys
        expired.forEach(::release)
        return expired.isNotEmpty()
    }

    fun clear(): Boolean {
        val had = reservations.isNotEmpty()
        reservations.clear()
        totals.clear()
        return had
    }

    private fun addToTotals(resources: List<T>) {
        resources.forEach { totals.merge(keyOf(it), amountOf(it), Int::plus) }
    }

    private fun removeFromTotals(resources: List<T>) {
        resources.forEach { resource ->
            val key = keyOf(resource)
            val left = (totals[key] ?: 0) - amountOf(resource)
            if (left <= 0) totals.remove(key) else totals[key] = left
        }
    }

    companion object {
        fun forItems() = ReservationManager<ItemStack>(::ItemStackKey, ItemStack::getCount)

        fun forFluids() = ReservationManager<FluidStack>(::FluidStackKey, FluidStack::getAmount)
    }
}
