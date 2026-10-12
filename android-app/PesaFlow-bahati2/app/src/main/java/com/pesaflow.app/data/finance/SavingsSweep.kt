package com.pesaflow.app.data.finance

// Nightly savings sweep: pure math, no Android. The worker feeds today's
// spend and the daily target; this decides whether to offer a sweep, flag
// an overrun, or stay silent. Silence is a first-class answer: without a
// real target there is nothing honest to say, and dust (under KSh 10)
// never triggers a notification.
sealed interface SweepVerdict {
    data class Sweep(val surplus: Double) : SweepVerdict
    data class Over(val overage: Double) : SweepVerdict
    data object Silent : SweepVerdict
}

fun sweepVerdict(spentToday: Double, dailyTarget: Double?, dust: Double = 10.0): SweepVerdict {
    if (dailyTarget == null || dailyTarget <= 0) return SweepVerdict.Silent
    val gap = dailyTarget - spentToday.coerceAtLeast(0.0)
    return when {
        gap >= dust -> SweepVerdict.Sweep(gap)
        gap <= -dust -> SweepVerdict.Over(-gap)
        else -> SweepVerdict.Silent
    }
}

// Daily target preference order: the semester allowance knows the term,
// the ALL envelope knows the month. First honest number wins.
fun dailyTargetFrom(weeklyAllowance: Double?, monthlyAll: Double?): Double? {
    val fromWeek = weeklyAllowance?.takeIf { it > 0 }?.div(7)
    if (fromWeek != null && fromWeek > 0) return fromWeek
    return monthlyAll?.takeIf { it > 0 }?.div(30)
}
