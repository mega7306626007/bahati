package com.pesaflow.app.parsers

import com.pesaflow.app.data.analytics.RecurringPattern
import com.pesaflow.app.data.finance.CashFlowProjection
import com.pesaflow.app.data.finance.DailyPoint
import com.pesaflow.app.data.finance.projectCashFlow
import com.pesaflow.app.data.models.Bill
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


/** Forward cash flow: paydays, bills, recurring → low point and broke date. */
class CashFlowProjectorTest {

    private val dayMs = 24L * 60 * 60 * 1000

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(y, m, d, h, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun bill(name: String, amount: Double, due: Long): Bill =
        Bill(name = name, amount = amount, dueDate = due, category = "Bills", frequency = "MONTHLY", status = "OPEN")

    private fun recurring(merchant: String, amount: Double, next: Long): RecurringPattern =
        RecurringPattern(
            merchant = merchant, category = "Kujibamba", amount = amount,
            medianIntervalDays = 30, occurrences = 4, firstSeen = 0, lastSeen = 0,
            confidence = 0.9f, nextExpectedDate = next, variance = 0.1, suggestion = ""
        )

    @Test
    fun `payday lands on its predicted day`() {
        val now = at(2026, Calendar.SEPTEMBER, 10, 9, 0)
        val payday = at(2026, Calendar.SEPTEMBER, 15, 0, 0)
        val p = projectCashFlow(
            balance = 1000.0, now = now, horizonDays = 30,
            paydays = listOf(Triple("HELB", 5000.0, payday))
        )
        val day15 = p.days.find { it.dayStart == payday }!!
        assertEquals(6000.0, day15.balance, 0.001)
        assertEquals(1, day15.events.size)
        assertEquals("HELB", day15.events[0].label)
        // Days before the payday are untouched.
        assertEquals(1000.0, p.days.first().balance, 0.001)
    }

    @Test
    fun `open bill subtracts on its due date`() {
        val now = at(2026, Calendar.SEPTEMBER, 10, 9, 0)
        val due = at(2026, Calendar.SEPTEMBER, 20, 0, 0)
        val p = projectCashFlow(
            balance = 3000.0, now = now, horizonDays = 30,
            bills = listOf(bill("Rent", 2500.0, due))
        )
        val day20 = p.days.find { it.dayStart == due }!!
        assertEquals(500.0, day20.balance, 0.001)
        assertEquals(-2500.0, day20.events[0].amount, 0.001)
    }

    @Test
    fun `paid bills never land`() {
        val now = at(2026, Calendar.SEPTEMBER, 10, 9, 0)
        val due = at(2026, Calendar.SEPTEMBER, 20, 0, 0)
        val p = projectCashFlow(
            balance = 3000.0, now = now, horizonDays = 30,
            bills = listOf(bill("Rent", 2500.0, due).copy(status = "PAID"))
        )
        assertEquals(3000.0, p.days.find { it.dayStart == due }!!.balance, 0.001)
        assertNull(p.brokeDate)
    }

    @Test
    fun `broke date is the first negative day`() {
        val now = at(2026, Calendar.SEPTEMBER, 10, 9, 0)
        val due = at(2026, Calendar.SEPTEMBER, 12, 0, 0)
        val p = projectCashFlow(
            balance = 1000.0, now = now, horizonDays = 30,
            bills = listOf(bill("Rent", 2500.0, due))
        )
        assertEquals(due, p.brokeDate)
        assertTrue(p.lowest.balance < 0)
    }

    @Test
    fun `positive run has no broke date and tracks the low`() {
        val now = at(2026, Calendar.SEPTEMBER, 10, 9, 0)
        val payday = at(2026, Calendar.SEPTEMBER, 25, 0, 0)
        val p = projectCashFlow(
            balance = 500.0, now = now, horizonDays = 30,
            paydays = listOf(Triple("HELB", 5000.0, payday))
        )
        assertNull(p.brokeDate)
        assertEquals(500.0, p.lowest.balance, 0.001)
        assertEquals(5500.0, p.endBalance, 0.001)
    }

    @Test
    fun `monthly recurring commitment lands on its next expected date`() {
        val now = at(2026, Calendar.SEPTEMBER, 10, 9, 0)
        val next = at(2026, Calendar.OCTOBER, 1, 0, 0)
        val p = projectCashFlow(
            balance = 2000.0, now = now, horizonDays = 40,
            recurring = listOf(recurring("Netflix", 650.0, next))
        )
        val day = p.days.find { it.dayStart == next }!!
        assertEquals(1350.0, day.balance, 0.001)
    }

    @Test
    fun `weekly recurring patterns are not monthly commitments`() {
        val now = at(2026, Calendar.SEPTEMBER, 10, 9, 0)
        val next = at(2026, Calendar.SEPTEMBER, 12, 0, 0)
        val weekly = recurring("Chama", 500.0, next).copy(medianIntervalDays = 7)
        val p = projectCashFlow(
            balance = 2000.0, now = now, horizonDays = 30,
            recurring = listOf(weekly)
        )
        assertEquals(2000.0, p.days.find { it.dayStart == next }!!.balance, 0.001)
    }

    @Test
    fun `daily burn compounds`() {
        val now = at(2026, Calendar.SEPTEMBER, 10, 9, 0)
        // 950 at 100/day crosses zero on the 10th day (index 9).
        val p = projectCashFlow(balance = 950.0, now = now, horizonDays = 10, dailyBurn = 100.0)
        assertEquals(-50.0, p.days[9].balance, 0.001)
        assertEquals(at(2026, Calendar.SEPTEMBER, 19, 0, 0), p.brokeDate)
    }

    @Test
    fun `exact zero balance is not broke`() {
        val now = at(2026, Calendar.SEPTEMBER, 10, 9, 0)
        val p = projectCashFlow(balance = 1000.0, now = now, horizonDays = 10, dailyBurn = 100.0)
        assertEquals(0.0, p.days[9].balance, 0.001)
        assertNull(p.brokeDate)
    }

    @Test
    fun `events outside the horizon are ignored`() {
        val now = at(2026, Calendar.SEPTEMBER, 10, 9, 0)
        val past = at(2026, Calendar.SEPTEMBER, 5, 0, 0)
        val future = at(2026, Calendar.OCTOBER, 20, 0, 0)
        val p = projectCashFlow(
            balance = 1000.0, now = now, horizonDays = 30,
            paydays = listOf(Triple("Old", 100.0, past), Triple("Later", 100.0, future))
        )
        assertEquals(1000.0, p.endBalance, 0.001)
    }

    @Test
    fun `empty inputs give a flat series`() {
        val now = at(2026, Calendar.SEPTEMBER, 10, 9, 0)
        val p = projectCashFlow(balance = 750.0, now = now, horizonDays = 30)
        assertEquals(30, p.days.size)
        p.days.forEach { assertEquals(750.0, it.balance, 0.001) }
        assertEquals(750.0, p.lowest.balance, 0.001)
        assertNull(p.brokeDate)
    }
}
