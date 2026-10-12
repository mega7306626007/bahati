package com.pesaflow.app.data.parsers

// Pending-to-ledger sync policy: which queued rows may confirm themselves.
// Single source for the dashboard bulk bar and the onboarding first-sync,
// so "sure" never means two different things on two screens. Pure logic.
object PendingPolicy {
    /** History-vouched threshold shared by every "confirm sure" surface. */
    const val SURE_CONFIDENCE = 0.85f

    /**
     * A row may auto-confirm only with a carrier reference code (M-Pesa TID,
     * bank ref — bank-grade records) AND sure confidence. Codeless shapes
     * (bare receipts, header-less catcher) always wait for human eyes.
     */
    fun isAutoApprovable(sourceTransactionId: String?, effectiveConfidence: Float): Boolean =
        !sourceTransactionId.isNullOrBlank() && effectiveConfidence >= SURE_CONFIDENCE

    /**
     * Approval-time upgrade: an "Other" (or blank) verdict first
     * consults learned memory, then keyword inference, before they hit
     * the ledger — approved rows arrive categorized and the health grade
     * can actually climb. Anything already categorized passes through.
     */
    fun upgradeOtherCategory(current: String, memorized: String?, inferred: String): String {
        if (current.isNotBlank() && !current.equals("Other", ignoreCase = true)) return current
        if (!memorized.isNullOrBlank()) return memorized
        if (inferred.isNotBlank() && !inferred.equals("Other", ignoreCase = true)) return inferred
        return current
    }

    /**
     * A row needs human eyes when it has no usable category: blank or
     * "Other". Everything else bulk-confirms — approval still runs the
     * memory+inference upgrade, so borderline rows arrive categorized.
     */
    fun needsCategory(pending: com.pesaflow.app.data.models.PendingTransaction): Boolean =
        pending.category.isBlank() || pending.category.equals("Other", ignoreCase = true)

    /** Pendings older than [maxAgeDays] — stale queue rot, surfaced for cleanup. */
    fun stalePendings(
        pendings: List<com.pesaflow.app.data.models.PendingTransaction>,
        now: Long,
        maxAgeDays: Int = 14
    ): List<com.pesaflow.app.data.models.PendingTransaction> {
        val cutoff = now - maxAgeDays.toLong() * 24 * 60 * 60 * 1000
        return pendings.filter { it.dateTimestamp < cutoff }
    }
}
