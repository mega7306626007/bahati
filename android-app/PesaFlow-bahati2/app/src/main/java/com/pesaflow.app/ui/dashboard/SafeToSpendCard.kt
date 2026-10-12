package com.pesaflow.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.parsers.LedgerRow
import com.pesaflow.app.data.time.inPastOrNow
import com.pesaflow.app.data.time.previousWeekRange
import com.pesaflow.app.data.time.thisWeekRange
import com.pesaflow.app.ui.analytics.BudgetRing
import com.pesaflow.app.ui.theme.AtmoType


@Composable
fun SafeToSpendCard(
    transactions: List<com.pesaflow.app.data.models.Transaction>,
    budgets: List<com.pesaflow.app.data.models.Budget>,
    goals: List<com.pesaflow.app.data.models.SavingsGoal>,
    bills: List<com.pesaflow.app.data.models.Bill>,
    flexibleCash: Double,
    hide: Boolean = false,
    // M-Pesa anchor: safe guidance reads against the pocket you actually
    // spend from. Formula unchanged — this only labels the anchor.
    mpesaCash: Double? = null,
    totalCash: Double? = null,
    lang: com.pesaflow.app.data.models.AppLanguage = com.pesaflow.app.data.models.AppLanguage.ENGLISH
) {
    val nowMs = System.currentTimeMillis()
    val budgetLimits = safeSpendBudgetLimits(budgets, nowMs)
    val monthly = budgetLimits.monthly
    val dailyExplicit = budgetLimits.daily
    val weeklyExplicit = budgetLimits.weekly
    val dayMs = 24L * 60 * 60 * 1000
    fun planDailyRate(g: com.pesaflow.app.data.models.SavingsGoal): Double {
        val left = (g.targetAmount - g.currentAmount).coerceAtLeast(0.0)
        if (left <= 0) return 0.0
        val days = ((g.targetTimestamp - nowMs) / dayMs).coerceAtLeast(1)
        return left / days
    }
    val planDaily = goals.sumOf { planDailyRate(it) }.toInt()
    val topPlan = goals.filter { it.targetAmount > it.currentAmount }.maxByOrNull { it.targetAmount - it.currentAmount }
    // PocketGuard-style leftover: bills due within 30 days come off the top,
    // daily-shared. Far-future bills (December fees in October) wait their turn.
    val billDaily = reserveBillDaily(bills, nowMs)
    val planNote = topPlan?.let { " That cash comes out of ${it.title}." } ?: ""
    var mode by remember { mutableStateOf("Day") }
    com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.LARGE) {
        Column(
            verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.sm)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(com.pesaflow.app.ui.language.dashT("safe_title", lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Day" to com.pesaflow.app.ui.language.dashT("day_chip", lang), "Week" to com.pesaflow.app.ui.language.dashT("week_chip", lang)).forEach { (m, label) ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(label) })
                    }
                }
            }
            if (mpesaCash != null && totalCash != null && !hide) {
                Text(
                    com.pesaflow.app.ui.language.dashT("safe_anchor", lang, mpesaCash.toInt().toString(), totalCash.toInt().toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (monthly == null && dailyExplicit == null && weeklyExplicit == null) {
                Text(
                    com.pesaflow.app.ui.language.dashT("safe_set_budget", lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                return@Column
            }
            val cal = java.util.Calendar.getInstance()
            val dayStart = (cal.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val now = System.currentTimeMillis()
            val day = 24L * 60 * 60 * 1000
            fun spentIn(from: Long, to: Long) = transactions.filter {
                it.type == com.pesaflow.app.data.models.TransactionType.EXPENSE &&
                    !it.isSample && it.dateTimestamp >= from && it.dateTimestamp < to && it.dateTimestamp <= now
            }.sumOf { it.amount }.toInt()
            // Weekday-aware pace: Saturdays that run hot earn a bigger slice,
            // quiet Tuesdays a smaller one. Learned per weekday from the last
            // 4 weeks (calendar-accurate); null (×1.0) until real history exists.
            val paceProfile = remember(transactions) {
                weekdayProfile(
                    transactions.filter { !it.isSample }.map {
                        LedgerRow(it.amount, it.type, it.category, it.merchant, it.dateTimestamp)
                    },
                    nowMs
                )
            }
            val weekdayFactor = paceProfile?.let { factorForToday(it, nowMs) }

            if (mode == "Day") {
                // Dynamic target: remaining money over remaining days — never a
                // frozen monthly/30 (on the 28th you divide by the 3 days left,
                // not 30). Yesterday already sits inside spentMonth, so no
                // separate rollover to double-count it. Every new transaction
                // shrinks remaining and the target moves the same day.
                val monthlyLimit = monthly
                    ?: dailyExplicit?.times(30)
                    ?: weeklyExplicit?.times(30.0 / 7.0)
                    ?: 0.0
                val spentMonth = com.pesaflow.app.data.money.monthScopedTotal(
                    transactions,
                    com.pesaflow.app.data.models.TransactionType.EXPENSE,
                    nowMs
                )
                val todaySpend = spentIn(dayStart, Long.MAX_VALUE)
                val yesterdaySpend = spentIn(dayStart - day, dayStart)
                val dom = cal.get(java.util.Calendar.DAY_OF_MONTH)
                val dim = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                val fig = safeDayFigure(
                    monthlyLimit, spentMonth, planDaily, billDaily,
                    weekdayFactor ?: 1.0, todaySpend, dom, dim, flexibleCash
                )
                val dailyTarget = fig.dailyTarget
                val allowance = fig.allowance
                val left = fig.left
                val expectedToDate = if (dim > 0) monthlyLimit * dom / dim else 0.0

                Row(verticalAlignment = Alignment.CenterVertically) {
                    BudgetRing(fraction = when {
                        allowance > 0 -> todaySpend.toFloat() / allowance
                        todaySpend > 0 || spentMonth > 0 -> 1f
                        else -> 0f
                    })
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            if (hide) "KSh ••••" else "KSh ${left.coerceAtLeast(0)}",
                            style = com.pesaflow.app.ui.theme.ppTypography.financialLarge,
                            color = if (left < 0) com.pesaflow.app.ui.theme.ppColors.error else com.pesaflow.app.ui.theme.ppColors.textPrimary
                        )
                        Text(
                            com.pesaflow.app.ui.language.dashT("left_today", lang, allowance.toString()) + if (planDaily > 0) com.pesaflow.app.ui.language.dashT("plans_keep_day", lang, planDaily.toString()) else "",
                            style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                            color = com.pesaflow.app.ui.theme.ppColors.textTertiary
                        )
                    }
                }
            Spacer(modifier = Modifier.height(8.dp))
            SafeMathRow(label = com.pesaflow.app.ui.language.dashT("safe_row_target", lang), value = "KSh $dailyTarget")
            if (weekdayFactor != null) {
                SafeMathRow(
                    label = com.pesaflow.app.ui.language.dashT("safe_row_pace", lang, "%.1f".format(weekdayFactor)),
                    value = "KSh ${(dailyTarget * weekdayFactor).toInt()}"
                )
            }
            SafeMathRow(label = com.pesaflow.app.ui.language.dashT("safe_row_spent_month", lang), value = "−KSh ${spentMonth.toInt()}")
            SafeMathRow(label = com.pesaflow.app.ui.language.dashT("safe_row_yesterday", lang), value = "−KSh $yesterdaySpend")
            SafeMathRow(label = com.pesaflow.app.ui.language.dashT("safe_row_plans", lang), value = "−KSh $planDaily")
            SafeMathRow(label = com.pesaflow.app.ui.language.dashT("safe_row_bills", lang), value = "−KSh $billDaily")
            SafeMathRow(label = com.pesaflow.app.ui.language.dashT("safe_row_spent_today", lang), value = "−KSh $todaySpend")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                when {
                    todaySpend == 0 && spentMonth == 0.0 ->
                            com.pesaflow.app.ui.language.dashT("safe_fresh", lang, allowance.toString())
                        allowance <= 0 ->
                            com.pesaflow.app.ui.language.dashT("safe_over", lang, (-fig.remaining.toInt()).toString(), spentMonth.toInt().toString(), monthlyLimit.toInt().toString(), "${fig.daysLeft}d")
                        allowance < 100 ->
                            com.pesaflow.app.ui.language.dashT("safe_tight", lang, allowance.toString(), (allowance * 0.8).toInt().toString(), (allowance * 0.2).toInt().toString())
                        spentMonth <= expectedToDate ->
                            com.pesaflow.app.ui.language.dashT("safe_onpace", lang, spentMonth.toInt().toString(), monthlyLimit.toInt().toString(), "${fig.daysLeft}d", allowance.toString())
                        else ->
                            com.pesaflow.app.ui.language.dashT("safe_overmonth", lang, spentMonth.toInt().toString(), monthlyLimit.toInt().toString(), "${fig.daysLeft}d", allowance.toString(), planNote)
                    } + if (left < 0 && allowance > 0) com.pesaflow.app.ui.language.dashT("safe_paused_day", lang) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isUnusualDay(todaySpend.toDouble(), dailyTarget * (weekdayFactor ?: 1.0))) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        com.pesaflow.app.ui.language.dashT("safe_unusual", lang, todaySpend.toString(), (dailyTarget * (weekdayFactor ?: 1.0)).toInt().toString()),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                val weekBase = weeklyExplicit
                    ?: monthly?.div(30)?.times(7)
                    ?: dailyExplicit?.times(7)
                    ?: 0.0
                val weekTarget = (weekBase - planDaily * 7 - billDaily * 7).toInt().coerceAtLeast(0)
                // Calendar weeks everywhere: this week to-date vs the complete
                // previous week. The old rolling 7-day blocks drifted a day at
                // a time and never matched the dashboard's own weekday chart.
                val week = thisWeekRange(nowMs)
                val prevWeek = previousWeekRange(nowMs)
                val prevWeekSpend = spentIn(prevWeek.startInclusive, prevWeek.endExclusive)
                val thisWeekSpend = transactions.filter {
                    it.type == com.pesaflow.app.data.models.TransactionType.EXPENSE && !it.isSample &&
                        it.dateTimestamp in week && inPastOrNow(it.dateTimestamp, nowMs)
                }.sumOf { it.amount }.toInt()
                // Allowance is exactly the target: last week's balance is copy,
                // not math (weeklyAllowance pins this — rollover doubled it).
                val weekRollover = weekTarget - prevWeekSpend
                val weekAllowance = minOf(weeklyAllowance(weekTarget), flexibleCash.coerceAtLeast(0.0).toInt())
                val weekLeft = weekAllowance - thisWeekSpend

                Row(verticalAlignment = Alignment.CenterVertically) {
                    BudgetRing(fraction = when {
                        weekAllowance > 0 -> thisWeekSpend.toFloat() / weekAllowance
                        thisWeekSpend > 0 -> 1f
                        else -> 0f
                    })
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            if (hide) "KSh ••••" else "KSh ${weekLeft.coerceAtLeast(0)}",
                            style = com.pesaflow.app.ui.theme.ppTypography.financialLarge,
                            color = if (weekLeft < 0) com.pesaflow.app.ui.theme.ppColors.error else com.pesaflow.app.ui.theme.ppColors.textPrimary
                        )
                        Text(
                            com.pesaflow.app.ui.language.dashT("left_week", lang, weekAllowance.toString()) + if (planDaily > 0) com.pesaflow.app.ui.language.dashT("plans_keep_week", lang, (planDaily * 7).toString()) else "",
                            style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                            color = com.pesaflow.app.ui.theme.ppColors.textTertiary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    when {
                        weekAllowance <= 0 ->
                            com.pesaflow.app.ui.language.dashT("safe_week_none", lang)
                        weekAllowance < 100 * 7 ->
                            com.pesaflow.app.ui.language.dashT("safe_week_tight", lang, weekAllowance.toString(), (weekAllowance / 7).toInt().toString(), (weekAllowance * 0.6).toInt().toString(), (weekAllowance * 0.4).toInt().toString())
                        prevWeekSpend <= weekTarget ->
                            com.pesaflow.app.ui.language.dashT("safe_week_good", lang, prevWeekSpend.toString(), weekTarget.toString(), weekAllowance.toString())
                        else ->
                            com.pesaflow.app.ui.language.dashT("safe_week_over", lang, (-weekRollover).toString(), prevWeekSpend.toString(), weekTarget.toString(), weekAllowance.toString(), planNote)
                    } + if (weekLeft < 0 && weekAllowance > 0) com.pesaflow.app.ui.language.dashT("safe_paused_week", lang) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}


@Composable
private fun SafeMathRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
