package de.devin.cbbees.content.domain.logistics

import java.util.UUID

interface Reservable<T> {

    fun reserve(beeId: UUID, resources: List<T>, tick: Long) {}

    fun releaseReservation(beeId: UUID) {}

    fun cleanupReservations(currentTick: Long, maxAge: Long = 600) {}

    fun clearReservations() {}
}
