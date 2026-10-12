package com.pesaflow.app.data.online

import kotlinx.serialization.Serializable

// Campus price intelligence. Anti-gaming rules live HERE (pure, tested),
// not on any server: one vote per reporter (latest wins), outliers beyond
// ±50% of the median are dropped, and a price needs 2+ distinct reporters
// inside a 30-day window before the app trusts it. Borrowed from
// PesaFlow-online (demo gateway left behind).
@Serializable
data class PriceReport(
    val spot: String, // e.g. "Naivas Roysambu", "CBD odeon stage"
    val item: String, // e.g. "chapati+beans", "matatu CBD"
    val price: Double,
    val reporterHash: String,
    val timestampMs: Long,
    val source: String = "USER_ENTERED"
)

fun aggregatePrice(
    reports: List<PriceReport>,
    nowMs: Long = System.currentTimeMillis(),
    windowDays: Int = 30,
    minReporters: Int = 2,
    outlierTolerance: Double = 0.5
): Double? {
    val fresh = reports.filter { it.price > 0 && nowMs - it.timestampMs <= windowDays * 24 * 3_600_000L }
    if (fresh.isEmpty()) return null
    val byReporter = fresh.groupBy { it.reporterHash.trim().lowercase() }
    if (byReporter.size < minReporters) return null
    // One vote per reporter: latest report each.
    val votes = byReporter.values.map { rs -> rs.maxByOrNull { it.timestampMs }!!.price }.sorted()
    val median = if (votes.size % 2 == 1) votes[votes.size / 2]
    else (votes[votes.size / 2 - 1] + votes[votes.size / 2]) / 2.0
    val kept = votes.filter { kotlin.math.abs(it - median) <= median * outlierTolerance }
    if (kept.isEmpty()) return null
    return kept.sorted().let { s ->
        if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }
}

interface PriceGateway {
    suspend fun fetch(spot: String, item: String): List<PriceReport>
    suspend fun submit(report: PriceReport)
}

// Offline-first submission queue: reports wait here until the sync gate
// opens (online + WiFi), so bundles are never spent on uploads.
class PriceOutbox {
    private val pending = ArrayDeque<PriceReport>()
    fun queue(report: PriceReport) { pending.addLast(report) }
    fun pending(): List<PriceReport> = pending.toList()
    fun size(): Int = pending.size
    fun clear() { pending.clear() }
}
