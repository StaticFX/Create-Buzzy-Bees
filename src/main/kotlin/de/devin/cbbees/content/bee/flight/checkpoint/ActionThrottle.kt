package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.config.CBBeesConfig

object ActionThrottle {
    private const val INITIAL_COST_NANOS = 250_000.0
    private const val SMOOTHING = 0.05
    private const val MAX_PER_TICK = 1000

    private var spentNanos = 0L
    private var lastTick = -1L
    private var averageCostNanos = INITIAL_COST_NANOS

    private val budgetNanos: Long get() = (CBBeesConfig.blockOperationBudgetMs.get() * 1_000_000).toLong()

    val capacityPerTick: Int get() = (budgetNanos / averageCostNanos).toInt().coerceIn(1, MAX_PER_TICK)

    fun canExecute(gameTime: Long): Boolean {
        if (gameTime != lastTick) {
            lastTick = gameTime
            spentNanos = 0
        }
        return spentNanos < budgetNanos
    }

    fun <T> measure(operation: () -> T): T {
        val start = System.nanoTime()
        try {
            return operation()
        } finally {
            val cost = System.nanoTime() - start
            spentNanos += cost
            averageCostNanos += (cost - averageCostNanos) * SMOOTHING
        }
    }
}
