package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.LedgerRow
import com.pesaflow.app.ui.dashboard.factorForToday
import com.pesaflow.app.ui.dashboard.isUnusualDay
import com.pesaflow.app.ui.dashboard.weekdayIndex
import com.pesaflow.app.ui.dashboard.weekdayProfile
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


/** Weekday pacing: hot-day factors, thin-history nulls, unusual days. */
class WeekdayPaceTest {

    private val dayMs = 24L * 60 * 60 * 1000

    /** Monday 2026-09-07 12:00 local. */
    private fun monday(): Long {
        return Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 7, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun row(amount: Double, ts: Long): LedgerRow {
        return LedgerRow(amount, TransactionType.EXPENSE, "Food", "M", ts)
    }

    @Test
    fun `monday indexes first sunday last`() {
        assertEquals(0, weekdayIndex(monday()))
        assertEquals(5, weekdayIndex(monday() + 5 * dayMs))
        assertEquals(6, weekdayIndex(monday() + 6 * dayMs))
    }

    @Test
    fun `hot saturday earns max factor quiet tuesday min`() {
        val mon = monday()
        val rows = mutableListOf<LedgerRow>()
        for (w in 0..3) {
            for (d in 0..6) {
                rows.add(row(if (d == 5) 1000.0 else 100.0, mon + (w * 7 + d) * dayMs))
            }
        }
        val now = mon + 28 * dayMs - 1
        val p = weekdayProfile(rows, now)!!
        assertEquals(1.4, p.factorFor(mon + 5 * dayMs), 0.001)
        // Hot Saturdays drag the mean up; every normal day sits at the floor.
        assertEquals(0.6, p.factorFor(mon + 1 * dayMs), 0.001)
        assertEquals(0.6, p.factorFor(mon), 0.001)
        // Calendar-day window covers the 28 days ending today: the Sep-7 row
        // predates the frame, so 27 distinct days are active. The old
        // (now - ts) / 24h bucket counted it against a frame that never
        // contained Sep 7 — sums and occurrences disagreed.
        assertTrue(p.activeDays == 27)
    }

    @Test
    fun `thin or empty history yields null`() {
        val mon = monday()
        val thin = (0..4).map { row(100.0, mon + it * dayMs) }
        assertNull(weekdayProfile(thin, mon + 10 * dayMs))
        assertNull(weekdayProfile(emptyList(), mon + 10 * dayMs))
    }

    @Test
    fun `partial window weights by actual occurrences`() {
        val mon = monday()
        // Spend days 1..10 (inside the window) so rates align with occurrences.
        // now sits at noon: occurrence instants and noon rows share day buckets.
        val rows = (1..10).map { row(100.0, mon + it * dayMs) }
        val p = weekdayProfile(rows, mon + 10 * dayMs, windowDays = 10)!!
        assertTrue(p.activeDays == 10)
        p.factors.forEach { assertEquals(1.0, it, 0.01) }
    }

    @Test
    fun `unusual day needs double plus 200 gap`() {
        assertTrue(isUnusualDay(1500.0, 500.0))
        assertTrue(isUnusualDay(300.0, 100.0))
        assertFalse(isUnusualDay(600.0, 500.0))
        assertFalse(isUnusualDay(250.0, 100.0))
        assertFalse(isUnusualDay(0.0, 500.0))
    }

    @Test
    fun `factorForToday is a pure alias`() {
        val mon = monday()
        val rows = (0..13).map { row(100.0, mon + it * dayMs) }
        val p = weekdayProfile(rows, mon + 14 * dayMs - 1, windowDays = 14)!!
        assertEquals(p.factorFor(mon + 3 * dayMs), factorForToday(p, mon + 3 * dayMs), 0.0)
    }
}
