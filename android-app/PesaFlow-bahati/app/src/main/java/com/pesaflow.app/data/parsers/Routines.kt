package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionType

// Morning routine + unknown clusters: the first scan should *conclude*,
// not dump 700 question marks on the user.
//
// Morning routine: 7–9am spends of the same amount (±KSh 50 for hiked
// prices), 3+ times → "breakfast is ~KSh X". Hostel residents are excluded
// by the caller (mess food is prepaid, not a till habit).
data class AmountStamp(val amount: Double, val ts: Long)

data class MorningRoutine(val amount: Int, val count: Int, val earlyHour: Int, val lateHour: Int)

private fun hourOf(ts: Long): Int =
    java.util.Calendar.getInstance().apply { timeInMillis = ts }.get(java.util.Calendar.HOUR_OF_DAY)

fun detectMorningRoutine(
    stamps: List<AmountStamp>,
    minCount: Int = 3,
    tol: Double = 50.0
): MorningRoutine? {
    val morning = stamps.filter { hourOf(it.ts) in 7..9 && it.amount > 0 }
    if (morning.size < minCount) return null
    // Greedy ±tol clustering on sorted amounts; biggest cluster wins.
    val sorted = morning.sortedBy { it.amount }
    var best: MutableList<AmountStamp> = mutableListOf()
    var cur: MutableList<AmountStamp> = mutableListOf()
    sorted.forEach { s ->
        if (cur.isEmpty() || s.amount - cur.first().amount <= tol) cur.add(s)
        else {
            if (cur.size > best.size) best = cur
            cur = mutableListOf(s)
        }
    }
    if (cur.size > best.size) best = cur
    if (best.size < minCount) return null
    val amounts = best.map { it.amount }.sorted()
    val median = if (amounts.size % 2 == 1) amounts[amounts.size / 2]
    else (amounts[amounts.size / 2 - 1] + amounts[amounts.size / 2]) / 2
    val hours = best.map { hourOf(it.ts) }
    return MorningRoutine(median.toInt(), best.size, hours.minOrNull() ?: 7, hours.maxOrNull() ?: 9)
}

// Unknown clusters: "Unknown" rows that look like the same habit (same
// hour, same amount ±tol) collapse into one label-once card instead of N
// unsure messages. Caller approves each cluster in one tap.
data class UnknownCluster(
    val count: Int,
    val medianAmount: Int,
    val hour: Int,
    val sample: String,
    val members: List<PendingTransaction>
)

fun clusterUnknowns(
    rows: List<PendingTransaction>,
    tol: Double = 50.0,
    minCount: Int = 2
): List<UnknownCluster> {
    val unknowns = rows.filter {
        it.category.equals("Unknown", ignoreCase = true) && it.type != TransactionType.INCOME
    }
    if (unknowns.size < minCount) return emptyList()
    val out = mutableListOf<UnknownCluster>()
    unknowns.groupBy { hourOf(it.dateTimestamp) }.values.forEach { hourRows ->
        val sorted = hourRows.sortedBy { it.amount }
        var cur = mutableListOf<PendingTransaction>()
        fun flush() {
            if (cur.size >= minCount) {
                val amts = cur.map { it.amount }.sorted()
                val med = if (amts.size % 2 == 1) amts[amts.size / 2]
                else (amts[amts.size / 2 - 1] + amts[amts.size / 2]) / 2
                out.add(
                    UnknownCluster(
                        count = cur.size,
                        medianAmount = med.toInt(),
                        hour = hourOf(cur.first().dateTimestamp),
                        sample = cur.groupingBy { it.merchant.ifBlank { "a till" } }.eachCount()
                            .maxByOrNull { it.value }?.key ?: "a till",
                        members = cur.toList()
                    )
                )
            }
            cur = mutableListOf()
        }
        sorted.forEach { p ->
            if (cur.isEmpty() || p.amount - cur.first().amount <= tol) cur.add(p)
            else flush().also { cur.add(p) }
        }
        flush()
    }
    return out.sortedByDescending { it.count }
}
