package com.pesaflow.app.data.ml

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Spending anomaly detector: robust modified z-score based on MAD
 * (median absolute deviation). No training needed, works cold-start from
 * day one. Flags a transaction when it exceeds threshold × typical spend
 * for its category.
 *
 * Pure Kotlin, deterministic statistics — labelled STAT, not neural.
 */
object AnomalyDetector {
    data class Verdict(val isAnomaly: Boolean, val score: Double, val median: Double, val reason: String)

    fun verdict(amount: Double, categoryHistory: List<Double>, threshold: Double = 3.5): Verdict {
        if (categoryHistory.size < 4) {
            return Verdict(false, 0.0, categoryHistory.median(), "insufficient evidence (<4 samples)")
        }
        val med = categoryHistory.median()
        val mad = categoryHistory.map { abs(it - med) }.median().coerceAtLeast(1.0)
        // Modified z-score: 0.6745 * (x - median) / MAD
        val score = 0.6745 * (amount - med) / mad
        return if (score > threshold) {
            Verdict(true, score, med, "KSh ${amount.toInt()} vs typical KSh ${med.toInt()} in category")
        } else {
            Verdict(false, score, med, "within normal band")
        }
    }

    private fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        val s = sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }
}

/**
 * Recurring payment detector: groups by normalized merchant, checks
 * interval regularity (CV of gaps) + amount stability (CV of amounts).
 * Deterministic — labelled RULES, not trained.
 */
object RecurringDetector {
    data class Candidate(
        val merchant: String,
        val occurrences: Int,
        val medianAmount: Double,
        val medianIntervalDays: Double,
        val amountCv: Double,
        val intervalCv: Double,
        val isRecurring: Boolean
    )

    fun detect(transactions: List<Pair<String, Pair<Double, Long>>>): List<Candidate> {
        val day = 24L * 60 * 60 * 1000
        return transactions.groupBy { it.first.lowercase().trim() }
            .filter { it.value.size >= 3 }
            .map { (merchant, rows) ->
                val amounts = rows.map { it.second.first }.sorted()
                val times = rows.map { it.second.second }.sorted()
                val gaps = times.zipWithNext { a, b -> (b - a).toDouble() / day }
                val medAmt = median(amounts)
                val medGap = median(gaps)
                val amtCv = cv(amounts, medAmt)
                val gapCv = if (medGap > 0) cv(gaps, medGap) else 99.0
                Candidate(
                    merchant = merchant,
                    occurrences = rows.size,
                    medianAmount = medAmt,
                    medianIntervalDays = medGap,
                    amountCv = amtCv,
                    intervalCv = gapCv,
                    // Weekly-ish (5-9d) or monthly-ish (25-35d) with stable amounts.
                    isRecurring = amtCv < 0.25 && gapCv < 0.35 &&
                        ((medGap in 5.0..9.0) || (medGap in 25.0..35.0) || (medGap in 12.0..16.0))
                )
            }.filter { it.isRecurring }
    }

    private fun median(xs: List<Double>): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    private fun cv(xs: List<Double>, mean: Double): Double {
        if (xs.isEmpty() || mean == 0.0) return 99.0
        val sd = sqrt(xs.map { (it - mean) * (it - mean) }.average())
        return sd / abs(mean)
    }
}

/**
 * Spending forecaster: trailing means with min/max range. Returns a range,
 * never a false point estimate. Deterministic statistics.
 */
object Forecaster {
    data class Forecast(val point: Double, val low: Double, val high: Double, val basis: String)

    fun daily(dailyTotals: List<Double>, horizonDays: Int = 7): Forecast {
        if (dailyTotals.isEmpty()) return Forecast(0.0, 0.0, 0.0, "no history")
        val window = dailyTotals.takeLast(14)
        val mean = window.average()
        val sd = sqrt(window.map { (it - mean) * (it - mean) }.average())
        val point = mean * horizonDays
        return Forecast(
            point = point,
            low = (point - sd * sqrt(horizonDays.toDouble())).coerceAtLeast(0.0),
            high = point + sd * sqrt(horizonDays.toDouble()),
            basis = "trailing ${window.size}d mean ±1sd"
        )
    }
}
