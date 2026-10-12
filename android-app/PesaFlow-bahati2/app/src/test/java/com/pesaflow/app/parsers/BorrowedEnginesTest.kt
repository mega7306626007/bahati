package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.AmountStamp
import com.pesaflow.app.data.parsers.campusFoods
import com.pesaflow.app.data.parsers.clusterUnknowns
import com.pesaflow.app.data.parsers.detectMorningRoutine
import com.pesaflow.app.data.parsers.hostelMonthly
import com.pesaflow.app.data.parsers.shouldAskRent
import com.pesaflow.app.data.parsers.shouldAskTransport
import com.pesaflow.app.data.parsers.suggestStarterBudgets
import com.pesaflow.app.data.schedule.CommutePlan
import com.pesaflow.app.data.schedule.TripRoute
import com.pesaflow.app.data.schedule.monthlyFare
import com.pesaflow.app.data.schedule.parseClock
import com.pesaflow.app.data.schedule.decodeRoutes
import com.pesaflow.app.data.schedule.encodeRoutes
import org.junit.Assert.*
import org.junit.Test


// Borrowed engines: commute math, morning-routine + unknown clustering,
// planner gears (campus food, hostel costs, starter budgets).
class BorrowedEnginesTest {

    @Test
    fun `clock parses forgivingly, garbage rejected`() {
        assertEquals(7 to 30, parseClock("7:30"))
        assertEquals(19 to 30, parseClock("7:30pm"))
        assertEquals(17 to 30, parseClock("1730"))
        assertEquals(7 to 0, parseClock("7"))
        assertNull(parseClock("banana"))
        assertNull(parseClock("3"))
    }

    @Test
    fun `monthly fare scales days trips fare`() {
        assertEquals(50.0 * 2 * 5 * 4.33, monthlyFare(50.0, 5), 0.01)
        assertEquals(0.0, monthlyFare(0.0, 5), 0.001)
        assertEquals(0.0, monthlyFare(50.0, 0), 0.001)
    }

    @Test
    fun `trip routes round-trip, bad rows skipped`() {
        val routes = listOf(
            TripRoute("Church", setOf("Sun"), 80.0, "08:00", "12:00"),
            TripRoute("Shags", setOf("Sat"), 350.0)
        )
        val back = decodeRoutes(encodeRoutes(routes))
        assertEquals(2, back.size)
        assertEquals("Church", back[0].name)
        assertEquals(80.0, back[0].fareOneWay, 0.001)
        assertTrue(decodeRoutes("garbage-row").isEmpty())
        assertTrue(decodeRoutes(null).isEmpty())
    }

    private fun pending(
        merchant: String,
        amount: Double,
        ts: Long,
        category: String = "Other",
        type: TransactionType = TransactionType.EXPENSE
    ) = PendingTransaction(
        amount = amount,
        type = type,
        category = category,
        merchant = merchant,
        dateTimestamp = ts,
        paymentMethod = PaymentMethod.MPESA,
        source = TransactionSource.MPESA_SMS,
        sourceTransactionId = null,
        rawText = merchant
    )

    private fun tsMs(day: Int, hour: Int): Long {
        val c = java.util.Calendar.getInstance()
        c.set(2026, java.util.Calendar.JUNE, day, hour, 15, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    @Test
    fun `morning routine concludes breakfast amount`() {
        val stamps = listOf(
            AmountStamp(150.0, tsMs(1, 8)),
            AmountStamp(160.0, tsMs(2, 8)),
            AmountStamp(155.0, tsMs(3, 7)),
            AmountStamp(2000.0, tsMs(4, 20))
        )
        val r = detectMorningRoutine(stamps)
        assertNotNull(r)
        assertEquals(3, r!!.count)
        assertTrue(r.amount in 150..160)
    }

    @Test
    fun `unknown clusters collapse same-hour habits`() {
        val rows = listOf(
            pending("Till 123", 100.0, tsMs(1, 8), "Unknown"),
            pending("Till 123", 105.0, tsMs(2, 8), "Unknown"),
            pending("Naivas", 2500.0, tsMs(3, 8), "Shopping")
        )
        val clusters = clusterUnknowns(rows)
        assertEquals(1, clusters.size)
        assertEquals(2, clusters[0].count)
        assertEquals(8, clusters[0].hour)
    }

    @Test
    fun `planner gears map campus data`() {
        assertTrue(campusFoods("University of Nairobi").any { it.price == 70.0 })
        assertEquals(1900.0, hostelMonthly("UoN") ?: 0.0, 0.001)
        assertNull(hostelMonthly(""))
        assertTrue(shouldAskRent("Hostel"))
        assertFalse(shouldAskRent("Parents"))
        assertFalse(shouldAskTransport("Walk"))
        val budgets = suggestStarterBudgets(20000.0, 6000.0, 0.0, 3000.0)
        assertEquals(20000.0, budgets["ALL"] ?: 0.0, 0.001)
        assertFalse(budgets.containsKey("Rent"))
    }

    @Test
    fun `commute plan is just data`() {
        val plan = CommutePlan(setOf("Mon", "Tue"), 60.0, "07:00", "18:00")
        assertEquals(2, plan.tripsPerDay)
        assertEquals(60.0 * 2 * 2 * 4.33, monthlyFare(plan.fareOneWay, plan.days.size, plan.tripsPerDay), 0.01)
    }
}
