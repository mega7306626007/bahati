package com.pesaflow.app.ui.recurring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.analytics.*
import com.pesaflow.app.ui.theme.PesaWarningContainer

@Composable
fun RecurringScreen(
    transactions: List<Transaction>,
    onMakeBill: (RecurringPattern) -> Unit = {}
) {
    val preview = remember(transactions) {
        buildRecurringPreview(transactions)
    }
    // Session dismissals: uncertain patterns the user rejects stay
    // hidden until the next scan recomputes them. Never auto-confirmed.
    var dismissed by remember { mutableStateOf(setOf<String>()) }
    fun dismissKey(p: RecurringPattern) = p.merchant + "|" + p.amount.toInt().toString()
    val livePatterns = remember(preview, dismissed) {
        preview.patterns.filter { dismissKey(it) !in dismissed }
    }
    val liveAtRisk = remember(preview, dismissed) {
        preview.atRisk.filter { dismissKey(it) !in dismissed }
    }
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("🔄 Recurring Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${preview.count} patterns · KSh ${preview.totalMonthlyCommitment.toInt()}/mo total", style = MaterialTheme.typography.bodySmall)
        }
        if (preview.patterns.isEmpty() && preview.atRisk.isEmpty()) {
            item {
                com.pesaflow.app.ui.theme.PesaEmptyState(
                    title = "No subscriptions found yet",
                    explanation = "Pay for Netflix, chama or insurance twice and the rhythm shows up here — then one tap makes it a bill."
                )
            }
        }
        // Confidence-ordered already (engine sorts desc). High-confidence
        // patterns get one decisive action; anything shaky must be opened
        // and verified first — never one-tapped into a bill.
        items(livePatterns) { pattern ->
            var billed by remember(pattern.merchant, pattern.amount) { mutableStateOf(false) }
            var showDetails by remember(pattern.merchant, pattern.amount) { mutableStateOf(false) }
            val high = pattern.confidence >= 0.75f
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (pattern.confidence < 0.5) PesaWarningContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(pattern.merchant, fontWeight = FontWeight.Bold)
                        Text("Confidence: ${"%.0f".format(pattern.confidence * 100)}%")
                    }
                    Text("KSh ${pattern.amount.toInt()} · every ${pattern.medianIntervalDays}d · ${pattern.occurrences}x")
                    if (high) {
                        Text(pattern.suggestion)
                        Text("Next expected: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(pattern.nextExpectedDate))}")
                    }
                    if (pattern.variance > 0.3) {
                        Text("⚠️ Irregular pattern — verify", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!billed) {
                        if (high) {
                            Button(onClick = {
                                onMakeBill(pattern)
                                billed = true
                            }) { Text("Confirm as recurring") }
                        } else {
                            TextButton(onClick = { showDetails = !showDetails }) {
                                Text(if (showDetails) "Hide details" else "Review details")
                            }
                        }
                        if (showDetails || high) {
                            if (!high) {
                                Text(pattern.suggestion)
                                Text("Next expected: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(pattern.nextExpectedDate))}")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!high) {
                                    TextButton(onClick = {
                                        onMakeBill(pattern)
                                        billed = true
                                    }) { Text("Make bill 📅") }
                                }
                                TextButton(onClick = { dismissed = dismissed + dismissKey(pattern) }) {
                                    Text("Not recurring", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    } else {
                        Text("Billed ✓ — find it under Bills.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (liveAtRisk.isNotEmpty()) {
            item {
                Text("⚠️ Irregular patterns to verify", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            items(liveAtRisk) { pattern ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = PesaWarningContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚠️ ${pattern.merchant} — ${pattern.occurrences}x with high variance", fontWeight = FontWeight.Bold)
                        Text("KSh ${pattern.amount.toInt()} · every ${pattern.medianIntervalDays}d")
                        TextButton(onClick = { dismissed = dismissed + dismissKey(pattern) }) {
                            Text("Not recurring", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
