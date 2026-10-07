package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionType

// Post-scan patterns, computed ONCE per scan and shared by every readout
// (step-2 cards, insights, prefills). One analysis, many mouths — no screen
// re-derives its own version of "your payday".
data class PaydayInfo(val who: String, val amount: Int, val dayOfMonth: Int, val count: Int)

data class ScanPatterns(
    val payday: PaydayInfo?,
    val topExpense: List<Pair<String, Int>>,
    val biggestOut: Pair<String, Int>?,
    val totalIn: Int,
    val spanDays: Int
)

private fun dayOfMonth(ts: Long): Int =
    java.util.Calendar.getInstance().apply { timeInMillis = ts }.get(java.util.Calendar.DAY_OF_MONTH)

fun analyzeScan(parsed: List<PendingTransaction>): ScanPatterns {
    if (parsed.isEmpty()) return ScanPatterns(null, emptyList(), null, 0, 0)
    // Payday: the repeat income sender with the biggest total. Needs 2+
    // sightings — one HELB ping is money, not a pattern.
    val payday = parsed.filter { it.type == TransactionType.INCOME }
        .groupBy { normalizeContact(it.merchant.ifBlank { "Unknown" }) }
        .values.filter { it.size >= 2 }
        .maxByOrNull { g -> g.sumOf { it.amount } }
        ?.let { g ->
            val amts = g.map { it.amount }.sorted()
            val med = if (amts.size % 2 == 1) amts[amts.size / 2]
            else (amts[amts.size / 2 - 1] + amts[amts.size / 2]) / 2
            val dom = g.groupingBy { dayOfMonth(it.dateTimestamp) }.eachCount()
                .maxByOrNull { it.value }?.key ?: 1
            PaydayInfo(g.first().merchant.trim(), med.toInt(), dom, g.size)
        }
    val outs = parsed.filter { it.type != TransactionType.INCOME }
    val top = outs.groupBy { it.category.ifBlank { "Unknown" } }
        .mapValues { e -> e.value.sumOf { it.amount }.toInt() }
        .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }
    val biggest = outs.maxByOrNull { it.amount }
        ?.let { (it.displayMerchant.ifBlank { it.merchant }) to it.amount.toInt() }
    val totalIn = parsed.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }.toInt()
    // Distinct calendar days, not (max − min)/24h: midnight clusterers count.
    val span = com.pesaflow.app.data.academic.distinctDays(parsed.map { it.dateTimestamp })
    return ScanPatterns(payday, top, biggest, totalIn, span)
}
