package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.config.CBBeesConfig

/**
 * Per-tick time budget all bees together may spend placing and breaking blocks.
 *
 * The budget never drops below [CBBeesConfig.blockOperationBudgetMs]. With
 * [CBBeesConfig.adaptiveBlockBudget] on, [adjust] grows it while bees are waiting for it and the
 * server's average tick time is under [CBBeesConfig.targetTickMs], and shrinks it again when the
 * tick time goes over, so large builds use spare tick time without pushing a busy server further.
 */
object ActionThrottle {
    private const val INITIAL_COST_NANOS = 250_000.0
    private const val SMOOTHING = 0.05
    private const val MAX_PER_TICK = 1000

    /** Share of the gap to the target tick time added per tick; small, as the server's average lags behind. */
    private const val GROW_GAIN = 0.01
    /** Share of the overshoot taken off per tick; larger, so lag is handed back quickly. */
    private const val SHRINK_GAIN = 0.05
    private const val SATURATED_SHARE = 0.9

    private var spentNanos = 0L
    private var lastTick = -1L
    private var averageCostNanos = INITIAL_COST_NANOS
    private var denied = false
    private var adaptiveBudgetNanos = 0L

    private val minBudgetNanos: Long get() = msToNanos(CBBeesConfig.blockOperationBudgetMs.get())

    private val budgetNanos: Long
        get() = if (CBBeesConfig.adaptiveBlockBudget.get()) maxOf(adaptiveBudgetNanos, minBudgetNanos) else minBudgetNanos

    val capacityPerTick: Int get() = (budgetNanos / averageCostNanos).toInt().coerceIn(1, MAX_PER_TICK)

    /** Moves the budget toward the target tick time; called once per server tick, before bees act. */
    fun adjust(averageTickNanos: Long) {
        val budget = budgetNanos
        val saturated = denied || spentNanos >= budget * SATURATED_SHARE
        denied = false
        adaptiveBudgetNanos = nextBudget(budget, averageTickNanos, saturated, minBudgetNanos, msToNanos(CBBeesConfig.targetTickMs.get()))
    }

    fun canExecute(gameTime: Long): Boolean {
        if (gameTime != lastTick) {
            lastTick = gameTime
            spentNanos = 0
        }
        return (spentNanos < budgetNanos).also { if (!it) denied = true }
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

    /**
     * The next budget: shrinks when the server is over [targetNanos], grows when it is under and the
     * budget was used up ([saturated]), and stays between [minNanos] and [targetNanos].
     */
    fun nextBudget(budget: Long, averageTickNanos: Long, saturated: Boolean, minNanos: Long, targetNanos: Long): Long {
        val gap = (targetNanos - averageTickNanos).toDouble()
        val step = when {
            gap < 0 -> gap * SHRINK_GAIN
            saturated -> gap * GROW_GAIN
            else -> 0.0
        }
        return (budget + step.toLong()).coerceIn(minNanos, maxOf(minNanos, targetNanos))
    }

    private fun msToNanos(ms: Double) = (ms * 1_000_000).toLong()
}
