package com.pesaflow.app.ui.buddy

import com.pesaflow.app.data.finance.Money
import com.pesaflow.app.data.finance.MoneyFormatter
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.ui.dashboard.BuddyMemory
import com.pesaflow.app.ui.dashboard.PendingCandidate
import com.pesaflow.app.ui.dashboard.PendingCandidateAction

// Turns reclassify/delete hints into concrete txIds against the canonical
// ledger list. Single match → command; several → numbered choices held in
// BuddyMemory ("2", "the second one", "all"); none → honest ask. The reply
// resolver consumes the memory so stale choices never fire twice.
// Pure apart from the memory handoff, unit-tested.
sealed interface TxnResolution {
    data class Single(val txIds: List<String>) : TxnResolution
    data class Multiple(val candidates: List<PendingCandidate>) : TxnResolution
    data object None : TxnResolution
}

object BuddyTxnResolver {

    fun label(tx: Transaction): String =
        "${MoneyFormatter.compact(Money.of(tx.amount))} · ${tx.merchant} · ${tx.category}"

    fun resolve(
        txs: List<Transaction>,
        merchant: String?,
        amount: Double?,
        action: PendingCandidateAction
    ): TxnResolution {
        val matches = BuddyTxnQuery.candidates(txs, merchant, amount)
        return when {
            matches.isEmpty() -> TxnResolution.None
            matches.size == 1 -> TxnResolution.Single(listOf(matches.first().id))
            else -> {
                val cands = matches.map { PendingCandidate(it.id, label(it), action) }
                BuddyMemory.pendingCandidates = cands
                TxnResolution.Multiple(cands)
            }
        }
    }

    private val ORDINALS = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
        "1st" to 1, "2nd" to 2, "3rd" to 3, "4th" to 4, "5th" to 5
    )

    // Returns a resolved intent when the message answers pending candidates,
    // clearing the memory either way. Null = not a candidate reply.
    fun resolveReply(normalized: String): BuddyIntent? {
        val cands = BuddyMemory.pendingCandidates ?: return null
        val t = normalized.trim()
        val wantsAll = t == "all" || t == "all of them" || t == "yote" || t == "zote"
        val pick: Int? = when {
            wantsAll -> null
            Regex("^number\\s?(\\d{1,2})$").find(t)?.groupValues?.get(1)?.toIntOrNull() != null ->
                Regex("^number\\s?(\\d{1,2})$").find(t)!!.groupValues[1].toInt()
            Regex("^(\\d{1,2})$").find(t)?.groupValues?.get(1)?.toIntOrNull() != null ->
                t.toInt()
            else -> ORDINALS.entries.firstOrNull { (w, _) -> t.contains(w) }?.value
        }
        if (!wantsAll && pick == null) return null
        BuddyMemory.pendingCandidates = null
        if (wantsAll) {
            val reclass = cands.mapNotNull { (it.action as? PendingCandidateAction.Reclassify)?.category }.firstOrNull()
            if (reclass != null && cands.all { it.action is PendingCandidateAction.Reclassify }) {
                return BuddyIntent.ResolvedReclassify(cands.map { it.txId }, reclass)
            }
            if (cands.all { it.action is PendingCandidateAction.Delete }) {
                // Bulk delete from chat stays unsupported — empty ids ask why.
                return BuddyIntent.ResolvedDelete(emptyList())
            }
            return null // bulk debt-pay and mixed sets: pick numbers instead
        }
        val chosen = cands.getOrNull((pick ?: return null) - 1) ?: return null
        return when (val a = chosen.action) {
            is PendingCandidateAction.Reclassify -> BuddyIntent.ResolvedReclassify(listOf(chosen.txId), a.category)
            PendingCandidateAction.Delete -> BuddyIntent.ResolvedDelete(listOf(chosen.txId))
            is PendingCandidateAction.DebtPay -> BuddyIntent.ResolvedDebtPay(a.debtId, a.amount)
        }
    }

    fun clearPending() {
        BuddyMemory.pendingCandidates = null
    }
}
