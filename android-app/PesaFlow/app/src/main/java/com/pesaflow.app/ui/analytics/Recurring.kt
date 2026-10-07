package com.pesaflow.app.ui.analytics

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType

// Recurring money: subscriptions, rent-like rhythms, payday cycles — learned
// from the ledger, never declared. Same math feeds Buddy, Insights and Bills.
data class RecurringHit(
    val merchant: String,
    val avgAmount: Double,
    val medianGapDays: Long,
    val lastSeen: Long,
    val times: Int
) {
    val isMonthly: Boolean get() = medianGapDays in 25..35
    val isWeekly: Boolean get() = medianGapDays in 6..8
    fun nextDue(): Long = lastSeen + medianGapDays * 24L * 60 * 60 * 1000
}

private const val DAY_MS = 24L * 60 * 60 * 1000

// Merchants hit 3+ times on a steady rhythm (weekly or monthly), amounts
// within ±35% of the mean. Returns loudest first.
fun detectRecurring(txs: List<Transaction>): List<RecurringHit> {
    return txs.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.merchant.trim().lowercase() }
        .mapNotNull { (key, list) ->
            if (list.size < 3) return@mapNotNull null
            val sorted = list.map { it.dateTimestamp }.sorted()
            val gaps = sorted.zipWithNext { a, b -> (b - a) / DAY_MS }
            if (gaps.isEmpty()) return@mapNotNull null
            val median = gaps.sorted()[gaps.size / 2]
            if (median !in 6..8 && median !in 25..35) return@mapNotNull null
            val avg = list.map { it.amount }.average()
            if (avg <= 0) return@mapNotNull null
            if (list.any { kotlin.math.abs(it.amount - avg) > avg * 0.35 + 1 }) return@mapNotNull null
            val label = list.maxByOrNull { it.dateTimestamp }?.merchant?.takeIf { it.isNotBlank() } ?: key
            RecurringHit(label, avg, median, sorted.maxOrNull() ?: 0L, list.size)
        }
        .sortedByDescending { it.avgAmount }
}

// Payday prediction: same INCOME sender on a monthly rhythm → next landing.
// Returns (merchant, amount, expectedTimestamp) soonest first.
fun predictPaydays(txs: List<Transaction>): List<Triple<String, Double, Long>> {
    val now = System.currentTimeMillis()
    return txs.filter { it.type == TransactionType.INCOME }
        .groupBy { it.merchant.trim().lowercase() }
        .mapNotNull { (_, list) ->
            if (list.size < 2) return@mapNotNull null
            val sorted = list.map { it.dateTimestamp }.sorted()
            val gaps = sorted.zipWithNext { a, b -> (b - a) / DAY_MS }
            if (gaps.isEmpty()) return@mapNotNull null
            val median = gaps.sorted()[gaps.size / 2]
            if (median !in 25..35) return@mapNotNull null
            val avg = list.map { it.amount }.average()
            if (avg <= 0) return@mapNotNull null
            val label = list.maxByOrNull { it.dateTimestamp }?.merchant?.takeIf { it.isNotBlank() } ?: "Income"
            Triple(label, avg, sorted.maxOrNull()!! + median * DAY_MS)
        }
        .filter { it.third > now - 7 * DAY_MS }
        .sortedBy { it.third }
}
