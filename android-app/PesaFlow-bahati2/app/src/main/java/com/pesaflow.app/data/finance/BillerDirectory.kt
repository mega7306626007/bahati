package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isFeeRow

// Curated Kenyan biller directory. Bill suggestions match HERE — known
// paybills, ISP names, utilities — never "any merchant you paid three
// times". A kibanda you visit daily is a habit, not a bill; Zuku at
// paybill 320320 every ~30 days is.
//
// Provenance per entry: "verified 2026" (checked against Kenyan paybill
// directories, April 2026) or "well-known" (long-stable public numbers —
// still worth one glance before trusting blindly). Append-only by design:
// add entries, never loosen thresholds.
data class BillerEntry(
    val displayName: String,
    val keywords: List<String>,
    val paybills: List<String>,
    val category: String,
    val source: String
)

val BILLER_DIRECTORY: List<BillerEntry> = listOf(
    // ---- Internet / telecom ----
    BillerEntry("Zuku Fiber", listOf("zuku", "wananchi"), listOf("320320"), "Internet", "verified 2026"),
    BillerEntry("Zuku TV", listOf("zuku tv"), listOf("320323"), "TV", "verified 2026"),
    BillerEntry("Faiba", listOf("faiba", "jtl", "jamii telecom"), listOf("776611", "251251"), "Internet", "verified 2026"),
    BillerEntry("Poa Internet", listOf("poa internet", "poa wifi", "poa"), listOf("7769384"), "Internet", "verified 2026"),
    BillerEntry("Safaricom Home Fibre", listOf("home fibre", "safaricom fibre"), emptyList(), "Internet", "well-known name"),
    BillerEntry("Safaricom Postpaid", listOf("postpaid", "postpay"), listOf("200200"), "Airtime", "well-known number"),
    BillerEntry("Safaricom Bundles", listOf("bundles"), listOf("898998"), "Airtime", "verified 2026"),
    BillerEntry("Airtel", listOf("airtel"), emptyList(), "Airtime", "well-known name"),
    BillerEntry("Telkom", listOf("telkom"), listOf("777711"), "Airtime", "verified 2026"),
    // ---- Power ----
    BillerEntry("KPLC Prepaid", listOf("kplc", "kenya power", "token"), listOf("888888"), "Electricity", "well-known number"),
    BillerEntry("KPLC Postpaid", listOf("kplc", "kenya power"), listOf("888899"), "Electricity", "well-known number"),
    // ---- Water ----
    BillerEntry("Nairobi Water", listOf("nairobi water"), listOf("444400"), "Water", "well-known number"),
    BillerEntry("Water", listOf("water"), emptyList(), "Water", "pattern"),
    // ---- TV ----
    BillerEntry("DStv", listOf("dstv", "multichoice"), listOf("423655"), "TV", "well-known number"),
    BillerEntry("GOtv", listOf("gotv"), emptyList(), "TV", "well-known name, paybill varies"),
    BillerEntry("StarTimes", listOf("startimes"), listOf("585858"), "TV", "well-known number"),
    // ---- Government levies ----
    BillerEntry("eCitizen", listOf("ecitizen", "e-citizen"), listOf("222222"), "Bills", "well-known number"),
    BillerEntry("KRA", listOf("kra", "kenya revenue"), listOf("572572"), "Bills", "well-known number"),
    BillerEntry("SHA Health", listOf("nhif", "sha", "social health"), listOf("200222"), "Health", "well-known number"),
    // ---- School ----
    BillerEntry("School Fees", listOf("school fees", "school"), emptyList(), "School", "pattern"),
    // ---- Rent (landlords vary — matched by home/place names + Rent rows) ----
    BillerEntry(
        "Rent", listOf("rent", "landlord", "caretaker", "hostel", "accommodation", "nyumba", "kodi"),
        emptyList(), "Rent", "pattern + your places"
    )
)

data class DirectoryBillHit(
    val biller: BillerEntry,
    val count: Int,
    val medianAmount: Double,
    val minAmount: Double,
    val maxAmount: Double,
    val medianGapDays: Long,
    val monthly: Boolean,
    val viaPaybill: Boolean,
    val evidence: String
)

private fun wordTokens(s: String): Set<String> =
    s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()

// Single-token keywords match whole tokens only ("kra" must not fire
// inside "okra"-style words, "maseno" must not fire inside longer
// compounds); multi-word phrases match substrings as usual.
private fun keywordHit(haystack: String, keyword: String): Boolean {
    val low = haystack.lowercase()
    return if (!keyword.contains(" ")) wordTokens(low).contains(keyword) else low.contains(keyword)
}

private fun paybillHit(haystack: String, paybill: String): Boolean =
    Regex("(?<!\\d)" + Regex.escape(paybill) + "(?!\\d)").containsMatchIn(haystack)

private fun medianOf(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    return sorted[sorted.size / 2]
}

/**
 * Directory-first bill suggestions. A row belongs to at most one entry
 * (paybill digit match wins, else the longest matching keyword), so one
 * payment never suggests two bills. Cadence bar mirrors the proven
 * recurring detector (≥3 rows, median gap 5–40 days); amounts cluster
 * around the MEDIAN (±40% + KSh 1), never a mean that one spike can drag.
 */
fun suggestBillsFromDirectory(
    txs: List<Transaction>,
    homeNames: List<String> = emptyList()
): List<DirectoryBillHit> {
    val rows = txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && !it.isFeeRow() }
    if (rows.isEmpty()) return emptyList()
    val rentBase = BILLER_DIRECTORY.first { it.displayName == "Rent" }
    val homeKeywords = homeNames.map { it.trim().lowercase() }.filter { it.length >= 3 }
    val rentKeywords = (rentBase.keywords + homeKeywords).distinct()
    val entries = BILLER_DIRECTORY.filter { it.displayName != "Rent" } + rentBase.copy(keywords = rentKeywords)

    // Assign each row to its single best entry.
    val buckets = mutableMapOf<BillerEntry, MutableList<Pair<Transaction, Boolean>>>()
    for (tx in rows) {
        val hay = tx.merchant + " " + tx.description
        var best: BillerEntry? = null
        var bestViaPaybill = false
        var bestLen = 0
        for (entry in entries) {
            val viaPaybill = entry.paybills.any { paybillHit(hay, it) }
            if (viaPaybill) {
                best = entry
                bestViaPaybill = true
                break
            }
            // Home-name keywords (your campus/place names) only count when
            // the row isn't already confidently something else — "MASENO
            // BOOKSHOP" filed as School must never pool into Rent, while a
            // "GREENVIEW COURT" row filed as Bills plausibly IS the rent.
            val longest = entry.keywords.filter { kw ->
                keywordHit(hay, kw) && (entry.displayName != "Rent" || kw !in homeKeywords ||
                    tx.category.equals("Rent", ignoreCase = true) ||
                    tx.category.equals("Other", ignoreCase = true) ||
                    tx.category.equals("Bills", ignoreCase = true) ||
                    tx.category.isBlank())
            }.maxOfOrNull { it.length } ?: 0
            if (longest > bestLen) {
                bestLen = longest
                best = entry
                bestViaPaybill = false
            }
        }
        if (best != null && bestLen > 0 || (best != null && bestViaPaybill)) {
            buckets.getOrPut(best) { mutableListOf() }.add(tx to bestViaPaybill)
        }
    }

    val hits = mutableListOf<DirectoryBillHit>()
    for ((entry, pairs) in buckets) {
        if (pairs.size < 3) continue
        val amounts = pairs.map { it.first.amount }
        val median = medianOf(amounts)
        if (median <= 0) continue
        if (amounts.any { kotlin.math.abs(it - median) > median * 0.4 + 1 }) continue
        val dayMs = 24L * 60 * 60 * 1000
        val stamps = pairs.map { it.first.dateTimestamp }.sorted()
        val gaps = stamps.zipWithNext { a, b -> (b - a) / dayMs }
        if (gaps.isEmpty()) continue
        val medianGap = gaps.sorted()[gaps.size / 2]
        if (medianGap < 5 || medianGap > 40) continue
        val viaPaybill = pairs.any { it.second }
        val evidence = buildString {
            if (viaPaybill) {
                val pb = entry.paybills.firstOrNull { pb ->
                    pairs.any { paybillHit(it.first.merchant + " " + it.first.description, pb) }
                }
                if (pb != null) append("paybill $pb · ")
            }
            append("${pairs.size} payments · ~every $medianGap days · usually KSh ${median.toInt()}")
            val lo = amounts.minOrNull()?.toInt() ?: 0
            val hi = amounts.maxOrNull()?.toInt() ?: 0
            if (hi > lo) append(" ($lo–$hi)")
        }
        hits.add(
            DirectoryBillHit(
                biller = entry,
                count = pairs.size,
                medianAmount = median,
                minAmount = amounts.minOrNull() ?: 0.0,
                maxAmount = amounts.maxOrNull() ?: 0.0,
                medianGapDays = medianGap,
                monthly = medianGap >= 25,
                viaPaybill = viaPaybill,
                evidence = evidence
            )
        )
    }
    return hits.sortedWith(compareByDescending<DirectoryBillHit> { it.count }.thenByDescending { it.medianAmount })
}
