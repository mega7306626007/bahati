package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.dashboard.paydaySpend
import org.junit.Assert.*
import org.junit.Test

private val day = 24L * 60 * 60 * 1000
private val base = 1700000000000L

private fun money(amount: Double, type: TransactionType, at: Long, merchant: String = "X"): Transaction =
    Transaction(
        amount = amount,
        type = type,
        category = if (type == TransactionType.INCOME) "Income" else "Food",
        dateTimestamp = at,
        merchant = merchant,
        description = "",
        paymentMethod = PaymentMethod.MPESA,
        source = TransactionSource.MPESA_SMS
    )

class PaydaySpendTest {
    @Test
    fun `fast burn detected after payday`() {
        val txs = listOf(
            money(10000.0, TransactionType.INCOME, base),
            money(2000.0, TransactionType.EXPENSE, base + day),
            money(3000.0, TransactionType.EXPENSE, base + 2 * day),
            money(1000.0, TransactionType.EXPENSE, base + 3 * day),
            money(500.0, TransactionType.EXPENSE, base + 20 * day)
        )
        val ps = paydaySpend(txs)
        assertNotNull(ps)
        assertTrue(ps!!.pctWithin3 >= 60)
        assertTrue(ps.medianLagDays <= 3.0)
    }

    @Test
    fun `slow stretch detected`() {
        val txs = listOf(
            money(10000.0, TransactionType.INCOME, base),
            money(1000.0, TransactionType.EXPENSE, base + 12 * day),
            money(1000.0, TransactionType.EXPENSE, base + 14 * day),
            money(1000.0, TransactionType.EXPENSE, base + 16 * day)
        )
        val ps = paydaySpend(txs)
        assertNotNull(ps)
        assertTrue(ps!!.medianLagDays >= 12)
        assertEquals(0.0, ps.pctWithin3, 0.001)
    }

    @Test
    fun `expenses attach to most recent payday`() {
        val txs = listOf(
            money(5000.0, TransactionType.INCOME, base),
            money(5000.0, TransactionType.INCOME, base + 30 * day),
            money(1000.0, TransactionType.EXPENSE, base + 31 * day),
            money(1000.0, TransactionType.EXPENSE, base + 32 * day),
            money(1000.0, TransactionType.EXPENSE, base + 33 * day)
        )
        val ps = paydaySpend(txs)
        assertNotNull(ps)
        assertEquals(2, ps!!.paydays)
        assertTrue(ps.medianLagDays <= 3.0)
    }

    @Test
    fun `no pattern without paydays or with thin data`() {
        assertNull(paydaySpend(emptyList()))
        assertNull(paydaySpend(listOf(
            money(500.0, TransactionType.EXPENSE, base),
            money(500.0, TransactionType.EXPENSE, base + day),
            money(500.0, TransactionType.EXPENSE, base + 2 * day)
        )))
        assertNull(paydaySpend(listOf(
            money(10000.0, TransactionType.INCOME, base),
            money(500.0, TransactionType.EXPENSE, base + day)
        )))
    }

    @Test
    fun `opening cash never counts as payday`() {
        val txs = listOf(
            money(5000.0, TransactionType.INCOME, base, "Opening balance")
                .copy(source = TransactionSource.OPENING),
            money(500.0, TransactionType.EXPENSE, base + day),
            money(500.0, TransactionType.EXPENSE, base + 2 * day),
            money(500.0, TransactionType.EXPENSE, base + 3 * day)
        )
        assertNull(paydaySpend(txs))
    }
}
