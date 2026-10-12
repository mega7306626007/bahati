package com.pesaflow.app.ui.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.SavingsGoal
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.analytics.*
import com.pesaflow.app.ui.theme.PesaWarningContainer

@Composable
fun GoalsScreen(
    goals: List<SavingsGoal>,
    transactions: List<Transaction>
) {
    val projections = remember(goals, transactions) {
        projectAllGoals(goals, transactions)
    }
    val suggestion = remember(goals, transactions) {
        suggestNewGoal(transactions, goals)
    }
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("🎯 Savings Goals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (suggestion != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("💡 Suggested Goal", fontWeight = FontWeight.Bold)
                        Text(suggestion, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        items(projections) { proj ->
            val color = when (proj.riskLevel) {
                GoalProjection.RiskLevel.ON_TRACK -> MaterialTheme.colorScheme.primary
                GoalProjection.RiskLevel.AT_RISK -> MaterialTheme.colorScheme.secondary
                GoalProjection.RiskLevel.DANGER -> MaterialTheme.colorScheme.error
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(proj.title, fontWeight = FontWeight.Bold)
                        Text("${proj.progressPercent}%", fontWeight = FontWeight.Bold, color = color)
                    }
                    Text("KSh ${proj.currentAmount.toInt()} of KSh ${proj.targetAmount.toInt()}")
                    Text("Remaining: KSh ${proj.remaining.toInt()} (${proj.daysToTarget}d)")
                    Text("Daily pace: KSh ${proj.currentDailyPace.toInt()} (need KSh ${proj.dailySavingsRequired.toInt()})")
                    Text("Risk: ${proj.riskLevel.name}", color = color, fontWeight = FontWeight.Bold)
                    proj.suggestions.forEach { s ->
                        Text("• $s", style = MaterialTheme.typography.bodySmall)
                    }
                    proj.categoryCuts.forEach { cut ->
                        Text("🔪 Trim ${cut.category} by KSh ${cut.amount.toInt()}/week", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
