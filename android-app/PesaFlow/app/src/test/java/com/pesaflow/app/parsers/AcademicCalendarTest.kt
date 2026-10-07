package com.pesaflow.app.parsers

import com.pesaflow.app.data.academic.academicStartYearFor
import com.pesaflow.app.data.academic.asTransaction
import com.pesaflow.app.data.academic.buildSpendProfile
import com.pesaflow.app.data.academic.budgetWindow
import com.pesaflow.app.data.academic.dayMs
import com.pesaflow.app.data.academic.overlapsBreak
import com.pesaflow.app.data.academic.parseClassDays
import com.pesaflow.app.data.academic.parseYearOfStudy
import com.pesaflow.app.data.academic.priorAcademicWindow
import com.pesaflow.app.data.academic.scanWindow
import com.pesaflow.app.data.academic.semesterBounds
import com.pesaflow.app.data.academic.semesterWindowsFor
import com.pesaflow.app.data.academic.tsMs
import com.pesaflow.app.data.academic.needsSemesterReset
import com.pesaflow.app.data.academic.anomalyWeeks
import com.pesaflow.app.data.academic.isSunday
import com.pesaflow.app.data.academic.observedTransportMonthly
import com.pesaflow.app.data.academic.relearnWindow
import com.pesaflow.app.data.academic.ScanWindow
import com.pesaflow.app.data.academic.weekKey
import com.pesaflow.app.data.academic.uniStartMs
import com.pesaflow.app.data.academic.updateSchoolDays
import com.pesaflow.app.data.academic.windowFor
import com.pesaflow.app.data.academic.SemKind
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


// Semester truth: Sep-Aug year, researched 2026 reporting dates, first-year
// vs returning windows, break exclusion, observed-only profiles.
class AcademicCalendarTest {

    private val now = tsMs(2026, Calendar.OCTOBER, 15, 12)

    private fun expense(amount: Double, category: String, y: Int, m: Int, d: Int, h: Int = 12, merchant: String = category): Transaction =
        Transaction(amount = amount, type = TransactionType.EXPENSE, category = category, dateTimestamp = tsMs(y, m, d, h), merchant = merchant)

    private fun income(amount: Double, merchant: String, y: Int, m: Int, d: Int): Transaction =
        Transaction(amount = amount, type = TransactionType.INCOME, category = "Salary", dateTimestamp = tsMs(y, m, d, 9), merchant = merchant)

    @Test
    fun `academic year splits into sem1 sem2 break`() {
        val wins = semesterWindowsFor(2026)
        assertEquals(3, wins.size)
        assertEquals(SemKind.SEM1, wins[0].kind)
        assertEquals(dayMs(2026, Calendar.SEPTEMBER, 1), wins[0].startMs)
        assertEquals(dayMs(2027, Calendar.JANUARY, 1), wins[1].startMs)
        assertEquals(SemKind.BREAK, wins[2].kind)
        assertEquals(dayMs(2027, Calendar.MAY, 1), wins[2].startMs)
        assertEquals(dayMs(2027, Calendar.SEPTEMBER, 1), wins[2].endMs)
    }

    @Test
    fun `windowFor locates any date`() {
        assertEquals(SemKind.SEM1, windowFor(tsMs(2026, Calendar.OCTOBER, 15, 12)).kind)
        assertEquals(SemKind.SEM2, windowFor(tsMs(2027, Calendar.MARCH, 10, 12)).kind)
        assertEquals(SemKind.BREAK, windowFor(tsMs(2027, Calendar.JUNE, 10, 12)).kind)
        assertEquals(2026, academicStartYearFor(tsMs(2026, Calendar.OCTOBER, 1, 1)))
        assertEquals(2026, academicStartYearFor(tsMs(2027, Calendar.APRIL, 1, 1)))
    }

    @Test
    fun `reporting dates pin first-year starts`() {
        assertEquals(dayMs(2026, Calendar.AUGUST, 18), uniStartMs("UoN", now))
        assertEquals(dayMs(2026, Calendar.SEPTEMBER, 1), uniStartMs("JKUAT", now))
        assertEquals(dayMs(2026, Calendar.SEPTEMBER, 1), uniStartMs("Chuka", now))
    }

    @Test
    fun `first-year window starts at reporting never before`() {
        val w = scanWindow(1, "UoN", now)
        assertTrue(w.firstYear)
        assertEquals(dayMs(2026, Calendar.AUGUST, 18), w.startMs)
        assertTrue(w.label.contains("reporting"))
        assertFalse(w.includesBreak)
    }

    @Test
    fun `returning window starts september first`() {
        val w = scanWindow(3, "UoN", now)
        assertFalse(w.firstYear)
        assertEquals(dayMs(2026, Calendar.SEPTEMBER, 1), w.startMs)
        assertFalse(w.includesBreak)
    }

    @Test
    fun `prior window is last september to april`() {
        val w = priorAcademicWindow(now)
        assertEquals(dayMs(2025, Calendar.SEPTEMBER, 1), w.startMs)
        assertEquals(dayMs(2026, Calendar.MAY, 1), w.endMs)
        assertFalse(w.includesBreak)
    }

    @Test
    fun `break overlap detected by month`() {
        assertTrue(overlapsBreak(dayMs(2027, Calendar.MAY, 15), dayMs(2027, Calendar.JUNE, 15)))
        assertFalse(overlapsBreak(dayMs(2026, Calendar.SEPTEMBER, 1), now))
    }

    @Test
    fun `profile reads fare rent payday and school days`() {
        val rows = mutableListOf<Transaction>()
        // Food 200 daily Sep 15 - Oct 14.
        var c = dayMs(2026, Calendar.SEPTEMBER, 15)
        val end = dayMs(2026, Calendar.OCTOBER, 15)
        while (c < end) {
            val cal = Calendar.getInstance().apply { timeInMillis = c }
            rows.add(expense(200.0, "Food", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)))
            c += 24L * 60 * 60 * 1000
        }
        // Fare 50 every weekday 7am.
        rows.filter { true }.map { it.dateTimestamp }.toSet().forEach { day ->
            val cal = Calendar.getInstance().apply { timeInMillis = day }
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            if (dow in Calendar.MONDAY..Calendar.FRIDAY) {
                rows.add(expense(50.0, "Transport", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 7, "Matatu"))
            }
        }
        rows.add(expense(5000.0, "Rent", 2026, Calendar.SEPTEMBER, 5))
        rows.add(expense(5000.0, "Rent", 2026, Calendar.OCTOBER, 5))
        rows.add(income(10000.0, "HELB", 2026, Calendar.SEPTEMBER, 1))
        rows.add(income(10000.0, "HELB", 2026, Calendar.OCTOBER, 1))

        val w = scanWindow(1, "UoN", now)
        val p = buildSpendProfile(rows, w)
        // Window Aug 18 - Oct 15 = 58 days; expenses 6000 food + 1100 fare + 10000 rent.
        assertEquals(58L, p.windowDays)
        assertEquals(17100.0 / 58, p.dailyAvg, 0.01)
        assertEquals(p.dailyAvg * 7, p.weeklyAvg, 0.01)
        assertEquals(p.dailyAvg * 30, p.monthlyAvg, 0.01)
        assertEquals(32, p.activeDays)
        assertEquals(setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY), p.schoolDays)
        assertEquals(50.0, p.fareDailyAvg, 0.01)
        assertTrue(p.fareWindowHint?.contains("7am") == true)
        assertEquals(5, p.rentDay)
        assertEquals(10000.0 / (58 / 30.0), p.rentMonthly, 5.0)
        assertEquals(10000.0, p.paydayAmount ?: -1.0, 0.01)
        assertEquals(1, p.paydayDay)
        assertEquals("HELB", p.paydayWho)
        assertEquals(6000.0 / (58 / 30.0), p.byCategoryMonthly["Food"] ?: -1.0, 5.0)
        assertFalse(p.skewed)
    }

    @Test
    fun `pending maps to transaction for shared math`() {
        val pt = com.pesaflow.app.data.models.PendingTransaction(
            amount = 50.0,
            type = TransactionType.EXPENSE,
            category = "Transport",
            merchant = "Matatu",
            dateTimestamp = now,
            paymentMethod = com.pesaflow.app.data.models.PaymentMethod.MPESA,
            source = com.pesaflow.app.data.models.TransactionSource.MPESA_SMS,
            sourceTransactionId = "X",
            rawText = "x"
        )
        val t = pt.asTransaction()
        assertEquals(50.0, t.amount, 0.001)
        assertEquals("Transport", t.category)
    }

    @Test
    fun `answer helpers parse year and class days`() {
        assertEquals(3, parseYearOfStudy("a|year=3|b"))
        assertEquals(1, parseYearOfStudy(""))
        assertEquals(setOf("Mon", "Wed", "Fri"), parseClassDays("x|classdays=Mon,Wed,Fri|y"))
        assertEquals(setOf("Mon", "Tue", "Wed", "Thu", "Fri"), parseClassDays(""))
    }

    @Test
    fun `spike week leaves the base but stays announced`() {
        val w = ScanWindow(
            startMs = dayMs(2026, Calendar.SEPTEMBER, 1),
            endMs = dayMs(2026, Calendar.OCTOBER, 1),
            label = "w",
            includesBreak = false,
            firstYear = false
        )
        val sep = Calendar.SEPTEMBER
        val rows = (1..30).map { d ->
            val spike = d in 21..27
            expense(if (spike) 500.0 else 100.0, "Food", 2026, sep, d)
        }
        assertEquals(1, anomalyWeeks(rows, w).size)
        val p = buildSpendProfile(rows, w)
        assertEquals(1, p.excludedWeeks)
        assertEquals(3500.0, p.excludedTotal, 0.01)
        assertEquals(100.0, p.dailyAvg, 0.01)
        assertEquals(0, anomalyWeeks(rows.take(10), w).size)
    }

    @Test
    fun `relearn clamps the window start`() {
        val w = com.pesaflow.app.data.academic.ScanWindow(
            startMs = dayMs(2026, Calendar.SEPTEMBER, 1),
            endMs = dayMs(2026, Calendar.OCTOBER, 15),
            label = "w",
            includesBreak = false,
            firstYear = true
        )
        val cut = dayMs(2026, Calendar.OCTOBER, 1)
        val narrowed = relearnWindow(w, cut)
        assertEquals(cut, narrowed.startMs)
        assertEquals(w.endMs, narrowed.endMs)
        assertTrue(narrowed.label.contains("re-learned"))
        assertEquals(w, relearnWindow(w, 0L))
    }

    @Test
    fun `week keys are stable within a week and sundays gate the card`() {
        val sunNoon = tsMs(2026, Calendar.OCTOBER, 11, 12)
        val sunEve = tsMs(2026, Calendar.OCTOBER, 11, 21)
        val mon = tsMs(2026, Calendar.OCTOBER, 12, 12)
        assertTrue(isSunday(sunNoon))
        assertFalse(isSunday(mon))
        assertEquals(weekKey(sunNoon), weekKey(sunEve))
        assertNotEquals(weekKey(sunNoon), weekKey(tsMs(2026, Calendar.OCTOBER, 18, 12)))
    }

    @Test
    fun `reset nudge fires in sept for legacy answers`() {
        val sept = tsMs(2026, Calendar.SEPTEMBER, 24, 12)
        val jan = tsMs(2026, Calendar.JANUARY, 15, 12)
        val jun = tsMs(2026, Calendar.JUNE, 15, 12)
        assertTrue(needsSemesterReset("name=X|year=2|group=HOSTEL_COOK", sept))
        assertTrue(needsSemesterReset("name=X|year=2", jan))
        assertFalse(needsSemesterReset("name=X|year=2", jun))
        assertFalse(needsSemesterReset("name=X|classdays=Mon,Tue", sept))
        assertFalse(needsSemesterReset("", sept))
    }

    @Test
    fun `observed transport pace scales school weeks`() {
        val w = ScanWindow(
            startMs = dayMs(2026, Calendar.SEPTEMBER, 1),
            endMs = dayMs(2026, Calendar.OCTOBER, 1),
            label = "w",
            includesBreak = false,
            firstYear = false
        )
        val rows = listOf(7, 14, 21, 28).map {
            expense(50.0, "Transport", 2026, Calendar.SEPTEMBER, it)
        } + listOf(1, 8, 15, 22).map {
            expense(60.0, "Transport", 2026, Calendar.SEPTEMBER, it)
        }
        assertEquals(110.0 * 30.0 / 7.0, observedTransportMonthly(rows, w), 0.01)
        assertEquals(0.0, observedTransportMonthly(emptyList(), w), 0.0)
    }

    @Test
    fun `check-in merge adds days and drops junk`() {
        assertEquals(
            setOf("Mon", "Tue", "Wed"),
            updateSchoolDays(setOf("Mon", "Tue"), setOf("Wed"))
        )
        assertEquals(
            setOf("Mon", "Tue"),
            updateSchoolDays(setOf("Mon", "Tue"), setOf("Xyz"))
        )
        assertEquals(
            setOf("Mon"),
            updateSchoolDays(setOf("Mon"), setOf("Mon"))
        )
    }

    @Test
    fun `budgetWindow semester spans the real term not rolling 120 days`() {
        // Oct 15 2026 → Sem 1 (Sep 1 – Jan 1). A rolling now−120d window
        // would have started in mid-May: the long break counted as term.
        val (start, end) = budgetWindow(BudgetType.SEMESTER, now)
        assertEquals(dayMs(2026, Calendar.SEPTEMBER, 1), start)
        assertEquals(dayMs(2027, Calendar.JANUARY, 1), end)
        assertTrue("start=$start", start > tsMs(2026, Calendar.MAY, 17, 0))
    }

    @Test
    fun `budgetWindow daily weekly monthly are calendar aligned`() {
        val (d0, d1) = budgetWindow(BudgetType.DAILY, now)
        assertEquals(tsMs(2026, Calendar.OCTOBER, 15, 0), d0)
        assertEquals(tsMs(2026, Calendar.OCTOBER, 16, 0), d1)
        // Oct 15 2026 is a Thursday → the week is Mon Oct 12 – Mon Oct 19.
        val (w0, w1) = budgetWindow(BudgetType.WEEKLY, now)
        assertEquals(tsMs(2026, Calendar.OCTOBER, 12, 0), w0)
        assertEquals(tsMs(2026, Calendar.OCTOBER, 19, 0), w1)
        val (m0, m1) = budgetWindow(BudgetType.MONTHLY, now)
        assertEquals(tsMs(2026, Calendar.OCTOBER, 1, 0), m0)
        assertEquals(tsMs(2026, Calendar.NOVEMBER, 1, 0), m1)
    }

    @Test
    fun `semesterBounds prefers profile dates over calendar`() {
        val (start, end) = semesterBounds(
            dayMs(2026, Calendar.SEPTEMBER, 8),
            dayMs(2026, Calendar.DECEMBER, 15),
            now
        )
        assertEquals(dayMs(2026, Calendar.SEPTEMBER, 8), start)
        assertEquals(dayMs(2026, Calendar.DECEMBER, 15), end)
    }

    @Test
    fun `semesterBounds falls back to the academic calendar`() {
        val (start, end) = semesterBounds(0L, 0L, now)
        assertEquals(dayMs(2026, Calendar.SEPTEMBER, 1), start)
        assertEquals(dayMs(2027, Calendar.JANUARY, 1), end)
    }

    @Test
    fun `semesterBounds ignores an inverted stale end`() {
        // End before start = stale data → the calendar decides the end.
        val (start, end) = semesterBounds(
            dayMs(2026, Calendar.SEPTEMBER, 1),
            dayMs(2026, Calendar.AUGUST, 1),
            now
        )
        assertEquals(dayMs(2026, Calendar.SEPTEMBER, 1), start)
        assertEquals(dayMs(2027, Calendar.JANUARY, 1), end)
    }

    @Test
    fun `semesterBounds in July is the long break`() {
        val (start, end) = semesterBounds(0L, 0L, tsMs(2026, Calendar.JULY, 10, 12))
        assertEquals(dayMs(2026, Calendar.MAY, 1), start)
        assertEquals(dayMs(2026, Calendar.SEPTEMBER, 1), end)
    }
}
