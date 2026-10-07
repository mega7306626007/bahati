package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.dashboard.buildInsights
import com.pesaflow.app.data.academic.SpendProfile
import com.pesaflow.app.ui.dashboard.monthEndForecast
import com.pesaflow.app.ui.dashboard.ord
import com.pesaflow.app.ui.dashboard.semesterDiffLines
import com.pesaflow.app.ui.dashboard.semesterResetText
import org.junit.Assert.*
import java.util.Calendar
import org.junit.Test


class SmartInsightsEngineTest {

    private fun tx(amount: Double, type: TransactionType, category: String) = Transaction(
        amount = amount,
        type = type,
        category = category,
        dateTimestamp = System.currentTimeMillis(),
        merchant = "Test",
        description = "",
        paymentMethod = PaymentMethod.CASH,
        source = TransactionSource.MANUAL
    )

    @Test
    fun `empty history returns single hint`() {
        val out = buildInsights(emptyList(), emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        assertEquals(1, out.size)
        assertTrue(out[0].contains("Add transactions"))
    }

    @Test
    fun `top category insight names food`() {
        val txs = listOf(
            tx(800.0, TransactionType.EXPENSE, "Food"),
            tx(200.0, TransactionType.EXPENSE, "Transport")
        )
        val out = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        assertTrue(out.any { it.contains("Food") })
    }

    @Test
    fun `over income verdict appears when overspent`() {
        val txs = listOf(
            tx(1000.0, TransactionType.INCOME, "Salary"),
            tx(1500.0, TransactionType.EXPENSE, "Shopping")
        )
        val out = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        assertTrue(out.any { it.contains("Danger") })
    }

    @Test
    fun `sheng output differs from english`() {
        val txs = listOf(tx(100.0, TransactionType.EXPENSE, "Food"))
        val en = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        val sh = buildInsights(txs, emptyList(), AppLanguage.SHENG, "", emptyList(), emptyList(), emptyList())
        assertNotEquals(en, sh)
    }

    @Test
    fun `forecast names the broke date`() {
        val oct15 = com.pesaflow.app.data.academic.tsMs(2026, Calendar.OCTOBER, 15, 12)
        val fc = monthEndForecast(5000.0, 500.0, 8000.0, 0.0, oct15)
        assertEquals(21, fc.brokeDay)
        assertEquals(13000.0, fc.projectedTotal, 0.01)
        val safe = monthEndForecast(5000.0, 500.0, 20000.0, 0.0, oct15)
        assertNull(safe.brokeDay)
        val over = monthEndForecast(9000.0, 500.0, 8000.0, 0.0, oct15)
        assertEquals(15, over.brokeDay)
    }

    @Test
    fun `ordinals read right`() {
        assertEquals("1st", ord(1))
        assertEquals("2nd", ord(2))
        assertEquals("3rd", ord(3))
        assertEquals("11th", ord(11))
        assertEquals("24th", ord(24))
    }

    @Test
    fun `forecast lines warn or reassure`() {
        val txs = listOf(tx(2000.0, TransactionType.EXPENSE, "Food"))
        val warn = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList(), balance = 1500.0)
        assertTrue(warn.any { it.contains("already over") })
        val safe = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList(), balance = 1e9)
        assertTrue(safe.any { it.contains("inside your") })
        val blind = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        assertFalse(blind.any { it.contains("pace projects") || it.contains("run dry") || it.contains("already over") })
    }

    private fun prof(
        daily: Double = 570.0,
        fare: Double = 50.0,
        days: Set<Int> = setOf(2, 3, 4, 5, 6),
        active: Int = 32
    ) = SpendProfile(
        dailyAvg = daily,
        weeklyAvg = daily * 7,
        monthlyAvg = daily * 30,
        byCategoryMonthly = emptyMap(),
        activeDays = active,
        windowDays = 58,
        schoolDays = days,
        fareDailyAvg = fare,
        fareWindowHint = null,
        rentMonthly = 0.0,
        rentDay = null,
        paydayAmount = null,
        paydayDay = null,
        paydayWho = null,
        skewed = false,
        skewNote = null
    )

    @Test
    fun `semester diff compares pace fares and days`() {
        val out = semesterDiffLines(
            prof(daily = 640.0, fare = 40.0, days = setOf(2, 3, 4, 5)),
            prof(daily = 570.0, fare = 50.0, days = setOf(2, 3, 4, 5, 6)),
            AppLanguage.ENGLISH
        )
        assertTrue(out.any { it.contains("+12%") })
        assertTrue(out.any { it.contains("40/day") })
        assertTrue(out.any { it.contains("fewer") })
        assertTrue(semesterDiffLines(prof(), prof().copy(activeDays = 0), AppLanguage.ENGLISH).isEmpty())
        assertTrue(semesterDiffLines(prof(), prof(), AppLanguage.ENGLISH).isEmpty())
    }

    @Test
    fun `blown global budget suppresses repeat category lines`() {
        val txs = listOf(
            tx(900.0, TransactionType.EXPENSE, "Food"),
            tx(700.0, TransactionType.EXPENSE, "Transport")
        )
        val budgets = listOf(
            Budget(category = "ALL", limitAmount = 1000.0, type = BudgetType.MONTHLY, startTimestamp = 0L, endTimestamp = 0L),
            Budget(category = "Food", limitAmount = 500.0, type = BudgetType.MONTHLY, startTimestamp = 0L, endTimestamp = 0L)
        )
        val out = buildInsights(txs, budgets, AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        // One breach, stated once: global line present, category echo gone.
        assertTrue(out.any { it.contains("budget finished") })
        assertFalse(out.any { it.contains("Food blown") })
        assertEquals(out.size, out.distinct().size)
    }

    @Test
    fun `reset text speaks the user language`() {
        assertTrue(semesterResetText(AppLanguage.ENGLISH).contains("class days"))
        assertTrue(semesterResetText(AppLanguage.SHENG).contains("class days"))
        assertTrue(semesterResetText(AppLanguage.KISWAHILI).contains("Profaili"))
    }
}
