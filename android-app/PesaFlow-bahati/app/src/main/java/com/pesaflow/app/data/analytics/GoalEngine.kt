package com.pesaflow.app.data.analytics

import com.pesaflow.app.data.models.SavingsGoal
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType

/**
 * Goal projection engine: pure Kotlin, no Android imports.
 *
 * Takes a savings goal and the transaction history, and projects:
 * - Date on track (if current pace continues)
 * - Daily savings required to hit target
 * - Auto-suggestion: how much to cut from categories
 * - Whether the goal is at risk
 */
data class GoalProjection(
    val title: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val remaining: Double,
    val targetTimestamp: Long,
    val daysToTarget: Long,
    val projectedDateDays: Long,
    val onTrack: Boolean,
    val dailySavingsRequired: Double,
    val currentDailyPace: Double,
    val riskLevel: RiskLevel,
    val suggestions: List<String>,
    val progressPercent: Int,
    val categoryCuts: List<CategoryCut>
) {
    enum class RiskLevel { ON_TRACK, AT_RISK, DANGER }
    data class CategoryCut(val category: String, val amount: Double)
}

/** Build a projection for a single goal from history and transactions. */
fun buildGoalProjection(
    goal: SavingsGoal,
    transactions: List<Transaction>,
    now: Long = System.currentTimeMillis(),
    horizonDays: Int = 90
): GoalProjection {
    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
    val progressPercent = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount * 100).toInt() else 0
    val daysRemaining = if (goal.targetTimestamp > 0) ((goal.targetTimestamp - now) / (24L * 60 * 60 * 1000)).coerceAtLeast(0) else horizonDays.toLong()
    val horizonStart = now - horizonDays * 24L * 60 * 60 * 1000
    val window = transactions.filter { it.dateTimestamp in horizonStart..now && it.type == TransactionType.SAVING && !it.isSample }
    val savedInWindow = window.sumOf { it.amount }
    val currentPaceDays = window.map { it.dateTimestamp / (24L * 60 * 60 * 1000) }.toSet().size.coerceAtLeast(1)
    val currentDailyPace = if (currentPaceDays > 0) savedInWindow / currentPaceDays else 0.0
    val dailySavingsRequired = if (daysRemaining > 0) remaining / daysRemaining else currentDailyPace
    val projectedDateDays = if (currentDailyPace > 0) (remaining / currentDailyPace).toLong() else horizonDays.toLong()
    val onTrack = projectedDateDays <= daysRemaining + 7

    val riskLevel = when {
        onTrack && currentDailyPace >= dailySavingsRequired -> GoalProjection.RiskLevel.ON_TRACK
        currentDailyPace < dailySavingsRequired * 0.5 -> GoalProjection.RiskLevel.DANGER
        else -> GoalProjection.RiskLevel.AT_RISK
    }

    // Category cuts: suggest reducing the top 3 expense categories
    // by 10% each to close the gap.
    val expenseWindow = transactions.filter { it.dateTimestamp in horizonStart..now && it.type == TransactionType.EXPENSE && !it.isSample }
    val categoryTotals = expenseWindow.groupBy { it.category }
        .mapValues { (_, rows) -> rows.sumOf { it.amount } }
        .filter { it.value > 0 }
        .toList()
        .sortedByDescending { it.second }
        .take(3)
    val gap = (dailySavingsRequired - currentDailyPace).coerceAtLeast(0.0)
    val categoryCuts = categoryTotals.map { (cat, total) ->
        val cut = (total * 0.10).coerceAtMost(gap * 7) // cap at weekly gap
        GoalProjection.CategoryCut(cat, cut)
    }.filter { it.amount > 0 }

    val suggestions = mutableListOf<String>()
    when (riskLevel) {
        GoalProjection.RiskLevel.ON_TRACK -> suggestions.add("On track — keep the pace")
        GoalProjection.RiskLevel.AT_RISK -> {
            suggestions.add("Close the gap: save KSh ${gap.toInt()}/day more")
            categoryCuts.take(2).forEach { cut ->
                suggestions.add("Trim ${cut.category} by KSh ${cut.amount.toInt()}/week")
            }
        }
        GoalProjection.RiskLevel.DANGER -> {
            suggestions.add("URGENT: KSh ${remaining.toInt()} left, only KSh ${currentDailyPace.toInt()}/day pace")
            categoryCuts.forEach { cut ->
                suggestions.add("Cut ${cut.category} by KSh ${cut.amount.toInt()}/week")
            }
            suggestions.add("Consider adjusting the target deadline")
        }
    }

    return GoalProjection(
        title = goal.title,
        targetAmount = goal.targetAmount,
        currentAmount = goal.currentAmount,
        remaining = remaining,
        targetTimestamp = goal.targetTimestamp,
        daysToTarget = daysRemaining,
        projectedDateDays = projectedDateDays,
        onTrack = onTrack,
        dailySavingsRequired = dailySavingsRequired,
        currentDailyPace = currentDailyPace,
        riskLevel = riskLevel,
        suggestions = suggestions,
        progressPercent = progressPercent,
        categoryCuts = categoryCuts
    )
}

/** Project ALL goals and rank by urgency. */
fun projectAllGoals(
    goals: List<SavingsGoal>,
    transactions: List<Transaction>,
    now: Long = System.currentTimeMillis()
): List<GoalProjection> {
    return goals.map { buildGoalProjection(it, transactions, now) }
        .sortedByDescending { it.riskLevel.ordinal }
}

/** Auto-suggest a new goal based on spending patterns. */
fun suggestNewGoal(
    transactions: List<Transaction>,
    existingGoals: List<SavingsGoal>,
    now: Long = System.currentTimeMillis()
): String? {
    val window = transactions.filter { it.dateTimestamp >= now - 90L * 24L * 60 * 60 * 1000 && it.dateTimestamp <= now && !it.isSample }
    val expenses = window.filter { it.type == TransactionType.EXPENSE }
    if (expenses.isEmpty()) return null
    val dayMs = 24L * 60 * 60 * 1000
    val distinctDays = expenses.map { it.dateTimestamp / dayMs }.toSet().size.coerceAtLeast(1)
    val avgMonthly = expenses.sumOf { it.amount } / distinctDays * 30.0
    val topCategory = expenses
        .groupBy { it.category }
        .maxByOrNull { it.value.sumOf { tx -> tx.amount } }?.key ?: return null
    // If user spends >5000/mo on a category, suggest a 1000 savings goal.
    if (avgMonthly > 5000) {
        val existingTitles = existingGoals.map { it.title.lowercase() }
        if (!existingTitles.contains("emergency fund".lowercase()) && !existingTitles.contains("safety net".lowercase())) {
            return "Emergency fund — save KSh 1000/month from $topCategory"
        }
    }
    return null
}
