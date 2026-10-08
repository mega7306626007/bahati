package com.pesaflow.app.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.pesaflow.app.ui.theme.InfoBlue
import com.pesaflow.app.data.academic.ScanWindow
import com.pesaflow.app.data.academic.observedTransportMonthly
import com.pesaflow.app.data.income.IncomeSourceStore
import com.pesaflow.app.data.models.*
import com.pesaflow.app.ui.analytics.BudgetRing
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintBudgetsJar
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private val PERIOD_TABS = listOf("Daily", "Weekly", "Monthly", "Semester")


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(viewModel: FinanceViewModel) {
    val budgets by viewModel.budgets.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val monthlyIncome by viewModel.monthlyIncome.collectAsState()
    val wallet by viewModel.availableBalance.collectAsState()
    val profile by viewModel.universityProfile.collectAsState()
    val allBills by viewModel.bills.collectAsState()
    val financialSnapshot by viewModel.financialSnapshot.collectAsState()
    var tab by remember { mutableStateOf("Monthly") }
    var calcIncome by remember { mutableStateOf("") }
    var calcRule by remember { mutableStateOf("Student") }
    var calcPeriod by remember { mutableStateOf(BudgetType.MONTHLY) }
    var calcStyle by remember { mutableStateOf(LifestylePreset.BALANCED) }
    // Proof tick for Apply: brief ✓ that reverts so re-apply stays possible.
    val ackScope = rememberCoroutineScope()
    var acked by remember { mutableStateOf(setOf<String>()) }
    fun ack(key: String) {
        acked = acked + key
        ackScope.launch { kotlinx.coroutines.delay(2000); acked = acked - key }
    }
    var autoDaily by remember { mutableStateOf(true) }
    var livingSel by remember { mutableStateOf(parseLiving(viewModel.getOnboardingAnswers())) }
    var includeTransport by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showPlanDialog by remember { mutableStateOf(false) }
    var sharingBudget by remember { mutableStateOf<Budget?>(null) }

    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    val dayStart = dayStartOf(now)
    // Weekly follows the shared calendar week (Mon–Sun, same as Home), not a
    // rolling 7 days — the two tabs finally agree on "this week". Every
    // window is bounded above too, so future-dated rows never count as spent.
    val (periodType, window, windowLabel) = when (tab) {
        "Daily" -> Triple(BudgetType.DAILY, dayStart to dayStart + day, "today")
        "Weekly" -> Triple(
            BudgetType.WEEKLY,
            com.pesaflow.app.data.academic.currentWeekRange(now),
            "this week · Mon–Sun"
        )
        "Semester" -> {
            // Real semester, never rolling now−120 days: profile dates win,
            // else the academic calendar (Sem 1 Sep–Dec, Sem 2 Jan–Apr,
            // break May–Aug). The rolling window billed long-break spending
            // as "this semester" — that's where an inflated 113000 came from.
            val hasProfileDates = (profile?.semesterStartTimestamp ?: 0L) > 0
            val (semStart, semEnd) = com.pesaflow.app.data.academic.semesterBounds(
                profile?.semesterStartTimestamp ?: 0L,
                profile?.semesterEndTimestamp ?: 0L,
                now
            )
            val inBreak = !hasProfileDates &&
                com.pesaflow.app.data.academic.windowFor(now).kind == com.pesaflow.app.data.academic.SemKind.BREAK
            Triple(
                BudgetType.SEMESTER,
                // Bounded above at now: future-dated rows never count as spent.
                semStart to minOf(semEnd, now + 1),
                if (inBreak) "the long break" else "this semester"
            )
        }
        else -> Triple(BudgetType.MONTHLY, monthStartOf(now) to (now + 1), "this month")
    }
    val (windowStart, windowEnd) = window
    val target = budgets.filter { it.type == periodType }.sumOf { it.limitAmount }
    // Daily/weekly/monthly windows reuse the canonical snapshot. Only the
    // less-common semester/annual paths still need a local transaction scan.
    val spent = when (tab) {
        "Daily" -> financialSnapshot.todayExpense
        "Weekly" -> financialSnapshot.weekExpense
        "Monthly" -> financialSnapshot.monthExpense
        else -> transactions
            .asSequence()
            .filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= windowStart && it.dateTimestamp < windowEnd }
            .sumOf { it.amount }
    }
    // Envelope carryover: last month's unspent rolls into this month's target.
    val prevWindowStart = monthStartOf(monthStartOf(now) - 24L * 60 * 60 * 1000)
    val lastSpent = if (tab == "Monthly") {
        financialSnapshot.lastMonthExpense
    } else {
        transactions
            .asSequence()
            .filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= prevWindowStart && it.dateTimestamp < windowStart }
            .sumOf { it.amount }
    }
    val carry = if (tab == "Monthly") (target - lastSpent).coerceAtLeast(0.0) else 0.0
    val displayTarget = target + carry
    val left = displayTarget - spent

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintBudgetsJar, bgRes = R.drawable.bg_budgets_jar)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Budgets & Plans", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
                actions = {
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("Add", color = MaterialTheme.colorScheme.primary)
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
                        label = { Text(p) }
                    )
                }
            }

            AtmosphereBand(
                workspace = AtmoWorkspace.BUDGET,
                title = "Envelopes",
                subtitle = "Daily to annual plans"
            )
            // Welcoming page: first open auto-builds envelopes from the
            // ledger's real monthly pace. Ledger updates → suggestions
            // update; one tap applies them as budgets.
            if (budgets.isEmpty()) {
                val cutoff90 = now - 90 * day
                val pace = { cat: String? ->
                    transactions.filter {
                        it.type == TransactionType.EXPENSE && it.dateTimestamp >= cutoff90 &&
                            (cat == null || it.category.equals(cat, ignoreCase = true))
                    }.sumOf { it.amount } / 3
                }
                val starter = com.pesaflow.app.data.parsers.suggestStarterBudgets(
                    monthlyExpense = pace(null),
                    monthlyFood = pace("Food"),
                    monthlyRent = pace("Rent"),
                    monthlyTransport = transactions.filter {
                        it.type == TransactionType.EXPENSE && it.dateTimestamp >= now - 30 * day &&
                            it.category.equals("Transport", ignoreCase = true)
                    }.sumOf { it.amount }
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Karibu! Your money suggests this 👋", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (starter.isEmpty()) {
                            Text(
                                "No spending history yet — scan M-Pesa in More → Settings, or log your first expense on Home, then come back: envelopes build themselves here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "Built from your last 90 days of real spending. Edit anything — one tap applies.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            starter.forEach { (cat, amt) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(cat, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("KSh ${amt.toInt()}/mo", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    starter.forEach { (cat, amt) ->
                                        viewModel.upsertBudget(cat, amt, BudgetType.MONTHLY)
                                    }
                                    ack("starter")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if ("starter" in acked) "Applied ✓" else "Apply all") }
                        }
                    }
                }
            }
            // Next-30-day outlook per top category (spec §15): trailing-mean
            // ranges from real ledger history vs your monthly envelopes.
            // Advice only — budgets change only when you tap Apply elsewhere.
            if (transactions.any { it.type == TransactionType.EXPENSE }) {
                val outlook = remember(transactions, budgets) {
                    val dayMs = 24L * 60 * 60 * 1000
                    val today = com.pesaflow.app.data.academic.dayStart(System.currentTimeMillis())
                    val topCats = transactions
                        .filter { it.type == TransactionType.EXPENSE }
                        .groupBy { it.category }
                        .mapValues { e -> e.value.sumOf { it.amount } }
                        .entries.sortedByDescending { it.value }.take(5)
                    topCats.mapNotNull { (cat, _) ->
                        val daily = (13 downTo 0).map { back ->
                            val dayTs = today - back * dayMs
                            transactions.filter {
                                it.type == TransactionType.EXPENSE &&
                                    it.category.equals(cat, ignoreCase = true) &&
                                    com.pesaflow.app.data.academic.dayStart(it.dateTimestamp) == dayTs
                            }.sumOf { it.amount }
                        }
                        if (daily.sum() <= 0) return@mapNotNull null
                        val f = runCatching {
                            com.pesaflow.app.data.ml.MlEngine.forecastMonth(daily)
                        }.getOrNull() ?: return@mapNotNull null
                        val envelope = budgets.firstOrNull {
                            it.type == BudgetType.MONTHLY && it.category.equals(cat, ignoreCase = true)
                        }?.limitAmount
                        Triple(cat, f, envelope)
                    }
                }
                if (outlook.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Next 30 days outlook 🔭", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Trailing-mean ranges from your real spending vs monthly envelopes. Advice only — nothing auto-changes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            outlook.forEach { (cat, f, envelope) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cat, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Projected KSh ${f.low.toInt()}–${f.high.toInt()}" +
                                                (envelope?.let { e ->
                                                    if (f.low > e) " · over KSh ${e.toInt()} envelope ⚠️"
                                                    else if (f.high <= e) " · within KSh ${e.toInt()} ✓"
                                                    else " · may breach KSh ${e.toInt()} ~"
                                                } ?: " · no envelope set"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
            // Expense calculator: income in, suggested monthly budgets out
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Budget Calculator 🧮", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Enter monthly income, pick a rule — get suggested budgets, apply in one tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = calcIncome,
                        onValueChange = { calcIncome = it },
                        label = { Text("Monthly income (KSh)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Student", "50/30/20").forEach { r ->
                            FilterChip(selected = (if (r == "Student") calcRule == "Student" else calcRule != "Student"), onClick = { calcRule = r }, label = { Text(if (r == "Student") "Campus Survival" else r) })
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Living situation — rent, fares and food follow it", style = MaterialTheme.typography.labelLarge)
                    LivingSituation.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { ls ->
                                FilterChip(selected = livingSel == ls, onClick = { livingSel = ls }, label = { Text(ls.short) })
                            }
                        }
                    }
                    Text(
                        livingSel.label + when {
                            !livingSel.hasRent -> " — no rent priced, fares + food adjusted."
                            livingSel.commuteHeavy -> " — long-route fares protected."
                            livingSel.cantCook -> " — buying every meal, food funded higher."
                            else -> " — full roof + food cover."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Include transport", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (livingSel.commuteHeavy) "Long route daily — full weight" else if (livingSel.commuteLight) "Short hop — light buffer" else "Off for walkers — keep a small buffer if you like",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = includeTransport, onCheckedChange = { includeTransport = it })
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(BudgetType.DAILY, BudgetType.WEEKLY, BudgetType.MONTHLY, BudgetType.SEMESTER, BudgetType.ANNUAL).forEach { t ->
                            FilterChip(
                                selected = calcPeriod == t,
                                onClick = { calcPeriod = t },
                                label = { Text(t.name.take(5)) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Lifestyle — same engine, different appetite", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LifestylePreset.entries.forEach { style ->
                            FilterChip(selected = calcStyle == style, onClick = { calcStyle = style }, label = { Text(style.label) })
                        }
                    }
                    Text(
                        when (calcStyle) {
                            LifestylePreset.COMMUTER_LITE -> "Early bus, home meals — transport first, food light."
                            LifestylePreset.FOODIE -> "Food protected above all."
                            LifestylePreset.SAVER -> "Savings pushed, lifestyle paused."
                            else -> "Standard campus split."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (calcPeriod == BudgetType.DAILY) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto from monthly", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("Daily = your monthly budgets ÷ 30", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = autoDaily, onCheckedChange = { autoDaily = it })
                        }
                    }
                    val monthlyBudgetsTotal = budgets.filter { it.type == BudgetType.MONTHLY }.sumOf { it.limitAmount }
                    // Expected income from More → Income (HELB, parents, hustle...) backs the
                    // base when the ledger is still empty early in the month.
                    val expectedIncome = IncomeSourceStore.totalExpected(androidx.compose.ui.platform.LocalContext.current).takeIf { it > 0 }
                    val calcBase = if (calcPeriod == BudgetType.DAILY && autoDaily && monthlyBudgetsTotal > 0) {
                        monthlyBudgetsTotal
                    } else {
                        calcIncome.toDoubleOrNull()?.takeIf { it > 0 } ?: monthlyIncome.takeIf { it > 0 } ?: expectedIncome
                    }
                    if (calcBase != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val nowMs = System.currentTimeMillis()
                        val openBills = allBills.filter { it.status != "PAID" }
                        // Smart survival-first engine: real bills as floors + real 90-day avgs.
                        val billMap = openBills.groupBy { it.category.lowercase() }
                            .mapValues { e -> e.value.sumOf { it.amount }.toInt() }
                        // A re-learned fare cutoff drops the old route from Transport
                        // history everywhere budgets look — other categories keep
                        // the full 90 days.
                        val bctx = androidx.compose.ui.platform.LocalContext.current
                        val fareCutoffMs = try {
                            bctx.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                                .getLong("fare_relearn_ms", 0L)
                        } catch (e: Exception) { 0L }
                        val histStart = nowMs - 90L * 24 * 60 * 60 * 1000
                        val avgMap = transactions.filter {
                            it.type == TransactionType.EXPENSE && it.dateTimestamp >= histStart &&
                                (fareCutoffMs <= 0L || !it.category.equals("Transport", ignoreCase = true) || it.dateTimestamp >= fareCutoffMs)
                        }.groupBy { it.category.lowercase() }
                            .mapValues { e -> (e.value.sumOf { it.amount } / 3 * periodScale(calcPeriod)).toInt() }
                            .toMutableMap().also { m ->
                                // Observed school-day fares beat the flat 90-day
                                // average for Transport: break weeks stop watering
                                // down term-time reality.
                                val fareWin = ScanWindow(maxOf(histStart, fareCutoffMs), nowMs, "90d", false, false)
                                val observed = observedTransportMonthly(transactions, fareWin)
                                if (observed > 0) m["transport"] = (observed * periodScale(calcPeriod)).toInt()
                            }
                        val rule = if (calcRule == "Student") BudgetRule.CAMPUS else BudgetRule.SPLIT
                        // Your fare first: saved Transport budget ÷ 30, else the
                        // onboarding daily figure — so commuters never get a
                        // watered-down fare envelope.
                        val fareDailyGuess = budgets
                            .firstOrNull { it.type == BudgetType.MONTHLY && it.category.equals("Transport", ignoreCase = true) }
                            ?.let { it.limitAmount / 30 }?.takeIf { it > 0 }
                            ?: com.pesaflow.app.ui.dashboard.parseTransportDaily(viewModel.getOnboardingAnswers())
                        val plan = smartBudget(
                            monthlyBase = calcBase,
                            rule = rule,
                            period = calcPeriod,
                            living = livingSel,
                            includeTransport = includeTransport,
                            openBillByCategory = billMap,
                            avg90ByCategory = avgMap,
                            style = calcStyle,
                            fareDaily = fareDailyGuess
                        )
                        // Daily auto mode still derives from monthly envelopes when present.
                        val periodName = plan.periodName
                        val prioritized: List<Triple<String, Int, Int>> = plan.suggestions.map { s ->
                            Triple(s.category, s.amount, avgMap[s.category.lowercase()] ?: 0)
                        }
                        val dropped: List<String> = plan.dropped
                        if (plan.tightMode) {
                            Text(
                                "Tight mode 🛡️ — money is little so Food + Rent eat first. " +
                                    "Savings and lifestyle pause instead of starving you.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            plan.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (dropped.isNotEmpty()) {
                            Text(
                                "Paused to protect Food + Rent: ${dropped.joinToString(", ")}",
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
                        val applySig = "$calcRule|$calcPeriod|$calcBase|$autoDaily|$livingSel|$includeTransport|${prioritized.size}"
                        Button(
                            onClick = {
                                viewModel.applyCalculatedBudgets(prioritized.map { (cat, amt, _) -> cat to amt.toDouble() }, calcPeriod)
                                ack(applySig)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text(if (applySig in acked) "Applied ${prioritized.size} ✓" else "Apply as $periodName Budgets", color = MaterialTheme.colorScheme.onPrimary) }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Log income first (or type it above) to see suggestions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Period hero card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("$tab Budget · $windowLabel", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (target <= 0) {
                        var quickAmount by remember(tab) { mutableStateOf("") }
                        // Option-wise money: each tab prefills from ITS basis —
                        // the semester pot (starting funding + semester income
                        // − semester spend) for Semester, this month's income
                        // for Daily/Weekly/Monthly. Type over it any time.
                        val quickSuggestion = if (tab == "Semester") {
                            val (semStart, semEnd) = com.pesaflow.app.data.academic.semesterBounds(
                                profile?.semesterStartTimestamp ?: 0L,
                                profile?.semesterEndTimestamp ?: 0L,
                                now
                            )
                            val windowTx = transactions.filter {
                                it.dateTimestamp >= semStart && it.dateTimestamp < minOf(semEnd, now + 1)
                            }
                            val potIncome = windowTx.filter {
                                it.type == TransactionType.INCOME &&
                                    it.source !in com.pesaflow.app.data.money.NON_STAT_SOURCES
                            }.sumOf { it.amount }
                            val windowSpent = windowTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                            ((profile?.startingFunding ?: 0.0) + potIncome - windowSpent).coerceAtLeast(0.0)
                        } else {
                            monthlyIncome
                        }
                        LaunchedEffect(quickSuggestion) {
                            if (quickAmount.isEmpty() && quickSuggestion > 0) {
                                quickAmount = quickSuggestion.toInt().toString()
                            }
                        }
                        Text(
                            "No $tab budget set yet.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Spent $windowLabel so far: KSh ${spent.toInt()}. Set your $tab limit here:",
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
                                label = { Text("KSh limit") },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    quickAmount.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                        viewModel.addBudget("ALL", it, periodType)
                                        quickAmount = ""
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) { Text("Set", color = MaterialTheme.colorScheme.onPrimary) }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BudgetRing(fraction = if (displayTarget > 0) (spent / displayTarget).toFloat() else 0f)
                            Spacer(modifier = Modifier.width(20.dp))
                            Column {
                                Text(
                                    "KSh ${left.toInt()}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "left of KSh ${displayTarget.toInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (carry > 0) {
                                    Text(
                                        "＋KSh ${carry.toInt()} rolled in from last month 🎲",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                // Tie the big budget number to actual money:
                                // what's in the wallet and what arrived this
                                // month — a 150000 limit means nothing to
                                // someone holding 5000.
                                Text(
                                    "In wallet: KSh ${wallet.toInt()} · income this month: +KSh ${monthlyIncome.toInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (spent / target).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (spent >= target) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            periodVerdict(tab, spent, displayTarget),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (spent > target) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Category budgets of this period
            val categoryBudgets = budgets.filter { it.type == periodType && it.category != "ALL" }
            if (categoryBudgets.isNotEmpty()) {
                Text("Category Budgets · $tab", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                categoryBudgets.forEach { budget ->
                    // Same window as the hero card above: the TAB's calendar
                    // period. The stored creation stamps used to gate this —
                    // a budget created "now → +30 days" expired, froze every
                    // bar at 0 no matter what you logged, and never matched
                    // the "Monthly · this month" header it sat under.
                    val cSpent = transactions
                        .filter {
                            it.type == TransactionType.EXPENSE &&
                                it.category == budget.category &&
                                it.dateTimestamp >= windowStart && it.dateTimestamp < windowEnd
                        }
                        .sumOf { it.amount }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(budget.category, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                TextButton(onClick = { viewModel.deleteBudget(budget.id) }) {
                                    Text("Remove", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            LinearProgressIndicator(
                                progress = { (cSpent / budget.limitAmount).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (cSpent >= budget.limitAmount) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "KSh ${cSpent.toInt()} of KSh ${budget.limitAmount.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (budget.sharedWith.isNotBlank()) {
                                val members = budget.sharedWith.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                Text(
                                    "Shared: ${members.joinToString(", ")} · KSh ${(budget.limitAmount / (members.size + 1)).toInt()} each",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { sharingBudget = budget }) {
                                    Text(
                                        if (budget.sharedWith.isBlank()) "Share" else "Edit share",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    "No $tab category budgets yet — give every shilling a job.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Create First Budget", color = MaterialTheme.colorScheme.onPrimary) }
            }

            // My Plans (savings goals with required pace)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("My Plans 🎯", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showPlanDialog = true }) { Text("+ Add Plan") }
            }
            if (savingsGoals.isEmpty()) {
                Text("No plans yet. A plan is a goal with a deadline — e.g. Laptop by December.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                savingsGoals.forEach { goal ->
                    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
                    val daysLeft = ((goal.targetTimestamp - now) / day).coerceAtLeast(0)
                    val perDay = if (daysLeft > 0) remaining / daysLeft else remaining
                    val fraction = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(goal.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                TextButton(onClick = { viewModel.deleteSavingsGoal(goal.id) }) {
                                    Text("Remove", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = InfoBlue,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "KSh ${goal.currentAmount.toInt()} of KSh ${goal.targetAmount.toInt()} · " +
                                    if (remaining <= 0) "complete! 🎉"
                                    else "KSh ${remaining.toInt()} to go · KSh ${perDay.toInt()}/day for $daysLeft days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Text("Fills from what you don't spend 💧", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            title = { Text("New Budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category (ALL = everything)") })
                    val suggestion = remember(category, transactions) {
                        if (category.isBlank()) null
                        else {
                            val sum90 = transactions.filter {
                                it.type == TransactionType.EXPENSE &&
                                    it.category.equals(category.trim(), ignoreCase = true) &&
                                    it.dateTimestamp >= System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                            }.sumOf { it.amount }
                            if (sum90 > 0) (sum90 / 3).toInt() else null
                        }
                    }
                    if (suggestion != null && limit.toDoubleOrNull() == null) {
                        TextButton(onClick = { limit = suggestion.toString() }) {
                            Text("Suggested: KSh $suggestion (your 3-month average) — tap to use")
                        }
                    }
                    OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text("Limit (KSh)") })
                    OutlinedTextField(value = sharedWith, onValueChange = { sharedWith = it }, label = { Text("Share with (names, comma separated)") })
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
                        viewModel.addBudget(category.trim(), limitVal, selectedType, sharedWith.trim())
                        showAddDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPlanDialog) {
        var title by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("") }
        var days by remember { mutableStateOf("90") }

        AlertDialog(
            onDismissRequest = { showPlanDialog = false },
            title = { Text("New Plan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("What do you want to accomplish?") })
                    OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Target (KSh)") })
                    OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("Days from now") })
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
                }) { Text("Save Plan") }
            },
            dismissButton = { TextButton(onClick = { showPlanDialog = false }) { Text("Cancel") } }
        )
    }

    sharingBudget?.let { b ->
        var names by remember { mutableStateOf(b.sharedWith) }
        AlertDialog(
            onDismissRequest = { sharingBudget = null },
            title = { Text("Share \"${b.category}\" budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Household names, comma separated. The limit splits evenly between you and them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(value = names, onValueChange = { names = it }, label = { Text("e.g. Brian, Faith") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.shareBudget(b.id, names.trim())
                    sharingBudget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { sharingBudget = null }) { Text("Cancel") } }
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


private fun periodVerdict(tab: String, spent: Double, target: Double): String {
    if (target <= 0) return ""
    val pct = (spent / target * 100).toInt()
    return when {
        spent > target -> "Over $tab budget by KSh ${(spent - target).toInt()} ($pct%) — essentials only. 🛑"
        pct >= 80 -> "$pct% used — KSh ${(target - spent).toInt()} left. Slow down. ⚠️"
        else -> "$pct% used — KSh ${(target - spent).toInt()} left. On track 👌."
    }
}
