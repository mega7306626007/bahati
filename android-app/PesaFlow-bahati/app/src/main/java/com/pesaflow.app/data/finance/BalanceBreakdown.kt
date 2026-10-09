package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isFeeRow

// Presentation-only breakdown of Current Balance (§5 of the transformation).
// Mirrors FinancialEngine.buildSnapshot's filters EXACTLY (!isSample,
// dateTimestamp <= now, TRANSFER excluded unless paired) so the explained
// numbers always reconcile with snap.liquid. Never used for math — the
// engine remains the single source of truth.
data class BalanceBreakdown(
    val received: Double,
    val spending: Double,
    // Carrier charges are wallet outflows but never "spending": the Why?
    // card shows them on their own line so the equation still balances.
    val fees: Double,
    val opening: Double,
    val saved: Double,
    val transferNet: Double,
    val liquid: Double,
    val futureExcludedCount: Int,
    val futureExcludedNet: Double,
    val sampleExcludedCount: Int
)

fun balanceBreakdown(txs: List<Transaction>, nowMs: Long): BalanceBreakdown {
    val future = txs.filter { it.dateTimestamp > nowMs }
    val futureNet = future.sumOf {
        when (it.type) {
            TransactionType.INCOME -> it.amount
            TransactionType.EXPENSE -> -it.amount
            TransactionType.SAVING -> -it.amount
            TransactionType.INVESTMENT -> -it.amount
            TransactionType.TRANSFER -> 0.0
        }
    }
    val samples = txs.count { it.isSample }
    val real = txs.filter { !it.isSample && it.dateTimestamp <= nowMs }
    val received = real.filter { it.type == TransactionType.INCOME && !it.isOpening }.sumOf { it.amount }
    val opening = real.filter { it.type == TransactionType.INCOME && it.isOpening }.sumOf { it.amount }
    val fees = real.filter { it.isFeeRow() }.sumOf { it.amount }
    val spending = real.filter { it.type == TransactionType.EXPENSE && !it.isFeeRow() }.sumOf { it.amount }
    val saved = real.filter { it.type == TransactionType.SAVING || it.type == TransactionType.INVESTMENT }.sumOf { it.amount }
    var tNet = 0.0
    real.filter { it.type == TransactionType.TRANSFER && it.transferGroupId != null }.forEach {
        when (it.transferSide) {
            "OUT" -> tNet -= it.amount
            "IN" -> tNet += it.amount
        }
    }
    return BalanceBreakdown(
        received = received,
        spending = spending,
        fees = fees,
        opening = opening,
        saved = saved,
        transferNet = tNet,
        liquid = received + opening - spending - fees - saved + tNet,
        futureExcludedCount = future.size,
        futureExcludedNet = futureNet,
        sampleExcludedCount = samples
    )
}
