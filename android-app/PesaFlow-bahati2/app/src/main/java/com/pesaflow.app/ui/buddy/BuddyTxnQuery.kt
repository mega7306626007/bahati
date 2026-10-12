package com.pesaflow.app.ui.buddy

import com.pesaflow.app.data.finance.Money
import com.pesaflow.app.data.finance.MoneyFormatter
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType

// Read-only transaction search over the canonical ledger list. The UI hands
// in viewModel.allTransactions; this layer only filters and summarizes — no
// second truth, no writes. Pure, unit-tested.
data class TxnFilter(
    val merchant: String? = null,
    val category: String? = null,
    val amount: Double? = null,
    val expenseOnly: Boolean = false,
    val incomeOnly: Boolean = false
) {
    fun isEmpty(): Boolean =
        merchant == null && category == null && amount == null && !expenseOnly && !incomeOnly
}

data class TxnQueryResult(
    val count: Int,
    val total: Double,
    val lines: List<String>
)

object BuddyTxnQuery {

    fun filter(txs: List<Transaction>, filter: TxnFilter): List<Transaction> =
        txs.filter { tx ->
            if (tx.isSample) return@filter false
            if (filter.expenseOnly && tx.type == TransactionType.INCOME) return@filter false
            if (filter.incomeOnly && tx.type != TransactionType.INCOME) return@filter false
            if (filter.category != null && !tx.category.equals(filter.category, ignoreCase = true)) return@filter false
            if (filter.merchant != null && !tx.merchant.contains(filter.merchant, ignoreCase = true)) return@filter false
            if (filter.amount != null && kotlin.math.abs(tx.amount - filter.amount) >= 0.01) return@filter false
            true
        }

    fun summarize(matches: List<Transaction>, maxLines: Int = 3): TxnQueryResult {
        val total = matches.sumOf { it.amount }
        val lines = matches.sortedByDescending { it.dateTimestamp }.take(maxLines).map {
            "• ${MoneyFormatter.compact(Money.of(it.amount))} · ${it.merchant} · ${it.category}"
        }
        return TxnQueryResult(matches.size, total, lines)
    }

    // Candidate matching for reclassify/delete: merchant token containment,
    // narrowed by amount when given. Most recent first.
    fun candidates(
        txs: List<Transaction>,
        merchant: String?,
        amount: Double?,
        limit: Int = 5
    ): List<Transaction> {
        val tokens = merchant?.lowercase()?.split(Regex("[^a-z0-9]+"))
            ?.filter { it.length > 2 }.orEmpty()
        return txs.filter { tx ->
            if (tx.isSample) return@filter false
            if (amount != null && kotlin.math.abs(tx.amount - amount) >= 0.01) return@filter false
            if (tokens.isNotEmpty()) {
                val low = tx.merchant.lowercase()
                if (tokens.none { low.contains(it) }) return@filter false
            }
            true
        }.sortedByDescending { it.dateTimestamp }.take(limit)
    }
}
