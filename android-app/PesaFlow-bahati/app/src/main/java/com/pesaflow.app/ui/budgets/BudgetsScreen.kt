package com.pesaflow.app.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.ui.theme.InfoBlue
import com.pesaflow.app.data.income.IncomeSourceStore
import com.pesaflow.app.data.models.*
import com.pesaflow.app.ui.analytics.BudgetRing
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.ExplainChip
import com.pesaflow.app.ui.theme.TintBudgetsJar
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private val PERIOD_TABS = listOf("Daily", "Weekly", "Monthly", "Semester")


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(viewModel: FinanceViewModel, onAskBuddy: () -> Unit = {}) {
    val budgets by viewModel.budgets.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val lang by viewModel.currentLanguage.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val monthlyIncome by viewModel.monthlyIncome.collectAsState()
    val financialSnapshot by viewModel.financialSnapshot.collectAsState()
    val incomeSources by viewModel.incomeSources.collectAsState()
    val allBills by viewModel.bills.collectAsState()
    var tab by remember { mutableStateOf("Monthly") }
    // Prefill once from detected income (ledger month, else declared
    // sources) so the calculator never opens blank. Anything typed wins.
    val detectedBase = monthlyIncome.takeIf { it > 0 }
        ?: viewModel.incomeSources.collectAsState().value.sumOf { com.pesaflow.app.data.income.IncomeSourceStore.budgetedMonthly(it) }.takeIf { it > 0 }
    var calcIncome by remember(detectedBase) { mutableStateOf(detectedBase?.toInt()?.toString() ?: "") }
    var calcRule by remember { mutableStateOf("Student") }
    var calcPeriod by remember { mutableStateOf(BudgetType.MONTHLY) }
    var calcCashBasis by remember { mutableStateOf(false) }
    var calcStyle by remember { mutableStateOf(LifestylePreset.BALANCED) }
    // Proof tick for Apply: brief ✓ that reverts so re-apply stays possible.
    val ackScope = rememberCoroutineScope()
    var acked by remember { mutableStateOf(setOf<String>()) }
    fun ack(key: String) {
        acked = acked + key
        ackScope.launch { kotlinx.coroutines.delay(2000); acked = acked - key }
    }
    var autoDaily by remember { mutableStateOf(true) }
    var personaSel by remember { mutableStateOf(parsePersona(viewModel.getOnboardingAnswers())) }
    // Watchlist: pinned envelopes report first in Smart Insights.
    val watchPrefs = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
    var watched by remember { mutableStateOf(com.pesaflow.app.data.ledger.Watchlist.read(watchPrefs)) }
    val haptics = LocalHapticFeedback.current
    var showAddDialog by remember { mutableStateOf(false) }
    var showPlanDialog by remember { mutableStateOf(false) }
    var sharingBudget by remember { mutableStateOf<Budget?>(null) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }

    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    // Tab windows come from the central helper: calendar weeks, bounded
    // ranges, human labels — one definition for every budget surface.
    val (periodType, periodWindow, windowLabel) =
        com.pesaflow.app.data.finance.budgetTabWindow(tab, now)
    // ALL master wins over the category breakdown (never both summed).
    val target = com.pesaflow.app.data.finance.masterOrCategoryTotal(budgets, periodType)
    val spent = transactions
        .filter {
            it.type == TransactionType.EXPENSE && !it.isSample &&
                it.dateTimestamp in periodWindow && it.dateTimestamp <= now
        }
        .sumOf { it.amount }
    // Envelope carryover: last period's unspent rolls into this one —
    // monthly envelopes roll from last month, weekly from last week.
    val prevWindowStart = monthStartOf(monthStartOf(now) - 24L * 60 * 60 * 1000)
    val lastSpent = transactions
        .filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= prevWindowStart && it.dateTimestamp < periodWindow.startInclusive }
        .sumOf { it.amount }
    val prevWeekStart = periodWindow.startInclusive - 7 * day
    val lastWeekSpent = transactions
        .filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= prevWeekStart && it.dateTimestamp < periodWindow.startInclusive }
        .sumOf { it.amount }
    // Rollover is informational only: last period's unspent already sits in
    // the ledger balance. Baking it into the target doubled "total monthly
    // budget" on fresh months (nothing spent last month → +100% phantom).
    // The hero target is exactly what was set — nothing more.
    val carry = when (tab) {
        "Monthly" -> (target - lastSpent).coerceAtLeast(0.0)
        "Weekly" -> (target - lastWeekSpent).coerceAtLeast(0.0)
        else -> 0.0
    }
    val carryLabel = if (tab == "Weekly") "last week" else "last month"
    val left = target - spent

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintBudgetsJar, bgRes = R.drawable.bg_budgets_jar)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(com.pesaflow.app.ui.language.budT("title", lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
                actions = {
                    TextButton(onClick = onAskBuddy) {
                        Text(com.pesaflow.app.ui.language.budT("ask_buddy", lang), color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { showAddDialog = true }) {
                        Text(com.pesaflow.app.ui.language.budT("add", lang), color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Period tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PERIOD_TABS.forEach { p ->
                    FilterChip(
                        selected = tab == p,
                        onClick = { tab = p },
                        label = {
                            Text(
                                com.pesaflow.app.ui.language.budT(
                                    when (p) {
                                        "Daily" -> "tab_daily"
                                        "Weekly" -> "tab_weekly"
                                        "Semester" -> "tab_semester"
                                        else -> "tab_monthly"
                                    }, lang
                                )
                            )
                        }
                    )
                }
            }

            AtmosphereBand(
                workspace = AtmoWorkspace.BUDGET,
                title = com.pesaflow.app.ui.language.budT("env_title", lang),
                subtitle = com.pesaflow.app.ui.language.budT("env_sub", lang)
            )
            // Expense calculator: income in, suggested monthly budgets out
            ExplainChip(
                label = com.pesaflow.app.ui.language.budT("how_suggested", lang),
                body = com.pesaflow.app.ui.language.budT("how_suggested_body", lang)
            )
            run {
                val safetyBufferLabel = com.pesaflow.app.ui.language.budT("safety_buffer", lang)
                val budgetFooter = com.pesaflow.app.ui.language.budT("budget_footer", lang)
                com.pesaflow.app.ui.theme.ExplainableAmount(
                    amount = "KSh ${financialSnapshot.flexible.toDouble().toInt()}",
                    label = com.pesaflow.app.data.finance.FinancialVocabulary.FLEXIBLE,
                    provenance = com.pesaflow.app.data.finance.Provenance.CALCULATED,
                    breakdown = listOf(
                        com.pesaflow.app.data.finance.FinancialVocabulary.CURRENT_BALANCE to "KSh ${financialSnapshot.liquid.toDouble().toInt()}",
                        com.pesaflow.app.data.finance.FinancialVocabulary.COMMITTED to "KSh ${financialSnapshot.committed.toDouble().toInt()}",
                        safetyBufferLabel to "KSh ${financialSnapshot.riskBuffer.toDouble().toInt()}"
                    ),
                    footer = budgetFooter
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(com.pesaflow.app.ui.language.budT("calc_title", lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        com.pesaflow.app.ui.language.budT("calc_sub", lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val dailyNeed = financialSnapshot.essentialAhead.toDouble() / 30.0
                    val daysUntilIncome = com.pesaflow.app.data.income.nextInflowDay(incomeSources, now)
                    val cashRecommendation = recommendedBudgetPeriod(
                        financialSnapshot.flexible.toDouble(),
                        dailyNeed,
                        daysUntilIncome
                    )
                    val recommendationWindow = cashRecommendation?.takeIf {
                        it == BudgetType.DAILY || it == BudgetType.WEEKLY
                    }?.let { com.pesaflow.app.data.finance.budgetWindowRange(it, now) }
                    val calendarDaysRemaining = recommendationWindow?.let {
                        kotlin.math.ceil((it.endExclusive - now).coerceAtLeast(0L).toDouble() / day)
                            .toInt().coerceAtLeast(1)
                    }
                    val recommendationDays = calendarDaysRemaining?.let { calendarDays ->
                        val periodDays = if (cashRecommendation == BudgetType.DAILY) 1 else calendarDays
                        minOf(periodDays, daysUntilIncome?.coerceAtLeast(1) ?: periodDays)
                    }
                    val recommendationSpent = recommendationWindow?.let { window ->
                        transactions.filter {
                            it.type == TransactionType.EXPENSE && !it.isSample &&
                                it.dateTimestamp in window && it.dateTimestamp <= now
                        }.sumOf { it.amount }
                    } ?: 0.0
                    val recommendedCash = recommendationDays?.let { safeDays ->
                        (recommendationSpent + minOf(
                            financialSnapshot.flexible.toDouble(),
                            dailyNeed * safeDays
                        )).toInt().coerceAtLeast(0)
                    }
                    if (cashRecommendation == BudgetType.DAILY || cashRecommendation == BudgetType.WEEKLY) {
                        Text(
                            com.pesaflow.app.ui.language.budT(
                                "cash_advice", lang,
                                financialSnapshot.flexible.toDouble().toInt().toString(),
                                recommendationSpent.toInt().toString(),
                                dailyNeed.toInt().toString(),
                                (recommendationDays ?: 1).toString(),
                                daysUntilIncome?.let { com.pesaflow.app.ui.language.budT("cash_in_days", lang, it.toString()) } ?: ""
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                calcPeriod = cashRecommendation
                                calcIncome = (recommendedCash ?: 0).toString()
                                calcCashBasis = true
                            },
                            enabled = (recommendedCash ?: 0) > 0
                        ) {
                            Text(
                                if (cashRecommendation == BudgetType.DAILY) com.pesaflow.app.ui.language.budT("plan_today_cash", lang)
                                else com.pesaflow.app.ui.language.budT("plan_week_cash", lang)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = calcIncome,
                        onValueChange = { calcIncome = it },
                        label = { Text(if (calcCashBasis) com.pesaflow.app.ui.language.budT("income_label_period", lang) else com.pesaflow.app.ui.language.budT("income_label_month", lang)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Student", "50/30/20").forEach { r ->
                            FilterChip(selected = (if (r == "Student") calcRule == "Student" else calcRule != "Student"), onClick = { calcRule = r }, label = { Text(if (r == "Student") com.pesaflow.app.ui.language.budT("rule_campus", lang) else r) })
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(com.pesaflow.app.ui.language.budT("setup_title", lang), style = MaterialTheme.typography.labelLarge)
                    Text(
                        com.pesaflow.app.ui.language.budPersona(personaSel.name, lang, blurb = true),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Persona.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { p ->
                                FilterChip(selected = personaSel == p, onClick = { personaSel = p }, label = { Text(com.pesaflow.app.ui.language.budPersona(p.name, lang)) })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(BudgetType.DAILY, BudgetType.WEEKLY, BudgetType.MONTHLY, BudgetType.SEMESTER, BudgetType.ANNUAL).forEach { t ->
                            FilterChip(
                                selected = calcPeriod == t,
                                onClick = {
                                    if (calcCashBasis) calcIncome = detectedBase?.toInt()?.toString() ?: ""
                                    calcPeriod = t
                                    calcCashBasis = false
                                },
                                label = { Text(t.name.take(5)) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(com.pesaflow.app.ui.language.budT("lifestyle_title", lang), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LifestylePreset.entries.forEach { style ->
                            FilterChip(selected = calcStyle == style, onClick = { calcStyle = style }, label = { Text(com.pesaflow.app.ui.language.budLifestyle(style.name, lang)) })
                        }
                    }
                    Text(
                        when (calcStyle) {
                            LifestylePreset.COMMUTER_LITE -> com.pesaflow.app.ui.language.budT("life_commuter", lang)
                            LifestylePreset.FOODIE -> com.pesaflow.app.ui.language.budT("life_foodie", lang)
                            LifestylePreset.SAVER -> com.pesaflow.app.ui.language.budT("life_saver", lang)
                            else -> com.pesaflow.app.ui.language.budT("life_balanced", lang)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (calcPeriod == BudgetType.DAILY && !calcCashBasis) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.pesaflow.app.ui.language.budT("auto_monthly", lang), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(com.pesaflow.app.ui.language.budT("auto_monthly_sub", lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = autoDaily, onCheckedChange = { autoDaily = it })
                        }
                    }
                    // Same master-wins rule: daily auto = ALL ÷ 30, not (ALL + cats) ÷ 30.
                    val monthlyBudgetsTotal = com.pesaflow.app.data.finance.masterOrCategoryTotal(budgets, BudgetType.MONTHLY)
                    // Expected income from More → Income (HELB, parents, hustle...) backs the
                    // base when the ledger is still empty early in the month.
                    val expectedIncome = incomeSources.sumOf { com.pesaflow.app.data.income.IncomeSourceStore.budgetedMonthly(it) }.takeIf { it > 0 }
                    val enteredBase = calcIncome.toDoubleOrNull()?.takeIf { it > 0 }
                    val calcBase = if (calcCashBasis) {
                        enteredBase?.div(periodScale(calcPeriod))
                    } else if (calcPeriod == BudgetType.DAILY && autoDaily && monthlyBudgetsTotal > 0) {
                        monthlyBudgetsTotal
                    } else {
                        enteredBase ?: monthlyIncome.takeIf { it > 0 } ?: expectedIncome
                    }
                    if (calcBase != null) {
                        // Rebudget nudge: income moved >25% since the saved
                        // monthly envelope — one tap recalculates, never auto.
                        val monthlyAllLimit = budgets.firstOrNull { it.category == "ALL" && it.type == BudgetType.MONTHLY }?.limitAmount ?: 0.0
                        if (!calcCashBasis && monthlyAllLimit > 0 && kotlin.math.abs(calcBase - monthlyAllLimit) / monthlyAllLimit > 0.25) {
                            TextButton(onClick = { calcIncome = calcBase.toInt().toString() }) {
                                Text(com.pesaflow.app.ui.language.budT("income_moved", lang, calcBase.toInt().toString()))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val nowMs = System.currentTimeMillis()
                        val openBills = allBills.filter { it.status != "PAID" && it.paidBy == "ME" }
                        // Reserve this month's share of each bill, using the
                        // outstanding remainder rather than the original total.
                        val billMap = openBills.groupBy { it.category.lowercase() }
                            .mapValues { e -> e.value.sumOf { monthlyBillReserve(it, nowMs) } }
                        val avgMapLedger = transactions.filter {
                            it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= nowMs - 90L * 24 * 60 * 60 * 1000
                        }.groupBy { it.category.lowercase() }
                            .mapValues { e -> (e.value.sumOf { it.amount } / 3 * periodScale(calcPeriod)).toInt() }
                        // Cold-start seed: onboarding guesses (rent, daily
                        // transport, weekly airtime) stand in where the ledger
                        // has no 90-day history yet. Ledger wins on conflict.
                        val guessAvg = remember {
                            val a = viewModel.getOnboardingAnswers()
                            fun num(key: String) = a.split("|").firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.toDoubleOrNull()?.takeIf { it > 0 }
                            buildMap<String, Int> {
                                num("rent")?.let { put("rent", it.toInt()) }
                                num("transport")?.let { put("transport", (it * 30).toInt()) }
                                num("airtime")?.let { put("airtime", (it * 4).toInt()) }
                            }
                        }
                        val avgMap = guessAvg + avgMapLedger
                        val rule = if (calcRule == "Student") BudgetRule.CAMPUS else BudgetRule.SPLIT
                        // Declared envelopes (matatu preset, onboarding, manual):
                        // a number the user already set is a promise the plan keeps.
                        val declaredMap = budgets.filter { it.type == BudgetType.MONTHLY }
                            .groupBy { it.category.lowercase() }
                            .mapValues { e -> e.value.sumOf { it.limitAmount }.toInt() }
                        val plan = smartBudget(
                            monthlyBase = calcBase,
                            rule = rule,
                            period = calcPeriod,
                            persona = personaSel,
                            openBillByCategory = billMap,
                            avg90ByCategory = avgMap,
                            style = calcStyle,
                            declaredByCategory = if (calcCashBasis) emptyMap() else declaredMap,
                            periodBudgetCap = if (calcCashBasis) enteredBase else null,
                            lang = lang
                        )
                        // Daily auto mode still derives from monthly envelopes when present.
                        val periodName = plan.periodName
                        val prioritized: List<Triple<String, Int, Int>> = plan.suggestions.map { s ->
                            Triple(s.category, s.amount, avgMap[s.category.lowercase()] ?: 0)
                        }
                        val dropped: List<String> = plan.dropped
                        if (plan.tightMode) {
                            Text(
                                com.pesaflow.app.ui.language.budT("tight_mode", lang),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            if (calcCashBasis) {
                                com.pesaflow.app.ui.language.budT("cash_plan", lang, (enteredBase?.toInt() ?: 0).toString())
                            } else plan.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val plannedTotal = plan.suggestions.sumOf { it.amount }
                        val periodBase = if (calcCashBasis) enteredBase ?: 0.0 else calcBase * periodScale(calcPeriod)
                        if (plannedTotal > periodBase) {
                            Text(
                                com.pesaflow.app.ui.language.budT("fund_gap", lang, (plannedTotal - periodBase).toInt().toString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (dropped.isNotEmpty()) {
                            Text(
                                com.pesaflow.app.ui.language.budT("paused_for", lang, dropped.joinToString(", ")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        plan.suggestions.forEach { s ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        s.category + if (s.shielded) " 🛡️" else "",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "KSh ${s.amount} · ${s.percent}%",
                                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    s.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val applySig = "$calcRule|$calcPeriod|$calcBase|$autoDaily|$personaSel|${prioritized.size}"
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.applyCalculatedBudgets(prioritized.map { (cat, amt, _) -> cat to amt.toDouble() }, calcPeriod)
                                ack(applySig)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !calcCashBasis || plannedTotal <= periodBase.toInt(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            val periodWord = com.pesaflow.app.ui.language.budT(
                                when (calcPeriod) {
                                    BudgetType.DAILY -> "tab_daily"
                                    BudgetType.WEEKLY -> "tab_weekly"
                                    BudgetType.SEMESTER -> "tab_semester"
                                    BudgetType.ANNUAL -> "tab_annual"
                                    else -> "tab_monthly"
                                }, lang
                            ).lowercase()
                            Text(
                                if (applySig in acked) com.pesaflow.app.ui.language.budT("applied", lang, prioritized.size.toString())
                                else com.pesaflow.app.ui.language.budT("apply_as", lang, periodWord),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(com.pesaflow.app.ui.language.budT("log_income", lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Period hero card
            com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.LARGE) {
                Column(verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.sm)) {
                    Text(
                        com.pesaflow.app.ui.language.budT(
                            "hero_line", lang,
                            com.pesaflow.app.ui.language.budT(when (tab) { "Daily" -> "tab_daily" "Weekly" -> "tab_weekly" "Semester" -> "tab_semester" else -> "tab_monthly" }, lang),
                            windowLabel
                        ),
                        style = com.pesaflow.app.ui.theme.ppTypography.labelLarge, color = com.pesaflow.app.ui.theme.ppColors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (target <= 0) {
                        var quickAmount by remember(tab) { mutableStateOf("") }
                        Text(
                            com.pesaflow.app.ui.language.budT(
                                "no_budget", lang,
                                com.pesaflow.app.ui.language.budT(when (tab) { "Daily" -> "tab_daily" "Weekly" -> "tab_weekly" "Semester" -> "tab_semester" else -> "tab_monthly" }, lang)
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            com.pesaflow.app.ui.language.budT(
                                "set_limit_hint", lang, windowLabel, spent.toInt().toString(),
                                com.pesaflow.app.ui.language.budT(when (tab) { "Daily" -> "tab_daily" "Weekly" -> "tab_weekly" "Semester" -> "tab_semester" else -> "tab_monthly" }, lang)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = quickAmount,
                                onValueChange = { quickAmount = it },
                                label = { Text(com.pesaflow.app.ui.language.budT("ksh_limit", lang)) },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    quickAmount.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                        // Upsert, never stack: repeated Sets replace
                                        // the ALL row instead of doubling the target.
                                        viewModel.upsertBudget("ALL", it, periodType)
                                        quickAmount = ""
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) { Text(com.pesaflow.app.ui.language.budT("set_btn", lang), color = MaterialTheme.colorScheme.onPrimary) }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BudgetRing(fraction = if (target > 0) (spent / target).toFloat() else 0f)
                            Spacer(modifier = Modifier.width(20.dp))
                            Column {
                                Text(
                                    "KSh ${left.toInt()}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    com.pesaflow.app.ui.language.budT("left_of", lang, target.toInt().toString()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (carry > 0) {
                                    Text(
                                        com.pesaflow.app.ui.language.budT(
                                            "carry", lang, carry.toInt().toString(),
                                            if (tab == "Weekly") com.pesaflow.app.ui.language.budT("last_week", lang)
                                            else com.pesaflow.app.ui.language.budT("last_month", lang)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        com.pesaflow.app.ui.theme.PpProgress(
                            fraction = (spent / target).toFloat(),
                            kind = if (spent >= target) com.pesaflow.app.ui.theme.PpProgressKind.ERROR else com.pesaflow.app.ui.theme.PpProgressKind.GOLD
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            com.pesaflow.app.data.finance.periodVerdict(
                                tab, spent, target,
                                java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH),
                                java.util.Calendar.getInstance().getActualMaximum(java.util.Calendar.DAY_OF_MONTH),
                                lang
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (spent > target) com.pesaflow.app.ui.theme.ppColors.error else com.pesaflow.app.ui.theme.ppColors.gold
                        )
                    }
                }
            }

            // Category budgets of this period
            val categoryBudgets = budgets.filter { it.type == periodType && it.category != "ALL" }
            if (categoryBudgets.isNotEmpty()) {
                com.pesaflow.app.ui.theme.PpSectionHeader(title = com.pesaflow.app.ui.language.budT("cat_budgets", lang, com.pesaflow.app.ui.language.budT(when (tab) { "Daily" -> "tab_daily" "Weekly" -> "tab_weekly" "Semester" -> "tab_semester" else -> "tab_monthly" }, lang)))
                categoryBudgets.forEach { budget ->
                    // Current-period progress per budget type (semester falls
                    // back to rolling 120d without a profile window).
                    val nowMs = System.currentTimeMillis()
                    val win = com.pesaflow.app.data.finance.budgetWindowRange(budget.type, nowMs)
                    val cSpent = com.pesaflow.app.data.finance.envelopeSpend(transactions, budget.category, win, nowMs)
                    val cLeft = (budget.limitAmount - cSpent).coerceAtLeast(0.0)
                    val cPct = (cSpent / budget.limitAmount * 100).coerceIn(0.0, 100.0)
                    com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.STANDARD) {
                        Column(verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.sm)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.foundation.layout.Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            androidx.compose.foundation.layout.Box(
                                                modifier = Modifier.size(10.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(com.pesaflow.app.ui.theme.categoryChartColor(budget.category))
                                            )
                                            Text(
                                                "${com.pesaflow.app.ui.theme.categoryEmoji(budget.category)} ${budget.category}",
                                                style = com.pesaflow.app.ui.theme.ppTypography.labelLarge,
                                                color = com.pesaflow.app.ui.theme.ppColors.textPrimary
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(onClick = {
                                                watched = com.pesaflow.app.data.ledger.Watchlist.toggle(watchPrefs, budget.category)
                                            }) {
                                                Text(
                                                    if (budget.category.lowercase() in watched) "★" else "☆",
                                                    color = com.pesaflow.app.ui.theme.ppColors.gold
                                                )
                                            }
                                            TextButton(onClick = { viewModel.deleteBudget(budget.id) }) {
                                                Text(com.pesaflow.app.ui.language.budT("remove", lang), style = com.pesaflow.app.ui.theme.ppTypography.labelMedium, color = com.pesaflow.app.ui.theme.ppColors.error)
                                            }
                                        }
                                    }
                            com.pesaflow.app.ui.theme.PpProgress(
                                fraction = (cSpent / budget.limitAmount).toFloat(),
                                kind = if (cSpent >= budget.limitAmount) com.pesaflow.app.ui.theme.PpProgressKind.ERROR else com.pesaflow.app.ui.theme.PpProgressKind.GOLD
                            )
                            Text(
                                if (cLeft > 0) com.pesaflow.app.ui.language.budT("bar_left", lang, cSpent.toInt().toString(), budget.limitAmount.toInt().toString(), cLeft.toInt().toString(), cPct.toInt().toString())
                                else com.pesaflow.app.ui.language.budT("bar_over", lang, cSpent.toInt().toString(), budget.limitAmount.toInt().toString(), (cSpent - budget.limitAmount).toInt().toString()),
                                style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                                color = com.pesaflow.app.ui.theme.ppColors.textTertiary
                            )
                            // Budget-vs-balance: a plan is not cash.
                            Text(
                                com.pesaflow.app.ui.language.budT("bar_note", lang, financialSnapshot.liquid.toDouble().toInt().toString()),
                                style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                                color = com.pesaflow.app.ui.theme.ppColors.textTertiary
                            )
                            // Parent fare allowance covers this envelope first.
                            if (budget.category.equals("Transport", ignoreCase = true)) {
                                incomeSources.firstOrNull {
                                    (it.kind == "PARENT" || it.kind == "GUARDIAN") &&
                                        (it.frequency == "DAILY" || it.frequency == "WEEKLY") && it.expectedAmount > 0
                                }?.let { fare ->
                                    val monthly = com.pesaflow.app.data.income.IncomeSourceStore.budgetedMonthly(fare).toInt()
                                    Text(
                                        com.pesaflow.app.ui.language.budT("fare_covers", lang, fare.frequencyLabel(), fare.expectedAmount.toInt().toString(), monthly.toString()),
                                        style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (budget.sharedWith.isNotBlank()) {
                                val members = com.pesaflow.app.data.ledger.splitUserList(budget.sharedWith)
                                Text(
                                    com.pesaflow.app.ui.language.budT("shared_line", lang, members.joinToString(", "), (budget.limitAmount / (members.size + 1)).toInt().toString()),
                                    style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = com.pesaflow.app.ui.theme.ppColors.brightBlue
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editingBudget = budget }) {
                                    Text(
                                        com.pesaflow.app.ui.language.budT("edit", lang),
                                        style = com.pesaflow.app.ui.theme.ppTypography.labelMedium,
                                        color = com.pesaflow.app.ui.theme.ppColors.gold
                                    )
                                }
                                TextButton(onClick = { sharingBudget = budget }) {
                                    Text(
                                        if (budget.sharedWith.isBlank()) com.pesaflow.app.ui.language.budT("share_btn", lang) else com.pesaflow.app.ui.language.budT("edit_share", lang),
                                        style = com.pesaflow.app.ui.theme.ppTypography.labelMedium,
                                        color = com.pesaflow.app.ui.theme.ppColors.gold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    com.pesaflow.app.ui.language.budT(
                        "no_cat", lang,
                        com.pesaflow.app.ui.language.budT(when (tab) { "Daily" -> "tab_daily" "Weekly" -> "tab_weekly" "Semester" -> "tab_semester" else -> "tab_monthly" }, lang)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text(com.pesaflow.app.ui.language.budT("create_first", lang), color = MaterialTheme.colorScheme.onPrimary) }
            }

            // My Plans (savings goals with required pace)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(com.pesaflow.app.ui.language.budT("plans_title", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showPlanDialog = true }) { Text(com.pesaflow.app.ui.language.budT("add_plan", lang)) }
            }
            if (savingsGoals.isEmpty()) {
                Text(com.pesaflow.app.ui.language.budT("no_plans", lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                savingsGoals.forEach { goal ->
                    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
                    val daysLeft = ((goal.targetTimestamp - now) / day).coerceAtLeast(0)
                    val perDay = if (daysLeft > 0) remaining / daysLeft else remaining
                    val fraction = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                    com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.STANDARD) {
                        Column(verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.sm)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    goal.title,
                                    style = com.pesaflow.app.ui.theme.ppTypography.labelLarge,
                                    color = com.pesaflow.app.ui.theme.ppColors.textPrimary
                                )
                                TextButton(onClick = { viewModel.deleteSavingsGoal(goal.id) }) {
                                    Text(com.pesaflow.app.ui.language.budT("remove", lang), style = com.pesaflow.app.ui.theme.ppTypography.labelMedium, color = com.pesaflow.app.ui.theme.ppColors.error)
                                }
                            }
                            com.pesaflow.app.ui.theme.PpProgress(
                                fraction = fraction,
                                kind = if (remaining <= 0) com.pesaflow.app.ui.theme.PpProgressKind.SUCCESS else com.pesaflow.app.ui.theme.PpProgressKind.GOLD
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (remaining <= 0) com.pesaflow.app.ui.language.budT("goal_done", lang, goal.currentAmount.toInt().toString(), goal.targetAmount.toInt().toString())
                                else com.pesaflow.app.ui.language.budT("goal_line", lang, goal.currentAmount.toInt().toString(), goal.targetAmount.toInt().toString(), remaining.toInt().toString(), perDay.toInt().toString(), daysLeft.toString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Text(com.pesaflow.app.ui.language.budT("fills_from", lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }

    if (showAddDialog) {
        var category by remember { mutableStateOf("") }
        var limit by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(BudgetType.MONTHLY) }
        var sharedWith by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(com.pesaflow.app.ui.language.budT("new_budget", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text(com.pesaflow.app.ui.language.budT("cat_label", lang)) })
                    val suggestion = remember(category, transactions) {
                        if (category.isBlank()) null
                        else {
                            val sum90 = transactions.filter {
                                it.type == TransactionType.EXPENSE && !it.isSample &&
                                    it.category.equals(category.trim(), ignoreCase = true) &&
                                    it.dateTimestamp >= System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                            }.sumOf { it.amount }
                            if (sum90 > 0) (sum90 / 3).toInt() else null
                        }
                    }
                    if (suggestion != null && limit.toDoubleOrNull() == null) {
                        TextButton(onClick = { limit = suggestion.toString() }) {
                            Text(com.pesaflow.app.ui.language.budT("suggested", lang, suggestion.toString()))
                        }
                    }
                    OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text(com.pesaflow.app.ui.language.budT("limit_label", lang)) })
                    OutlinedTextField(value = sharedWith, onValueChange = { sharedWith = it }, label = { Text(com.pesaflow.app.ui.language.budT("share_with", lang)) })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BudgetType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.name.take(5)) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val limitVal = limit.toDoubleOrNull()
                    if (limitVal != null && limitVal > 0 && category.isNotBlank()) {
                        // Upsert: re-saving an envelope replaces it — editing
                        // must never stack a silent twin that doubles the bar.
                        viewModel.upsertBudget(category.trim(), limitVal, selectedType, sharedWith.trim())
                        showAddDialog = false
                    }
                }) { Text(com.pesaflow.app.ui.language.budT("save", lang)) }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(com.pesaflow.app.ui.language.budT("cancel", lang)) } }
        )
    }

    if (showPlanDialog) {
        var title by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("") }
        var days by remember { mutableStateOf("90") }

        AlertDialog(
            onDismissRequest = { showPlanDialog = false },
            title = { Text(com.pesaflow.app.ui.language.budT("new_plan", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(com.pesaflow.app.ui.language.budT("plan_what", lang)) })
                    OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text(com.pesaflow.app.ui.language.budT("plan_target", lang)) })
                    OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text(com.pesaflow.app.ui.language.budT("plan_days", lang)) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val targetVal = target.toDoubleOrNull()
                    val daysVal = days.toIntOrNull()
                    if (targetVal != null && targetVal > 0 && daysVal != null && title.isNotBlank()) {
                        viewModel.addSavingsGoal(title.trim(), targetVal, daysVal)
                        showPlanDialog = false
                    }
                }) { Text(com.pesaflow.app.ui.language.budT("save_plan", lang)) }
            },
            dismissButton = { TextButton(onClick = { showPlanDialog = false }) { Text(com.pesaflow.app.ui.language.budT("cancel", lang)) } }
        )
    }

    editingBudget?.let { b ->
        var amount by remember { mutableStateOf(b.limitAmount.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { editingBudget = null },
            title = { Text(com.pesaflow.app.ui.language.budT("edit_title", lang, b.category)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        com.pesaflow.app.ui.language.budT("edit_sub", lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text(com.pesaflow.app.ui.language.budT("limit_label", lang)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    amount.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                        viewModel.upsertBudget(b.category, it, b.type, b.sharedWith)
                    }
                    editingBudget = null
                }) { Text(com.pesaflow.app.ui.language.budT("save", lang)) }
            },
            dismissButton = { TextButton(onClick = { editingBudget = null }) { Text(com.pesaflow.app.ui.language.budT("cancel", lang)) } }
        )
    }

    sharingBudget?.let { b ->
        var names by remember { mutableStateOf(b.sharedWith) }
        AlertDialog(
            onDismissRequest = { sharingBudget = null },
            title = { Text(com.pesaflow.app.ui.language.budT("share_title", lang, b.category)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        com.pesaflow.app.ui.language.budT("share_sub", lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(value = names, onValueChange = { names = it }, label = { Text(com.pesaflow.app.ui.language.budT("share_eg", lang)) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.shareBudget(b.id, names.trim())
                    sharingBudget = null
                }) { Text(com.pesaflow.app.ui.language.budT("save", lang)) }
            },
            dismissButton = { TextButton(onClick = { sharingBudget = null }) { Text(com.pesaflow.app.ui.language.budT("cancel", lang)) } }
        )
    }
}


private fun dayStartOf(now: Long): Long {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun monthStartOf(now: Long): Long {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
    c.set(java.util.Calendar.DAY_OF_MONTH, 1)
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}
