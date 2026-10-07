package com.pesaflow.app.parsers

import com.pesaflow.app.data.academic.DAY_MS
import com.pesaflow.app.data.academic.ScanWindow
import com.pesaflow.app.data.academic.anomalyWeeks
import com.pesaflow.app.data.academic.currentWeekRange
import com.pesaflow.app.data.academic.dayMs
import com.pesaflow.app.data.academic.dayStart
import com.pesaflow.app.data.academic.last7DaysRange
import com.pesaflow.app.data.academic.previousWeekRange
import com.pesaflow.app.data.academic.prior7DaysRange
import com.pesaflow.app.data.academic.distinctDays
import com.pesaflow.app.data.academic.tsMs
import com.pesaflow.app.data.academic.weekBuckets
import com.pesaflow.app.data.academic.wholeWeeksCeil
import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.academic.weekKey
import com.pesaflow.app.data.academic.weekStartMonday
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.analytics.ExpenditureRange
import com.pesaflow.app.ui.analytics.aggregateExpenditure
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone


// One definition of a week: Monday 00:00 local to next Monday 00:00.
// Graphs, dashboard, profile, check-ins and reports all derive from it.
class WeekTimelineTest {

    // Oct 2026: Thu 1, Sun 4, Sun 11, Mon 12, Wed 14, Sun 18.
    private val sunOct11Noon = tsMs(2026, Calendar.OCTOBER, 11, 12)
    private val monOct12Noon = tsMs(2026, Calendar.OCTOBER, 12, 12)
    private val wedOct14Noon = tsMs(2026, Calendar.OCTOBER, 14, 12)

    private fun expense(amount: Double, y: Int, m: Int, d: Int, h: Int = 12): Transaction =
        Transaction(
            amount = amount,
            type = TransactionType.EXPENSE,
            category = "Food",
            dateTimestamp = tsMs(y, m, d, h),
            merchant = "Food"
        )

    @Test
    fun `week starts monday midnight`() {
        // Sunday belongs to the week starting the previous Monday.
        assertEquals(dayMs(2026, Calendar.OCTOBER, 5), weekStartMonday(sunOct11Noon))
        assertEquals(dayMs(2026, Calendar.OCTOBER, 12), weekStartMonday(monOct12Noon))
        assertEquals(dayMs(2026, Calendar.OCTOBER, 5), weekStartMonday(tsMs(2026, Calendar.OCTOBER, 10, 23)))
        val c = Calendar.getInstance().apply { timeInMillis = weekStartMonday(sunOct11Noon) }
        assertEquals(Calendar.MONDAY, c.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `week start survives a dst switch`() {
        val oldTz = TimeZone.getDefault()
        // US DST starts Mar 8 2026 02:00: Mar 8 00:00 minus 6x24h in ms
        // lands at 23:00, not midnight. Calendar math must hold midnight.
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        try {
            val dstSunday = tsMs(2026, Calendar.MARCH, 8, 12)
            val monday = weekStartMonday(dstSunday)
            val c = Calendar.getInstance().apply { timeInMillis = monday }
            assertEquals(0, c.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, c.get(Calendar.MINUTE))
            assertEquals(2, c.get(Calendar.DAY_OF_MONTH))
            assertEquals(Calendar.MARCH, c.get(Calendar.MONTH))
        } finally {
            TimeZone.setDefault(oldTz)
        }
    }

    @Test
    fun `week key is the monday date in any locale`() {
        val oldLocale = Locale.getDefault()
        // Sunday-first locales (US) file Sunday under the COMING week with
        // WEEK_OF_YEAR; the Monday-date key must not move.
        Locale.setDefault(Locale.US)
        try {
            assertEquals("2026-10-05", weekKey(sunOct11Noon))
            assertEquals("2026-10-12", weekKey(monOct12Noon))
            assertEquals(weekKey(sunOct11Noon), weekKey(tsMs(2026, Calendar.OCTOBER, 11, 21)))
            assertNotEquals(weekKey(sunOct11Noon), weekKey(tsMs(2026, Calendar.OCTOBER, 18, 12)))
        } finally {
            Locale.setDefault(oldLocale)
        }
    }

    @Test
    fun `current and previous week ranges join exactly`() {
        val (curStart, curEnd) = currentWeekRange(wedOct14Noon)
        assertEquals(dayMs(2026, Calendar.OCTOBER, 12), curStart)
        assertEquals(dayMs(2026, Calendar.OCTOBER, 19), curEnd)
        val (prevStart, prevEnd) = previousWeekRange(wedOct14Noon)
        assertEquals(prevEnd, curStart)
        assertEquals(7 * DAY_MS, curEnd - curStart)
        assertEquals(7 * DAY_MS, prevEnd - prevStart)
        assertTrue(wedOct14Noon in curStart until curEnd)
    }

    @Test
    fun `rolling seven day windows join exactly`() {
        val (last7Start, last7End) = last7DaysRange(wedOct14Noon)
        val (priorStart, priorEnd) = prior7DaysRange(wedOct14Noon)
        assertEquals(priorEnd, last7Start)
        assertEquals(7 * DAY_MS, last7End - last7Start)
        assertEquals(7 * DAY_MS, priorEnd - priorStart)
        assertEquals(dayStart(wedOct14Noon) - 6 * DAY_MS, last7Start)
        assertTrue(wedOct14Noon in last7Start until last7End)
    }

    @Test
    fun `week buckets are contiguous monday weeks ending now`() {
        val buckets = weekBuckets(12, wedOct14Noon)
        assertEquals(12, buckets.size)
        buckets.forEach { (w0, w1) ->
            assertEquals(7 * DAY_MS, w1 - w0)
            val c = Calendar.getInstance().apply { timeInMillis = w0 }
            assertEquals(Calendar.MONDAY, c.get(Calendar.DAY_OF_WEEK))
        }
        buckets.zipWithNext { a, b -> assertEquals(a.second, b.first) }
        val (last0, last1) = buckets.last()
        assertEquals(dayMs(2026, Calendar.OCTOBER, 12), last0)
        assertEquals(dayMs(2026, Calendar.OCTOBER, 19), last1)
    }

    @Test
    fun `weekly graph bins follow calendar weeks`() {
        val rows = listOf(
            expense(500.0, 2026, Calendar.OCTOBER, 12, 10),
            expense(300.0, 2026, Calendar.OCTOBER, 11, 10)
        )
        val bins = aggregateExpenditure(rows, ExpenditureRange.WEEKLY, wedOct14Noon)
        assertEquals(12, bins.size)
        val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
        val mondayLabel = fmt.format(java.util.Date(dayMs(2026, Calendar.OCTOBER, 12)))
        val prevMondayLabel = fmt.format(java.util.Date(dayMs(2026, Calendar.OCTOBER, 5)))
        assertEquals(500.0, bins.first { it.label == mondayLabel }.amount, 0.001)
        assertEquals(300.0, bins.first { it.label == prevMondayLabel }.amount, 0.001)
        assertEquals(800.0, bins.sumOf { it.amount }, 0.001)
    }

    @Test
    fun `flat spending across a midweek window start is not a spike`() {
        // Window Fri Oct 2 – Mon Oct 12: two thin stubs around one full week.
        // Stub totals (300, 100) used to drag the median to 300 and flag the
        // honest 700 week; the full-week median (700) stays clean.
        val w = ScanWindow(
            startMs = dayMs(2026, Calendar.OCTOBER, 2),
            endMs = dayMs(2026, Calendar.OCTOBER, 12),
            label = "w",
            includesBreak = false,
            firstYear = false
        )
        var c = dayMs(2026, Calendar.OCTOBER, 2)
        val rows = mutableListOf<Transaction>()
        while (c < dayMs(2026, Calendar.OCTOBER, 12)) {
            val cal = Calendar.getInstance().apply { timeInMillis = c }
            rows.add(expense(100.0, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)))
            c += DAY_MS
        }
        assertTrue(anomalyWeeks(rows, w).isEmpty())
    }

    @Test
    fun `semester allowance rounds weeks up not down`() {
        assertEquals(0, wholeWeeksCeil(0))
        assertEquals(1, wholeWeeksCeil(1))
        assertEquals(1, wholeWeeksCeil(7))
        // 8 days left is 2 weeks of money, 13 days is 2 — truncation used
        // to hand the whole remainder to "this week".
        assertEquals(2, wholeWeeksCeil(8))
        assertEquals(2, wholeWeeksCeil(13))
        assertEquals(2, wholeWeeksCeil(14))
        assertEquals(3, wholeWeeksCeil(15))
    }

    @Test
    fun `distinct days counts calendars not millisecond spans`() {
        assertEquals(0, distinctDays(emptyList()))
        val one = listOf(tsMs(2026, Calendar.OCTOBER, 14, 9))
        assertEquals(1, distinctDays(one))
        // Mon 23:00 → Wed 22:00 is 47h (old estimate: 2); lived days: 3.
        val c = Calendar.getInstance()
        fun at(day: Int, hour: Int): Long {
            val d = c.clone() as Calendar
            d.set(2026, Calendar.OCTOBER, day, hour, 0, 0)
            d.set(Calendar.MILLISECOND, 0)
            return d.timeInMillis
        }
        assertEquals(3, distinctDays(listOf(at(12, 23), at(13, 1), at(14, 22))))
    }

    @Test
    fun `weekly income converts at 52 over 12`() {
        // Same factor as transport math: ×4 understated weekly earners ~8%.
        assertEquals(2000.0 * 52 / 12, IncomeSource(expectedAmount = 2000.0, frequency = "WEEKLY").monthlyEquivalent(), 0.001)
        assertEquals(3000.0, IncomeSource(expectedAmount = 100.0, frequency = "DAILY").monthlyEquivalent(), 0.001)
        assertEquals(5000.0, IncomeSource(expectedAmount = 5000.0, frequency = "MONTHLY").monthlyEquivalent(), 0.001)
    }

    @Test
    fun `a real spike still flags against the clean median`() {
        val w = ScanWindow(
            startMs = dayMs(2026, Calendar.SEPTEMBER, 1),
            endMs = dayMs(2026, Calendar.OCTOBER, 1),
            label = "w",
            includesBreak = false,
            firstYear = false
        )
        val rows = (1..30).map { d ->
            expense(if (d in 21..27) 500.0 else 100.0, 2026, Calendar.SEPTEMBER, d)
        }
        assertEquals(1, anomalyWeeks(rows, w).size)
    }
}
