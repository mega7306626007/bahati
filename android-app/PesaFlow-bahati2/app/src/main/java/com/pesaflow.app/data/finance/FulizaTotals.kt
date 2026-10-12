package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isFeeRow
import com.pesaflow.app.data.models.isFulizaBorrowing

// Fuliza bookkeeping, computed from the ledger — never estimated silently.
// Three lanes, matching the parser's subcategories exactly:
// - borrowed: INCOME rows the parser flagged as borrowing ("Borrowed funds").
// - repaid: EXPENSE rows flagged "Fuliza repayment" (true principal service;
//   "Borrowed spend" — Fuliza-funded spending OUT — is deliberately excluded,
//   it is new borrowing, not repayment).
// - chargesObserved: EXPENSE rows flagged "Fuliza charges" (maintenance /
//   access / service fees) plus fee-pot rows that mention Fuliza.
// Access fee (1% per drawdown) and the daily tariff below are Safaricom's
// published terms (Oct-2022 restructure): shown as labelled estimates next
// to observed figures, never mixed into them.
data class FulizaTotals(
    val borrowed: Double,
    val repaid: Double,
    val chargesObserved: Double,
    val outstanding: Double,
    val accessEstimate: Double,
    val borrowCount: Int,
    val repayCount: Int,
    val chargeCount: Int
)

fun fulizaTotals(txs: List<Transaction>): FulizaTotals {
    val real = txs.filter { !it.isSample }
    val borrowedRows = real.filter { it.type == TransactionType.INCOME && it.isFulizaBorrowing() }
    val repaidRows = real.filter { it.type == TransactionType.EXPENSE && it.subcategory == "Fuliza repayment" }
    val chargeRows = real.filter {
        it.type == TransactionType.EXPENSE && (
            it.subcategory == "Fuliza charges" ||
                (it.isFeeRow() && (it.merchant + " " + it.description).contains("fuliza", ignoreCase = true))
            )
    }
    val borrowed = borrowedRows.sumOf { it.amount }
    val repaid = repaidRows.sumOf { it.amount }
    return FulizaTotals(
        borrowed = borrowed,
        repaid = repaid,
        chargesObserved = chargeRows.sumOf { it.amount },
        outstanding = (borrowed - repaid).coerceAtLeast(0.0),
        accessEstimate = borrowed * 0.01,
        borrowCount = borrowedRows.size,
        repayCount = repaidRows.size,
        chargeCount = chargeRows.size
    )
}

// Lifetime M-Pesa/carrier fee bleed from the ledger (fee-pot rows live here
// permanently; the prefs pot only keeps the current month). Same exclusion
// rules as budgets: samples out, transfers never spending.
fun lifetimeFeeTotal(txs: List<Transaction>): Double =
    txs.filter { !it.isSample && it.isFeeRow() }.sumOf { it.amount }

// Safaricom Fuliza tariff (Oct-2022 restructure, confirmed 2025–2026):
// 1% one-off access fee per drawdown + daily maintenance from the next
// midnight on the outstanding balance + 20% excise on the maintenance fee.
// 0–100 free; 101–1000 free for the first 3 days, then banded.
data class FulizaBand(val min: Double, val max: Double, val daily: Double, val dailyWithExcise: Double)

val FULIZA_TARIFF: List<FulizaBand> = listOf(
    FulizaBand(0.0, 100.0, 0.0, 0.0),
    FulizaBand(101.0, 500.0, 2.5, 3.0),
    FulizaBand(501.0, 1000.0, 5.0, 6.0),
    FulizaBand(1001.0, 1500.0, 18.0, 21.6),
    FulizaBand(1501.0, 2500.0, 20.0, 24.0),
    FulizaBand(2501.0, 70000.0, 25.0, 30.0)
)

/** Daily maintenance (ex-excise) for an outstanding balance. ≤100 is free. */
fun dailyRateFor(outstanding: Double): Double =
    FULIZA_TARIFF.firstOrNull { outstanding in it.min..it.max }?.daily ?: 25.0

/** One-off access fee estimate: 1% of the amount drawn. */
fun accessFeeFor(amount: Double): Double = amount * 0.01

/** First-3-days waiver applies to drawdowns of KSh 1,000 and below. */
fun waiverApplies(amount: Double): Boolean = amount in 1.0..1000.0
