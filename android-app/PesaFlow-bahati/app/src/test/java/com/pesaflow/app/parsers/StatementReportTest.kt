package com.pesaflow.app.parsers

import com.pesaflow.app.data.exports.reportTypeLabel
import com.pesaflow.app.data.exports.statementReportModel
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test


// PesaSense-style statement report: wallet-truth totals, fee separation,
// per-row SIM provenance. Renderer is Android-only; all math pinned here.
class StatementReportTest {

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
        sim: Int = -1
    ) = Transaction(
        amount = amount, type = type, category = category, subcategory = subcategory,
        merchant = merchant, dateTimestamp = ts, paymentMethod = PaymentMethod.MPESA,
        source = TransactionSource.MPESA_SMS, sourceTransactionId = code, simSlot = sim,
        isSample = sample
    )

    @Test
    fun `income counts all inflows expenses exclude fees net ignores fees`() {
        val txs = listOf(
            tx(20000.0, TransactionType.INCOME, "HELB", "Salary"),
            tx(5000.0, TransactionType.INCOME, "Fuliza", "Debt", subcategory = "Borrowed funds"),
            tx(500.0, TransactionType.EXPENSE, "DANIEL MAYOLI"),
            tx(7.0, TransactionType.EXPENSE, "Withdrawal Charge", subcategory = "Transaction Cost")
        )
        val r = statementReportModel(txs, now)
        // Wallet truth: borrowed legs are money in; fees never inflate spending.
        assertEquals(25000.0, r.income, 0.001)
        assertEquals(500.0, r.expenses, 0.001)
        assertEquals(7.0, r.fees, 0.001)
        assertEquals(24500.0, r.net, 0.001)
        assertEquals(4, r.count)
        assertEquals(0, r.excludedCount)
    }

    @Test
    fun `samples never export but chosen windows bound everything`() {
        val txs = listOf(
            tx(20000.0, TransactionType.INCOME, "HELB", sample = true),
            tx(9999.0, TransactionType.EXPENSE, "Future Shop", ts = now + 10 * day),
            tx(100.0, TransactionType.EXPENSE, "Kibanda", "Food")
        )
        // All time = every real row, future-dated included; only demo
        // samples are excluded (and counted as EXCLUDED).
        val all = statementReportModel(txs, now)
        assertEquals(0.0, all.income, 0.001)
        assertEquals(10099.0, all.expenses, 0.001)
        assertEquals(2, all.count)
        assertEquals(1, all.excludedCount)
        // A duration-scoped export states exactly what it covers.
        val month = statementReportModel(txs, now, startMs = now - 30 * day, endMs = now)
        assertEquals(100.0, month.expenses, 0.001)
        assertEquals(1, month.count)
        assertTrue(month.periodLabel.isNotBlank())
    }

    @Test
    fun `type labels mirror the reference report vocabulary`() {
        assertEquals("Money Sent", reportTypeLabel(tx(10.0, TransactionType.EXPENSE, "NAIVAS")))
        assertEquals("Money Received", reportTypeLabel(tx(10.0, TransactionType.INCOME, "NANCY")))
        assertEquals("Transaction Cost", reportTypeLabel(tx(7.0, TransactionType.EXPENSE, "Charge", subcategory = "Transaction Cost")))
        assertEquals("Fuliza Borrow", reportTypeLabel(tx(100.0, TransactionType.INCOME, "Fuliza", subcategory = "Borrowed funds")))
        assertEquals("Fuliza Repayment", reportTypeLabel(tx(50.0, TransactionType.EXPENSE, "Fuliza", subcategory = "Fuliza repayment")))
        assertEquals("Money Transfer", reportTypeLabel(tx(60.0, TransactionType.TRANSFER, "KCB")))
        assertEquals("Savings Move", reportTypeLabel(tx(70.0, TransactionType.SAVING, "M-Shwari")))
    }

    @Test
    fun `rows carry code sim and fee columns newest first`() {
        val txs = listOf(
            tx(500.0, TransactionType.EXPENSE, "DANIEL MAYOLI", code = "UJ3CC8Z3YZ", ts = now - 2 * day, sim = 1),
            tx(200.0, TransactionType.EXPENSE, "Shop", code = null, ts = now - day, sim = 0)
        )
        val r = statementReportModel(txs, now)
        assertEquals(2, r.rows.size)
        assertEquals("UJ3CC8Z3YZ", r.rows[1].code)
        assertEquals("–", r.rows[0].code)
        assertEquals("SIM 2", r.rows[1].sim)
        assertEquals("SIM 1", r.rows[0].sim)
        assertEquals("KES 0", if (r.rows[0].fee > 0) "x" else "KES 0")
        // Newest first.
        assertTrue(r.rows[0].date.isNotBlank())
        assertEquals(1, r.byCategory.size)
        assertEquals(100, r.byCategory[0].pct)
    }

    @Test
    fun `category rollup ranks with counts and period labels`() {
        val txs = listOf(
            tx(100.0, TransactionType.EXPENSE, "A", "Food", ts = now - 40 * day),
            tx(300.0, TransactionType.EXPENSE, "B", "Transport", ts = now - day),
            tx(200.0, TransactionType.EXPENSE, "C", "Transport", ts = now - day)
        )
        val r = statementReportModel(txs, now)
        assertEquals(2, r.byCategory.size)
        assertEquals("Transport", r.byCategory[0].name)
        assertEquals(500.0, r.byCategory[0].total, 0.001)
        assertEquals(2, r.byCategory[0].count)
        assertTrue(r.periodLabel.contains("–"))
        assertTrue(r.generatedLabel.isNotBlank())
    }
}
