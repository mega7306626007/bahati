package com.pesaflow.app.ui.dashboard

import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.SavingsGoal
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType

// Safe-to-spend engine (pure, tested). Every number the card shows comes
// from here — the UI only renders.
//
// What it fixes vs the old card:
//  - Adaptive daily rate: remaining budget ÷ remaining days, not monthly÷30.
//    Day 28 with a blown budget says ~0 instead of a fresh full day.
//  - Rollover is capped: skipping a day earns at most +50% tomorrow, never
//    a doubled allowance; overspend carries at most −100% (floor 0).
//  - Bills count only when due soon (≤30 days), prorated by days until due.
//    Semester fees due in 90 days no longer eat today.
//  - Fare reserve applies on school days only.
//  - Goals without a date spread over 30 days; overdue goals stop punishing
//    today (reported separately).
//  - Pace-aware verdicts: ahead/behind monthly pace reshapes the message.
data class SafeSpend(
    val hasBudget: Boolean,
    val baseLabel: String,
    val dailyTarget: Int,
    val planDaily: Int,
    val billDaily: Int,
    val fareToday: Int,
    val fareSkipped: Boolean,
    val rollover: Int,
    val allowance: Int,
    val spent: Int,
    val left: Int,
    val pacePct: Int,
    val daysLeftMonth: Int,
    val monthLeft: Int,
    // Week lens, real numbers (Mon–Sun via the shared timeline): spent so
    // far this week, the prorated share expected by now, and the pace %.
    val weekSpent: Int,
    val weekExpected: Int,
    val weekPacePct: Int,
    val overdueGoals: Int,
    val overdueTotal: Int,
    val noIncomeMonth: Boolean
)

private const val DAY_MS = 24L * 60 * 60 * 1000

internal fun computeSafeSpend(
    txs: List<Transaction>,
    budgets: List<Budget>,
    goals: List<SavingsGoal>,
    bills: List<Bill>,
    fareReserve: Int,
    schoolToday: Boolean,
    nowMs: Long
): SafeSpend {
    val monthly = budgets.firstOrNull { it.category == "ALL" }?.limitAmount?.takeIf { it > 0 }
    val dailyExplicit = budgets.filter { it.type == BudgetType.DAILY }.sumOf { it.limitAmount }.takeIf { it > 0 }
    if (monthly == null && dailyExplicit == null) {
        return SafeSpend(
            hasBudget = false, baseLabel = "", dailyTarget = 0, planDaily = 0,
            billDaily = 0, fareToday = 0, fareSkipped = false, rollover = 0,
            allowance = 0, spent = 0, left = 0, pacePct = 100, daysLeftMonth = 0,
            monthLeft = 0, weekSpent = 0, weekExpected = 0, weekPacePct = 100,
            overdueGoals = 0, overdueTotal = 0, noIncomeMonth = false
        )
    }
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    // Shared timeline: dayStart is one definition app-wide, and neighbours
    // use Calendar.add so a day stays a day across DST changes.
    val dayStart = com.pesaflow.app.data.academic.dayStart(nowMs)
    fun shiftDay(base: Long, delta: Int): Long =
        java.util.Calendar.getInstance().apply {
            timeInMillis = base
            add(java.util.Calendar.DAY_OF_YEAR, delta)
        }.timeInMillis
    val tomorrowStart = shiftDay(dayStart, 1)
    val yesterdayStart = shiftDay(dayStart, -1)
    val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val elapsed = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val daysLeft = (daysInMonth - elapsed + 1).coerceAtLeast(1)
    val monthStart = java.util.Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(java.util.Calendar.DAY_OF_MONTH, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    // One counting rule with MoneyMath.monthScopedTotal: seeded opening
    // cash and watermarked sample rows never shape allowances.
    fun spentIn(from: Long, to: Long) = txs.filter {
        it.type == TransactionType.EXPENSE &&
            it.source !in com.pesaflow.app.data.money.NON_STAT_SOURCES &&
            it.dateTimestamp >= from && it.dateTimestamp < to
    }.sumOf { it.amount }

    val monthSpent = spentIn(monthStart, nowMs + 1)
    val monthIncome = txs.filter {
        it.type == TransactionType.INCOME &&
            it.source !in com.pesaflow.app.data.money.NON_STAT_SOURCES &&
            it.dateTimestamp >= monthStart && it.dateTimestamp < nowMs + 1
    }.sumOf { it.amount }

    // Adaptive base: what remains ÷ days remaining — ONE rule for both
    // budget shapes. A Daily budget no longer means "flat forever": its
    // month is daily × days-in-month, rebased against days left, so day 28
    // with unspent money is not day 1 and a blown month says 0 instead of
    // a fresh full daily figure. Pace runs off the same month plan — the
    // old daily branch hard-coded pacePct = 100, so the card claimed
    // "always 100%" no matter what you logged.
    val monthPlan = dailyExplicit?.times(daysInMonth) ?: monthly!!
    val baseLabel = "what's left ÷ days left"
    val remaining = (monthPlan - monthSpent).coerceAtLeast(0.0)
    val adaptive = remaining / daysLeft
    val expected = monthPlan * elapsed / daysInMonth
    val pacePct = if (expected > 0) (monthSpent / expected * 100).toInt() else 100

    var planDaily = 0.0
    var overdue = 0
    var overdueTotal = 0.0
    goals.forEach { g ->
        val leftGoal = (g.targetAmount - g.currentAmount).coerceAtLeast(0.0)
        if (leftGoal <= 0) return@forEach
        when {
            g.targetTimestamp <= 0L -> planDaily += leftGoal / 30
            g.targetTimestamp <= nowMs -> { overdue++; overdueTotal += leftGoal }
            else -> {
                val days = ((g.targetTimestamp - nowMs) / DAY_MS).coerceAtLeast(1)
                planDaily += leftGoal / days
            }
        }
    }

    // Bills: only the near ones bite, prorated by days until due.
    var billDaily = 0.0
    bills.filter { it.status != "PAID" }.forEach { b ->
        if (b.amount <= 0) return@forEach
        billDaily += if (b.dueDate <= 0L) {
            b.amount / 30
        } else {
            val until = b.dueDate - nowMs
            if (until > 30 * DAY_MS) 0.0
            else b.amount / ((until / DAY_MS).coerceAtLeast(1))
        }
    }

    val fareToday = if (schoolToday) fareReserve.coerceAtLeast(0) else 0
    val target = (adaptive - planDaily - billDaily - fareToday).coerceAtLeast(0.0).toInt()

    // Rollover, clamped: yesterday's thrift earns ≤ +50%, yesterday's blowout
    // costs at most today (never negative allowance).
    val yesterdaySpend = spentIn(yesterdayStart, dayStart).toInt()
    val rollover = (target - yesterdaySpend).coerceIn(-target, target / 2)
    val allowance = (target + rollover).coerceAtLeast(0)
    // Bounded above at tomorrow: future-dated rows used to leak into
    // "spent today" with no ceiling.
    val todaySpend = spentIn(dayStart, tomorrowStart).toInt()
    val left = allowance - todaySpend

    // Week pace (Mon–Sun): spent so far vs the prorated share expected by
    // now. Same shape as the month pace — monthly slice × elapsed days —
    // so the two verdicts can never contradict the same ledger.
    val weekStart = com.pesaflow.app.data.academic.weekStartMonday(nowMs)
    val weekSpent = spentIn(weekStart, nowMs + 1)
    val elapsedWeek = ((dayStart - weekStart) / DAY_MS + 1).coerceIn(1, 7)
    // Same month plan as above, so daily-only wallets get a real week lens
    // instead of the old monthly!! crash path / flat daily × elapsed.
    val weekExpected = monthPlan * elapsedWeek / daysInMonth
    val weekPacePct = if (weekExpected > 0) (weekSpent / weekExpected * 100).toInt() else 100

    return SafeSpend(
        hasBudget = true,
        baseLabel = baseLabel,
        dailyTarget = target,
        planDaily = planDaily.toInt(),
        billDaily = billDaily.toInt(),
        fareToday = fareToday,
        fareSkipped = !schoolToday && fareReserve > 0,
        rollover = rollover,
        allowance = allowance,
        spent = todaySpend,
        left = left,
        pacePct = pacePct,
        daysLeftMonth = daysLeft,
        monthLeft = (monthPlan - monthSpent).toInt(),
        weekSpent = weekSpent.toInt(),
        weekExpected = weekExpected.toInt(),
        weekPacePct = weekPacePct,
        overdueGoals = overdue,
        overdueTotal = overdueTotal.toInt(),
        noIncomeMonth = monthIncome <= 0 && monthSpent > 0
    )
}

// Semester runway lens (dynamic horizon): unlike the monthly card above,
// this looks to the academic semester end and locks full unpaid bills due
// before it. Pure + tested; the card only renders. Personal burn pace comes
// from the user's own trailing-14-day mean — never an arbitrary floor.
internal data class SemesterRunway(
    val daysLeft: Int,
    val lockedBills: Double,
    val discretionary: Double,
    val dailyToSemesterEnd: Double,
    // Days the discretionary pool lasts at personal pace; null = no spend signal.
    val runwayDays: Int?,
    val alert: String?
)

internal fun semesterRunway(
    txs: List<Transaction>,
    bills: List<Bill>,
    balance: Double,
    nowMs: Long
): SemesterRunway {
    val (_, semEnd) = com.pesaflow.app.data.academic.semesterBounds(0L, 0L, nowMs)
    val daysLeft = ((semEnd - nowMs) / DAY_MS + 1).toInt().coerceAtLeast(1)
    val locked = bills.filter { it.status != "PAID" && it.dueDate > 0 && it.dueDate <= semEnd }
        .sumOf { it.amount }
    val discretionary = (balance - locked).coerceAtLeast(0.0)
    val daily = discretionary / daysLeft
    val today = com.pesaflow.app.data.academic.dayStart(nowMs)
    val pace = (13 downTo 0).map { back ->
        val day = today - back * DAY_MS
        txs.filter {
            it.type == TransactionType.EXPENSE &&
                it.source !in com.pesaflow.app.data.money.NON_STAT_SOURCES &&
                it.dateTimestamp >= day && it.dateTimestamp < day + DAY_MS
        }.sumOf { it.amount }
    }.average().takeIf { it > 0 }
    val runway = pace?.let { (discretionary / it).toInt() }
    val alert = when {
        discretionary <= 0 && locked > 0 ->
            "⚠️ Danger Zone: KSh ${balance.toInt()} held vs KSh ${locked.toInt()} unpaid bills. Prioritize essentials."
        runway != null && runway < daysLeft ->
            "⚡ Runway Alert: cash lasts ~$runway days at your pace, semester ends in $daysLeft. Kitchen-stock mode."
        else -> null
    }
    return SemesterRunway(daysLeft, locked, discretionary, daily, runway, alert)
}

// "transport=150" from onboarding answers → the user's own daily fare.
// Feeds fare guards and the budget engine so shields follow the person.
internal fun parseTransportDaily(answers: String): Double =
    answers.split("|").firstOrNull { it.startsWith("transport=") }
        ?.removePrefix("transport=")?.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0

// "classdays=Mon,Tue,Wed" from onboarding answers → is today school?
internal fun parseClassDaysSet(answers: String): Set<String> =
    answers.split("|").firstOrNull { it.startsWith("classdays=") }
        ?.removePrefix("classdays=")?.split(",")?.map { it.trim() }
        ?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

internal fun isSchoolToday(answers: String, nowMs: Long): Boolean {
    val days = parseClassDaysSet(answers)
    if (days.isEmpty()) return true
    val today = when (java.util.Calendar.getInstance().apply { timeInMillis = nowMs }.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> "Mon"
        java.util.Calendar.TUESDAY -> "Tue"
        java.util.Calendar.WEDNESDAY -> "Wed"
        java.util.Calendar.THURSDAY -> "Thu"
        java.util.Calendar.FRIDAY -> "Fri"
        java.util.Calendar.SATURDAY -> "Sat"
        else -> "Sun"
    }
    return today in days
}
