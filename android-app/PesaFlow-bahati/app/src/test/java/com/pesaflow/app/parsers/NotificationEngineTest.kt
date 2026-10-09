package com.pesaflow.app.parsers

import com.pesaflow.app.data.notifications.*
import com.pesaflow.app.data.models.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class NotificationEngineTest {

    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1, 12, 0) }.timeInMillis
    private fun bill(name: String, amount: Double, dueDate: Long, status: String = "UNPAID") =
        Bill(name = name, amount = amount, dueDate = dueDate, category = "Bills", status = status)

    @Test
    fun `billDueTodayIsUrgent`() {
        val engine = NotificationEngine()
        val b = bill("Rent", 5000.0, now) // due today
        val events = engine.checkBills(listOf(b), now)
        assertEquals(1, events.size)
        assertEquals(NotificationType.BILL_DUE_SOON, events[0].type)
        assertEquals(NotificationPriority.URGENT, events[0].priority)
        assertTrue(events[0].title.contains("today"))
    }

    @Test
    fun `billDueIn3DaysIsHigh`() {
        val engine = NotificationEngine()
        val due = now + 3 * 24L * 60 * 60 * 1000
        val b = bill("Electric", 200.0, due)
        val events = engine.checkBills(listOf(b), now)
        assertEquals(1, events.size)
        assertEquals(NotificationPriority.HIGH, events[0].priority)
        assertTrue(events[0].title.contains("3d"))
    }

    @Test
    fun `billDueIn5DaysNoAlert`() {
        val engine = NotificationEngine()
        val due = now + 5 * 24L * 60 * 60 * 1000
        val b = bill("Internet", 300.0, due)
        val events = engine.checkBills(listOf(b), now, leadDays = 3)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `billOverdueIsUrgent`() {
        val engine = NotificationEngine()
        val due = now - 5 * 24L * 60 * 60 * 1000
        val b = bill("Rent", 5000.0, due, status = "OWING")
        val events = engine.checkBills(listOf(b), now)
        assertEquals(1, events.size)
        assertEquals(NotificationType.BILL_OVERDUE, events[0].type)
        assertEquals(NotificationPriority.URGENT, events[0].priority)
        assertTrue(events[0].title.contains("OVERDUE"))
    }

    @Test
    fun `paidBillNoAlert`() {
        val engine = NotificationEngine()
        val due = now - 2 * 24L * 60 * 60 * 1000
        val b = bill("Rent", 5000.0, due, status = "PAID")
        val events = engine.checkBills(listOf(b), now)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `debtOverdueTriggers`() {
        val engine = NotificationEngine()
        val due = now - 10 * 24L * 60 * 60 * 1000
        val d = Debt(person = "John", amount = 2000.0, dateBorrowed = now - 30 * 24L * 60 * 60 * 1000, dueDate = due, status = "OWING")
        val events = engine.checkDebts(listOf(d), now)
        assertEquals(1, events.size)
        assertEquals(NotificationPriority.HIGH, events[0].priority)
        assertTrue(events[0].title.contains("Overdue"))
    }

    @Test
    fun `budgetBreachedUrgent`() {
        val engine = NotificationEngine()
        val spent = mapOf("Food" to 5000.0)
        val b = Budget(category = "Food", limitAmount = 4000.0, type = BudgetType.WEEKLY, startTimestamp = now, endTimestamp = now + 7 * 24L * 60 * 60 * 1000)
        val events = engine.checkBudgets(listOf(b), spent, now)
        assertEquals(1, events.size)
        assertEquals(NotificationPriority.URGENT, events[0].priority)
        assertTrue(events[0].title.contains("blown"))
    }

    @Test
    fun `budgetWarningAt80`() {
        val engine = NotificationEngine()
        val spent = mapOf("Food" to 3200.0)
        val b = Budget(category = "Food", limitAmount = 4000.0, type = BudgetType.WEEKLY, startTimestamp = now, endTimestamp = now + 7 * 24L * 60 * 60 * 1000)
        val events = engine.checkBudgets(listOf(b), spent, now)
        assertEquals(1, events.size)
        assertEquals(NotificationPriority.HIGH, events[0].priority)
        assertTrue(events[0].title.contains("warning"))
    }

    @Test
    fun `budgetBelow80NoAlert`() {
        val engine = NotificationEngine()
        val spent = mapOf("Food" to 3000.0)
        val b = Budget(category = "Food", limitAmount = 4000.0, type = BudgetType.WEEKLY, startTimestamp = now, endTimestamp = now + 7 * 24L * 60 * 60 * 1000)
        val events = engine.checkBudgets(listOf(b), spent, now)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `unusualSpendTriggers`() {
        val engine = NotificationEngine()
        val events = engine.checkUnusualSpend(todaySpend = 1500.0, yesterdaySpend = 0.0, dailyTarget = 500.0, weekdayFactor = 1.0)
        assertTrue(events.any { it.type == NotificationType.UNUSUAL_SPEND && it.priority == NotificationPriority.HIGH })
    }

    @Test
    fun `normalSpendNoUnusualAlert`() {
        val engine = NotificationEngine()
        val events = engine.checkUnusualSpend(todaySpend = 400.0, yesterdaySpend = 300.0, dailyTarget = 500.0, weekdayFactor = 1.0)
        assertTrue(events.none { it.type == NotificationType.UNUSUAL_SPEND && it.priority == NotificationPriority.HIGH })
    }

    @Test
    fun `goalMilestone90Triggers`() {
        val engine = NotificationEngine()
        val g = SavingsGoal(title = "Phone", targetAmount = 10000.0, currentAmount = 9000.0)
        val events = engine.checkGoalMilestones(listOf(g), now)
        assertEquals(1, events.size)
        assertEquals(NotificationPriority.NORMAL, events[0].priority)
        assertTrue(events[0].title.contains("near completion"))
    }

    @Test
    fun `goalMilestone50TriggersLow`() {
        val engine = NotificationEngine()
        val g = SavingsGoal(title = "Phone", targetAmount = 10000.0, currentAmount = 5000.0)
        val events = engine.checkGoalMilestones(listOf(g), now)
        assertEquals(1, events.size)
        assertEquals(NotificationPriority.LOW, events[0].priority)
    }

    @Test
    fun `weeklyRitualWednesdayOnly`() {
        val engine = NotificationEngine()
        val wednesday = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 7, 12, 0) }.timeInMillis
        val event = engine.checkWeeklyRitual(null, wednesday)
        assertNotNull(event)
        assertEquals(NotificationType.WEEKLY_RITUAL, event!!.type)
    }

    @Test
    fun `weeklyRitualNonWednesdayNull`() {
        val engine = NotificationEngine()
        val monday = now // Oct 1 2026 is a Thursday, use a Monday
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 5, 12, 0) } // Monday
        val event = engine.checkWeeklyRitual(null, cal.timeInMillis)
        assertNull(event)
    }

    @Test
    fun `rentReminderWhenClose`() {
        val engine = NotificationEngine()
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1, 12, 0) }
        val event = engine.checkRentReminder(rentDayOfMonth = cal.get(Calendar.DAY_OF_MONTH), now = cal.timeInMillis)
        assertNotNull(event)
        assertEquals(NotificationType.RENT_REMINDER, event!!.type)
    }

    @Test
    fun `rentReminderWhenFarOutNull`() {
        val engine = NotificationEngine()
        val event = engine.checkRentReminder(rentDayOfMonth = 25, now = now)
        assertNull(event)
    }

    @Test
    fun `evaluateAllAggregates`() {
        val engine = NotificationEngine()
        val b = bill("Rent", 5000.0, now - 1 * 24L * 60 * 60 * 1000, "OWING")
        val g = SavingsGoal(title = "Phone", targetAmount = 10000.0, currentAmount = 9000.0)
        val events = engine.evaluateAll(
            bills = listOf(b), debts = emptyList(),
            budgets = emptyList(), spentByCategory = emptyMap(),
            goals = listOf(g),
            todaySpend = 1500.0, yesterdaySpend = 0.0,
            dailyTarget = 500.0, weekdayFactor = 1.0,
            lastReviewDay = null, rentDayOfMonth = 1,
            now = now
        )
        assertTrue(events.isNotEmpty())
        assertTrue(events.any { it.type == NotificationType.BILL_OVERDUE })
        assertTrue(events.any { it.type == NotificationType.UNUSUAL_SPEND })
    }
}
