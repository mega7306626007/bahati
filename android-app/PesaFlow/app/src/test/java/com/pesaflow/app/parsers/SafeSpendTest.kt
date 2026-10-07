package com.pesaflow.app.parsers

import com.pesaflow.app.data.academic.tsMs
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.SavingsGoal
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.dashboard.computeSafeSpend
import com.pesaflow.app.ui.dashboard.isSchoolToday
import com.pesaflow.app.ui.dashboard.parseClassDaysSet
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

private val day = 24L * 60 * 60 * 1000
// Oct 15 2026, midday: elapsed 15, 31-day month, 17 days left.
private val now = tsMs(2026, Calendar.OCTOBER, 15, 12)

private fun exp(amount: Double, at: Long, merchant: String = "Kibanda"): Transaction = Transaction(
    amount = amount,
    type = TransactionType.EXPENSE,
    category = "Food",
    dateTimestamp = at,
    merchant = merchant,
    description = "",
    paymentMethod = PaymentMethod.CASH,
    source = TransactionSource.MANUAL
)

private fun monthlyAll(limit: Double): Budget = Budget(
    category = "ALL", limitAmount = limit, type = BudgetType.MONTHLY,
    startTimestamp = now - 30 * day, endTimestamp = now + 30 * day
)

private fun dailyBudget(limit: Double): Budget = Budget(
    category = "Food", limitAmount = limit, type = BudgetType.DAILY,
    startTimestamp = now - day, endTimestamp = now + day
)

class SafeSpendTest {
    @Test
    fun `no budget means no number`() {
        val s = computeSafeSpend(emptyList(), emptyList(), emptyList(), emptyList(), 0, true, now)
        assertFalse(s.hasBudget)
    }

    @Test
    fun `adaptive rate follows remaining over days left`() {
        // 31000 monthly, 15500 spent by Oct 15 → ~911/day over 17 days.
        val txs = listOf(exp(15500.0, tsMs(2026, Calendar.OCTOBER, 10, 12)))
        val s = computeSafeSpend(txs, listOf(monthlyAll(31000.0)), emptyList(), emptyList(), 0, true, now)
        assertTrue("target=${s.dailyTarget}", s.dailyTarget in 800..1050)
        assertEquals(17, s.daysLeftMonth)
    }

    @Test
    fun `blown budget late month says zero not fresh day`() {
        val txs = listOf(exp(30000.0, tsMs(2026, Calendar.OCTOBER, 10, 12)))
        val s = computeSafeSpend(
            txs, listOf(monthlyAll(20000.0)), emptyList(), emptyList(), 0, true,
            tsMs(2026, Calendar.OCTOBER, 28, 12)
        )
        assertEquals(0, s.dailyTarget)
        assertEquals(0, s.allowance)
    }

    @Test
    fun `skipped day earns at most half again never double`() {
        // Nothing spent yesterday or today: allowance ≤ 1.5× target.
        val s = computeSafeSpend(emptyList(), listOf(monthlyAll(30000.0)), emptyList(), emptyList(), 0, true, now)
        assertTrue("allowance=${s.allowance} target=${s.dailyTarget}", s.allowance <= (s.dailyTarget * 1.5).toInt() + 1)
        assertTrue(s.rollover <= s.dailyTarget / 2)
    }

    @Test
    fun `blowout yesterday cannot drive allowance negative`() {
        val txs = listOf(exp(9000.0, tsMs(2026, Calendar.OCTOBER, 14, 12)))
        val s = computeSafeSpend(txs, listOf(monthlyAll(30000.0)), emptyList(), emptyList(), 0, true, now)
        assertTrue(s.allowance >= 0)
    }

    @Test
    fun `far bill does not eat today near bill does`() {
        val far = Bill(name = "Fees", amount = 50000.0, dueDate = now + 90 * day, category = "School", status = "UNPAID")
        val sFar = computeSafeSpend(emptyList(), listOf(monthlyAll(30000.0)), emptyList(), listOf(far), 0, true, now)
        assertEquals(0, sFar.billDaily)
        val near = Bill(name = "Rent", amount = 3000.0, dueDate = now + day, category = "Rent", status = "UNPAID")
        val sNear = computeSafeSpend(emptyList(), listOf(monthlyAll(30000.0)), emptyList(), listOf(near), 0, true, now)
        assertTrue("billDaily=${sNear.billDaily}", sNear.billDaily >= 1500)
    }

    @Test
    fun `overdue goals stop punishing today`() {
        val overdue = SavingsGoal(title = "Laptop", targetAmount = 20000.0, currentAmount = 5000.0, targetTimestamp = now - 5 * day)
        val s = computeSafeSpend(emptyList(), listOf(monthlyAll(60000.0)), listOf(overdue), emptyList(), 0, true, now)
        assertEquals(0, s.planDaily)
        assertEquals(1, s.overdueGoals)
        assertEquals(15000, s.overdueTotal)
    }

    @Test
    fun `dateless goal spreads over thirty days`() {
        val open = SavingsGoal(title = "Trip", targetAmount = 30000.0, currentAmount = 0.0, targetTimestamp = 0L)
        val s = computeSafeSpend(emptyList(), listOf(monthlyAll(90000.0)), listOf(open), emptyList(), 0, true, now)
        assertEquals(1000, s.planDaily)
    }

    @Test
    fun `fare only on school days`() {
        val sSchool = computeSafeSpend(emptyList(), listOf(monthlyAll(30000.0)), emptyList(), emptyList(), 100, true, now)
        val sHome = computeSafeSpend(emptyList(), listOf(monthlyAll(30000.0)), emptyList(), emptyList(), 100, false, now)
        assertEquals(100, sSchool.fareToday)
        assertEquals(0, sHome.fareToday)
        assertTrue(sHome.fareSkipped)
    }

    @Test
    fun `trace 800-a-day persona stays sane mid-month`() {
        // Parents + Long commuter, 24000/mo (~800/day), fare 150, on pace.
        val txs = listOf(
            exp(12000.0, tsMs(2026, Calendar.OCTOBER, 10, 12)),
            exp(150.0, tsMs(2026, Calendar.OCTOBER, 14, 12), "Matatu")
        )
        val s = computeSafeSpend(
            txs, listOf(monthlyAll(24000.0)), emptyList(), emptyList(), 150, true, now
        )
        // Remaining 11850 ÷ 17 ≈ 697, minus 150 fare ≈ 547.
        assertTrue("target=${s.dailyTarget}", s.dailyTarget in 450..650)
        assertTrue("pace=${s.pacePct}", s.pacePct in 90..115)
        assertEquals(150, s.fareToday)
        // KSh 100 left is under half HIS day → critical band fires.
        assertTrue(100 < s.dailyTarget / 2)
    }

    @Test
    fun `trace 200-a-day persona same shillings different truth`() {
        // Same life, 6000/mo (~200/day), fare 60, on pace.
        val txs = listOf(exp(2900.0, tsMs(2026, Calendar.OCTOBER, 10, 12)))
        val s = computeSafeSpend(
            txs, listOf(monthlyAll(6000.0)), emptyList(), emptyList(), 60, true, now
        )
        // Remaining 3100 ÷ 17 ≈ 182, minus 60 fare ≈ 122.
        assertTrue("target=${s.dailyTarget}", s.dailyTarget in 80..170)
        // Same KSh 100 is over half HER day → not critical. Same number,
        // opposite meaning — that is why bands are ratios, not shillings.
        assertFalse(100 < s.dailyTarget / 2)
        assertTrue(s.allowance > 0)
    }

    @Test
    fun `trace payday splurge tightens the rest of the month`() {
        // 15000 blown in the first 3 days of a 20000 month.
        val txs = listOf(exp(15000.0, tsMs(2026, Calendar.OCTOBER, 3, 12)))
        val s = computeSafeSpend(txs, listOf(monthlyAll(20000.0)), emptyList(), emptyList(), 0, true, now)
        // Remaining 5000 ÷ 17 ≈ 294 — the splurge is priced in, no lectures.
        assertTrue("target=${s.dailyTarget}", s.dailyTarget in 200..400)
        assertTrue("pace=${s.pacePct}", s.pacePct > 120)
    }

    @Test
    fun `class days parse and match today`() {
        assertEquals(setOf("Mon", "Tue"), parseClassDaysSet("x|classdays=Mon,Tue"))
        // Oct 15 2026 is a Thursday.
        assertTrue(isSchoolToday("x|classdays=Mon,Tue,Wed,Thu,Fri", now))
        assertFalse(isSchoolToday("x|classdays=Mon,Tue", now))
        assertTrue(isSchoolToday("", now))
    }

    @Test
    fun `week pace follows monday anchored spending`() {
        // Week Mon Oct 12: 3000 Tue + 2000 Wed, now Thu Oct 15.
        val txs = listOf(
            exp(3000.0, tsMs(2026, Calendar.OCTOBER, 13, 12)),
            exp(2000.0, tsMs(2026, Calendar.OCTOBER, 14, 12))
        )
        val s = computeSafeSpend(txs, listOf(monthlyAll(31000.0)), emptyList(), emptyList(), 0, true, now)
        assertEquals(5000, s.weekSpent)
        // 4 elapsed days of a 31-day month: 31000×4/31 ≈ 4000 expected.
        assertTrue("expected=${s.weekExpected}", s.weekExpected in 3900..4100)
        assertTrue("pace=${s.weekPacePct}", s.weekPacePct in 115..135)
    }

    @Test
    fun `last week spending does not pollute this week pace`() {
        // 15500 on Sat Oct 10 belongs to last week (Mon Oct 5–11).
        val txs = listOf(exp(15500.0, tsMs(2026, Calendar.OCTOBER, 10, 12)))
        val s = computeSafeSpend(txs, listOf(monthlyAll(31000.0)), emptyList(), emptyList(), 0, true, now)
        assertEquals(0, s.weekSpent)
        assertTrue("pace=${s.weekPacePct}", s.weekPacePct < 50)
    }

    @Test
    fun `future dated rows never count as spent today`() {
        val txs = listOf(
            exp(500.0, tsMs(2026, Calendar.OCTOBER, 15, 10)),
            exp(9000.0, tsMs(2026, Calendar.OCTOBER, 20, 12))
        )
        val s = computeSafeSpend(txs, listOf(monthlyAll(31000.0)), emptyList(), emptyList(), 0, true, now)
        assertEquals(500, s.spent)
    }

    @Test
    fun `daily budget rebases by days left instead of staying flat`() {
        // 1000/day × 31-day October = 31000 month plan. 15500 spent by the
        // 15th → 15500 remaining ÷ 17 days ≈ 911, not the frozen 1000.
        val txs = listOf(exp(15500.0, tsMs(2026, Calendar.OCTOBER, 10, 12)))
        val s = computeSafeSpend(txs, listOf(dailyBudget(1000.0)), emptyList(), emptyList(), 0, true, now)
        assertTrue("target=${s.dailyTarget}", s.dailyTarget in 850..950)
        assertEquals(17, s.daysLeftMonth)
        assertEquals("what's left ÷ days left", s.baseLabel)
    }

    @Test
    fun `daily budget pace is real not stuck at 100`() {
        // 25000 spent by Oct 15 vs expected 31000×15/31 = 15000 → ~166%.
        // The old daily branch hard-coded pacePct = 100 forever.
        val txs = listOf(exp(25000.0, tsMs(2026, Calendar.OCTOBER, 14, 12)))
        val s = computeSafeSpend(txs, listOf(dailyBudget(1000.0)), emptyList(), emptyList(), 0, true, now)
        assertTrue("pace=${s.pacePct}", s.pacePct > 120)
    }

    @Test
    fun `late month with money unspent concentrates into days left`() {
        // Day 28, nothing spent: 1000×31 over the 4 days left = 7750/day —
        // "why 30 yet we are on the 28th" answered: it is no longer 30.
        val s = computeSafeSpend(
            emptyList(), listOf(dailyBudget(1000.0)), emptyList(), emptyList(), 0, true,
            tsMs(2026, Calendar.OCTOBER, 28, 12)
        )
        assertTrue("target=${s.dailyTarget}", s.dailyTarget in 7000..8500)
    }

    @Test
    fun `daily budget blown month says zero not a fresh daily figure`() {
        // 32000 spent against a 1000×31 plan by day 28: nothing left —
        // the old branch handed back the full 1000 anyway.
        val txs = listOf(exp(32000.0, tsMs(2026, Calendar.OCTOBER, 20, 12)))
        val s = computeSafeSpend(
            txs, listOf(dailyBudget(1000.0)), emptyList(), emptyList(), 0, true,
            tsMs(2026, Calendar.OCTOBER, 28, 12)
        )
        assertEquals(0, s.dailyTarget)
        assertEquals(0, s.allowance)
    }
}
