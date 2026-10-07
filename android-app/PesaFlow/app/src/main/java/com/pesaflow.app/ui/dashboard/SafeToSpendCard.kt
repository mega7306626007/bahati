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
import com.pesaflow.app.ui.analytics.BudgetRing
import com.pesaflow.app.ui.theme.AtmoType


@Composable
fun SafeToSpendCard(
    transactions: List<com.pesaflow.app.data.models.Transaction>,
    budgets: List<com.pesaflow.app.data.models.Budget>,
    goals: List<com.pesaflow.app.data.models.SavingsGoal>,
    bills: List<com.pesaflow.app.data.models.Bill>,
    hide: Boolean = false,
    fareReserve: Int = 0,
    schoolToday: Boolean = true,
    // Forecast invalidation revision (spec §15): any ML correction bumps
    // this, recomputing the outlook band below — stale bands never show.
    forecastVersion: Int = 0
) {
    val nowMs = System.currentTimeMillis()
    val s = remember(transactions, budgets, goals, bills, fareReserve, schoolToday) {
        computeSafeSpend(transactions, budgets, goals, bills, fareReserve, schoolToday, nowMs)
    }
    var mode by remember { mutableStateOf("Day") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
                    )
                )
                .padding(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Safe to Spend 🛡️", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Day", "Week").forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) })
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (!s.hasBudget) {
                Text(
                    "Set a Daily budget or a monthly ALL budget on the Budget tab and I'll compute your allowance — paced by the month, guarded by fares, plans and due bills.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Used-share of today's allowance. A wiped allowance with
                // nothing spent is 0%, not a permanent 100% — the old
                // `else 1f` made the ring claim fully-used whenever
                // allowance hit 0 even though KSh 0 was left of KSh 0.
                BudgetRing(
                    fraction = when {
                        s.allowance > 0 -> s.spent.toFloat() / s.allowance
                        s.spent > 0 -> 1f
                        else -> 0f
                    }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        if (hide) "KSh ••••" else "KSh ${s.left.coerceAtLeast(0)}",
                        style = AtmoType.figure,
                        color = if (s.left < 0) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "left of KSh ${s.allowance} today · ${s.baseLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SafeMathRow(label = "Daily target (${s.baseLabel})", value = "KSh ${s.dailyTarget}")
            if (s.rollover != 0) SafeMathRow(
                label = if (s.rollover > 0) "Yesterday's thrift (+capped)" else "Yesterday's overrun",
                value = (if (s.rollover > 0) "+KSh " else "−KSh ") + Math.abs(s.rollover)
            )
            if (s.planDaily > 0) SafeMathRow(label = "Plans reserve", value = "−KSh ${s.planDaily}")
            if (s.billDaily > 0) SafeMathRow(label = "Bills due soon", value = "−KSh ${s.billDaily}")
            if (s.fareToday > 0) SafeMathRow(label = "Fare reserve 🚌", value = "−KSh ${s.fareToday}")
            SafeMathRow(label = "Spent today", value = "−KSh ${s.spent}")
            if (s.baseLabel.startsWith("what's")) {
                SafeMathRow(
                    label = "Month pace (${s.pacePct}%) · ${s.daysLeftMonth}d left",
                    value = if (s.monthLeft >= 0) "KSh ${s.monthLeft} left" else "KSh ${-s.monthLeft} over"
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                buildString {
                    when {
                        s.allowance <= 0 -> append("Overrun wiped today out — spend KSh 0 if you can. 🛑")
                        // No fixed shilling "truths": critical means less than
                        // half YOUR day. KSh 100 is lunch for one student and
                        // half a day for another.
                        s.allowance < s.dailyTarget / 2 -> append("KSh ${s.allowance} left — under half your day. Prioritize: Food KSh ${(s.allowance * 0.8).toInt()} + essentials KSh ${(s.allowance * 0.2).toInt()}. 💪")
                        s.pacePct >= 120 -> append("Running ${s.pacePct}% ahead of monthly pace — today tightened to KSh ${s.allowance}. 🐢")
                        s.pacePct <= 80 && s.monthLeft > 0 -> append("${s.pacePct}% of pace — KSh ${s.monthLeft} still free this month. Today: KSh ${s.allowance}. 🎉")
                        s.rollover > 0 -> append("Yesterday came under — today stretches to KSh ${s.allowance}. 🎉")
                        s.rollover < 0 -> append("Yesterday overshot — today tightens to KSh ${s.allowance}.")
                        else -> append("On pace (${s.pacePct}%) — KSh ${s.allowance} is a fair today.")
                    }
                    if (s.fareSkipped) append(" No school today — fare stays in pocket. 🚌")
                    if (s.overdueGoals > 0) append(" ${s.overdueGoals} goal(s) overdue (KSh ${s.overdueTotal} short) — budgets paused, not today.")
                    if (s.noIncomeMonth) append(" No income logged this month — this assumes money exists. 💡")
                    if (s.left < 0 && s.allowance > 0) append(" You've passed today's allowance — pause till tomorrow. ⏸️")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (s.baseLabel.startsWith("what's")) {
                SafeMathRow(
                    label = "Week pace Mon–Sun (${s.weekPacePct}%)",
                    value = "KSh ${s.weekSpent} of ~KSh ${s.weekExpected}"
                )
            }
            if (mode == "Week") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    buildString {
                        append("This week (Mon–Sun): KSh ${s.weekSpent} spent of ~KSh ${s.weekExpected} expected by now (${s.weekPacePct}%).")
                        when {
                            s.weekPacePct >= 120 -> append(" Front-loaded days eat weekends — ease off till Monday. 🐢")
                            s.weekPacePct <= 80 -> append(" Room to breathe — a planned treat fits. 🎉")
                            else -> append(" Steady week. 🙂")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 7-day outlook from ledger history (range, never a promise).
                val dayMs = 24L * 60 * 60 * 1000
                val dailyTotals = remember(transactions) {
                    val today = com.pesaflow.app.data.academic.dayStart(System.currentTimeMillis())
                    (13 downTo 0).map { back ->
                        val day = today - back * dayMs
                        transactions.filter {
                            it.type == com.pesaflow.app.data.models.TransactionType.EXPENSE &&
                                com.pesaflow.app.data.academic.dayStart(it.dateTimestamp) == day
                        }.sumOf { it.amount }
                    }
                }
                val outlook = remember(dailyTotals, forecastVersion) {
                    com.pesaflow.app.data.ml.MlEngine.forecastWeek(dailyTotals)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Next 7 days: KSh ${outlook.low.toInt()}–${outlook.high.toInt()} (${outlook.basis}; rev $forecastVersion).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Semester runway lens: dynamic horizon to term end with full
            // unpaid-bill reserves (see semesterRunway). Numbers only — the
            // alert tiers speak when there is something to say.
            val runway = remember(transactions, bills) {
                val balance = com.pesaflow.app.data.money.ledgerBalance(transactions)
                semesterRunway(transactions, bills, balance, System.currentTimeMillis())
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Semester runway: KSh ${runway.dailyToSemesterEnd.toInt()}/day over ${runway.daysLeft}d" +
                    (runway.runwayDays?.let { " · cash lasts ~${it}d at your pace" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            runway.alert?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
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
