package com.pesaflow.app.data.money

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import java.util.Calendar

// Single home for hero money math: every balance, income and savings figure
// the UI shows must come through here, so the ledger can never be counted
// two different ways on two different screens.

// Rows that must never move statistics: seeded opening cash (real money in
// hand, but never earned) and watermarked sample rows. They still count in
// the balance — cash is cash — just never in rates, verdicts or averages.
val NON_STAT_SOURCES = setOf(TransactionSource.OPENING, TransactionSource.SAMPLE)

fun ledgerBalance(txs: List<Transaction>): Double = txs.sumOf {
    when (it.type) {
        TransactionType.INCOME -> it.amount
        TransactionType.EXPENSE -> -it.amount
        TransactionType.SAVING -> -it.amount
        TransactionType.INVESTMENT -> -it.amount
    }
}

// Ziidi holding: top-ups in as SAVING, withdrawals back as INCOME (both
// merchant "Ziidi"). Never negative — a ledger can't over-withdraw. The hero
// card adds it back because moving money into Ziidi already left the
// balance: the pair is neutral by construction, so hero == total funds
// under control, not a double count.
fun ziidiHoldings(txs: List<Transaction>): Double = txs.sumOf {
    when {
        it.type == TransactionType.SAVING && it.merchant.contains("ziidi", ignoreCase = true) -> it.amount
        it.type == TransactionType.INCOME && it.merchant.contains("ziidi", ignoreCase = true) -> -it.amount
        else -> 0.0
    }
}.coerceAtLeast(0.0)

fun heroMoney(txs: List<Transaction>): Double = ledgerBalance(txs) + ziidiHoldings(txs)

fun isCurrentMonth(ts: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
    val ref = Calendar.getInstance().apply { timeInMillis = nowMs }
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return c.get(Calendar.YEAR) == ref.get(Calendar.YEAR) &&
        c.get(Calendar.MONTH) == ref.get(Calendar.MONTH)
}

fun monthScopedTotal(
    txs: List<Transaction>,
    type: TransactionType,
    nowMs: Long = System.currentTimeMillis(),
    excludeSources: Set<TransactionSource> = NON_STAT_SOURCES
): Double = txs.filter {
    it.type == type && it.source !in excludeSources && isCurrentMonth(it.dateTimestamp, nowMs)
}.sumOf { it.amount }

// Drift zones (mental-model mismatch fix): the ledger and the last M-Pesa
// SMS balance disagree all the time — small gaps are noise, big ones need a
// human. Thresholds tuned for student wallets (KSh 50 ≈ a fare, 500 ≈ rent
// scale). Minor drift NEVER auto-posts: every ledger row stays visible,
// user-confirmed and undoable — "invisible adjustments" would corrupt
// statistics, undo and trust at once.
sealed interface ReconciliationStatus {
    object InSync : ReconciliationStatus
    data class MinorDrift(val amount: Double) : ReconciliationStatus
    data class MajorDrift(val amount: Double) : ReconciliationStatus
}

fun evaluateDrift(smsBalance: Double, computedBalance: Double): ReconciliationStatus {
    val diff = smsBalance - computedBalance
    val absolute = kotlin.math.abs(diff)
    return when {
        absolute <= 50.0 -> ReconciliationStatus.InSync
        absolute <= 500.0 -> ReconciliationStatus.MinorDrift(diff)
        else -> ReconciliationStatus.MajorDrift(diff)
    }
}

// Prorated category pace: a KSh 300 spend on day 2 of a KSh 1200 envelope
// is on pace, not a crisis. Static 80/100% lines stay as the hard rails;
// this adds the early-warning tier between them.
enum class BudgetPace { SAFE, ON_TRACK, AT_RISK, EXCEEDED }

fun evaluateCategoryPace(
    actualSpent: Double,
    monthlyBudget: Double,
    currentDay: Int,
    totalDaysInMonth: Int
): BudgetPace {
    if (monthlyBudget <= 0) return BudgetPace.SAFE
    if (actualSpent > monthlyBudget) return BudgetPace.EXCEEDED
    val expected = monthlyBudget * (currentDay.coerceAtLeast(1).toDouble() / totalDaysInMonth.coerceAtLeast(1))
    val ratio = actualSpent / expected.coerceAtLeast(1.0)
    return when {
        ratio <= 1.0 -> BudgetPace.SAFE
        ratio <= 1.25 -> BudgetPace.ON_TRACK
        else -> BudgetPace.AT_RISK
    }
}
