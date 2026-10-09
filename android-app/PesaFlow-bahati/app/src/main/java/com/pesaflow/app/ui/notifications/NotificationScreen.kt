package com.pesaflow.app.ui.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.notifications.*
import com.pesaflow.app.data.models.*

@Composable
fun NotificationCenter(
    events: List<NotificationEvent>,
    dismissedKeys: Set<String> = emptySet(),
    onDismiss: (NotificationEvent) -> Unit = {}
) {
    val visible = events.filter { eventKey(it) !in dismissedKeys }
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("Notifications (${visible.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(visible) { event ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (event.priority) {
                        NotificationPriority.URGENT -> com.pesaflow.app.ui.theme.PesaDangerContainer
                        NotificationPriority.HIGH -> com.pesaflow.app.ui.theme.PesaWarningContainer
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(event.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(event.priority.name, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(event.body, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onDismiss(event) }) { Text("Dismiss") }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

fun eventKey(event: NotificationEvent): String =
    "${event.type}:${event.title}:${event.timestamp}"

@Composable
fun NotificationChecker(
    engine: NotificationEngine = NotificationEngine(),
    bills: List<Bill> = emptyList(),
    debts: List<Debt> = emptyList(),
    budgets: List<Budget> = emptyList(),
    goals: List<SavingsGoal> = emptyList(),
    spentByCategory: Map<String, Double> = emptyMap(),
    todaySpend: Double = 0.0,
    yesterdaySpend: Double = 0.0,
    dailyTarget: Double = 500.0,
    weekdayFactor: Double = 1.0,
    lastReviewDay: String? = null,
    rentDayOfMonth: Int = 1,
    now: Long = System.currentTimeMillis(),
    onEvents: (List<NotificationEvent>) -> Unit = {}
) {
    var dismissed by remember { mutableStateOf(setOf<String>()) }
    val events = remember(bills, debts, budgets, goals, spentByCategory, todaySpend, yesterdaySpend, dailyTarget, weekdayFactor, now) {
        engine.evaluateAll(
            bills = bills, debts = debts, budgets = budgets,
            spentByCategory = spentByCategory, goals = goals,
            todaySpend = todaySpend, yesterdaySpend = yesterdaySpend,
            dailyTarget = dailyTarget, weekdayFactor = weekdayFactor,
            lastReviewDay = lastReviewDay, rentDayOfMonth = rentDayOfMonth,
            now = now
        )
    }
    LaunchedEffect(events) { onEvents(events) }
    NotificationCenter(
        events = events,
        dismissedKeys = dismissed,
        onDismiss = { dismissed = dismissed + eventKey(it) }
    )
}
