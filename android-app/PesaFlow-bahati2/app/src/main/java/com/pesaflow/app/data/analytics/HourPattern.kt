package com.pesaflow.app.data.analytics

import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.LedgerRow
import java.util.Calendar

// Hourly pattern: which 3-hour window owns the outflow? Pure, unit-tested.
// Needs 5+ expenses and a 35%+ share to speak — thin or flat data stays silent.
data class HourPattern(
    val peakStartHour: Int,
    val sharePct: Int,
    val count: Int
)

fun hourlyPeak(rows: List<LedgerRow>): HourPattern? {
    val expenses = rows.filter { it.type == TransactionType.EXPENSE && it.amount > 0 }
    if (expenses.size < 5) return null
    val total = expenses.sumOf { it.amount }
    if (total <= 0) return null
    val byHour = IntArray(24)
    val sums = DoubleArray(24)
    expenses.forEach { e ->
        val h = Calendar.getInstance().apply { timeInMillis = e.ts }.get(Calendar.HOUR_OF_DAY)
        byHour[h]++
        sums[h] += e.amount
    }
    // Best contiguous 3-hour window by money (wraps midnight).
    var bestStart = 0
    var bestSum = -1.0
    for (start in 0 until 24) {
        var s = 0.0
        for (k in 0 until 3) s += sums[(start + k) % 24]
        if (s > bestSum) {
            bestSum = s
            bestStart = start
        }
    }
    val share = (bestSum / total * 100).toInt()
    if (share < 35) return null
    return HourPattern(bestStart, share, expenses.size)
}

// "13" → "1pm", "0" → "12am" — insight copy reads clock, not integers.
fun hourLabel(h: Int): String {
    val hh = ((h % 24) + 24) % 24
    return when (hh) {
        0 -> "12am"
        12 -> "12pm"
        in 1..11 -> "${hh}am"
        else -> "${hh - 12}pm"
    }
}
