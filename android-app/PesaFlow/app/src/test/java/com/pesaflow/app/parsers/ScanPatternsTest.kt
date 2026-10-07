package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.analyzeScan
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

private fun scanTs(month: Int, day: Int): Long {
    val c = Calendar.getInstance()
    c.set(2026, month, day, 12, 0, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun row(
    merchant: String, amount: Double, type: TransactionType, category: String, at: Long
): PendingTransaction = PendingTransaction(
    amount = amount,
    type = type,
    category = category,
    merchant = merchant,
    dateTimestamp = at,
    paymentMethod = PaymentMethod.MPESA,
    source = TransactionSource.MPESA_SMS,
    sourceTransactionId = null,
    rawText = "$merchant $amount"
)

class ScanPatternsTest {
    private fun sample() = listOf(
        row("HELB", 8000.0, TransactionType.INCOME, "HELB", scanTs(Calendar.AUGUST, 5)),
        row("HELB", 8200.0, TransactionType.INCOME, "HELB", scanTs(Calendar.SEPTEMBER, 5)),
        row("Mum", 1000.0, TransactionType.INCOME, "Parent", scanTs(Calendar.SEPTEMBER, 6)),
        row("Naivas", 2500.0, TransactionType.EXPENSE, "Shopping", scanTs(Calendar.SEPTEMBER, 7)),
        row("Klabu", 70.0, TransactionType.EXPENSE, "Food", scanTs(Calendar.SEPTEMBER, 8)),
        row("Klabu", 70.0, TransactionType.EXPENSE, "Food", scanTs(Calendar.SEPTEMBER, 9))
    )

    @Test
    fun `payday is the repeat income sender`() {
        val p = analyzeScan(sample()).payday
        assertNotNull(p)
        assertEquals("HELB", p!!.who)
        assertEquals(2, p.count)
        assertEquals(5, p.dayOfMonth)
        assertTrue(p.amount in 7900..8300)
    }

    @Test
    fun `one ping is money not a pattern`() {
        val rows = listOf(
            row("HELB", 8000.0, TransactionType.INCOME, "HELB", scanTs(Calendar.SEPTEMBER, 5)),
            row("Klabu", 70.0, TransactionType.EXPENSE, "Food", scanTs(Calendar.SEPTEMBER, 6)),
            row("Klabu", 70.0, TransactionType.EXPENSE, "Food", scanTs(Calendar.SEPTEMBER, 7))
        )
        assertNull(analyzeScan(rows).payday)
    }

    @Test
    fun `top categories biggest and totals read out`() {
        val s = analyzeScan(sample())
        assertEquals("Shopping", s.topExpense.first().first)
        assertEquals(2500, s.topExpense.first().second)
        assertEquals("Naivas" to 2500, s.biggestOut)
        assertEquals(17200, s.totalIn)
    }

    @Test
    fun `span counts real days not the request window`() {
        val s = analyzeScan(sample())
        // 6 distinct days carry texts (Aug 5, Sep 5–9), not the 36-day
        // max−min estimate.
        assertEquals(6, s.spanDays)
    }

    @Test
    fun `midnight clusterers count every calendar day`() {
        fun at(day: Int, hour: Int): Long {
            val c = Calendar.getInstance()
            c.set(2026, Calendar.SEPTEMBER, day, hour, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        val rows = listOf(
            row("A", 100.0, TransactionType.EXPENSE, "Food", at(7, 23)),
            row("B", 100.0, TransactionType.EXPENSE, "Food", at(8, 1)),
            row("C", 100.0, TransactionType.EXPENSE, "Food", at(9, 22))
        )
        // 47 hours max−min estimated 2 days; the user lived 3 calendar days.
        assertEquals(3, analyzeScan(rows).spanDays)
    }

    @Test
    fun `empty scan is an empty pattern`() {
        val s = analyzeScan(emptyList())
        assertNull(s.payday)
        assertTrue(s.topExpense.isEmpty())
        assertNull(s.biggestOut)
        assertEquals(0, s.spanDays)
    }
}
