package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.formatLedgerAudit
import com.pesaflow.app.data.finance.ledgerAudit
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test


// Ledger audit: read-only truth dump for "where did my money go" hunts.
// Nothing here writes, deletes or reclassifies — findings first.
class LedgerAuditTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    private fun tx(
        amount: Double,
        type: TransactionType,
        merchant: String = "M",
        category: String = "Other",
        subcategory: String = "",
        code: String? = null,
        ts: Long = now - day,
        sample: Boolean = false,
        opening: Boolean = false,
        transferGroup: String? = null
    ) = Transaction(
        amount = amount, type = type, category = category, subcategory = subcategory,
        merchant = merchant, dateTimestamp = ts, paymentMethod = PaymentMethod.MPESA,
        source = TransactionSource.MPESA_SMS, sourceTransactionId = code,
        isSample = sample, isOpening = opening, transferGroupId = transferGroup
    )

    @Test
    fun `savings spike surfaces largest first with full detail`() {
        val txs = listOf(
            tx(50000.0, TransactionType.SAVING, "KCB transfer", "Transfers", code = "BANK01"),
            tx(40000.0, TransactionType.SAVING, "M-Shwari", "Savings", code = "MSH01"),
            tx(250.0, TransactionType.EXPENSE, "Kibanda", "Food")
        )
        val a = ledgerAudit(txs, now)
        assertEquals(90000.0, a.byType[TransactionType.SAVING] ?: 0.0, 0.001)
        assertEquals(2, a.byTypeCount[TransactionType.SAVING])
        assertEquals(2, a.savingsTop.size)
        assertEquals(50000.0, a.savingsTop[0].amount, 0.001)
        assertEquals("KCB transfer", a.savingsTop[0].merchant)
        assertEquals("BANK01", a.savingsTop[0].sourceTransactionId)
        val text = formatLedgerAudit(a)
        assertTrue(text.contains("SAVING: KSh 90000 (2 rows)"))
        assertTrue(text.contains("KCB transfer"))
    }

    @Test
    fun `income list includes borrowed and internal legs`() {
        val txs = listOf(
            tx(20000.0, TransactionType.INCOME, "HELB", "Salary"),
            tx(5000.0, TransactionType.INCOME, "Fuliza", "Debt", subcategory = "Borrowed funds"),
            tx(3000.0, TransactionType.INCOME, "M-Pesa Ziidi", "Savings", subcategory = "Ziidi transfer")
        )
        val a = ledgerAudit(txs, now)
        assertEquals(28000.0, a.byType[TransactionType.INCOME] ?: 0.0, 0.001)
        assertEquals(3, a.incomeTop.size)
        assertEquals("HELB", a.incomeTop[0].merchant)
    }

    @Test
    fun `sms statement twins group by code amount direction`() {
        val txs = listOf(
            tx(500.0, TransactionType.EXPENSE, "DANIEL MAYOLI", code = "UJ3CC8Z3YZ"),
            tx(500.0, TransactionType.EXPENSE, "DANIEL MAYOLI", code = "UJ3CC8Z3YZ"),
            tx(7.0, TransactionType.EXPENSE, "Withdrawal Charge", code = "UJ3CC8Z3YZ", subcategory = "Transaction Cost"),
            tx(500.0, TransactionType.INCOME, "DANIEL MAYOLI", code = "UJ3CC8Z3YZ")
        )
        val a = ledgerAudit(txs, now)
        // Same code, three (amount, direction) triples: only the exact pair groups.
        assertEquals(1, a.duplicateGroups.size)
        assertEquals(2, a.duplicateGroups[0].count)
        assertEquals(TransactionType.EXPENSE, a.duplicateGroups[0].type)
        // Fee rows ride the fee sum, never spending — but stay in the dump.
        assertEquals(7.0, a.feeSum, 0.001)
        assertTrue(formatLedgerAudit(a).contains("×2"))
    }

    @Test
    fun `samples futures opening and unpaired transfers counted separately`() {
        val txs = listOf(
            tx(20000.0, TransactionType.INCOME, "HELB", sample = true),
            tx(9999.0, TransactionType.EXPENSE, "Future Shop", ts = now + 10 * day),
            tx(360.0, TransactionType.INCOME, "Opening", opening = true),
            tx(500.0, TransactionType.TRANSFER, "Agent", transferGroup = null)
        )
        val a = ledgerAudit(txs, now)
        assertEquals(1, a.sampleCount)
        assertEquals(1, a.futureCount)
        assertEquals(360.0, a.openingSum, 0.001)
        assertEquals(1, a.unpairedTransferCount)
        assertEquals(500.0, a.unpairedTransferSum, 0.001)
        // Samples never leak into type totals.
        assertEquals(360.0, a.byType[TransactionType.INCOME] ?: 0.0, 0.001)
        val text = formatLedgerAudit(a)
        assertTrue(text.contains("Samples: 1 rows"))
        assertTrue(text.contains("Future-dated: 1 rows"))
        assertTrue(text.contains("Opening equity: KSh 360"))
    }
}
