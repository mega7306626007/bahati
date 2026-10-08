package com.pesaflow.app.data.money

import com.pesaflow.app.data.academic.currentWeekRange
import com.pesaflow.app.data.academic.dayStart
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import java.util.Calendar

/**
 * Canonical, cached financial snapshot.
 *
 * The ledger is still the source of truth, but expensive scans now happen once
 * per ledger emission instead of independently inside every screen and Buddy.
 */
data class FinancialSnapshot(
    val nowMs: Long,
    val dayStartMs: Long,
    val yesterdayStartMs: Long,
    val yesterdayEndMs: Long,
    val weekStartMs: Long,
    val weekEndMs: Long,
    val monthStartMs: Long,
    val monthEndMs: Long,
    val lastMonthStartMs: Long,
    val lastMonthEndMs: Long,
    val daysLeftInMonth: Int,
    val balance: Double,
    val monthIncome: Double,
    val monthExpense: Double,
    val todayExpense: Double,
    val yesterdayExpense: Double,
    val weekExpense: Double,
    val lastMonthExpense: Double,
    val totalSavings: Double,
    val ziidi: Double,
    val monthExpenseByCategory: Map<String, Double>,
    val weekExpenseByCategory: Map<String, Double>,
    val yesterdayExpenseByCategory: Map<String, Double>,
    val monthIncomeByCategory: Map<String, Double>,
    val topExpenses: List<Transaction>
) {
    fun monthCategory(category: String): Double =
        monthExpenseByCategory[category] ?: 0.0

    fun weekCategory(category: String): Double =
        weekExpenseByCategory[category] ?: 0.0

    fun yesterdayCategory(category: String): Double =
        yesterdayExpenseByCategory[category] ?: 0.0
}

private data class Bounds(
    val dayStart: Long,
    val tomorrowStart: Long,
    val yesterdayStart: Long,
    val weekStart: Long,
    val weekEnd: Long,
    val monthStart: Long,
    val monthEnd: Long,
    val lastMonthStart: Long,
    val lastMonthEnd: Long,
    val daysLeftInMonth: Int
)

private fun bounds(nowMs: Long): Bounds {
    val dayStartMs = dayStart(nowMs)
    val dayCal = Calendar.getInstance().apply { timeInMillis = dayStartMs }
    dayCal.add(Calendar.DAY_OF_YEAR, 1)
    val tomorrow = dayCal.timeInMillis

    val yesterdayCal = Calendar.getInstance().apply { timeInMillis = dayStartMs }
    yesterdayCal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = yesterdayCal.timeInMillis

    val (weekStart, weekEnd) = currentWeekRange(nowMs)

    val monthCal = Calendar.getInstance().apply { timeInMillis = dayStartMs }
    monthCal.set(Calendar.DAY_OF_MONTH, 1)
    val monthStart = monthCal.timeInMillis
    monthCal.add(Calendar.MONTH, 1)
    val monthEnd = monthCal.timeInMillis
    monthCal.add(Calendar.MONTH, -2)
    val lastMonthStart = monthCal.timeInMillis
    monthCal.add(Calendar.MONTH, 1)
    val lastMonthEnd = monthCal.timeInMillis

    val nowCal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val daysLeft = nowCal.getActualMaximum(Calendar.DAY_OF_MONTH) -
        nowCal.get(Calendar.DAY_OF_MONTH) + 1

    return Bounds(
        dayStart = dayStartMs,
        tomorrowStart = tomorrow,
        yesterdayStart = yesterday,
        weekStart = weekStart,
        weekEnd = weekEnd,
        monthStart = monthStart,
        monthEnd = monthEnd,
        lastMonthStart = lastMonthStart,
        lastMonthEnd = lastMonthEnd,
        daysLeftInMonth = daysLeft
    )
}

fun buildFinancialSnapshot(
    transactions: List<Transaction>,
    nowMs: Long = System.currentTimeMillis()
): FinancialSnapshot {
    val b = bounds(nowMs)

    var balance = 0.0
    var monthIncome = 0.0
    var monthExpense = 0.0
    var todayExpense = 0.0
    var yesterdayExpense = 0.0
    var weekExpense = 0.0
    var lastMonthExpense = 0.0
    var totalSavings = 0.0
    var ziidi = 0.0

    val monthExpenseByCategory = linkedMapOf<String, Double>()
    val weekExpenseByCategory = linkedMapOf<String, Double>()
    val yesterdayExpenseByCategory = linkedMapOf<String, Double>()
    val monthIncomeByCategory = linkedMapOf<String, Double>()

    fun add(map: MutableMap<String, Double>, key: String, amount: Double) {
        map[key] = (map[key] ?: 0.0) + amount
    }

    for (tx in transactions) {
        val includeInStats = tx.source !in NON_STAT_SOURCES

        balance += when (tx.type) {
            TransactionType.INCOME -> tx.amount
            TransactionType.EXPENSE,
            TransactionType.SAVING,
            TransactionType.INVESTMENT -> -tx.amount
        }

        when {
            tx.type == TransactionType.SAVING &&
                tx.merchant.contains("ziidi", ignoreCase = true) -> ziidi += tx.amount
            tx.type == TransactionType.INCOME &&
                tx.merchant.contains("ziidi", ignoreCase = true) -> ziidi -= tx.amount
        }

        if (tx.type == TransactionType.SAVING && includeInStats) {
            totalSavings += tx.amount
        }

        if (tx.type == TransactionType.EXPENSE && includeInStats) {
            if (tx.dateTimestamp >= b.dayStart && tx.dateTimestamp < b.tomorrowStart) {
                todayExpense += tx.amount
            }
            if (tx.dateTimestamp >= b.yesterdayStart && tx.dateTimestamp < b.dayStart) {
                yesterdayExpense += tx.amount
                add(yesterdayExpenseByCategory, tx.category, tx.amount)
            }
            if (tx.dateTimestamp >= b.weekStart && tx.dateTimestamp < b.weekEnd) {
                weekExpense += tx.amount
                add(weekExpenseByCategory, tx.category, tx.amount)
            }
            if (tx.dateTimestamp >= b.monthStart && tx.dateTimestamp < b.monthEnd) {
                monthExpense += tx.amount
                add(monthExpenseByCategory, tx.category, tx.amount)
            }
            if (tx.dateTimestamp >= b.lastMonthStart && tx.dateTimestamp < b.lastMonthEnd) {
                lastMonthExpense += tx.amount
            }
        }

        if (tx.type == TransactionType.INCOME && includeInStats &&
            tx.dateTimestamp >= b.monthStart && tx.dateTimestamp < b.monthEnd
        ) {
            monthIncome += tx.amount
            add(monthIncomeByCategory, tx.category, tx.amount)
        }
    }

    val topExpenses = transactions.asSequence()
        .filter { it.type == TransactionType.EXPENSE }
        .sortedByDescending { it.amount }
        .take(5)
        .toList()

    return FinancialSnapshot(
        nowMs = nowMs,
        dayStartMs = b.dayStart,
        yesterdayStartMs = b.yesterdayStart,
        yesterdayEndMs = b.dayStart,
        weekStartMs = b.weekStart,
        weekEndMs = b.weekEnd,
        monthStartMs = b.monthStart,
        monthEndMs = b.monthEnd,
        lastMonthStartMs = b.lastMonthStart,
        lastMonthEndMs = b.lastMonthEnd,
        daysLeftInMonth = b.daysLeftInMonth,
        balance = balance,
        monthIncome = monthIncome,
        monthExpense = monthExpense,
        todayExpense = todayExpense,
        yesterdayExpense = yesterdayExpense,
        weekExpense = weekExpense,
        lastMonthExpense = lastMonthExpense,
        totalSavings = totalSavings,
        ziidi = ziidi.coerceAtLeast(0.0),
        monthExpenseByCategory = monthExpenseByCategory,
        weekExpenseByCategory = weekExpenseByCategory,
        yesterdayExpenseByCategory = yesterdayExpenseByCategory,
        monthIncomeByCategory = monthIncomeByCategory,
        topExpenses = topExpenses
    )
}
