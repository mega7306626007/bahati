package com.pesaflow.app.parsers

import com.pesaflow.app.data.academic.ScanWindow
import com.pesaflow.app.data.academic.SpendProfile
import com.pesaflow.app.data.academic.dayMs
import com.pesaflow.app.data.academic.detectRoutineChange
import com.pesaflow.app.data.academic.RoutineChange
import com.pesaflow.app.data.academic.tsMs
import com.pesaflow.app.data.academic.detectExamMode
import com.pesaflow.app.data.academic.ExamSignal
import com.pesaflow.app.data.academic.faresByDay
import com.pesaflow.app.data.academic.FreeDayDividend
import com.pesaflow.app.data.academic.freeDayDividends
import com.pesaflow.app.data.academic.weekdayFareAverages
import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.budgets.LivingSituation
import com.pesaflow.app.ui.dashboard.buildInsights
import com.pesaflow.app.ui.dashboard.buildSemesterInsights
import org.junit.Assert.*
import org.junit.Test


class SemesterInsightsTest {

    private fun expense(amount: Double, category: String): Transaction =
        Transaction(
            amount = amount,
            type = TransactionType.EXPENSE,
            category = category,
            dateTimestamp = System.currentTimeMillis(),
            merchant = "M-Pesa"
        )

    private fun profile(skewed: Boolean = false) = SpendProfile(
        dailyAvg = 570.0,
        weeklyAvg = 3990.0,
        monthlyAvg = 17100.0,
        byCategoryMonthly = mapOf("Food" to 3103.0),
        activeDays = 32,
        windowDays = 58,
        schoolDays = setOf(2, 3, 4, 5, 6),
        fareDailyAvg = 50.0,
        fareWindowHint = "around 7am (mostly 5am–9am, 22 days seen)",
        rentMonthly = 5172.0,
        rentDay = 5,
        paydayAmount = 10000.0,
        paydayDay = 1,
        paydayWho = "HELB",
        skewed = skewed,
        skewNote = if (skewed) "Includes May–Aug break months — holiday spending is mixed in. Confirm term-time life." else null
    )

    private fun window() = ScanWindow(
        startMs = 0L,
        endMs = 1L,
        label = "Since reporting · 18 Aug",
        includesBreak = false,
        firstYear = true
    )

    @Test
    fun `unknown top category asks instead of lecturing`() {
        val txs = listOf(
            expense(100.0, "Unknown"),
            expense(100.0, "Unknown"),
            expense(100.0, "Unknown"),
            expense(50.0, "Food")
        )
        val out = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        assertTrue(out.any { it.contains("unclassified") })
        assertFalse(out.any { it.contains("Most spending: Unknown") })
    }

    @Test
    fun `semester readout states pace fare rent payday`() {
        val out = buildSemesterInsights(profile(), window(), AppLanguage.ENGLISH, "")
        assertTrue(out.any { it.contains("570") && it.contains("18 Aug") })
        assertTrue(out.any { it.contains("50") && it.contains("7am") })
        assertTrue(out.any { it.contains("5th") })
        assertTrue(out.any { it.contains("HELB") })
        assertTrue(out.any { it.contains("Mon–Fri") })
    }

    @Test
    fun `skewed profile carries the holiday warning`() {
        val out = buildSemesterInsights(profile(skewed = true), window(), AppLanguage.ENGLISH, "")
        assertTrue(out.any { it.contains("break months") })
    }

    @Test
    fun `empty profile invites approvals`() {
        val out = buildSemesterInsights(profile().copy(activeDays = 0), window(), AppLanguage.ENGLISH, "")
        assertEquals(1, out.size)
        assertTrue(out[0].contains("No semester spending"))
    }

    @Test
    fun `fares average per weekday observed`() {
        val w = ScanWindow(
            startMs = dayMs(2026, java.util.Calendar.SEPTEMBER, 1),
            endMs = dayMs(2026, java.util.Calendar.OCTOBER, 15),
            label = "w",
            includesBreak = false,
            firstYear = true
        )
        fun ride(amount: Double, y: Int, m: Int, d: Int, h: Int): Transaction =
            Transaction(
                amount = amount,
                type = TransactionType.EXPENSE,
                category = "Transport",
                dateTimestamp = tsMs(y, m, d, h),
                merchant = "Matatu"
            )
        val rows = listOf(
            ride(50.0, 2026, java.util.Calendar.OCTOBER, 5, 7),
            ride(50.0, 2026, java.util.Calendar.OCTOBER, 12, 7),
            ride(60.0, 2026, java.util.Calendar.OCTOBER, 6, 8)
        )
        val avgs = weekdayFareAverages(rows, w)
        assertEquals(50.0, avgs[java.util.Calendar.MONDAY] ?: -1.0, 0.01)
        assertEquals(60.0, avgs[java.util.Calendar.TUESDAY] ?: -1.0, 0.01)
        assertNull(avgs[java.util.Calendar.WEDNESDAY])
    }

    @Test
    fun `declared day with no fares gets one question`() {
        val out = buildSemesterInsights(
            profile().copy(schoolDays = setOf(2)),
            window(), AppLanguage.ENGLISH, "", setOf("Mon", "Tue")
        )
        assertTrue(out.any { it.contains("Tue") && it.contains("Profile") })
    }

    @Test
    fun `matching days ask nothing`() {        val out = buildSemesterInsights(
            profile().copy(schoolDays = setOf(2, 3)),
            window(), AppLanguage.ENGLISH, "", setOf("Mon", "Tue")
        )
        assertFalse(out.any { it.contains("no fares") })
        val out2 = buildSemesterInsights(profile(), window(), AppLanguage.ENGLISH, "")
        assertFalse(out2.any { it.contains("no fares") })
    }

    private fun fareRow(amount: Double, y: Int, m: Int, d: Int): Transaction =
        Transaction(
            amount = amount,
            type = TransactionType.EXPENSE,
            category = "Transport",
            dateTimestamp = tsMs(y, m, d, 7),
            merchant = "Matatu"
        )

    @Test
    fun `fare doubling across fortnights is a routine change`() {
        val cal = java.util.Calendar.OCTOBER
        val rows = listOf(
            fareRow(50.0, 2026, java.util.Calendar.SEPTEMBER, 21),
            fareRow(50.0, 2026, java.util.Calendar.SEPTEMBER, 22),
            fareRow(50.0, 2026, java.util.Calendar.SEPTEMBER, 23),
            fareRow(50.0, 2026, java.util.Calendar.SEPTEMBER, 24),
            fareRow(50.0, 2026, java.util.Calendar.SEPTEMBER, 25),
            fareRow(50.0, 2026, java.util.Calendar.SEPTEMBER, 28),
            fareRow(100.0, 2026, cal, 5),
            fareRow(100.0, 2026, cal, 6),
            fareRow(100.0, 2026, cal, 7),
            fareRow(100.0, 2026, cal, 8),
            fareRow(100.0, 2026, cal, 9),
            fareRow(100.0, 2026, cal, 12)
        )
        val rc = detectRoutineChange(rows, tsMs(2026, cal, 15, 12))
        assertNotNull(rc)
        assertEquals(100.0, rc!!.recentDaily, 0.01)
        assertEquals(50.0, rc.priorDaily, 0.01)
    }

    @Test
    fun `steady fares and thin data are not changes`() {
        val cal = java.util.Calendar.OCTOBER
        val steady = listOf(
            fareRow(50.0, 2026, java.util.Calendar.SEPTEMBER, 21),
            fareRow(50.0, 2026, java.util.Calendar.SEPTEMBER, 22),
            fareRow(50.0, 2026, java.util.Calendar.SEPTEMBER, 23),
            fareRow(50.0, 2026, cal, 5),
            fareRow(50.0, 2026, cal, 6),
            fareRow(50.0, 2026, cal, 7)
        )
        assertNull(detectRoutineChange(steady, tsMs(2026, cal, 15, 12)))
        assertNull(detectRoutineChange(steady.take(2), tsMs(2026, cal, 15, 12)))
    }

    @Test
    fun `excluded weeks are announced not hidden`() {
        val out = buildSemesterInsights(
            profile().copy(excludedWeeks = 1, excludedTotal = 3500.0),
            window(), AppLanguage.ENGLISH, ""
        )
        assertTrue(out.any { it.contains("unusual") && it.contains("3500") })
        val clean = buildSemesterInsights(profile(), window(), AppLanguage.ENGLISH, "")
        assertFalse(clean.any { it.contains("unusual") })
    }

    @Test
    fun `routine change surfaces one ask line`() {
        val out = buildSemesterInsights(
            profile(), window(), AppLanguage.ENGLISH, "",
            routineChange = RoutineChange(100.0, 50.0, 6, 6)
        )
        assertTrue(out.any { it.contains("shifted") && it.contains("100") })
        val quiet = buildSemesterInsights(profile(), window(), AppLanguage.ENGLISH, "")
        assertFalse(quiet.any { it.contains("shifted") })
    }

    @Test
    fun `fare spread 30% triggers one ask line`() {
        val w = ScanWindow(
            startMs = dayMs(2026, java.util.Calendar.OCTOBER, 1),
            endMs = dayMs(2026, java.util.Calendar.OCTOBER, 31),
            label = "w",
            includesBreak = false,
            firstYear = false
        )
        fun ride(amount: Double, y: Int, m: Int, d: Int, h: Int): Transaction =
            Transaction(
                amount = amount,
                type = TransactionType.EXPENSE,
                category = "Transport",
                dateTimestamp = tsMs(y, m, d, h),
                merchant = "Matatu"
            )
        val rows = listOf(
            ride(30.0, 2026, java.util.Calendar.OCTOBER, 1, 7),
            ride(30.0, 2026, java.util.Calendar.OCTOBER, 2, 7),
            ride(30.0, 2026, java.util.Calendar.OCTOBER, 3, 7),
            ride(30.0, 2026, java.util.Calendar.OCTOBER, 4, 7),
            ride(30.0, 2026, java.util.Calendar.OCTOBER, 5, 7),
            ride(30.0, 2026, java.util.Calendar.OCTOBER, 6, 7),
            ride(100.0, 2026, java.util.Calendar.OCTOBER, 7, 7),
            ride(100.0, 2026, java.util.Calendar.OCTOBER, 8, 7),
            ride(100.0, 2026, java.util.Calendar.OCTOBER, 9, 7),
            ride(100.0, 2026, java.util.Calendar.OCTOBER, 10, 7),
            ride(100.0, 2026, java.util.Calendar.OCTOBER, 11, 7)
        )
        val avgs = weekdayFareAverages(rows, w)
        val rads = faresByDay(avgs)
        assertTrue(rads.size >= 2)
        assertTrue(rads.any { it.value >= 30.0 * 1.3 })
    }

    @Test
    fun `steady data gives no spread line`() {
        val out = buildSemesterInsights(profile(), window(), AppLanguage.ENGLISH, "")
        assertFalse(out.any { it.contains("Dearest") })
    }

    @Test
    fun `zero-spend school days pay a dividend`() {
        val cal = java.util.Calendar.OCTOBER
        val now = tsMs(2026, cal, 15, 12)
        val rows = listOf(
            fareRow(80.0, 2026, cal, 9),
            fareRow(50.0, 2026, cal, 13)
        )
        val d = freeDayDividends(rows, setOf(2, 3, 4, 5, 6), 50.0, now)
        assertNotNull(d)
        assertEquals(3, d!!.freeDows.size)
        assertEquals(150.0, d.total, 0.01)
    }

    @Test
    fun `no school days or no fare means no dividend`() {
        val now = tsMs(2026, java.util.Calendar.OCTOBER, 15, 12)
        assertNull(freeDayDividends(emptyList(), emptySet(), 50.0, now))
        assertNull(freeDayDividends(emptyList(), setOf(2, 3), 0.0, now))
        val busy = (8..14).map { fareRow(50.0, 2026, java.util.Calendar.OCTOBER, it) }
        assertNull(freeDayDividends(busy, setOf(2, 3, 4, 5, 6), 50.0, now))
    }

    private fun timedRow(amount: Double, category: String, y: Int, m: Int, d: Int, h: Int): Transaction =
        Transaction(
            amount = amount,
            type = TransactionType.EXPENSE,
            category = category,
            dateTimestamp = tsMs(y, m, d, h),
            merchant = "M-Pesa"
        )

    @Test
    fun `printing plus late nights is exam season`() {
        val cal = java.util.Calendar.OCTOBER
        val now = tsMs(2026, cal, 15, 12)
        val rows = listOf(
            timedRow(100.0, "Printing", 2026, java.util.Calendar.SEPTEMBER, 20, 10),
            timedRow(50.0, "Food", 2026, java.util.Calendar.SEPTEMBER, 21, 22),
            timedRow(60.0, "Food", 2026, java.util.Calendar.SEPTEMBER, 22, 23),
            timedRow(150.0, "Printing", 2026, cal, 5, 10),
            timedRow(150.0, "Printing", 2026, cal, 8, 11),
            timedRow(40.0, "Food", 2026, cal, 5, 22),
            timedRow(40.0, "Food", 2026, cal, 6, 23),
            timedRow(40.0, "Food", 2026, cal, 7, 22)
        )
        val sig = detectExamMode(rows, now)
        assertNotNull(sig)
        assertEquals(300.0, sig!!.printingRecent, 0.01)
    }

    @Test
    fun `steady printing or lone nights are not exams`() {
        val cal = java.util.Calendar.OCTOBER
        val now = tsMs(2026, cal, 15, 12)
        val steady = listOf(
            timedRow(100.0, "Printing", 2026, java.util.Calendar.SEPTEMBER, 20, 10),
            timedRow(100.0, "Printing", 2026, cal, 5, 10)
        )
        assertNull(detectExamMode(steady, now))
        val nightsOnly = listOf(
            timedRow(40.0, "Food", 2026, cal, 5, 22),
            timedRow(40.0, "Food", 2026, cal, 6, 23),
            timedRow(40.0, "Food", 2026, cal, 7, 22)
        )
        assertNull(detectExamMode(nightsOnly, now))
    }

    @Test
    fun `exam line offers to pause fare guards`() {
        val out = buildSemesterInsights(
            profile(), window(), AppLanguage.ENGLISH, "",
            exam = ExamSignal(300.0, 100.0, 5, 2)
        )
        assertTrue(out.any { it.contains("exam season") })
        val quiet = buildSemesterInsights(profile(), window(), AppLanguage.ENGLISH, "")
        assertFalse(quiet.any { it.contains("exam season") })
    }

    @Test
    fun `dividend line celebrates bankable fares`() {
        val out = buildSemesterInsights(
            profile(), window(), AppLanguage.ENGLISH, "",
            dividend = FreeDayDividend(listOf(2, 4), 50.0, 100.0)
        )
        assertTrue(out.any { it.contains("bank it") && it.contains("100") })
        val quiet = buildSemesterInsights(profile(), window(), AppLanguage.ENGLISH, "")
        assertFalse(quiet.any { it.contains("bank it") })
    }
}
