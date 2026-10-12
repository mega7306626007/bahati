package com.pesaflow.app.ui.dashboard

import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.LedgerRow
import com.pesaflow.app.data.time.startOfDay
import java.util.Calendar

// Weekday-aware pacing: Saturdays that run hot earn a bigger slice, quiet
// Tuesdays a smaller one. Factors learned per weekday from recent history,
// calendar-accurate (a 10-day history weights each weekday by its actual
// occurrences, not by 4). Null until real history exists — ×1.0 meanwhile.
// Pure logic; the card only renders.
private const val PACE_DAY_MS = 24L * 60 * 60 * 1000

/** Monday-first index 0..6. */
fun weekdayIndex(ts: Long): Int {
    val dow = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK)
    return (dow + 5) % 7
}

data class WeekdayProfile(
    /** Per-weekday spend multipliers, Monday-first, clamped 0.6..1.4. */
    val factors: DoubleArray,
    /** Overall daily mean over the window. */
    val dailyMean: Double,
    /** Distinct days with any expense in-window. */
    val activeDays: Int
) {
    fun factorFor(ts: Long): Double = factors[weekdayIndex(ts)]
}

/**
 * Learn weekday factors from EXPENSE rows inside [now - windowDays, now].
 * Null when history is thinner than minDays distinct days or totals zero —
 * the card then paces flat instead of inventing rhythms.
 */
fun weekdayProfile(
    rows: List<LedgerRow>,
    now: Long,
    windowDays: Int = 28,
    minDays: Int = 7
): WeekdayProfile? {
    val sums = DoubleArray(7)
    val occ = IntArray(7)
    // Occurrences: each calendar day in the window counts for its weekday,
    // so partial windows don't dilute quiet days.
    for (off in 0 until windowDays) {
        occ[weekdayIndex(now - off * PACE_DAY_MS)]++
    }
    val activeSeen = BooleanArray(windowDays)
    rows.forEach {
        if (it.type != TransactionType.EXPENSE || it.amount <= 0) return@forEach
        // Bucket-consistent window: a row counts for sums AND activity only
        // when its calendar day is inside — the old (now - ts) / 24h bucket
        // misfiled edge-day rows whenever the time-of-day differed.
        val dayIdx = ((startOfDay(now) - startOfDay(it.ts)) / PACE_DAY_MS).toInt()
        if (dayIdx < 0 || dayIdx >= windowDays) return@forEach
        sums[weekdayIndex(it.ts)] += it.amount
        activeSeen[dayIdx] = true
    }
    val activeDays: Int = activeSeen.count { it }
    val totalOcc = occ.sum()
    val total = sums.sum()
    if (totalOcc < minDays || activeDays < minDays || total <= 0) return null
    val mean = total / totalOcc
    if (mean <= 0) return null
    val factors = DoubleArray(7) { d ->
        if (occ[d] == 0) 1.0 else (sums[d] / occ[d] / mean).coerceIn(0.6, 1.4)
    }
    return WeekdayProfile(factors, mean, activeDays)
}

/** Null-safe accessor for call sites: no profile means flat pacing. */
fun factorForToday(profile: WeekdayProfile?, ts: Long): Double = profile?.factorFor(ts) ?: 1.0

/** Unusual-day flag: spent 2x+ over today's expectation with a 200+ gap. */
fun isUnusualDay(spentToday: Double, expectedToday: Double): Boolean {
    return spentToday > 2 * expectedToday && spentToday - expectedToday >= 200
}
