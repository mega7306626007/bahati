package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.AmountStamp
import com.pesaflow.app.data.parsers.clusterUnknowns
import com.pesaflow.app.data.parsers.detectMorningRoutine
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

private fun morningTs(dayOffset: Int, hour: Int, min: Int = 15): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_MONTH, -dayOffset)
    c.set(Calendar.HOUR_OF_DAY, hour)
    c.set(Calendar.MINUTE, min)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun unk(merchant: String, amount: Double, at: Long): PendingTransaction = PendingTransaction(
    amount = amount,
    type = TransactionType.EXPENSE,
    category = "Unknown",
    merchant = merchant,
    dateTimestamp = at,
    paymentMethod = PaymentMethod.MPESA,
    source = TransactionSource.MPESA_SMS,
    sourceTransactionId = null,
    rawText = "$merchant $amount"
)

class RoutinesTest {
    @Test
    fun `same morning amount is breakfast`() {
        val stamps = (1..5).map { AmountStamp(70.0, morningTs(it, 8)) } +
            listOf(AmountStamp(500.0, morningTs(1, 19)))
        val r = detectMorningRoutine(stamps)
        assertNotNull(r)
        assertEquals(70, r!!.amount)
        assertEquals(5, r.count)
    }

    @Test
    fun `hiked prices within fifty still match`() {
        val stamps = listOf(70.0, 70.0, 80.0, 100.0, 70.0).mapIndexed { i, a ->
            AmountStamp(a, morningTs(i + 1, 7 + (i % 3)))
        }
        val r = detectMorningRoutine(stamps)
        assertNotNull(r)
        assertEquals(5, r!!.count)
    }

    @Test
    fun `night spends never count as morning`() {
        val stamps = (1..5).map { AmountStamp(70.0, morningTs(it, 21)) }
        assertNull(detectMorningRoutine(stamps))
    }

    @Test
    fun `two sightings are not a habit`() {
        val stamps = (1..2).map { AmountStamp(70.0, morningTs(it, 8)) }
        assertNull(detectMorningRoutine(stamps))
    }

    @Test
    fun `unknowns cluster by hour and amount`() {
        val rows = (1..4).map { unk("Klabu", 70.0, morningTs(it, 8)) } +
            (1..3).map { unk("Stage", 50.0, morningTs(it, 18)) } +
            listOf(unk("Once", 999.0, morningTs(1, 12)))
        val clusters = clusterUnknowns(rows)
        assertEquals(2, clusters.size)
        assertEquals(4, clusters.first().count)
        assertEquals(70, clusters.first().medianAmount)
        assertEquals(8, clusters.first().hour)
    }

    @Test
    fun `known rows never cluster`() {
        val rows = listOf(
            unk("Klabu", 70.0, morningTs(1, 8)).copy(category = "Food"),
            unk("Klabu", 70.0, morningTs(2, 8)).copy(category = "Food")
        )
        assertTrue(clusterUnknowns(rows).isEmpty())
    }
}
