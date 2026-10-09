package com.pesaflow.app.parsers

import com.pesaflow.app.data.analytics.hourLabel
import com.pesaflow.app.data.analytics.hourlyPeak
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.LedgerRow
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


class HourPatternTest {

    private fun at(hour: Int, dayOffset: Int = 0): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, -dayOffset)
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, 10)
        return c.timeInMillis
    }

    private fun spend(amount: Double, ts: Long) =
        LedgerRow(amount, TransactionType.EXPENSE, "Food", "M", ts)

    @Test
    fun `lunch crowd peaks midday`() {
        val rows = (0..6).flatMap { d ->
            listOf(spend(300.0, at(13, d)), spend(50.0, at(21, d)))
        }
        val p = hourlyPeak(rows)!!
        assertTrue(p.peakStartHour in 11..13)
        assertTrue(p.sharePct >= 35)
        assertEquals(14, p.count)
    }

    @Test
    fun `thin data stays silent`() {
        val rows = (0..3).map { spend(300.0, at(13, it)) }
        assertNull(hourlyPeak(rows))
    }

    @Test
    fun `flat day stays silent`() {
        val rows = (0..11).map { spend(100.0, at(it * 2, it % 3)) }
        assertNull(hourlyPeak(rows))
    }

    @Test
    fun `income ignored only outflow counts`() {
        val rows = (0..6).map { spend(300.0, at(13, it)) } +
            listOf(LedgerRow(50000.0, TransactionType.INCOME, "Salary", "E", at(9, 0)))
        val p = hourlyPeak(rows)!!
        assertTrue(p.sharePct >= 35)
    }

    @Test
    fun `clock labels read right`() {
        assertEquals("12am", hourLabel(0))
        assertEquals("1pm", hourLabel(13))
        assertEquals("12pm", hourLabel(12))
        assertEquals("11pm", hourLabel(23))
    }
}
