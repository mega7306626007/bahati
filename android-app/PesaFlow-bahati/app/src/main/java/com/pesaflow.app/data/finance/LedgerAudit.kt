package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isFeeRow


// Read-only ledger audit: when headline figures look wrong (Received too
// high, Saved impossibly large), this names every suspect row instead of
// touching formulas. Nothing here writes, deletes or reclassifies —
// findings go to the user first, fixes second.
data class AuditRow(
    val dateTimestamp: Long,
    val amount: Double,
    val merchant: String,
    val type: TransactionType,
    val category: String,
    val subcategory: String,
    val source: TransactionSource,
    val sourceTransactionId: String?,
    val isSample: Boolean
)

data class DuplicateGroup(
    val code: String,
    val amount: Double,
    val type: TransactionType,
    val count: Int
)

data class LedgerAudit(
    val byType: Map<TransactionType, Double>,
    val byTypeCount: Map<TransactionType, Int>,
    // SAVING + INVESTMENT largest-first: the "where did my money go" list.
    val savingsTop: List<AuditRow>,
    // All INCOME largest-first (borrowed/internal legs included): the
    // "why is Received higher than reality" list.
    val incomeTop: List<AuditRow>,
    // Same (code, amount, direction) twice or more: SMS+statement twins.
    val duplicateGroups: List<DuplicateGroup>,
    val sampleCount: Int,
    val sampleSum: Double,
    val futureCount: Int,
    val futureSum: Double,
    val openingSum: Double,
    val feeSum: Double,
    val unpairedTransferCount: Int,
    val unpairedTransferSum: Double
)

fun ledgerAudit(
    txs: List<Transaction>,
    nowMs: Long = System.currentTimeMillis(),
    topN: Int = 20
): LedgerAudit {
    fun row(t: Transaction) = AuditRow(
        t.dateTimestamp, t.amount, t.merchant, t.type, t.category,
        t.subcategory, t.source, t.sourceTransactionId, t.isSample
    )
    val byType = TransactionType.values().associateWith { type ->
        txs.filter { it.type == type && !it.isSample }.sumOf { it.amount }
    }
    val byTypeCount = TransactionType.values().associateWith { type ->
        txs.count { it.type == type && !it.isSample }
    }
    val dups = txs.filter { !it.sourceTransactionId.isNullOrBlank() }
        .groupBy { Triple(it.sourceTransactionId!!, it.amount, it.type) }
        .filter { it.value.size > 1 }
        .map { DuplicateGroup(it.key.first, it.key.second, it.key.third, it.value.size) }
        .sortedByDescending { it.count }
    val unpaired = txs.filter {
        it.type == TransactionType.TRANSFER && it.transferGroupId.isNullOrBlank() && !it.isSample
    }
    return LedgerAudit(
        byType = byType,
        byTypeCount = byTypeCount,
        savingsTop = txs.filter {
            (it.type == TransactionType.SAVING || it.type == TransactionType.INVESTMENT) && !it.isSample
        }.sortedByDescending { it.amount }.take(topN).map(::row),
        incomeTop = txs.filter { it.type == TransactionType.INCOME && !it.isSample }
            .sortedByDescending { it.amount }.take(topN).map(::row),
        duplicateGroups = dups,
        sampleCount = txs.count { it.isSample },
        sampleSum = txs.filter { it.isSample }.sumOf { it.amount },
        futureCount = txs.count { it.dateTimestamp > nowMs && !it.isSample },
        futureSum = txs.filter { it.dateTimestamp > nowMs && !it.isSample }.sumOf {
            when (it.type) {
                TransactionType.INCOME -> it.amount
                else -> -it.amount
            }
        },
        openingSum = txs.filter { it.isOpening }.sumOf { it.amount },
        feeSum = txs.filter { it.isFeeRow() && !it.isSample }.sumOf { it.amount },
        unpairedTransferCount = unpaired.size,
        unpairedTransferSum = unpaired.sumOf { it.amount }
    )
}

// One scrollable truth dump for the Settings audit dialog: totals by type,
// then the suspect lists with dates and codes. Amounts carry KSh ints;
// dates render d MMM yy.
fun formatLedgerAudit(a: LedgerAudit): String {
    val fmt = java.text.SimpleDateFormat("d MMM yy", java.util.Locale.getDefault())
    fun date(ts: Long) = fmt.format(java.util.Date(ts))
    val sb = StringBuilder()
    fun money(v: Double) = "KSh " + kotlin.math.abs(v).toInt()
    sb.appendLine("BY TYPE (samples excluded):")
    TransactionType.values().forEach { t ->
        sb.appendLine("· $t: ${money(a.byType[t] ?: 0.0)} (${a.byTypeCount[t] ?: 0} rows)")
    }
    sb.appendLine("Fees inside expenses above: ${money(a.feeSum)}")
    sb.appendLine("Opening equity: ${money(a.openingSum)}")
    sb.appendLine("Samples: ${a.sampleCount} rows (${money(a.sampleSum)})")
    sb.appendLine("Future-dated: ${a.futureCount} rows (net ${money(a.futureSum)})")
    sb.appendLine("Unpaired transfers: ${a.unpairedTransferCount} rows (${money(a.unpairedTransferSum)})")
    if (a.duplicateGroups.isEmpty()) {
        sb.appendLine("Duplicates: none — every code appears once per amount+direction.")
    } else {
        sb.appendLine("DUPLICATE GROUPS (same code+amount+direction):")
        a.duplicateGroups.take(10).forEach { g ->
            sb.appendLine("· ${g.code} ${money(g.amount)} ${g.type} ×${g.count}")
        }
    }
    if (a.savingsTop.isNotEmpty()) {
        sb.appendLine("SAVINGS/INVESTMENT (largest first):")
        a.savingsTop.forEach { r ->
            sb.appendLine("· ${date(r.dateTimestamp)} ${money(r.amount)} ${r.merchant} [${r.category}${if (r.subcategory.isNotBlank()) "/" + r.subcategory else ""}] ${r.source}${r.sourceTransactionId?.let { " $it" }.orEmpty()}")
        }
    }
    if (a.incomeTop.isNotEmpty()) {
        sb.appendLine("INCOME (largest first, borrowed/internal included):")
        a.incomeTop.forEach { r ->
            sb.appendLine("· ${date(r.dateTimestamp)} ${money(r.amount)} ${r.merchant} [${r.category}${if (r.subcategory.isNotBlank()) "/" + r.subcategory else ""}] ${r.source}${r.sourceTransactionId?.let { " $it" }.orEmpty()}")
        }
    }
    return sb.toString().trimEnd()
}
