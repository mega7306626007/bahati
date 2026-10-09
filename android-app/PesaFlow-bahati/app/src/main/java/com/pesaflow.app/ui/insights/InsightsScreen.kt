package com.pesaflow.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.analytics.HistoricalTrendLineChart
import com.pesaflow.app.ui.analytics.MetricDistributionDonutChart
import com.pesaflow.app.ui.budgets.parsePersona
import com.pesaflow.app.ui.dashboard.SmartInsightsCard
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintInsightsGraph
import com.pesaflow.app.viewmodels.FinanceViewModel


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InsightsScreen(viewModel: FinanceViewModel, onNameMerchant: (String) -> Unit = {}) {
    val transactions by viewModel.allTransactions.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val snapshot by viewModel.financialSnapshot.collectAsState()
    val nlpInputText by viewModel.nlpInputText.collectAsState()
    val extractedNlp by viewModel.extractedNlpTransaction.collectAsState()
    var chartFilter by remember { mutableStateOf<String?>(null) }
    var velocityDays by remember { mutableStateOf(14) }


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
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (nlpInputText.isNotBlank()) viewModel.parseAndProcessNlp()
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.parseAndProcessNlp() },
                            enabled = nlpInputText.isNotBlank(),
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
                                    Text(
                                        "Sure: ${(tx.confidenceScore * 100).toInt()}%" + (if (tx.confidenceScore < 0.75f) " — double-check the category" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.LARGE) {
                    Column(verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.sm)) {
                        com.pesaflow.app.ui.theme.PpSectionHeader(
                            title = "Spending footprint",
                            subtitle = "Tap a slice label to highlight it"
                        )
                        val distribution = remember(transactions) {
                            transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample }.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }
                        }
                        if (distribution.isNotEmpty()) {
                            MetricDistributionDonutChart(
                                dataPoints = distribution,
                                selectedCategory = chartFilter,
                                onSelectCategory = { chartFilter = if (chartFilter == it) null else it }
                            )
                            // Contact cards for categorization: who is behind
                            // the selected slice (else the biggest one), with
                            // totals and counts. Naming one teaches the
                            // engine — future rows file themselves.
                            val focusCat = chartFilter
                                ?: distribution.maxByOrNull { it.value }?.key
                            if (focusCat != null) {
                                val leaders = remember(transactions, focusCat) {
                                    transactions.filter {
                                        it.type == TransactionType.EXPENSE && !it.isSample &&
                                            it.category.equals(focusCat, ignoreCase = true)
                                    }.groupBy { it.merchant.trim().ifBlank { "Unknown" } }
                                        .mapValues { (_, rows) -> rows.sumOf { it.amount } to rows.size }
                                        .toList().sortedByDescending { it.second.first }.take(3)
                                }
                                if (leaders.isNotEmpty()) {
                                    Text(
                                        "Top in $focusCat — name one and future rows file themselves:",
                                        style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                                        color = com.pesaflow.app.ui.theme.ppColors.textTertiary
                                    )
                                    leaders.forEach { (merchant, totalCount) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "$merchant · KSh ${totalCount.first.toInt()} · ${totalCount.second}×",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1
                                            )
                                            TextButton(onClick = { onNameMerchant(merchant) }) {
                                                Text("Name", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text("Add transactions to generate interactive graphs.", style = com.pesaflow.app.ui.theme.ppTypography.bodySmall, color = com.pesaflow.app.ui.theme.ppColors.textTertiary, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
            item {
                com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.LARGE) {
                    Column(verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.sm)) {
                        com.pesaflow.app.ui.theme.PpSectionHeader(
                            title = "Velocity trend",
                            subtitle = "Daily spend, oldest → newest (equal days, no fake spikes)"
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 14, 30).forEach { d ->
                                FilterChip(
                                    selected = velocityDays == d,
                                    onClick = { velocityDays = d },
                                    label = { Text("$d days") }
                                )
                            }
                        }
                        val linePoints = remember(transactions, velocityDays) {
                            com.pesaflow.app.ui.analytics.dailySpendSeries(transactions, velocityDays)
                        }
                        if (linePoints.sum() > 0) {
                            HistoricalTrendLineChart(
                                points = linePoints,
                                chartDescription = "Daily spending, last $velocityDays days, oldest to newest. Total KSh ${linePoints.sum().toInt()}."
                            )
                        } else {
                            Text("No spending in the last $velocityDays days — log an expense and the trend draws itself.", style = com.pesaflow.app.ui.theme.ppTypography.bodySmall, color = com.pesaflow.app.ui.theme.ppColors.textTertiary)
                        }
                    }
                }
            }

            item {
                com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.LARGE) {
                    Column(verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.sm)) {
                        com.pesaflow.app.ui.theme.PpSectionHeader(
                            title = "Spend by SIM",
                            subtitle = "Which line is burning the money"
                        )
                        val simSpend = remember(transactions) {
                            transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.simSlot >= 0 }
                                .groupBy { it.simSlot }
                                .mapValues { (_, rows) -> rows.sumOf { it.amount } to rows.size }
                        }
                        if (simSpend.isEmpty()) {
                            Text("No dual-SIM transactions yet — approve M-Pesa rows to see per-line spend.", style = com.pesaflow.app.ui.theme.ppTypography.bodySmall, color = com.pesaflow.app.ui.theme.ppColors.textTertiary)
                        } else {
                            val totalSim = simSpend.values.sumOf { it.first }
                            simSpend.toSortedMap().forEach { (slot, pair) ->
                                val spent = pair.first
                                val count = pair.second
                                val pct = if (totalSim > 0) (spent / totalSim * 100).toInt() else 0
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("SIM ${slot + 1}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("KSh ${spent.toInt()} · $count txns · $pct%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { if (totalSim > 0) (spent / totalSim).toFloat() else 0f },
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                    )
                                }
                            }
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
                    incomeSources = viewModel.incomeSources.collectAsState().value,
                    // Real persona, not the HOSTEL_COOK default: far commuters
                    // must never hear "walk", non-cooks never hear "cook".
                    persona = parsePersona(viewModel.getOnboardingAnswers()),
                    heldBalance = snapshot.liquid.toDouble()
                )
            }
            }
        }
    }
}
