package com.pesaflow.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.analytics.HistoricalTrendLineChart
import com.pesaflow.app.ui.analytics.MetricDistributionDonutChart
import com.pesaflow.app.ui.dashboard.SmartInsightsCard
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintInsightsGraph
import com.pesaflow.app.viewmodels.FinanceViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: FinanceViewModel) {
    val transactions by viewModel.allTransactions.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val availableBalance by viewModel.availableBalance.collectAsState()
    val nlpInputText by viewModel.nlpInputText.collectAsState()
    val extractedNlp by viewModel.extractedNlpTransaction.collectAsState()
    var chartFilter by remember { mutableStateOf<String?>(null) }


    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintInsightsGraph, bgRes = R.drawable.bg_insights_graph)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Insights 📊", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Color.Transparent).padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Natural Language Processing Input Box Section
            item {
                AtmosphereBand(
                    workspace = AtmoWorkspace.INSIGHTS,
                    title = "See clearly",
                    subtitle = "Charts · words · advice"
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Natural Language Input", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = nlpInputText,
                            onValueChange = { viewModel.nlpInputText.value = it },
                            placeholder = { Text("e.g. nimebuy lunch ya 250 mpesa") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.parseAndProcessNlp() },
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Parse Text", color = MaterialTheme.colorScheme.onPrimary)
                        }
                        if (extractedNlp == null && nlpInputText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Hiyo sijaelewa — include an amount and place, e.g. 'nimebuy lunch 250 mpesa'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }


                        extractedNlp?.let { tx ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Detected: ${tx.category} ${tx.type.name.lowercase()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text("Amount: KSh ${tx.amount}", color = MaterialTheme.colorScheme.onSurface)
                                    Text("Merchant/Scope: ${tx.merchant}", color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        TextButton(onClick = { viewModel.extractedNlpTransaction.value = null }) { Text("Cancel", color = Color.Red) }
                                        Button(onClick = { viewModel.commitExtractedNlp() }) { Text("Confirm Insertion") }
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // Visualization Analytics Charts Component Block
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Spending Footprint Breakdown · this month", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tap a slice label to highlight it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Month-scoped: an all-time donut billed every imported
                        // M-Pesa row as "your spending" — the split now answers
                        // "where did THIS month's money go?".
                        val distribution = remember(transactions) {
                            transactions.filter {
                                it.type == TransactionType.EXPENSE &&
                                    com.pesaflow.app.data.money.isCurrentMonth(it.dateTimestamp)
                            }.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }
                        }
                        if (distribution.isNotEmpty()) {
                            MetricDistributionDonutChart(
                                dataPoints = distribution,
                                selectedCategory = chartFilter,
                                onSelectCategory = { chartFilter = if (chartFilter == it) null else it }
                            )
                        } else {
                            Text("Add transactions to generate interactive graphs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Velocity Analytics Trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Your last 10 expenses, oldest → newest", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val linePoints = remember(transactions) { transactions.filter { it.type == com.pesaflow.app.data.models.TransactionType.EXPENSE }.take(10).map { it.amount }.reversed() }
                        if (linePoints.isNotEmpty()) {
                            HistoricalTrendLineChart(
                                points = linePoints,
                                chartDescription = "Spending trend, last 10 expenses, oldest to newest. Latest KSh ${linePoints.last().toInt()}."
                            )
                        } else {
                            Text("No spending yet — log an expense and the trend draws itself.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }


            // Smart Insights Engine Section (talks from real data)
            item {
                SmartInsightsCard(
                    transactions = transactions,
                    budgets = budgets,
                    lang = currentLanguage,
                    name = userName,
                    bills = bills,
                    debts = debts,
                    goals = savingsGoals,
                    balance = availableBalance
                )
            }
            // On-device forecast + ranked meals (spec §15: recomputes on correction).
            // Numbers come from the ledger; ML returns ranges/suggestions only.
            item {
                val forecastVersion by viewModel.forecastVersion.collectAsState()
                val feedback by viewModel.modelFeedback.collectAsState()
                val dayMs = 24L * 60 * 60 * 1000
                val dailyTotals = remember(transactions) {
                    val today = com.pesaflow.app.data.academic.dayStart(System.currentTimeMillis())
                    (13 downTo 0).map { back ->
                        val day = today - back * dayMs
                        transactions.filter {
                            it.type == TransactionType.EXPENSE &&
                                com.pesaflow.app.data.academic.dayStart(it.dateTimestamp) == day
                        }.sumOf { it.amount }
                    }
                }
                val forecast = remember(transactions, forecastVersion) {
                    com.pesaflow.app.data.ml.MlEngine.forecastWeek(dailyTotals)
                }
                val rankedMeals = remember(availableBalance) {
                    val budget = 200.0
                    com.pesaflow.app.data.ml.MlEngine.affordableMeals(budget)
                        .map { meal ->
                            com.pesaflow.app.data.ml.RecommendationRanker.Option(
                                id = "${meal.item} @ ${meal.venue}",
                                costKes = meal.priceKes,
                                distanceM = null,
                                preferenceMatch = 0.5,
                                sourceReliability = if (meal.verified.contains("VERIFIED")) 0.9 else 0.5
                            )
                        }
                        .let { com.pesaflow.app.data.ml.MlEngine.rankOptions(it, budget) }
                        .take(3)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("7-day spending forecast", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "KSh ${forecast.low.toInt()} – ${forecast.high.toInt()} (${forecast.basis}; rev $forecastVersion)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Corrections recalculate this band — stale forecasts are never shown as truth. Feedback logged: ${feedback.size}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Top meals within KSh 200", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (rankedMeals.isEmpty()) {
                            Text(
                                "No verified options under budget — raise the demo budget, not the facts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            rankedMeals.forEach { ranked ->
                                Text(
                                    "• ${ranked.id} — ${ranked.explanation}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        // Anomaly watch: latest expense vs its own category history
                        // (MAD z-score, cold-start safe — needs 4+ samples).
                        val anomaly = remember(transactions) {
                            val latest = transactions
                                .filter { it.type == TransactionType.EXPENSE }
                                .maxByOrNull { it.dateTimestamp } ?: return@remember null
                            val history = transactions
                                .filter {
                                    it.type == TransactionType.EXPENSE &&
                                        it.category == latest.category && it.id != latest.id
                                }.map { it.amount }
                            val verdict = runCatching {
                                com.pesaflow.app.data.ml.MlEngine.checkAnomaly(latest.amount, history)
                            }.getOrNull() ?: return@remember null
                            if (verdict.isAnomaly) latest to verdict else null
                        }
                        anomaly?.let { (tx, verdict) ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Unusual: KSh ${tx.amount.toInt()} at ${tx.merchant} — ${verdict.reason}.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            }
        }
    }
}
