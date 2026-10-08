package com.pesaflow.app.ui.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.ui.theme.categoryEmoji
import com.pesaflow.app.ui.theme.toKSh
import com.pesaflow.app.ui.theme.PesaSpacing
import com.pesaflow.app.ui.theme.AtmoType
import com.pesaflow.app.ui.theme.HeroFinanceCard
import com.pesaflow.app.ui.theme.PesaLoadingRow
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintDashboardForest
import com.pesaflow.app.R
import com.pesaflow.app.data.models.UserRhythm
import com.pesaflow.app.data.models.RhythmKind
import com.pesaflow.app.data.repositories.FinanceRepository
import com.pesaflow.app.ui.budgets.LivingSituation
import com.pesaflow.app.ui.budgets.scaledFareReserve
import com.pesaflow.app.ui.dashboard.RhythmConfirmCard
import com.pesaflow.app.ui.dashboard.RhythmEngine


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onQuickAdd: (TransactionType) -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val availableBalance by viewModel.availableBalance.collectAsState()
    val income by viewModel.monthlyIncome.collectAsState()
    val expenses by viewModel.monthlyExpenses.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val pendingTransactions by viewModel.pendingTransactions.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val userRhythms by viewModel.userRhythms.collectAsState()
    val confirmedRhythms by viewModel.confirmedRhythms.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val hideBalances by viewModel.hideBalances.collectAsState()
    val hiddenSections by viewModel.hiddenSections.collectAsState()
    val ledgerLoaded by viewModel.ledgerLoaded.collectAsState()
    val profile by viewModel.universityProfile.collectAsState()
    // One living group drives budgets, insights, Buddy and fare guards together.
    val livingGroup = com.pesaflow.app.ui.budgets.parseLiving(viewModel.getOnboardingAnswers())
    val ziidiSaved by viewModel.ziidiSaved.collectAsState()
    var fixWallet by remember { mutableStateOf(false) }
    var fixAmt by remember { mutableStateOf("") }

    val engine = remember { RhythmEngine(viewModel.repository) }
    val proposals = remember(transactions, livingGroup) { engine.propose(transactions, livingGroup) }
    // Rhythm cards the user swiped away this session — no server round-trip,
    // the dismiss button just works (it used to do nothing).
    var dismissedRhythms by remember { mutableStateOf(setOf<String>()) }
    val unconfirmed = proposals.filter {
        p -> confirmedRhythms.none { it.kind == p.kind.name && it.category == p.category } &&
            "${p.kind}:${p.category}" !in dismissedRhythms
    }
    var confirmAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // LaunchedEffect, not a composition side-effect: confirming used to
    // re-fire on every recomposition while confirmAll stayed true.
    LaunchedEffect(confirmAll) {
        if (confirmAll) {
            viewModel.confirmAllRhythms()
            snackbar.showSnackbar("Rhythm patterns saved.")
            confirmAll = false
        }
    }
    // Week values, finally shown: this week (Mon–Sun) by default, the whole
    // ledger only if you ask. Old cumulative-everywhere is gone.
    // Single source of truth (academic.weekStartMonday): the old inline
    // Calendar.set(DAY_OF_WEEK, MONDAY) follows the device locale — with a
    // Sunday-first locale it lands on TOMORROW when today is Sunday, hiding
    // the whole week including today. Recomputed with transactions so it
    // never goes stale past midnight either.
    var weekAllTime by remember { mutableStateOf(false) }
    val weekStartMs = remember(transactions) {
        com.pesaflow.app.data.academic.weekStartMonday(System.currentTimeMillis())
    }
    val weekdaySpending = remember(transactions, weekAllTime, weekStartMs) {
        val cal = Calendar.getInstance()
        val map = mutableMapOf("Mon" to 0.0, "Tue" to 0.0, "Wed" to 0.0, "Thu" to 0.0, "Fri" to 0.0, "Sat" to 0.0, "Sun" to 0.0)
        transactions
            .filter { it.type == TransactionType.EXPENSE && (weekAllTime || it.dateTimestamp >= weekStartMs) }
            .forEach { tx ->
                cal.timeInMillis = tx.dateTimestamp
                val dayStr = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "Mon"
                    Calendar.TUESDAY -> "Tue"
                    Calendar.WEDNESDAY -> "Wed"
                    Calendar.THURSDAY -> "Thu"
                    Calendar.FRIDAY -> "Fri"
                    Calendar.SATURDAY -> "Sat"
                    Calendar.SUNDAY -> "Sun"
                    else -> null
                }
                if (dayStr != null) { map[dayStr] = (map[dayStr] ?: 0.0) + tx.amount }
            }
        map
    }

    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var quickEditTx by remember { mutableStateOf<Transaction?>(null) }
    var showAllPending by remember { mutableStateOf(false) }
    var ledgerFilter by remember { mutableStateOf<String?>(null) }
    // Contact-card grouping is hoisted here: LazyListScope is not a
    // @Composable context, so remember() must not live inside the list.
    val contactCards = remember(pendingTransactions) {
        com.pesaflow.app.data.parsers.buildContactCards(pendingTransactions)
    }
    // Safe-to-spend inputs: fare guard follows YOUR fare for heavy routes
    // (Transport budget ÷ 30, else onboarding figure), school-day aware
    // from onboarding class days. Recomputes whenever the ledger moves.
    val schoolToday = remember(transactions) {
        isSchoolToday(viewModel.getOnboardingAnswers(), System.currentTimeMillis())
    }
    val fareDailyGuess = remember(transactions, budgets) {
        budgets.firstOrNull { it.type == BudgetType.MONTHLY && it.category.equals("Transport", ignoreCase = true) }
            ?.let { it.limitAmount / 30 }?.takeIf { it > 0 }
            ?: parseTransportDaily(viewModel.getOnboardingAnswers())
    }
    // Face memory: remembered relations + usual categories ride on the
    // cards ("Nancy · Mother · usually Upkeep").
    val dashContext = LocalContext.current
    val faceMemory = remember(pendingTransactions) {
        try {
            com.pesaflow.app.data.parsers.readContactMemories(
                dashContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE).all
            )
        } catch (e: Exception) { emptyMap() }
    }
    val faceLabels = remember(faceMemory) {
        faceMemory.mapValues { it.value.label }.filterValues { it.isNotBlank() }
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintDashboardForest, bgRes = R.drawable.bg_dashboard_forest)
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbar) },
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                content = {
                    item {
                        val money = availableBalance + ziidiSaved
                        // Privacy mode: one tap masks every figure on the most
                        // sensitive screen in the app — glanced at in public,
                        // not just a settings toggle. SafeToSpendCard already
                        // takes `hide`; the hero masks here.
                        HeroFinanceCard(
                            greeting = "Money I Have",
                            dateLine = "Wallet + Ziidi",
                            availableLabel = "Balance",
                            availableValue = if (hideBalances) "••••" else money.toKSh(),
                            incomeValue = if (hideBalances) "••••" else income.toKSh(),
                            expenseValue = if (hideBalances) "••••" else expenses.toKSh(),
                            safeToSpendValue = if (hideBalances) "••••" else availableBalance.toKSh(),
                            // This row is raw wallet balance, not the computed
                            // safe spend (see SafeToSpendCard below) — label it
                            // so Balance (Wallet + Ziidi) vs Wallet always adds up.
                            safeToSpendLabel = "Wallet",
                            onHideToggle = { viewModel.setHideBalances(!hideBalances) },
                            hideLabel = if (hideBalances) "Show" else "Hide"
                        )
                    }
                    // Drift zones: ledger vs last M-Pesa SMS balance. InSync
                    // stays silent; stale SMS (>24h) never shows — any spend
                    // after the SMS invalidates the comparison. Tapping posts
                    // one visible, tagged, undoable row (never silent).
                    item {
                        val driftCtx = LocalContext.current
                        val drift = remember(availableBalance) {
                            val (sms, at) = com.pesaflow.app.data.parsers.readMpesaBalance(driftCtx)
                                ?: return@remember null
                            if (System.currentTimeMillis() - at > 24L * 60 * 60 * 1000) return@remember null
                            when (val s = com.pesaflow.app.data.money.evaluateDrift(sms, availableBalance)) {
                                is com.pesaflow.app.data.money.ReconciliationStatus.InSync -> null
                                else -> Triple(sms, at, s)
                            }
                        }
                        drift?.let { (sms, at, status) ->
                            val age = com.pesaflow.app.data.parsers.balanceAgeText(at, System.currentTimeMillis())
                            val major = status is com.pesaflow.app.data.money.ReconciliationStatus.MajorDrift
                            val amount = when (status) {
                                is com.pesaflow.app.data.money.ReconciliationStatus.MinorDrift -> status.amount
                                is com.pesaflow.app.data.money.ReconciliationStatus.MajorDrift -> status.amount
                                else -> 0.0
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        if (major) "Wallet drift — let's fix it 🤝"
                                        else "Small wallet drift",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "M-Pesa said KSh ${sms.toInt()} $age; ledger shows KSh ${availableBalance.toInt()} (gap KSh ${amount.toInt()}). " +
                                            if (major) "Likely unlogged cash — match it with one tap?"
                                            else "Probably a small unlogged spend — match it?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = {
                                        scope.launch {
                                            viewModel.reconcileToWallet(sms) { adjusted ->
                                                scope.launch {
                                                    snackbar.showSnackbar(
                                                        if (adjusted == 0.0) "Already reconciled today. ✅"
                                                        else "Matched wallet (KSh ${adjusted.toInt()}). ✅"
                                                    )
                                                }
                                            }
                                        }
                                    }) { Text(if (major) "Match wallet" else "Match quietly") }
                                }
                            }
                        }
                    }
                    item {
                        SchoolCheckinCard()
                    }
                    if (unconfirmed.isNotEmpty()) {
                        item {
                            Text("Rhythm guesses (" + unconfirmed.size + ")", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { confirmAll = true }) { Text("Confirm all") }
                        }
                        items(unconfirmed) { hyp ->
                            RhythmConfirmCard(
                                hypothesis = hyp,
                                onConfirm = { h ->
                                    scope.launch {
                                        viewModel.upsertRhythm(UserRhythm(
                                            kind = h.kind.name, category = h.category,
                                            confidence = h.confidence, hint = h.hint,
                                            dayOfMonth = h.dayOfMonth, amount = h.amount,
                                            sourceCode = h.supportingCodes.firstOrNull() ?: ""
                                        ))
                                        snackbar.showSnackbar("Saved: " + h.hint)
                                    }
                                },
                                onDismiss = {
                                    dismissedRhythms = dismissedRhythms + "${hyp.kind}:${hyp.category}"
                                }
                            )
                        }
                    }
                    // Pending as contact cards: one card per person/merchant with
                    // dates, so "Nancy ×14" is one tap instead of 14 rows.
                    if (pendingTransactions.isNotEmpty() && "pending" !in hiddenSections) {
                        item {
                            // Both bulk actions live here, side by side: clean
                            // dupes FIRST, then confirm — Confirm all can never
                            // bake doubles into the ledger.
                            val dupePendingCount = remember(pendingTransactions) {
                                com.pesaflow.app.data.parsers.pendingIdsToRemove(
                                    com.pesaflow.app.data.parsers.findDuplicatePendingGroups(pendingTransactions)
                                ).size
                            }
                            // Title full-width with ellipsis + wrapping action row:
                            // narrow screens used to stack "Confirm allsure (225)"
                            // glyphs vertically off-screen in a single SpaceBetween row.
                            Text(
                                "Pending · ${pendingTransactions.size} to confirm",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (dupePendingCount > 0) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            viewModel.cleanDuplicatePending { n ->
                                                scope.launch {
                                                    snackbar.showSnackbar(
                                                        if (n > 0) "Removed $n duplicate(s) — confirm the rest. ✅"
                                                        else "Nothing to remove."
                                                    )
                                                }
                                            }
                                        }
                                    }) {
                                        Text("Remove duplicates ($dupePendingCount)", maxLines = 1)
                                    }
                                }
                                TextButton(onClick = { viewModel.approveAllPending() }) {
                                    Text("Confirm all", maxLines = 1)
                                }
                            }
                            Text(
                                "Grouped by person — confirm one card, not every SMS.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val cards = contactCards
                        items(cards.take(10), key = { "contact-${it.name}-${it.total}" }) { card ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            buildString {
                                                append(card.name)
                                                faceLabels[com.pesaflow.app.data.parsers.normalizeContact(card.name)]?.let { append(" · ") ; append(it) }
                                                append(" ×")
                                                append(card.count)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "KSh ${card.total.toInt()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        buildString {
                                            append(android.text.format.DateFormat.format("d MMM", card.firstSeen))
                                            append(" → ")
                                            append(android.text.format.DateFormat.format("d MMM", card.lastSeen))
                                            append(" · ")
                                            append(card.topCategory)
                                            faceMemory[com.pesaflow.app.data.parsers.normalizeContact(card.name)]
                                                ?.category?.takeIf { it.isNotBlank() }?.let { append(" · usually ") ; append(it) }
                                            card.members.firstOrNull { it.displayCategory.isNotBlank() }
                                                ?.displayCategory?.let { append(" · ") ; append(it) }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            card.members.forEach { viewModel.approvePending(it, it.category) }
                                        }) { Text("Confirm ${card.count}") }
                                        TextButton(onClick = {
                                            card.members.forEach { viewModel.rejectPending(it) }
                                        }) { Text("Ignore") }
                                    }
                                }
                            }
                        }
                        if (cards.size > 10) {
                            item {
                                Text(
                                    "+ ${cards.size - 10} more people in Transactions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (weekAllTime) "Spending by weekday · all time"
                                else "This week · KSh ${weekdaySpending.values.sum().toInt()} spent",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !weekAllTime,
                                onClick = { weekAllTime = false },
                                label = { Text("This week", style = MaterialTheme.typography.bodySmall) }
                            )
                            FilterChip(
                                selected = weekAllTime,
                                onClick = { weekAllTime = true },
                                label = { Text("All time", style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { d ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            d,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "${(weekdaySpending[d] ?: 0.0).toInt()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // 7-day spending trend — the shape of your week at a glance
                    item {
                        val (trendStart, _) = com.pesaflow.app.data.academic.last7DaysRange(System.currentTimeMillis())
                        val trendDays = remember(transactions, trendStart) {
                            val dayMs = 24L * 60 * 60 * 1000
                            val cal = Calendar.getInstance()
                            (0..6).map { offset ->
                                val dayStart = trendStart + offset * dayMs
                                val spent = transactions
                                    .filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= dayStart && it.dateTimestamp < dayStart + dayMs }
                                    .sumOf { it.amount }
                                cal.timeInMillis = dayStart
                                val label = when (cal.get(Calendar.DAY_OF_WEEK)) {
                                    Calendar.MONDAY -> "M"
                                    Calendar.TUESDAY -> "T"
                                    Calendar.WEDNESDAY -> "W"
                                    Calendar.THURSDAY -> "T"
                                    Calendar.FRIDAY -> "F"
                                    Calendar.SATURDAY -> "S"
                                    Calendar.SUNDAY -> "S"
                                    else -> "?"
                                }
                                label to spent
                            }
                        }
                        val maxSpent = trendDays.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Last 7 days · KSh ${trendDays.sumOf { it.second }.toInt()} spent",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    trendDays.forEach { (label, spent) ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            val barHeight = (spent / maxSpent * 40).coerceAtLeast(2.0)
                                            Box(
                                                modifier = Modifier
                                                    .width(16.dp)
                                                    .height(barHeight.dp)
                                                    .background(
                                                        if (spent > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if ("safe" !in hiddenSections) {
                        item {
                            SafeToSpendCard(
                                transactions = transactions,
                                budgets = budgets,
                                goals = savingsGoals,
                                bills = bills,
                                hide = hideBalances,
                                fareReserve = scaledFareReserve(livingGroup, fareDailyGuess),
                                schoolToday = schoolToday,
                                forecastVersion = viewModel.forecastVersion.collectAsState().value
                            )
                        }
                    }
                    // Bills due soon — what's coming out of the account next.
                    item {
                        val now = System.currentTimeMillis()
                        val upcoming = bills
                            .filter { it.status != "PAID" && it.dueDate > now }
                            .sortedBy { it.dueDate }
                            .take(3)
                        if (upcoming.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Bills due soon",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    upcoming.forEach { bill ->
                                        val daysLeft = ((bill.dueDate - now) / (24L * 60 * 60 * 1000)).toInt()
                                        val dueLabel = when {
                                            daysLeft <= 0 -> "today"
                                            daysLeft == 1 -> "tomorrow"
                                            else -> "in ${daysLeft}d"
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                bill.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                "KSh ${bill.amount.toInt()} · $dueLabel",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (daysLeft <= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Spending breakdown — where this month's money went.
                    item {
                        val monthStart = com.pesaflow.app.data.academic.dayStart(
                            java.util.Calendar.getInstance().apply {
                                timeInMillis = System.currentTimeMillis()
                                set(java.util.Calendar.DAY_OF_MONTH, 1)
                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        )
                        // Same counting rule as MoneyMath.monthScopedTotal (see
                        // SmartInsightsEngine + budget worker): seeded/sample
                        // rows never inflate the breakdown behind budgets.
                        val monthExpenses = transactions.filter {
                            it.type == TransactionType.EXPENSE && it.dateTimestamp >= monthStart &&
                                it.source !in com.pesaflow.app.data.money.NON_STAT_SOURCES
                        }
                        val byCategory = monthExpenses.groupBy { it.category }
                            .mapValues { e -> e.value.sumOf { it.amount } }
                            .entries.sortedByDescending { it.value }
                            .take(4)
                        val totalMonth = byCategory.sumOf { it.value }
                        if (byCategory.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "This month · KSh ${totalMonth.toInt()} spent",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    byCategory.forEach { (cat, amt) ->
                                        val pct = if (totalMonth > 0) (amt / totalMonth * 100).toInt() else 0
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                cat,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                "KSh ${amt.toInt()} · $pct%",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Savings goals — progress toward what matters.
                    item {
                        val activeGoals = savingsGoals
                            .filter { it.currentAmount < it.targetAmount }
                            .sortedByDescending { it.currentAmount / it.targetAmount.coerceAtLeast(1.0) }
                            .take(3)
                        if (activeGoals.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Savings goals",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    activeGoals.forEach { goal ->
                                        val pct = (goal.currentAmount / goal.targetAmount.coerceAtLeast(1.0) * 100).toInt().coerceIn(0, 100)
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    goal.title,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    "$pct%",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            androidx.compose.material3.LinearProgressIndicator(
                                                progress = { pct / 100f },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            Spacer(Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if ("recent" !in hiddenSections) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Transactions", style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { onNavigate("search") }) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = "Search transactions",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        // Category quick-filter: tap a chip to see only that
                        // category's recent rows; tap again (or Clear) to reset.
                        val topCategories = transactions.filter { it.type == TransactionType.EXPENSE }
                            .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                            .entries.sortedByDescending { it.value }.take(4).map { it.key }
                        if (topCategories.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = ledgerFilter == null,
                                    onClick = { ledgerFilter = null },
                                    label = { Text("All", style = MaterialTheme.typography.bodySmall) }
                                )
                                topCategories.forEach { cat ->
                                    FilterChip(
                                        selected = ledgerFilter == cat,
                                        onClick = { ledgerFilter = if (ledgerFilter == cat) null else cat },
                                        label = { Text(cat, style = MaterialTheme.typography.bodySmall) }
                                    )
                                }
                            }
                        }
                        val visible = transactions.filter { ledgerFilter == null || it.category == ledgerFilter }
                            .sortedByDescending { it.dateTimestamp }.take(5)
                        if (!ledgerLoaded && visible.isEmpty()) {
                            // Room has not spoken yet — a skeleton, not the
                            // "Nothing yet" that reads as "you have no data".
                            PesaLoadingRow()
                        } else if (visible.isEmpty()) {
                            Text("Nothing yet — log or scan an expense.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            visible.forEach { tx ->
                                // Tap to audit: full date, source, and the raw
                                // SMS behind the row — "I never did this" needs
                                // a one-tap answer, not a guess.
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { editingTx = tx },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "${tx.merchant}: ${tx.amount.toKSh()} · ${tx.category}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "${tx.type.name.lowercase()} · ${tx.dateTimestamp.let { d -> android.text.format.DateFormat.format("MMM d, h:mm", d) }} · ${sourceLabel(tx.source)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (visible.size < transactions.filter { ledgerFilter == null || it.category == ledgerFilter }.size) {
                                TextButton(onClick = { onNavigate("transactions") }) {
                                    Text("View all")
                                }
                            }
                        }
                        }
                    }
                }
            )
        }
    }

    // Row audit dialog: every ledger row can explain itself — exact date,
    // where it came from (SMS / typed / onboarding figure / sample) and the
    // raw message behind it. Edit opens the same dialog as Transactions;
    // Delete offers Undo via the snackbar.
    editingTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { editingTx = null },
            title = { Text(tx.merchant, fontWeight = FontWeight.Bold, maxLines = 2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${if (tx.type == TransactionType.EXPENSE) "−" else "+"} ${tx.amount.toKSh()} · ${tx.category}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        android.text.format.DateFormat.format("EEE d MMM yyyy, h:mm a", tx.dateTimestamp).toString(),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Source: ${sourceLabel(tx.source)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (tx.description.isNotBlank()) {
                        Text(
                            when (tx.source) {
                                TransactionSource.MPESA_SMS,
                                TransactionSource.NOTIFICATION,
                                TransactionSource.SHARE_TO_APP -> "The message behind this row"
                                else -> "Why this row exists"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(tx.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransactionWithUndo(tx)
                    editingTx = null
                    scope.launch {
                        // Explicit Short + dismiss: M3 defaults action snackbars
                        // to Indefinite, which pins them over the nav bar until
                        // replaced — the stuck-banner report.
                        val result = snackbar.showSnackbar(
                            "Deleted ${tx.merchant}",
                            actionLabel = "Undo",
                            withDismissAction = true,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoLast()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        editingTx = null
                        quickEditTx = tx
                    }) { Text("Edit") }
                    TextButton(onClick = { editingTx = null }) { Text("Close") }
                }
            }
        )
    }

    // Edit from the dashboard audit dialog: same QuickAddDialog, pre-filled,
    // and the row keeps its id (real UPDATE, not delete+insert).
    quickEditTx?.let { tx ->
        QuickAddDialog(
            viewModel = viewModel,
            defaultType = tx.type,
            onDismiss = { quickEditTx = null },
            existing = tx
        )
    }
}

// Human-readable provenance for the audit dialog and ledger rows.
private fun sourceLabel(s: TransactionSource): String = when (s) {
    TransactionSource.MANUAL -> "typed by you"
    TransactionSource.MPESA_SMS -> "an M-Pesa SMS"
    TransactionSource.NOTIFICATION -> "a live notification"
    TransactionSource.SHARE_TO_APP -> "shared into the app"
    TransactionSource.RECEIPT_OCR -> "receipt scan"
    TransactionSource.CSV_IMPORT -> "CSV import"
    TransactionSource.NLP -> "your note"
    TransactionSource.OPENING -> "onboarding figure you entered"
    TransactionSource.SAMPLE -> "sample data"
}

// Sunday one-tap check-in: "which days did you go to school this week?"
// Renders only on Sundays and only until saved. Answers persist per week
// (checkin_days_<weekKey>) and SmartInsightsCard merges the latest week
// into the busy-day map, so a 5-second tap corrects the model.
@Composable
private fun SchoolCheckinCard() {
    val context = LocalContext.current
    if (!com.pesaflow.app.data.academic.isSunday()) return
    val prefs = remember {
        context.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
    }
    val key = remember { com.pesaflow.app.data.academic.weekKey() }
    var done by remember { mutableStateOf(prefs.getBoolean("checkin_done_$key", false)) }
    if (done) return
    var picked by remember {
        mutableStateOf(
            prefs.getString("checkin_days_$key", "").orEmpty()
                .split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        )
    }
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Which days did you go to school this week?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.take(4).forEach { d ->
                    FilterChip(
                        selected = d in picked,
                        onClick = { picked = if (d in picked) picked - d else picked + d },
                        label = { Text(d, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.drop(4).forEach { d ->
                    FilterChip(
                        selected = d in picked,
                        onClick = { picked = if (d in picked) picked - d else picked + d },
                        label = { Text(d, style = MaterialTheme.typography.bodySmall) }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = {
                    prefs.edit()
                        .putString("checkin_days_$key", picked.joinToString(","))
                        .putBoolean("checkin_done_$key", true)
                        .apply()
                    done = true
                }) {
                    Text("Save")
                }
            }
        }
    }
}
