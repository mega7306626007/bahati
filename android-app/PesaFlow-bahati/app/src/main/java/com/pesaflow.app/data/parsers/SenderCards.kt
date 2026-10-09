package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionType

// Sender cards: one card per unknown sender instead of 700 rows to confirm.
// "Nancy · 12 transactions · 3 Jan – 28 Mar" — the user names her once and
// every row (past + future) resolves. Pure function, zero Android deps.
data class SenderCard(
    val merchant: String,
    val count: Int,
    val expenseTotal: Double,
    val incomeTotal: Double,
    val firstSeen: Long,
    val lastSeen: Long,
    val suggestedCategory: String
)

// System counterparties never need a "who is this?" — only human-like
// unknown senders surface as cards. After parsing, the user must never be
// asked who OD, Data, Ziidi, Fuliza or Okoa Jahazi are: carriers file
// themselves. Short generic words (fee, charge, data…) match on word
// boundaries only — "Coffee House" is a person-place, not a charge.
private val KNOWN_ENTITIES = setOf(
    "safaricom", "airtel", "telkom", "equitel",
    "kplc", "kenya power", "nairobi water", "nws",
    "kcb", "equity", "absa", "stanbic", "co-op", "coop",
    "family bank", "dtb", "ncba", "stanchart", "i&m",
    "helb", "fuliza", "shwari", "okoa", "sacco", "ziidi",
    "overdraft", "overdraw", "od loan", "o/d",
    "kra", "nhif", "nssf", "e-citizen", "ecitizen"
)
private val KNOWN_WORDS = listOf(
    "data", "minutes", "airtime", "bundle", "bundles",
    "fee", "fees", "charge", "charges", "reversal", "reversed"
)

fun isKnownEntity(merchant: String): Boolean {
    val low = merchant.trim().lowercase()
    if (low.isEmpty() || low == "unknown party") return true
    // Pure Till/Paybill numbers are places, not people — no naming needed.
    if (low.matches(Regex("^(till\\s*)?[0-9]{5,9}$"))) return true
    if (KNOWN_ENTITIES.any { low.contains(it) }) return true
    return KNOWN_WORDS.any { Regex("\\b$it\\b").containsMatchIn(low) }
}

// Merges a fresh scan into the accumulated cards: dedupes repeat merchants
// and — the important part — drops cards for anyone named SINCE the scan
// ran (step-1 contacts added after scanning must never resurface).
// Pure, so the no-repeat-asking rule is unit-pinned, not UI-hopeful.
fun mergeSenderCards(
    existing: List<SenderCard>,
    fresh: List<SenderCard>,
    isNamed: (String) -> Boolean = { false }
): List<SenderCard> {
    val seen = existing.map { it.merchant }.toSet()
    return (existing + fresh.filter { it.merchant !in seen })
        .filterNot { isNamed(it.merchant) }
}

// Groups parsed rows by sender. isNamed covers user aliases (MerchantMemory):
// already-named senders never surface again. A card is earned by frequency
// (16+ texts) OR weight (KSh 1,500+ moved) — a one-off needs no naming
// ceremony, but real money always introduces itself. Sorted busiest-first so
// the first cards the user confirms clear the most rows.
fun groupSenderCards(
    parsed: List<PendingTransaction>,
    isNamed: (String) -> Boolean = { false },
    minTransactions: Int = 16,
    minTotal: Double = 1500.0
): List<SenderCard> {
    val byMerchant = parsed
        .filter { it.merchant.isNotBlank() }
        .groupBy { it.merchant.trim() }
        .filterKeys { !isKnownEntity(it) && !isNamed(it) }
    return byMerchant.map { (merchant, rows) ->
        val expenses = rows.filter { it.type == TransactionType.EXPENSE }
        val incomes = rows.filter { it.type == TransactionType.INCOME }
        // Suggested category = whatever most of their rows already say.
        val suggested = rows.groupingBy { it.category }.eachCount()
            .maxByOrNull { it.value }?.key ?: "Other"
        SenderCard(
            merchant = merchant,
            count = rows.size,
            expenseTotal = expenses.sumOf { it.amount },
            incomeTotal = incomes.sumOf { it.amount },
            firstSeen = rows.minOf { it.dateTimestamp },
            lastSeen = rows.maxOf { it.dateTimestamp },
            suggestedCategory = suggested
        )
    }.filter { it.count > minTransactions || it.expenseTotal + it.incomeTotal >= minTotal }
        .sortedByDescending { it.count }
}
