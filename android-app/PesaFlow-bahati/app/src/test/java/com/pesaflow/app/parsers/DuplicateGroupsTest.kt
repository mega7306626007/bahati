package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.viewmodels.exactDuplicateGroups
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


/** Duplicate grouping: same stamp, tight clock, samples never count. */
class DuplicateGroupsTest {

    private val min = 60_000L

    /** Monday 2026-09-07 12:00 local. */
    private fun base(): Long {
        return Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 7, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun tx(amount: Double, merchant: String, ts: Long, method: PaymentMethod = PaymentMethod.MPESA, sample: Boolean = false) =
        Transaction(
            amount = amount,
            type = TransactionType.EXPENSE,
            category = "Food",
            dateTimestamp = ts,
            merchant = merchant,
            paymentMethod = method,
            isSample = sample
        )

    @Test
    fun `minutes apart same day groups keep earliest`() {
        val b = base()
        val rows = listOf(tx(100.0, "Kibanda", b + 5 * min), tx(100.0, "Kibanda", b), tx(100.0, "Kibanda", b + 9 * min))
        val groups = exactDuplicateGroups(rows)
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].size)
        assertEquals(b, groups[0][0].dateTimestamp)
    }

    @Test
    fun `different days never group`() {
        val b = base()
        val rows = listOf(tx(100.0, "Kibanda", b), tx(100.0, "Kibanda", b + 24 * 60 * min))
        assertTrue(exactDuplicateGroups(rows).isEmpty())
    }

    @Test
    fun `eleven minutes apart same day does not group`() {
        val b = base()
        val rows = listOf(tx(100.0, "Kibanda", b), tx(100.0, "Kibanda", b + 11 * min))
        assertTrue(exactDuplicateGroups(rows).isEmpty())
    }

    @Test
    fun `different method or merchant never group`() {
        val b = base()
        assertTrue(exactDuplicateGroups(listOf(
            tx(100.0, "Kibanda", b), tx(100.0, "Kibanda", b + min, PaymentMethod.CASH)
        )).isEmpty())
        assertTrue(exactDuplicateGroups(listOf(
            tx(100.0, "Kibanda", b), tx(100.0, "Java", b + min)
        )).isEmpty())
    }

    @Test
    fun `samples never count as duplicates`() {
        val b = base()
        val rows = listOf(tx(100.0, "Kibanda", b), tx(100.0, "Kibanda", b + min, sample = true))
        assertTrue(exactDuplicateGroups(rows).isEmpty())
    }

    @Test
    fun `empty and single row yield nothing`() {
        assertTrue(exactDuplicateGroups(emptyList()).isEmpty())
        assertTrue(exactDuplicateGroups(listOf(tx(100.0, "Kibanda", base()))).isEmpty())
    }
}
