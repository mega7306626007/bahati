package com.pesaflow.app.ui.dashboard

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.money.NON_STAT_SOURCES

// Payday-spend timing: does money burn the weekend it lands, or stretch?
// Every expense is tied to its most recent payday (income rows only —
// opening cash and samples were never earned). Lags over 30 days are
// unattributed (different money era). Needs 1+ paydays and 3+ attributed
// expenses, otherwise there is no pattern yet — just noise.
data class PaydaySpend(
    val paydays: Int,
    val attributed: Int,
    val pctWithin3: Double,
    val medianLagDays: Double
)

internal fun paydaySpend(txs: List<Transaction>): PaydaySpend? {
    val day = 24L * 60 * 60 * 1000
    val incomes = txs
        .filter { it.type == TransactionType.INCOME && it.source !in NON_STAT_SOURCES }
        .map { it.dateTimestamp }
        .sorted()
    if (incomes.isEmpty()) return null
    val expenses = txs
        .filter { it.type == TransactionType.EXPENSE }
        .sortedBy { it.dateTimestamp }
    if (expenses.size < 3) return null
    val lags = expenses.mapNotNull { e ->
        val prev = incomes.lastOrNull { it <= e.dateTimestamp } ?: return@mapNotNull null
        val lag = (e.dateTimestamp - prev).toDouble() / day
        if (lag <= 30.0) lag else null
    }
    if (lags.size < 3) return null
    val sorted = lags.sorted()
    val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
    else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    val pct3 = sorted.count { it <= 3.0 } * 100.0 / sorted.size
    return PaydaySpend(incomes.size, sorted.size, pct3, median)
}
