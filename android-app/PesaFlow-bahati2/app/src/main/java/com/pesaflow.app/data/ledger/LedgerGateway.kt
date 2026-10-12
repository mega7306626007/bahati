package com.pesaflow.app.data.ledger

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.repositories.FinanceRepository


/** Single commit funnel for typed inputs (manual, NLP, CSV, share).
 *  SMS auto-detect keeps its own coded-dedupe pipeline and lands here normalized.
 *  Every commit: validate → normalize merchant → default category → insert.
 *  Returns the inserted row (for undo) or null when invalid. */
object LedgerGateway {

    fun normalizeMerchant(raw: String, fallbackCategory: String): String {
        val clean = raw.trim().replace("\\s+".toRegex(), " ")
        if (clean.isEmpty()) return fallbackCategory.ifBlank { "General" }
        if (clean.equals("general", ignoreCase = true)) return fallbackCategory.ifBlank { "General" }
        // Title-case every word ("java house" -> "Java House"), not just the first.
        return clean.split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
    }


    suspend fun commit(
        repository: FinanceRepository,
        amount: Double,
        type: TransactionType,
        category: String,
        merchant: String,
        method: PaymentMethod,
        source: TransactionSource = TransactionSource.MANUAL,
        dateTimestamp: Long = System.currentTimeMillis(),
        description: String = "",
        isSample: Boolean = false,
        batchId: String? = null,
        notes: String = ""
    ): Transaction? {
        if (!amount.isFinite() || amount <= 0 || amount > 1_000_000_000) return null
        // Clamp fat-finger futures (year 2036 dates): anything past tomorrow
        // lands today instead of corrupting runway math.
        val now = System.currentTimeMillis()
        val safeDate = if (dateTimestamp > now + 24L * 60 * 60 * 1000) now else dateTimestamp
        val tx = Transaction(
            amount = kotlin.math.round(amount * 100) / 100.0,
            type = type,
            category = category.ifBlank {
                if (type == TransactionType.INCOME) "Salary" else "Food"
            },
            merchant = normalizeMerchant(merchant, category),
            description = description,
            paymentMethod = method,
            source = source,
            dateTimestamp = safeDate,
            isSample = isSample,
            batchId = batchId,
            notes = notes
        )
        repository.insertTransaction(tx)
        return tx
    }
}
