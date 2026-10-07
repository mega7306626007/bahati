package com.pesaflow.app.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Context
import com.pesaflow.app.data.academic.buildSpendProfile
import com.pesaflow.app.data.academic.needsSemesterReset
import com.pesaflow.app.data.academic.parseClassDays
import com.pesaflow.app.data.academic.parseYearOfStudy
import com.pesaflow.app.data.academic.priorAcademicWindow
import com.pesaflow.app.data.academic.scanWindow
import com.pesaflow.app.data.schedule.WeekPlan


@Composable
fun SmartInsightsCard(
    transactions: List<com.pesaflow.app.data.models.Transaction>,
    budgets: List<com.pesaflow.app.data.models.Budget>,
    lang: com.pesaflow.app.data.models.AppLanguage,
    name: String,
    bills: List<com.pesaflow.app.data.models.Bill>,
    debts: List<com.pesaflow.app.data.models.Debt>,
    goals: List<com.pesaflow.app.data.models.SavingsGoal>,
    group: com.pesaflow.app.ui.budgets.LivingSituation = com.pesaflow.app.ui.budgets.LivingSituation.HOSTEL_COOK,
    balance: Double = 0.0
) {
    val context = LocalContext.current
    // Class days from onboarding count as busy school days until the user
    // pastes a real timetable (WeekPlan.suggestFromText) — busy-vs-free
    // insights work from day one instead of waiting.
    val answers = remember {
        try {
            context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
                .getString("onboarding_answers", "").orEmpty()
        } catch (e: Exception) {
            ""
        }
    }
    // Latest Sunday check-in (if any) wins over onboarding chips: a 5-second
    // tap corrects the busy-day map the insights read from.
    val checkin = remember {
        try {
            val p = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
            p.all.keys.filter { it.startsWith("checkin_days_") }.maxOrNull()
                ?.let { p.getString(it, "").orEmpty().split(",").map { d -> d.trim() }.filter { it.isNotBlank() }.toSet() }
                ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }
    val weekPlan = remember {
        val stored = WeekPlan.load(context)
        stored + parseClassDays(answers).associateWith { d ->
            stored[d].orEmpty() + setOf(WeekPlan.MORNING, WeekPlan.AFTERNOON)
        } + checkin.associateWith { d ->
            stored[d].orEmpty() + setOf(WeekPlan.MORNING, WeekPlan.AFTERNOON)
        }
    }
    // Semester diff for returning students: current window vs last year's
    // Sep–Apr rows from the full ledger. Silent without prior history.
    val diffLines = remember(transactions, answers, lang) {
        if (parseYearOfStudy(answers) <= 1) emptyList()
        else {
            val now = System.currentTimeMillis()
            val prevWin = priorAcademicWindow(now)
            val prevRows = transactions.filter { it.dateTimestamp in prevWin.startMs until prevWin.endMs }
            if (prevRows.isEmpty()) emptyList()
            else {
                val curWin = scanWindow(parseYearOfStudy(answers), "", now)
                val curRows = transactions.filter { it.dateTimestamp in curWin.startMs until curWin.endMs }
                semesterDiffLines(buildSpendProfile(curRows, curWin), buildSpendProfile(prevRows, prevWin), lang)
            }
        }
    }
    val insights = remember(transactions, budgets, lang, name, bills, debts, goals, weekPlan, group, answers, checkin, balance, diffLines) {
        val base = buildInsights(transactions, budgets, lang, name, bills, debts, goals, weekPlan, group, balance).toMutableList()
        if (needsSemesterReset(answers)) base.add(0, semesterResetText(lang))
        base.addAll(diffLines)
        base.toList()
    }
    if (insights.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Smart Insights 💡", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            insights.forEach { insight ->
                Text("• $insight", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
