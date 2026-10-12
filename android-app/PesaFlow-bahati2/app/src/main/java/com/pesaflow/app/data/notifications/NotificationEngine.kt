package com.pesaflow.app.data.notifications

import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.Debt
import com.pesaflow.app.data.models.SavingsGoal
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import java.util.Calendar

/**
 * Intelligent notification engine: pure Kotlin, no Android imports.
 *
 * Evaluates whether a notification should fire, and what kind,
 * from live data. The Android layer (WorkManager/AlarmManager)
 * schedules the check; this engine decides the verdict.
 */
enum class NotificationPriority(val level: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    URGENT(3)
}

enum class NotificationType {
    BILL_DUE_SOON,
    BILL_OVERDUE,
    BUDGET_BREACH,
    UNUSUAL_SPEND,
    GOAL_MILESTONE,
    DEBT_OVERDUE,
    WEEKLY_RITUAL,
    RENT_REMINDER
}

data class NotificationEvent(
    val type: NotificationType,
    val priority: NotificationPriority,
    val title: String,
    val body: String,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap()
)

data class NotificationBudget(
    val category: String,
    val spent: Double,
    val limit: Double,
    val pctUsed: Int,
    val breach: Boolean
)

/** Pure engine: no Android imports. Callers handle scheduling. */
class NotificationEngine {

    /** Check bills for upcoming/overdue reminders. */
    fun checkBills(
        bills: List<Bill>,
        now: Long = System.currentTimeMillis(),
        leadDays: Int = 3
    ): List<NotificationEvent> {
        val events = mutableListOf<NotificationEvent>()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val dayStart = cal.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        bills.filter { it.status != "PAID" }.forEach { bill ->
            val dueCal = Calendar.getInstance().apply {
                timeInMillis = bill.dueDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val daysUntil = (dueCal.timeInMillis - dayStart) / (24L * 60 * 60 * 1000)
            when {
                daysUntil == 0L -> {
                    events.add(NotificationEvent(
                        NotificationType.BILL_DUE_SOON,
                        NotificationPriority.URGENT,
                        "Due today: ${bill.name}",
                        "KSh ${bill.amount.toInt()} — pay today",
                        now,
                        mapOf("billId" to bill.id, "amount" to bill.amount.toString())
                    ))
                }
                daysUntil in 1..leadDays -> {
                    events.add(NotificationEvent(
                        NotificationType.BILL_DUE_SOON,
                        NotificationPriority.HIGH,
                        "Due in ${daysUntil.toInt()}d: ${bill.name}",
                        "KSh ${bill.amount.toInt()} — ${bill.frequency} reminder",
                        now,
                        mapOf("billId" to bill.id, "daysUntil" to daysUntil.toString())
                    ))
                }
                daysUntil < 0 -> {
                    events.add(NotificationEvent(
                        NotificationType.BILL_OVERDUE,
                        NotificationPriority.URGENT,
                        "OVERDUE: ${bill.name}",
                        "KSh ${bill.amount.toInt()} was due ${(-daysUntil).toInt()}d ago",
                        now,
                        mapOf("billId" to bill.id, "daysOverdue" to (-daysUntil).toString())
                    ))
                }
            }
        }
        return events
    }

    /** Check debts for overdue. */
    fun checkDebts(
        debts: List<Debt>,
        now: Long = System.currentTimeMillis()
    ): List<NotificationEvent> {
        val events = mutableListOf<NotificationEvent>()
        debts.filter { it.status == "OWING" || it.status == "OVERDUE" }.forEach { debt ->
            val daysUntil = (debt.dueDate - now) / (24L * 60 * 60 * 1000)
            if (daysUntil < 0 && debt.status != "PAID") {
                events.add(NotificationEvent(
                    NotificationType.DEBT_OVERDUE,
                    NotificationPriority.HIGH,
                    "Overdue: ${debt.person}",
                    "KSh ${debt.amount.toInt()} was due ${(-daysUntil).toInt()}d ago (${debt.direction})",
                    now,
                    mapOf("debtId" to debt.id, "daysOverdue" to (-daysUntil).toString())
                ))
            }
        }
        return events
    }

    /** Detect budget breaches from current spend vs limits. */
    fun checkBudgets(
        budgets: List<Budget>,
        spentByCategory: Map<String, Double>,
        now: Long = System.currentTimeMillis()
    ): List<NotificationEvent> {
        val events = mutableListOf<NotificationEvent>()
        budgets.filter { it.limitAmount > 0 }.forEach { budget ->
            val spent = spentByCategory[budget.category] ?: 0.0
            val pctUsed = if (budget.limitAmount > 0) ((spent / budget.limitAmount) * 100).toInt() else 0
            if (pctUsed >= 100) {
                events.add(NotificationEvent(
                    NotificationType.BUDGET_BREACH,
                    NotificationPriority.URGENT,
                    "Budget blown: ${budget.category}",
                    "KSh ${spent.toInt()} of KSh ${budget.limitAmount.toInt()} used ($pctUsed%)",
                    now,
                    mapOf("category" to budget.category, "pctUsed" to pctUsed.toString())
                ))
            } else if (pctUsed >= 80) {
                events.add(NotificationEvent(
                    NotificationType.BUDGET_BREACH,
                    NotificationPriority.HIGH,
                    "Budget warning: ${budget.category}",
                    "KSh ${spent.toInt()} of KSh ${budget.limitAmount.toInt()} used ($pctUsed%)",
                    now,
                    mapOf("category" to budget.category, "pctUsed" to pctUsed.toString())
                ))
            }
        }
        return events
    }

    /** Detect unusual spend vs the learned weekday profile. */
    fun checkUnusualSpend(
        todaySpend: Double,
        yesterdaySpend: Double,
        dailyTarget: Double,
        weekdayFactor: Double,
        now: Long = System.currentTimeMillis()
    ): List<NotificationEvent> {
        val events = mutableListOf<NotificationEvent>()
        val expected = dailyTarget * weekdayFactor
        if (todaySpend > 2 * expected && todaySpend - expected >= 200) {
            events.add(NotificationEvent(
                NotificationType.UNUSUAL_SPEND,
                NotificationPriority.HIGH,
                "Unusual spend detected",
                "KSh ${todaySpend.toInt()} today vs KSh ${expected.toInt()} expected — pause non-essentials",
                now,
                mapOf("todaySpend" to todaySpend.toString(), "expected" to expected.toString())
            ))
        }
        if (yesterdaySpend > dailyTarget && todaySpend > 0) {
            events.add(NotificationEvent(
                NotificationType.UNUSUAL_SPEND,
                NotificationPriority.NORMAL,
                "Yesterday went over budget",
                "KSh ${yesterdaySpend.toInt()} vs KSh ${dailyTarget.toInt()} target",
                now,
                mapOf("yesterdaySpend" to yesterdaySpend.toString())
            ))
        }
        return events
    }

    /** Check savings goal milestones. */
    fun checkGoalMilestones(
        goals: List<SavingsGoal>,
        now: Long = System.currentTimeMillis()
    ): List<NotificationEvent> {
        val events = mutableListOf<NotificationEvent>()
        goals.filter { it.targetAmount > it.currentAmount }.forEach { goal ->
            val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount * 100).toInt() else 0
            if (progress >= 90 && progress < 100) {
                events.add(NotificationEvent(
                    NotificationType.GOAL_MILESTONE,
                    NotificationPriority.NORMAL,
                    "Goal near completion: ${goal.title}",
                    "$progress% — only KSh ${(goal.targetAmount - goal.currentAmount).toInt()} left",
                    now,
                    mapOf("goalId" to goal.id, "progress" to progress.toString())
                ))
            } else if (progress >= 50 && progress < 100 && progress % 25 == 0) {
                events.add(NotificationEvent(
                    NotificationType.GOAL_MILESTONE,
                    NotificationPriority.LOW,
                    "Goal milestone: ${goal.title}",
                    "$progress% saved — KSh ${goal.currentAmount.toInt()} of ${goal.targetAmount.toInt()}",
                    now,
                    mapOf("goalId" to goal.id, "progress" to progress.toString())
                ))
            }
        }
        return events
    }

    /** Weekly ritual prompt: suggest reviewing when the week is 3 days old. */
    fun checkWeeklyRitual(
        lastReviewDay: String?,
        now: Long = System.currentTimeMillis()
    ): NotificationEvent? {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek != Calendar.WEDNESDAY) return null
        val todayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(now))
        if (lastReviewDay == todayKey) return null
        return NotificationEvent(
            NotificationType.WEEKLY_RITUAL,
            NotificationPriority.NORMAL,
            "Weekly review time",
            "It is Wednesday — time for your PesaFlow weekly review ritual",
            now,
            mapOf("today" to todayKey)
        )
    }

    /** Rent reminder for persona-inconsistent anchors. */
    fun checkRentReminder(
        rentDayOfMonth: Int,
        now: Long = System.currentTimeMillis()
    ): NotificationEvent? {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val daysUntil = rentDayOfMonth - currentDay
        if (daysUntil in 0..2) {
            return NotificationEvent(
                NotificationType.RENT_REMINDER,
                NotificationPriority.HIGH,
                "Rent day approaching",
                "Rent is due on the ${rentDayOfMonth}th — KSh due soon",
                now,
                mapOf("rentDay" to rentDayOfMonth.toString())
            )
        }
        return null
    }

    /** Aggregate all checks into one prioritized list. */
    fun evaluateAll(
        bills: List<Bill>,
        debts: List<Debt>,
        budgets: List<Budget>,
        spentByCategory: Map<String, Double>,
        goals: List<SavingsGoal>,
        todaySpend: Double,
        yesterdaySpend: Double,
        dailyTarget: Double,
        weekdayFactor: Double,
        lastReviewDay: String?,
        rentDayOfMonth: Int,
        now: Long = System.currentTimeMillis()
    ): List<NotificationEvent> {
        val all = mutableListOf<NotificationEvent>()
        all.addAll(checkBills(bills, now))
        all.addAll(checkDebts(debts, now))
        all.addAll(checkBudgets(budgets, spentByCategory, now))
        all.addAll(checkUnusualSpend(todaySpend, yesterdaySpend, dailyTarget, weekdayFactor, now))
        all.addAll(checkGoalMilestones(goals, now))
        all.addAll(checkWeeklyRitual(lastReviewDay, now).let { if (it != null) listOf(it) else emptyList() })
        all.addAll(checkRentReminder(rentDayOfMonth, now).let { if (it != null) listOf(it) else emptyList() })
        return all.sortedBy { it.priority.level }
    }
}
