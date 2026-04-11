package de.devin.cbbees.util

import de.devin.cbbees.CreateBuzzyBeez

/**
 * Defers callbacks to the *next* server tick, unlike [net.minecraft.server.MinecraftServer.execute]
 * which drains its queue inside the current tick (so chained `execute { execute { ... } }` calls
 * all run back-to-back without yielding).
 *
 * Used to spread long-running calculations across server ticks without blocking. Each call to
 * [nextTick] adds a single continuation; [runScheduled] (called once per server tick by
 * `CCRServerEvents.onServerTick`) snapshots and drains the current list. Anything scheduled
 * *during* a callback lands in a fresh list and runs on the following tick.
 */
@ServerSide
object ServerTickScheduler {

    private val pending = mutableListOf<() -> Unit>()

    /** Schedule [callback] to run on the next server tick. Thread-safe. */
    fun nextTick(callback: () -> Unit) {
        synchronized(pending) {
            pending.add(callback)
        }
    }

    /** Drains all currently-pending callbacks exactly once. Called from the server tick event. */
    fun runScheduled() {
        val toRun = synchronized(pending) {
            if (pending.isEmpty()) return
            val copy = pending.toList()
            pending.clear()
            copy
        }
        for (runnable in toRun) {
            try {
                runnable.invoke()
            } catch (t: Throwable) {
                CreateBuzzyBeez.LOGGER.error("ServerTickScheduler task failed", t)
            }
        }
    }

    /** Clears all pending tasks. Called on server stop. */
    fun clear() {
        synchronized(pending) { pending.clear() }
    }
}
