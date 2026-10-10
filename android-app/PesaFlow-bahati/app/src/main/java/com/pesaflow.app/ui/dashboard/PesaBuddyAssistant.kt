package com.pesaflow.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.*
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.viewmodels.FinanceViewModel
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.SkinBuddy
import com.pesaflow.app.ui.theme.TintBuddyTwilight
import com.pesaflow.app.ui.theme.skinCardColor
import com.pesaflow.app.R


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PesaBuddyAssistant(
    viewModel: FinanceViewModel,
    onNavigate: (String) -> Unit = {},
    screenContext: String? = null
) {
    val availableBalance by viewModel.availableBalance.collectAsState()
    val monthlyIncome by viewModel.monthlyIncome.collectAsState()
    val monthlyExpenses by viewModel.monthlyExpenses.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val totalSavings by viewModel.totalSavings.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    var chatFocused by remember { mutableStateOf(false) }


    var chatInput by remember { mutableStateOf("") }
    val messages = remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    // Action execution: every BuddyBrain.BuddyAction funnels here. Destructive steps
    // only arrive via tapped cards (never prose), each reports what it did,
    // and anything reversible stays undoable on Home.
    val prefs = viewModel.getApplication<android.app.Application>()
        .getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
    fun sureRows(rows: List<PendingTransaction>): List<PendingTransaction> = rows.filter {
        com.pesaflow.app.data.parsers.PendingPolicy.isAutoApprovable(
            it.sourceTransactionId,
            com.pesaflow.app.data.ledger.ConfidenceMemory.effective(prefs, it.merchant, it.confidenceScore)
        )
    }
    fun runBuddyAction(host: ChatMessage, action: BuddyBrain.BuddyAction) {
        // Consume the card so it can't double-fire.
        messages.value = messages.value.map { if (it == host) it.copy(action = null) else it }
        val live = viewModel.pendingTransactions.value
        // Follow-ups carry the card's voice so taps answer in the language
        // the card was proposed in — never silently English.
        fun say(text: String) {
            messages.value = (messages.value + ChatMessage(text = text, isUser = false, lang = host.lang)).takeLast(40)
        }
        val strings = com.pesaflow.app.ui.buddy.BuddyStrings
        when (action) {
            is BuddyBrain.BuddyAction.ExecuteCommand -> {
                val result = com.pesaflow.app.ui.buddy.BuddyActionExecutor.execute(
                    viewModel, action.command, confirmed = true, lang = host.lang
                )
                say(result.message)
            }
            is BuddyBrain.BuddyAction.ConfirmAllSure -> {
                val rows = sureRows(live)
                if (rows.isEmpty()) say(strings.confirmNone(host.lang))
                else {
                    viewModel.approveAllPending(rows)
                    say(strings.confirmingSure(rows.size, host.lang))
                }
            }
            is BuddyBrain.BuddyAction.ConfirmMatch -> {
                val rows = BuddyBrain.rowsFor(action.merchant, action.amount, live)
                if (rows.isEmpty()) say(strings.rowsAlreadyCleared(host.lang))
                else {
                    rows.forEach { viewModel.approvePending(it, it.category) }
                    say(strings.confirmedCount(rows.size, host.lang))
                }
            }
            is BuddyBrain.BuddyAction.IgnoreMatch -> {
                val rows = if (action.merchant == null && action.amount == null) live
                else BuddyBrain.rowsFor(action.merchant, action.amount, live)
                if (rows.isEmpty()) say(strings.nothingToIgnore(host.lang))
                else {
                    rows.forEach { viewModel.rejectPending(it) }
                    say(strings.ignoredCount(rows.size, host.lang))
                }
            }
            is BuddyBrain.BuddyAction.CleanDuplicates -> {
                viewModel.sweepDuplicatePending { n ->
                    say(if (n == 0) strings.dupesClean(host.lang) else strings.dupesRemoved(n, host.lang))
                }
            }
            is BuddyBrain.BuddyAction.Categorize -> {
                val rows = BuddyBrain.rowsFor(action.merchant, null, live)
                if (rows.isEmpty()) say(strings.noRowsForName(host.lang))
                else {
                    rows.forEach { viewModel.approvePending(it, action.category) }
                    say(strings.filedUnder(rows.size, action.category, host.lang))
                }
            }
            is BuddyBrain.BuddyAction.SetBudget -> {
                viewModel.upsertBudget(action.category, action.amount, BudgetType.MONTHLY)
                say(strings.budgetSet(action.category, "KSh ${action.amount.toInt()}", host.lang))
            }
            is BuddyBrain.BuddyAction.AddBill -> {
                val due = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
                viewModel.addBill(action.name, action.amount, due, "Bills", "ONE_TIME")
                say(strings.billAdded(action.name, "KSh ${action.amount.toInt()}", 7, host.lang))
            }
            is BuddyBrain.BuddyAction.AddDebt -> {
                val due = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                viewModel.addDebt(action.person, action.amount, due, "", if (action.iOwe) "I_OWE" else "THEY_OWE")
                say(strings.debtRecorded(action.iOwe, action.person, "KSh ${action.amount.toInt()}", host.lang))
            }
            is BuddyBrain.BuddyAction.AddGoal -> {
                viewModel.addSavingsGoal(action.title, action.amount, 90)
                say(strings.goalCreated(action.title, "KSh ${action.amount.toInt()}", host.lang))
            }
            is BuddyBrain.BuddyAction.ReviewQueue -> {
                say(strings.sayReview(host.lang))
            }
            is BuddyBrain.BuddyAction.Undo -> {
                if (viewModel.hasUndo()) {
                    viewModel.undoLast()
                    say(strings.undone(host.lang))
                } else say(strings.nothingToUndo(host.lang))
            }
        }
    }


    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintBuddyTwilight, bgRes = R.drawable.bg_buddy_twilight, blurRadius = if (chatFocused) 4f else 0f)
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PesaBuddy 🤖", fontWeight = FontWeight.Bold)
                        Text("online • answers from your data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    TextButton(onClick = { messages.value = emptyList() }) {
                        Text("Clear", style = MaterialTheme.typography.bodySmall)
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
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Chat Messages Area
            AtmosphereBand(
                workspace = AtmoWorkspace.BUDDY,
                title = "Ask anything",
                subtitle = "Answers from your data",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            ScrollableChatArea(
                messages = messages.value,
                modifier = Modifier.weight(1f),
                onAction = { msg, action -> runBuddyAction(msg, action) },
                onDismissAction = { msg ->
                    messages.value = messages.value.map { if (it == msg) it.copy(action = null) else it }
                }
            )


            // Quick suggestions (tap to ask)
            if (messages.value.isEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Explain the app", "Why safe today?", "Review pending?", "Confirm sure ones?", "How am I doing?", "Top spends?", "Run out?", "Bills due?", "Afford 500?", "Laptop by December?", "My stock?", "What I lack?").forEach { s ->
                        AssistChip(onClick = { chatFocused = true; processUserInput(s, viewModel, messages, onNavigate = onNavigate, screenContext = screenContext) }, label = { Text(s) })
                    }
                }
            }


            // Input Area
            ChatInputArea(
                chatInput = chatInput,
                onFocusChange = { chatFocused = it },
                onSend = { userInput ->
                    processUserInput(userInput, viewModel, messages, onNavigate = onNavigate, screenContext = screenContext)
                    chatInput = ""
                    chatFocused = false
                }
            )
        }
    }
    }
}


@Composable
fun ChatInputArea(
    chatInput: String,
    onSend: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit = {}
) {
    var localInput by remember { mutableStateOf(chatInput) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = localInput,
            onValueChange = { localInput = it },
            placeholder = { Text("Ask PesaBuddy...") },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f).onFocusChanged { onFocusChange(it.isFocused) }
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                if (localInput.isNotBlank()) {
                    onSend(localInput)
                    localInput = ""
                }
            },
            shape = CircleShape,
            contentPadding = PaddingValues(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}


@Composable
fun ScrollableChatArea(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    onAction: (ChatMessage, BuddyBrain.BuddyAction) -> Unit = { _, _ -> },
    onDismissAction: (ChatMessage) -> Unit = {}
) {
    val state = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) state.animateScrollToItem(messages.size - 1)
    }
    LazyColumn(
        state = state,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(messages) { message ->
            ChatMessageBubble(message = message, onAction = onAction, onDismissAction = onDismissAction)
        }
    }
}


@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onAction: (ChatMessage, BuddyBrain.BuddyAction) -> Unit = { _, _ -> },
    onDismissAction: (ChatMessage) -> Unit = {}
) {
    val time = remember(message.timestamp) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isUser) MaterialTheme.colorScheme.primary else skinCardColor(SkinBuddy),
            shape = RoundedCornerShape(
                topStart = if (message.isUser) 16.dp else 4.dp,
                topEnd = if (message.isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                message.action?.let { action ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAction(message, action) }) {
                            Text("Do it ✓")
                        }
                        TextButton(onClick = { onDismissAction(message) }) {
                            Text("Not now")
                        }
                    }
                }
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}


enum class ChatMessageRole { USER, ASSISTANT }


data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    // Agentic actions ride on assistant messages: the card renders tap-to-run
    // buttons, and NOTHING executes from prose alone. Null = plain answer.
    val action: BuddyBrain.BuddyAction? = null,
    // Voice the card was proposed in, so tapping it answers in the same
    // language. Plain answers already carry their voice in the text.
    val lang: com.pesaflow.app.data.models.AppLanguage = com.pesaflow.app.data.models.AppLanguage.ENGLISH
)


// Phase-1 bridge: builds the canonical read state from the same ViewModel
// flows the screens read, runs the modular command pipeline, and renders the
// outcome. True = handled (legacy chain skipped); false = legacy continues.
fun runPhaseOne(
    userInput: String,
    viewModel: FinanceViewModel,
    messages: MutableState<List<ChatMessage>>,
    displayInput: String,
    onNavigate: (String) -> Unit,
    screenContext: String? = null
): Boolean {
    val fin = viewModel.financialSnapshot.value
    val openBills = viewModel.bills.value.filter { it.status != "PAID" }
    val openDebts = viewModel.debts.value.filter { it.status != "PAID" }
    // Simulation inputs only (never stored as truth): month-to-date burn
    // over elapsed days, and current rent from budget or own bills.
    val txs = viewModel.allTransactions.value
    val nowCal = java.util.Calendar.getInstance()
    val lastCal = (nowCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, -1) }
    fun inCal(ts: Long, cal: java.util.Calendar): Boolean {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        return c.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
            c.get(java.util.Calendar.MONTH) == cal.get(java.util.Calendar.MONTH)
    }
    val monthExpenseForBurn = txs.filter {
        it.type == TransactionType.EXPENSE && !it.isSample && inCal(it.dateTimestamp, nowCal)
    }.sumOf { it.amount }
    val lastMonthTx = txs.filter {
        it.type == TransactionType.EXPENSE && !it.isSample && inCal(it.dateTimestamp, lastCal)
    }
    fun topOf(rows: List<com.pesaflow.app.data.models.Transaction>): String =
        rows.groupingBy { it.category }.fold(0.0) { acc, t -> acc + t.amount }
            .maxByOrNull { it.value }?.key.orEmpty()
    val weekMs = 7L * 24 * 60 * 60 * 1000
    val dueSoon = openBills.filter {
        it.paidBy == "ME" && it.dueDate > 0 && it.dueDate <= System.currentTimeMillis() + weekMs
    }
    // Canonical semester runway: term dates + starting funds from the saved
    // profile, commitments from the financial engine. Null when unset.
    val semProfile = viewModel.universityProfile.value
    val semesterRunway = semProfile?.let { p ->
        com.pesaflow.app.data.finance.calculateSemesterRunway(
            transactions = txs,
            startTimestamp = p.semesterStartTimestamp,
            endTimestamp = p.semesterEndTimestamp,
            startingFunding = p.startingFunding,
            committed = fin.committed.toDouble(),
            now = System.currentTimeMillis()
        )
    }
    val dayOfMonth = nowCal.get(java.util.Calendar.DAY_OF_MONTH).coerceAtLeast(1)
    val budgetsNow = viewModel.budgets.value
    val currentRent = budgetsNow.firstOrNull {
        it.category.equals("Rent", ignoreCase = true) && it.type == BudgetType.MONTHLY
    }?.limitAmount ?: viewModel.bills.value.firstOrNull {
        it.paidBy == "ME" && it.status != "PAID" && it.name.contains("rent", ignoreCase = true)
    }?.amount
    // Response voice: the query's language wins, else the global setting.
    // Figures and entity names never change with it — only the words.
    val lang = com.pesaflow.app.ui.buddy.BuddyLanguage.resolve(
        userInput, viewModel.currentLanguage.value
    )
    val state = com.pesaflow.app.ui.buddy.BuddyReadState(
        lang = lang,
        liquid = fin.liquid.toDouble(),
        flexible = fin.flexible.toDouble(),
        safeToday = fin.safeToday.toDouble(),
        safeWeek = fin.safeWeek.toDouble(),
        openBillCount = openBills.size,
        openBillTotal = openBills.filter { it.paidBy == "ME" }
            .sumOf { it.amountRemaining.takeIf { r -> r > 0 } ?: it.amount },
        openDebtCount = openDebts.size,
        openDebtTotal = openDebts.sumOf { it.amount },
        goalCount = viewModel.savingsGoals.value.size,
        balanceExplanation = BuddyBrain.explainMetric(userInput, fin),
        dataQuality = fin.quality.name,
        dailyBurn = monthExpenseForBurn / dayOfMonth,
        currentRent = currentRent,
        reminderLines = com.pesaflow.app.data.notifications.BuddyReminderScheduler
            .list(viewModel.getApplication<android.app.Application>().applicationContext)
            .map { "• ${it.title} (${it.scheduleLabel})" },
        pendingCount = viewModel.pendingTransactions.value.size,
        thisMonthExpense = monthExpenseForBurn,
        thisMonthTop = topOf(txs.filter {
            it.type == TransactionType.EXPENSE && !it.isSample && inCal(it.dateTimestamp, nowCal)
        }),
        lastMonthExpense = lastMonthTx.sumOf { it.amount },
        lastMonthTop = topOf(lastMonthTx),
        billsDueSoonCount = dueSoon.size,
        billsDueSoonTotal = dueSoon.sumOf { it.amountRemaining.takeIf { r -> r > 0 } ?: it.amount },
        semester = semesterRunway,
        monthIncome = viewModel.monthlyIncome.value,
        expectedIncomeLines = com.pesaflow.app.data.income.expectedIncomeLandings(
            viewModel.incomeSources.value, System.currentTimeMillis(), 30
        ).take(5).map { (label, amount, at) ->
            val day = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
                .format(java.util.Date(at))
            "• $label · ${com.pesaflow.app.data.finance.MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(amount))} · $day"
        }
    )
    // Pending-queue rows stay on the legacy confirm cards: if the legacy
    // parser claims this query with an executable action, don't divert it.
    // (Review listing alone is not a claim — navigation may still win.)
    val legacyClaim = BuddyBrain.parseAction(
        BuddyBrain.normalize(userInput.lowercase()), viewModel.pendingTransactions.value
    )
    if (legacyClaim is BuddyBrain.BuddyProposal.Do &&
        legacyClaim.action !is BuddyBrain.BuddyAction.ReviewQueue
    ) return false
    // Screen-aware bare questions: "why?" on Budgets explains budget
    // progress from the same monthly math the screen shows.
    val calNow = java.util.Calendar.getInstance()
    val nowMs = calNow.timeInMillis
    val monthlyBudgetNow = com.pesaflow.app.data.finance.masterOrCategoryTotal(
        budgetsNow, BudgetType.MONTHLY, nowMs
    ).takeIf { it > 0.0 }
    val monthExpenseNow = txs.filter {
        it.type == TransactionType.EXPENSE && !it.isSample &&
            java.util.Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }.let { c ->
                c.get(java.util.Calendar.YEAR) == calNow.get(java.util.Calendar.YEAR) &&
                    c.get(java.util.Calendar.MONTH) == calNow.get(java.util.Calendar.MONTH)
            }
    }.sumOf { it.amount }
    val budgetExplanation = BuddyBrain.explainMetric(
        "why budget", fin.explanations, fin.liquid, monthlyBudgetNow, monthExpenseNow
    )
    return when (val outcome = com.pesaflow.app.ui.buddy.BuddyPhaseOne.handle(
        userInput, state, txs, txs.map { it.merchant }.distinct(), screenContext, budgetExplanation,
        viewModel.bills.value, viewModel.debts.value
    )) {
        is com.pesaflow.app.ui.buddy.BuddyOutcome.Compound -> {
            // Several requests, one message: answers merge into a single
            // reply, every mutation keeps its OWN confirm card, navigations
            // fire in order. Nothing executes from prose.
            val base = messages.value.filterNot { !it.isUser && it.text == "Thinking..." }
            val texts = mutableListOf<String>()
            val cards = mutableListOf<com.pesaflow.app.ui.buddy.BuddyOutcome.ProposeCommand>()
            outcome.parts.forEach { part ->
                when (part) {
                    is com.pesaflow.app.ui.buddy.BuddyOutcome.Answer -> texts.add(part.text)
                    is com.pesaflow.app.ui.buddy.BuddyOutcome.Ask -> texts.add(part.text)
                    is com.pesaflow.app.ui.buddy.BuddyOutcome.Navigate -> {
                        texts.add(part.label)
                        onNavigate(part.route)
                    }
                    is com.pesaflow.app.ui.buddy.BuddyOutcome.ExecuteNow -> {
                        val result = com.pesaflow.app.ui.buddy.BuddyActionExecutor.execute(
                            viewModel, part.command, confirmed = false, lang = lang
                        )
                        texts.add(result.message)
                    }
                    is com.pesaflow.app.ui.buddy.BuddyOutcome.ProposeCommand -> cards.add(part)
                    else -> Unit
                }
            }
            if (outcome.partial) texts.add(com.pesaflow.app.ui.buddy.BuddyStrings.partialNote(lang))
            val followed = mutableListOf(ChatMessage(text = displayInput, isUser = true))
            if (texts.isNotEmpty()) followed.add(ChatMessage(text = texts.joinToString("\n\n"), isUser = false))
            cards.forEach {
                followed.add(
                    ChatMessage(
                        text = it.summary + com.pesaflow.app.ui.buddy.BuddyStrings.tapToRun(lang),
                        isUser = false,
                        action = BuddyBrain.BuddyAction.ExecuteCommand(it.command),
                        lang = lang
                    )
                )
            }
            messages.value = (base + followed).takeLast(40)
            true
        }
        is com.pesaflow.app.ui.buddy.BuddyOutcome.ExecuteNow -> {
            val result = com.pesaflow.app.ui.buddy.BuddyActionExecutor.execute(
                viewModel, outcome.command, confirmed = false, lang = lang
            )
            val base = messages.value.filterNot { !it.isUser && it.text == "Thinking..." }
            messages.value = (base + listOf(
                ChatMessage(text = displayInput, isUser = true),
                ChatMessage(text = result.message, isUser = false)
            )).takeLast(40)
            true
        }
        is com.pesaflow.app.ui.buddy.BuddyOutcome.ProposeCommand -> {
            val base = messages.value.filterNot { !it.isUser && it.text == "Thinking..." }
            messages.value = (base + listOf(
                ChatMessage(text = displayInput, isUser = true),
                ChatMessage(
                    text = outcome.summary + com.pesaflow.app.ui.buddy.BuddyStrings.tapToRun(lang),
                    isUser = false,
                    action = BuddyBrain.BuddyAction.ExecuteCommand(outcome.command),
                    lang = lang
                )
            )).takeLast(40)
            true
        }
        is com.pesaflow.app.ui.buddy.BuddyOutcome.Answer -> {
            val base = messages.value.filterNot { !it.isUser && it.text == "Thinking..." }
            messages.value = (base + listOf(
                ChatMessage(text = displayInput, isUser = true),
                ChatMessage(text = outcome.text, isUser = false)
            )).takeLast(40)
            true
        }
        is com.pesaflow.app.ui.buddy.BuddyOutcome.Navigate -> {
            val base = messages.value.filterNot { !it.isUser && it.text == "Thinking..." }
            messages.value = (base + listOf(
                ChatMessage(text = displayInput, isUser = true),
                ChatMessage(text = outcome.label, isUser = false)
            )).takeLast(40)
            onNavigate(outcome.route)
            true
        }
        is com.pesaflow.app.ui.buddy.BuddyOutcome.Ask -> {
            val base = messages.value.filterNot { !it.isUser && it.text == "Thinking..." }
            messages.value = (base + listOf(
                ChatMessage(text = displayInput, isUser = true),
                ChatMessage(text = outcome.text, isUser = false)
            )).takeLast(40)
            true
        }
        com.pesaflow.app.ui.buddy.BuddyOutcome.Unhandled -> false
    }
}

fun processUserInput(
    userInput: String,
    viewModel: FinanceViewModel,
    messages: MutableState<List<ChatMessage>>,
    mlAssisted: Boolean = false,
    displayInput: String = userInput,
    onNavigate: (String) -> Unit = {},
    screenContext: String? = null
) {
    // Intent layer: normalize (append-only synonyms, old branches keep matching),
    // follow-up memory ("and yesterday?"), close-tie disambiguation.
    val qRaw = userInput.lowercase()
    val q = BuddyBrain.normalize(qRaw)
    BuddyBrain.rewriteFollowUp(qRaw, q)?.let {
        return processUserInput(it, viewModel, messages, displayInput = displayInput, onNavigate = onNavigate, screenContext = screenContext)
    }
    // Phase-1 command pipeline: typed intents over canonical state, navigation
    // through the real nav bridge. Handles read-only + navigation; anything
    // else returns false and the legacy chain below runs unchanged.
    if (runPhaseOne(userInput, viewModel, messages, displayInput, onNavigate, screenContext)) return
    val early: String? = BuddyBrain.disambiguate(q)
    // Trained-model assist: only when the rules draw a blank, once per
    // query. A sure model routes into its verified branch; anything else
    // falls through to the generic fallback exactly as before.
    if (early == null && !mlAssisted) {
        val ruleTop = BuddyBrain.classify(q).firstOrNull()?.conf ?: 0f
        if (ruleTop < 0.35f) {
            val ctx = viewModel.getApplication<android.app.Application>().applicationContext
            val suggestion = com.pesaflow.app.data.ml.MlIntentAssist.suggest(ctx, qRaw)
            if (suggestion != null && BuddyBrain.shouldMlAssist(ruleTop, suggestion.confidence)) {
                BuddyBrain.mlAssistExpansion(suggestion.label)?.let { expansion ->
                    return processUserInput(
                        "$userInput $expansion",
                        viewModel,
                        messages,
                        mlAssisted = true,
                        displayInput = displayInput,
                        onNavigate = onNavigate,
                        screenContext = screenContext
                    )
                }
            }
        }
    }
    // Agentic actions preempt answers: commands must never get answered as
    // questions. Read-only proposals (review) execute at once; everything
    // that writes renders as a tap-to-confirm card — this function executes
    // nothing itself.
    val pendings = viewModel.pendingTransactions.value
    // Card voice: the query's language wins, else the global setting — so a
    // later tap answers in the same language instead of defaulting English.
    val cardLang = com.pesaflow.app.ui.buddy.BuddyLanguage.resolve(
        userInput, viewModel.currentLanguage.value
    )
    BuddyBrain.parseAction(q, pendings)?.let { proposal ->
        val userMsg = ChatMessage(text = displayInput, isUser = true)
        val base = messages.value.filterNot { !it.isUser && it.text == "Thinking..." }
        when (proposal) {
            is BuddyBrain.BuddyProposal.Ask -> {
                messages.value = (base + listOf(userMsg, ChatMessage(text = proposal.text, isUser = false, lang = cardLang))).takeLast(40)
            }
            is BuddyBrain.BuddyProposal.Do -> {
                if (proposal.action is BuddyBrain.BuddyAction.ReviewQueue) {
                    val strings = com.pesaflow.app.ui.buddy.BuddyStrings
                    val top = pendings.take(5).joinToString("\n") {
                        "• ${it.merchant} · KSh ${it.amount.toInt()} · ${it.category}"
                    }.ifBlank { strings.reviewQueueEmpty(cardLang) }
                    val extra = if (pendings.size > 5) strings.reviewQueueMore(pendings.size - 5, cardLang) else ""
                    val hint = if (pendings.isNotEmpty()) strings.reviewQueueHint(cardLang) else ""
                    messages.value = (base + listOf(
                        userMsg,
                        ChatMessage(text = strings.reviewQueueWaiting(pendings.size, "$top$extra$hint", cardLang), isUser = false, lang = cardLang)
                    )).takeLast(40)
                } else {
                    messages.value = (base + listOf(
                        userMsg,
                        ChatMessage(
                            text = proposal.summary.replaceFirstChar { it.uppercase() } + " — tap below to run it. Nothing happens until you do.",
                            isUser = false,
                            action = proposal.action,
                            lang = cardLang
                        )
                    )).takeLast(40)
                }
            }
        }
        return
    }
    val txs = viewModel.allTransactions.value
    val nowCal = java.util.Calendar.getInstance()
    val nowMs = nowCal.timeInMillis
    val dayStart = (nowCal.clone() as java.util.Calendar).apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    // "This week" means the Monday-start calendar week, matching the
    // dashboard chart and every insight — not a rolling 7 days.
    val week = com.pesaflow.app.data.time.thisWeekRange(nowMs)
    fun inMonth(ts: Long): Boolean {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        return c.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
            c.get(java.util.Calendar.MONTH) == nowCal.get(java.util.Calendar.MONTH)
    }
    val monthTx = txs.filter { inMonth(it.dateTimestamp) && it.dateTimestamp <= nowMs && !it.isSample }
    val monthIncome = monthTx.filter { it.isEarnedIncome() }.sumOf { it.amount }
    val monthExpenses = monthTx.filter { it.type == TransactionType.EXPENSE }
    val monthExpense = monthExpenses.sumOf { it.amount }
    val todaySpend = txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp in dayStart..nowMs }.sumOf { it.amount }
    val weekSpend = txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp in week && com.pesaflow.app.data.time.inPastOrNow(it.dateTimestamp, nowMs) }.sumOf { it.amount }
    val financial = viewModel.financialSnapshot.value
    val balance = financial.liquid.toDouble()
    val saved = viewModel.totalSavings.value
    val goals = viewModel.savingsGoals.value
    val belongings = viewModel.belongings.value
    val pantry = viewModel.kitchenStock.value
    val byCat = monthTx.filter { it.type == TransactionType.EXPENSE }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
    val topCat = byCat.maxByOrNull { it.value }
    val budgets = viewModel.budgets.value
    val monthlyBudget = com.pesaflow.app.data.finance.masterOrCategoryTotal(
        budgets,
        BudgetType.MONTHLY,
        nowMs
    ).takeIf { it > 0.0 }
    val daysLeft = nowCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) - nowCal.get(java.util.Calendar.DAY_OF_MONTH) + 1
    val yesterdayStart = dayStart - 24L * 60 * 60 * 1000
    val yesterdaySpend = txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= yesterdayStart && it.dateTimestamp < dayStart }.sumOf { it.amount }
    val openBills = viewModel.bills.value.filter { it.status != "PAID" }
    // Engine uses amountRemaining (partial payments reduce the bill).
    // Amount alone double-counts money already paid.
    val openBillTotal = openBills.filter { it.paidBy == "ME" }.sumOf { it.amountRemaining.takeIf { r -> r > 0 } ?: it.amount }
    val externallyFundedBillTotal = openBills.filter { it.paidBy != "ME" }.sumOf { it.amountRemaining.takeIf { r -> r > 0 } ?: it.amount }
    val openDebts = viewModel.debts.value.filter { it.status != "PAID" }
    val openDebtTotal = openDebts.sumOf { it.amount }
    // Use the same obligation, reservation and Fuliza treatment as the
    // dashboard instead of rebuilding a second, potentially divergent total.
    val committed = financial.committed.toDouble()
    val freeBalance = financial.flexible.toDouble()
    val mealCount = viewModel.mealItems.value.size
    val foodBudgetAmt = budgets.firstOrNull {
        it.type == BudgetType.MONTHLY && it.category.equals("Food", ignoreCase = true)
    }?.limitAmount
    val askedCat = byCat.keys.filter { it !in listOf("Food", "Transport", "Rent", "Airtime", "Data") }.firstOrNull { q.contains(it.lowercase()) }
    val nmRaw = viewModel.nickname.value.ifBlank { viewModel.userName.value }
    val nmEx = if (nmRaw.isNotBlank()) " $nmRaw" else ""
    val foodTotal = byCat["Food"] ?: 0.0
    val transportTotal = byCat["Transport"] ?: 0.0
    // Persona tracks: advice divides by setup — far commuters never hear
    // "walk", non-cooks never hear "cook at home".
    val persona = com.pesaflow.app.ui.budgets.parsePersona(viewModel.getOnboardingAnswers())
    val farCommute = persona == com.pesaflow.app.ui.budgets.Persona.PARENTS_FAR || persona == com.pesaflow.app.ui.budgets.Persona.RENT_COMMUTE
    val noCook = persona == com.pesaflow.app.ui.budgets.Persona.HOSTEL_NOCOOK
    val response = BuddyBrain.explainMetric(
        q,
        financial.explanations,
        financial.liquid,
        monthlyBudget,
        monthExpense
    )
        ?: BuddyBrain.appGuide(q)
        ?: early
        ?: when {
        // Small talk first: these carry no money entities, and several overlap
        // greeting keywords ("habari yako" contains "habari"), so they must
        // precede the greeting branch to ever fire.
        q.contains("who are you") || q.contains("your name") || q.contains("jina lako") || q.contains("wewe ni nani") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.whoAreYou(cardLang)

        com.pesaflow.app.ui.buddy.BuddyTextNorm.hasWord(q, "joke") || com.pesaflow.app.ui.buddy.BuddyTextNorm.hasWord(q, "jokes") || q.contains("funny") || q.contains("chekesha") || q.contains("nichekeshe") ->
            run {
                val idx = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) % com.pesaflow.app.ui.buddy.BuddyStrings.JOKE_COUNT
                com.pesaflow.app.ui.buddy.BuddyStrings.joke(idx, cardLang)
            }

        com.pesaflow.app.ui.buddy.BuddyTextNorm.hasWord(q, "sorry") || com.pesaflow.app.ui.buddy.BuddyTextNorm.hasWord(q, "pole") || q.contains("samahani") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.sorryReply(cardLang)

        q.contains("how are you") || q.contains("uko aje") || q.contains("habari yako") || q.contains("uko vipi") || q.contains("how is it going") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.howAreYou(cardLang)

        q.contains("good morning") || q.contains("asubuhi njema") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.goodMorning(cardLang)

        q.contains("good night") || q.contains("usiku mwema") || q.contains("lala salama") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.goodNight(cardLang)

        q.contains("i love you") || q.contains("nakupenda") || q.contains("love you") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.loveYou(cardLang)

        q.contains("hello") || q == "hi" || q.contains("hey") || q.contains("habari") || q.contains("sasa") || q.contains("mambo") ->
            run {
                // Onboarding answers feed the greeting: your stated worry, if any.
                val worry = viewModel.getOnboardingAnswers().split("|")
                    .firstOrNull { it.startsWith("worry=") }
                    ?.removePrefix("worry=")?.takeIf { it.isNotBlank() }
                com.pesaflow.app.ui.buddy.BuddyStrings.greeting(nmEx, worry, cardLang)
            }

        q.contains("help") || q.contains("what can you") || q.contains("how do i") || q.contains("unaeza") || q.contains("nisaidie") || q.contains("saidia") ->
            BuddyBrain.appGuide("explain the app") ?: com.pesaflow.app.ui.buddy.BuddyStrings.helpFallback(cardLang)

        q.contains("thank") || q.contains("asante") || q.contains("poa") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.thanks(cardLang)

        // Campus-life topics: specific keywords, no money-entity overlap, so
        // they sit with small talk — before finance branches ever see them.
        (q.contains("another") && (q.contains("joke") || q.contains("one"))) || q.contains("ingine") || q.contains("eka ingine") || q.contains("more jokes") ->
            run {
                val idx = (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) + 1) % com.pesaflow.app.ui.buddy.BuddyStrings.JOKE_COUNT
                com.pesaflow.app.ui.buddy.BuddyStrings.joke(idx, cardLang)
            }

        q.contains("exam") || q.contains("mtihani") || q.contains("revision") || q.contains("kusoma") ->
            run {
                val tip = if (noCook) "Hostel cooker or kiosk ugali over daily chips runs — protect the study-fuel line first."
                else "Bulk-cook ugali + sukuma + ndengu twice a week; ad-hoc kibanda runs are where exam-month budgets die."
                com.pesaflow.app.ui.buddy.BuddyStrings.examSeason(tip, cardLang)
            }

        (q.contains("helb") && (q.contains("lini") || q.contains("when") || q.contains("come") || q.contains("kuja") || q.contains("watch"))) ->
            run {
                val expected = viewModel.universityProfile.value?.helbExpected ?: 0.0
                if (expected > 0) com.pesaflow.app.ui.buddy.BuddyStrings.helbWatch(expected.toInt().toString(), cardLang)
                else com.pesaflow.app.ui.buddy.BuddyStrings.helbWatchNone(cardLang)
            }

        (q.contains("nauli") && (q.contains("panda") || q.contains("high") || q.contains("hike") || q.contains("expensive"))) || q.contains("fare hike") || q.contains("fare high") ->
            run {
                val appCtx = viewModel.getApplication<android.app.Application>().applicationContext
                val farePrefs = appCtx.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                val oneWay = farePrefs.getString("school_fare_one_way", null)?.toDoubleOrNull() ?: 0.0
                val label = if (oneWay > 0) oneWay.toInt().toString() else "your usual"
                com.pesaflow.app.ui.buddy.BuddyStrings.fareAdvice(label, (byCat["Transport"] ?: 0.0).toInt().toString(), cardLang)
            }

        q.contains("nini nikule") || q.contains("nile nini") || q.contains("cheap meal") || q.contains("cheap food") || q.contains("broke meal") || q.contains("what should i eat") ->
            run {
                val cheapest = viewModel.mealItems.value.filter { it.source == "Cook" && it.price > 0 }.minByOrNull { it.price }
                if (cheapest != null) com.pesaflow.app.ui.buddy.BuddyStrings.cheapEats(cheapest.name, cheapest.price.toInt().toString(), cardLang)
                else com.pesaflow.app.ui.buddy.BuddyStrings.cheapEatsNone(cardLang)
            }

        q.contains("how do i save") || q.contains("how to save") || q.contains("nita-save") || q.contains("nisave") || q.contains("saving tip") || q.contains("kuweka akiba") ->
            run {
                val progress = if (goals.isEmpty()) "no savings goal yet — set one under Goals and I'll track it"
                else {
                    val g = goals.maxByOrNull { it.targetAmount }
                    if (g != null && g.targetAmount > 0) "${g.title}: KSh ${g.currentAmount.toInt()} of KSh ${g.targetAmount.toInt()} (${(g.currentAmount / g.targetAmount * 100).toInt()}%)"
                    else "${goals.size} goal${if (goals.size == 1) "" else "s"} on the board"
                }
                com.pesaflow.app.ui.buddy.BuddyStrings.saveTip(progress, cardLang)
            }

        q.contains("nimechoka") || q.contains("give up") || q.contains("demotivat") || q.contains("nimegive") || q.contains("tired of being broke") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.pepTalk(cardLang)

        (q.contains("transport") && (q.contains("yangu") || q.contains("month") || q.contains("mwezi") || q.contains("total"))) || q.contains("usafiri wangu") || q.contains("my rides") ->
            run {
                val rides = monthExpenses.filter { it.category.equals("Transport", ignoreCase = true) }
                com.pesaflow.app.ui.buddy.BuddyStrings.transportMonth(rides.sumOf { it.amount }.toInt().toString(), rides.size.toString(), cardLang)
            }

        q.contains("nishauri") || q.contains("shauri") || (q.contains("advice") && !q.contains("afford")) ->
            run {
                val cat = topCat?.key ?: "Food"
                val amt = (topCat?.value ?: 0.0).toInt().toString()
                val tip = when {
                    cat.equals("Food", ignoreCase = true) -> if (noCook) "Kiosk ugali + sukuma plates beat daily chips money — same fullness, half the spend." else "Two bulk-cook days a week; the kibanda is a convenience tax."
                    cat.equals("Transport", ignoreCase = true) -> if (farCommute) "Far commute is fixed — attack the fare, not the trip: off-peak travel and the 2 km walk rule." else "Walk trips under 2 km; your legs are a free SACCO."
                    cat.equals("Airtime", ignoreCase = true) || cat.equals("Data", ignoreCase = true) -> "Night bundles + Wi-Fi-first downloads; daytime streaming is the leak."
                    else -> "Cap it with a weekly envelope and check it every Sunday — awareness is half the saving."
                }
                com.pesaflow.app.ui.buddy.BuddyStrings.adviceTip(cat, amt, tip, cardLang)
            }

        q.contains("today") || q.contains("leo") ->
            if (txs.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.todayEmpty(cardLang)
            else com.pesaflow.app.ui.buddy.BuddyStrings.todaySpend(todaySpend.toInt().toString(), monthExpense.toInt().toString(), cardLang)

        (q.contains("week") || q.contains("wiki")) && askedCat != null ->
            com.pesaflow.app.ui.buddy.BuddyStrings.weekCategory(askedCat, txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp in week && com.pesaflow.app.data.time.inPastOrNow(it.dateTimestamp, nowMs) && it.category.equals(askedCat, ignoreCase = true) }.sumOf { it.amount }.toInt().toString(), cardLang)

        ((q.contains("yesterday") || q.contains("jana")) && askedCat != null) ->
            com.pesaflow.app.ui.buddy.BuddyStrings.yesterdayCategory(askedCat, txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= yesterdayStart && it.dateTimestamp < dayStart && it.category.equals(askedCat, ignoreCase = true) }.sumOf { it.amount }.toInt().toString(), cardLang)

        q.contains("top spends") || q.contains("biggest expenses") || q.contains("largest expenses") || q.contains("orodha") ->
            run {
                val top5 = txs.filter { it.type == TransactionType.EXPENSE && !it.isSample }.sortedByDescending { it.amount }.take(5)
                if (top5.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.topEmpty(cardLang)
                else com.pesaflow.app.ui.buddy.BuddyStrings.topHits(top5.joinToString("; ") { "${it.merchant.take(20)} KSh ${it.amount.toInt()}" }, cardLang)
            }

        q.contains("run out") || q.contains("nitakwisha") || q.contains("how long will") ->
            run {
                if (txs.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.runOutNoData(cardLang)
                else {
                    val elapsed = com.pesaflow.app.data.time.daysElapsedInWeek(nowMs).coerceAtLeast(1)
                    val burn = if (weekSpend > 0) weekSpend / elapsed else monthExpense / 30
                    if (burn <= 0 || freeBalance <= 0) {
                        if (balance <= 0) com.pesaflow.app.ui.buddy.BuddyStrings.runOutNoBurn(balance.toInt().toString(), cardLang)
                        else com.pesaflow.app.ui.buddy.BuddyStrings.runOutHeld(balance.toInt().toString(), committed.toInt().toString(), freeBalance.toInt().toString(), cardLang)
                    } else com.pesaflow.app.ui.buddy.BuddyStrings.runOutRunway(burn.toInt().toString(), freeBalance.toInt().toString(), (freeBalance / burn).toInt().toString(), committed, cardLang)
                }
            }

        q.contains("due this week") || q.contains("due soon") || q.contains("bills due") || q.contains("inadaiwa") ->
            run {
                val soon = openBills.filter { it.dueDate <= System.currentTimeMillis() + 7 * 24L * 60 * 60 * 1000 }.sortedBy { it.dueDate }
                if (soon.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.billsDueNone(cardLang)
                else com.pesaflow.app.ui.buddy.BuddyStrings.billsDue(soon.joinToString("; ") { "${it.name} KSh ${it.amount.toInt()}" }, cardLang)
            }

        q.contains("week") || q.contains("wiki") || q.contains("7 days") ->
            if (txs.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.weekEmpty(cardLang)
            else com.pesaflow.app.ui.buddy.BuddyStrings.weekSpend(weekSpend.toInt().toString(), (weekSpend / com.pesaflow.app.data.time.daysElapsedInWeek(nowMs).coerceAtLeast(1)).toInt().toString(), cardLang)

        q.contains("budget") || q.contains("bajeti") ->
            if (monthlyBudget == null) com.pesaflow.app.ui.buddy.BuddyStrings.budgetNone(cardLang)
            else {
                val pct = (monthExpense / monthlyBudget * 100).toInt()
                com.pesaflow.app.ui.buddy.BuddyStrings.budgetStatus(monthlyBudget.toInt().toString(), monthExpense.toInt().toString(), pct, cardLang)
            }

        q.contains("safe") || q.contains("daily") || q.contains("per day") || q.contains("kila siku") || q.contains("can i spend") ->
            if (txs.isEmpty() && monthlyBudget == null) com.pesaflow.app.ui.buddy.BuddyStrings.safeNone(cardLang)
            else {
                val budgetRemaining = monthlyBudget?.let { (it - monthExpense).coerceAtLeast(0.0) } ?: freeBalance
                val daily = minOf(
                    financial.safeToday.toDouble().coerceAtLeast(0.0).toInt(),
                    buddySafeDaily(budgetRemaining, freeBalance, daysLeft)
                )
                com.pesaflow.app.ui.buddy.BuddyStrings.safeGuide(daily.toString(), daysLeft.toString(), committed, cardLang)
            }

        q.contains("biggest") || q.contains("largest") || q.contains("most") || q.contains("mingi") || q.contains("kubwa") || q.contains("natumia pesa mingi") || q.contains("burn") || q.contains("wapi") ->
            if (topCat == null) com.pesaflow.app.ui.buddy.BuddyStrings.biggestNone(cardLang)
            else com.pesaflow.app.ui.buddy.BuddyStrings.biggestTop(topCat.key, topCat.value.toInt().toString(), monthExpense.toInt().toString(), cardLang)

        q.contains("balance") || q.contains("baki") || q.contains("salio") || q.contains("niko na") || q.contains("remaining") || q.contains("left") || q.contains("nisalio") ->
            run {
                val appCtx = viewModel.getApplication<android.app.Application>().applicationContext
                val mp = com.pesaflow.app.data.parsers.readMpesaBalance(appCtx)
                com.pesaflow.app.ui.buddy.BuddyStrings.balanceLine(balance.toInt().toString(), monthIncome.toInt().toString(), monthExpense.toInt().toString(), mp?.let { " M-Pesa SMS says KSh ${it.first.toInt()} (${com.pesaflow.app.data.parsers.balanceAgeText(it.second, System.currentTimeMillis())})." } ?: "", cardLang)
            }

        q.contains("afford") || q.contains("naeza") || q.contains("can i buy") ->
            run {
                val num = Regex("(\\d[\\d,]*)").find(q)?.value?.replace(",", "")?.toDoubleOrNull()
                    ?: BuddyBrain.extractAmount(q)
                val wctx = viewModel.getApplication<android.app.Application>().applicationContext
                val wnote = com.pesaflow.app.data.parsers.readMpesaBalance(wctx)?.let { " (wallet: KSh ${it.first.toInt()})" } ?: ""
                if (num == null) com.pesaflow.app.ui.buddy.BuddyStrings.affordPrice(cardLang)
                else if (num <= freeBalance && (monthlyBudget == null || monthExpense + num <= monthlyBudget)) com.pesaflow.app.ui.buddy.BuddyStrings.affordYes(balance.toInt().toString(), freeBalance.toInt().toString(), committed.toInt().toString(), financial.riskBuffer.toDouble().toInt().toString(), num.toInt().toString(), monthlyBudget != null, wnote, cardLang)
                else com.pesaflow.app.ui.buddy.BuddyStrings.affordNo(balance.toInt().toString(), freeBalance.toInt().toString(), committed.toInt().toString(), financial.riskBuffer.toDouble().toInt().toString(), num.toInt().toString(), monthlyBudget?.let { (it - monthExpense).toInt().toString() }, wnote, cardLang)
            }

        q.contains("split") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.splitHelp(cardLang)

        q.contains("transport") || q.contains("fare") || q.contains("nauli") || q.contains("matatu") || q.contains("boda") ->
            run {
                val fareSrc = viewModel.incomeSources.value.firstOrNull {
                    (it.kind == "PARENT" || it.kind == "GUARDIAN") &&
                        (it.frequency == "DAILY" || it.frequency == "WEEKLY") && it.expectedAmount > 0
                }
                if ((q.contains("when") || q.contains("coming") || q.contains("allowance") || q.contains("give")) && fareSrc != null) {
                    val who = fareSrc.label.ifBlank { fareSrc.displayKind() }
                    when (fareSrc.frequency) {
                        "DAILY" -> com.pesaflow.app.ui.buddy.BuddyStrings.fareDaily(who, fareSrc.expectedAmount.toInt().toString(), freeBalance.toInt().toString(), cardLang)
                        else -> com.pesaflow.app.ui.buddy.BuddyStrings.fareWeekly(who, fareSrc.expectedAmount.toInt().toString(), freeBalance.toInt().toString(), cardLang)
                    }
                } else {
                    val facts = viewModel.userContextFacts.value
                    val home = facts["housing.current"] ?: "home"
                    val mode = facts["transport.primaryMode"] ?: "your usual mode"
                    val stages = facts["transport.homeToCampus"]
                    if (txs.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.transportNone(cardLang)
                    else com.pesaflow.app.ui.buddy.BuddyStrings.transportRoute(home, mode, stages, cardLang)
                }
            }

        q.contains("yesterday") || q.contains("jana") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.yesterdaySpend(yesterdaySpend.toInt().toString(), todaySpend.toInt().toString(), cardLang)

        q.contains("busy") || q.contains("free") || q.contains("lecture") || q.contains("timetable") || q.contains("darasa") ->
            run {
                val appCtx = viewModel.getApplication<android.app.Application>().applicationContext
                val week = com.pesaflow.app.data.schedule.WeekPlan.load(appCtx)
                if (week.values.all { it.isEmpty() }) com.pesaflow.app.ui.buddy.BuddyStrings.timetableEmpty(cardLang)
                else {
                    val dayMap = listOf("monday" to "Mon", "tuesday" to "Tue", "wednesday" to "Wed", "thursday" to "Thu", "friday" to "Fri", "saturday" to "Sat", "sunday" to "Sun")
                    val named = dayMap.firstOrNull { q.contains(it.first) }?.second
                    if (named != null) {
                        val slots = week[named].orEmpty()
                        if (slots.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.dayFree(named, cardLang)
                        else {
                            val tip = com.pesaflow.app.data.schedule.WeekPlan.suggestionFor(named, slots) ?: ""
                            com.pesaflow.app.ui.buddy.BuddyStrings.dayBusy(named, slots.sorted().joinToString(", "), tip, cardLang)
                        }
                    } else if (q.contains("cook") || q.contains("free")) {
                        val free = com.pesaflow.app.data.schedule.WeekPlan.freeEvenings(appCtx)
                        if (free.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.eveningsBusy(cardLang)
                        else com.pesaflow.app.ui.buddy.BuddyStrings.freeEvenings(free.joinToString(", "), cardLang)
                    } else {
                        val busiest = week.maxByOrNull { it.value.size }
                        com.pesaflow.app.ui.buddy.BuddyStrings.heaviestDay(busiest?.key ?: "—", (busiest?.value?.size ?: 0).toString(), cardLang)
                    }
                }
            }

        q.contains("summary") || q.contains("breakdown") || q.contains("split") || q.contains("report") || q.contains("overview") || q.contains("muhtasari") ->
            if (txs.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.summaryEmpty(cardLang)
            else {
                val top3 = byCat.entries.sortedByDescending { it.value }.take(3)
                    .joinToString("; ") { "${it.key} KSh ${it.value.toInt()}" }
                com.pesaflow.app.ui.buddy.BuddyStrings.summaryLine(monthIncome.toInt().toString(), monthExpense.toInt().toString(), balance.toInt().toString(), top3, cardLang)
            }

        q.contains("how am i") || q.contains("nitakuwa aje") || q.contains("verdict") || q.contains("am i ok") || q.contains("naisonga") ->
            if (txs.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.verdictEmpty(cardLang)
            else if (monthIncome > 0 && monthExpense > monthIncome) com.pesaflow.app.ui.buddy.BuddyStrings.verdictOver(nmEx, (monthExpense - monthIncome).toInt().toString(), topCat?.key ?: "variable costs", cardLang)
            else if (monthIncome > 0) com.pesaflow.app.ui.buddy.BuddyStrings.verdictOnTrack(nmEx, (monthIncome - monthExpense).toInt().toString(), monthIncome.toInt().toString(), cardLang)
            else com.pesaflow.app.ui.buddy.BuddyStrings.verdictNoIncome(monthExpense.toInt().toString(), cardLang)

        askedCat != null ->
            com.pesaflow.app.ui.buddy.BuddyStrings.categorySpend(askedCat, byCat[askedCat]!!.toInt().toString(), cardLang)

        q.contains("bill") || q.contains("rent due") || q.contains("lipia") ->
            if (openBills.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.billsNone(cardLang)
            else com.pesaflow.app.ui.buddy.BuddyStrings.billsLine(openBills.size.toString(), openBillTotal.toInt().toString(), externallyFundedBillTotal.toInt().toString(), openBills.take(3).joinToString("; ") {
                    "${it.name} KSh ${it.amount.toInt()} (${com.pesaflow.app.ui.buddy.BuddyStrings.billWho(it.paidBy, cardLang)})"
                }, cardLang)

        q.contains("debt") || q.contains("owe") || q.contains("borrow") || q.contains("madeni") || q.contains("deni") ->
            if (openDebts.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.debtsNone(cardLang)
            else com.pesaflow.app.ui.buddy.BuddyStrings.debtsLine(openDebts.size.toString(), openDebtTotal.toInt().toString(), openDebts.take(3).joinToString("; ") { "${it.person} KSh ${it.amount.toInt()}" }, cardLang)

        q.contains("menu") || q.contains("meal") || q.contains("food budget") || q.contains("chakula") ->
            if (mealCount == 0) com.pesaflow.app.ui.buddy.BuddyStrings.menuEmpty(cardLang)
            else com.pesaflow.app.ui.buddy.BuddyStrings.menuLine(mealCount.toString(), foodBudgetAmt?.toInt()?.toString(), foodBudgetAmt?.let { (it / 30).toInt().toString() }, cardLang)

        q.contains("bye") || q.contains("later") || q.contains("baadaye") || q.contains("tutaonana") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.bye(cardLang)

        q.contains("cut") || q.contains("reduce") || q.contains("punguza") || q.contains("what can i save") ->
            run {
                val tips = mutableListOf<String>()
                if (foodTotal > monthExpense * 0.3) tips.add(
                    com.pesaflow.app.ui.buddy.BuddyStrings.cutTipFood(((foodTotal / monthExpense) * 100).toInt().toString(), (foodTotal * 0.2).toInt().toString(), !noCook, cardLang)
                )
                if (transportTotal > monthExpense * 0.2) tips.add(
                    com.pesaflow.app.ui.buddy.BuddyStrings.cutTipTransport(farCommute, cardLang)
                )
                if (byCat.any { it.key == "Kujibamba" && it.value > monthExpense * 0.1 }) tips.add(com.pesaflow.app.ui.buddy.BuddyStrings.cutTipKujibamba(((byCat["Kujibamba"]!! / monthExpense) * 100).toInt().toString(), cardLang))
                if (byCat.any { it.key == "Airtime" && it.value > 500 }) tips.add(com.pesaflow.app.ui.buddy.BuddyStrings.cutTipAirtime(byCat["Airtime"]!!.toInt().toString(), cardLang))
                if (tips.isEmpty()) tips.add(com.pesaflow.app.ui.buddy.BuddyStrings.cutTipBalanced(cardLang))
                com.pesaflow.app.ui.buddy.BuddyStrings.cutHead(tips.joinToString(". "), cardLang)
            }

        q.contains("track") || q.contains("on track") || q.contains("am i") || q.contains("progress") ->
            if (monthlyBudget == null) com.pesaflow.app.ui.buddy.BuddyStrings.trackNone(cardLang)
            else {
                val pct = (monthExpense / monthlyBudget * 100).toInt()
                val daysInMonth = nowCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                val dayOfMonth = nowCal.get(java.util.Calendar.DAY_OF_MONTH)
                val expected = monthlyBudget * dayOfMonth / daysInMonth
                val diff = monthExpense - expected
                if (monthExpense <= expected) com.pesaflow.app.ui.buddy.BuddyStrings.trackOnTrack(monthExpense.toInt().toString(), expected.toInt().toString(), dayOfMonth.toString(), pct.toString(), cardLang)
                else com.pesaflow.app.ui.buddy.BuddyStrings.trackOver(diff.toInt().toString(), monthExpense.toInt().toString(), expected.toInt().toString(), pct.toString(), daysLeft.toString(), cardLang)
            }

        q.contains("compare") || q.contains("vs last") || q.contains("difference") ->
            run {
                val ref = (nowCal.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.MONTH, -1)
                    set(
                        java.util.Calendar.DAY_OF_MONTH,
                        minOf(
                            nowCal.get(java.util.Calendar.DAY_OF_MONTH),
                            getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                        )
                    )
                }
                val previousMonthStart = (ref.clone() as java.util.Calendar).apply {
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val lastMonthExp = txs.filter {
                    it.type == TransactionType.EXPENSE && !it.isSample &&
                        it.dateTimestamp >= previousMonthStart && it.dateTimestamp <= ref.timeInMillis
                }.sumOf { it.amount }
                if (monthExpenses.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.compareThin(cardLang)
                else if (lastMonthExp <= 0) com.pesaflow.app.ui.buddy.BuddyStrings.compareNoLast(cardLang)
                else {
                    val diff = monthExpense - lastMonthExp
                    val pct = ((diff / lastMonthExp) * 100).toInt()
                    val throughDay = nowCal.get(java.util.Calendar.DAY_OF_MONTH)
                    val note = com.pesaflow.app.ui.buddy.BuddyStrings.compareNote(throughDay.toString(), cardLang)
                    if (diff <= 0) note + com.pesaflow.app.ui.buddy.BuddyStrings.compareLower(monthExpense.toInt().toString(), lastMonthExp.toInt().toString(), (-pct).toString(), cardLang)
                    else note + com.pesaflow.app.ui.buddy.BuddyStrings.compareHigher(monthExpense.toInt().toString(), lastMonthExp.toInt().toString(), pct.toString(), topCat?.key ?: "spending", cardLang)
                }
            }

        q.contains("all category") || q.contains("breakdown") || q.contains("every category") ->
            if (byCat.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.breakdownNone(cardLang)
            else {
                val lines = byCat.entries.sortedByDescending { it.value }.take(8)
                    .joinToString("; ") { "${it.key} KSh ${it.value.toInt()}" }
                com.pesaflow.app.ui.buddy.BuddyStrings.breakdownLines(lines, monthExpense.toInt().toString(), cardLang)
            }

        q.contains("saving") || q.contains("saved") || q.contains("how much saved") ->
            if (goals.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.savingsNone(cardLang)
            else {
                val totalTarget = goals.sumOf { it.targetAmount }
                val totalSaved = goals.sumOf { it.currentAmount }
                val pct = if (totalTarget > 0) (totalSaved / totalTarget * 100).toInt() else 0
                com.pesaflow.app.ui.buddy.BuddyStrings.savingsLine(totalSaved.toInt().toString(), totalTarget.toInt().toString(), pct.toString(), goals.size.toString(), cardLang)
            }

        q.contains("food") && (q.contains("how much") || q.contains("spend")) ->
            com.pesaflow.app.ui.buddy.BuddyStrings.foodMonth(foodTotal.toInt().toString(), foodBudgetAmt?.let { com.pesaflow.app.ui.buddy.BuddyStrings.foodBudgetState(it.toInt().toString(), foodTotal <= it, cardLang) }, cardLang)

        q.contains("rent") && (q.contains("how much") || q.contains("spend")) ->
            com.pesaflow.app.ui.buddy.BuddyStrings.rentMonth((byCat["Rent"] ?: 0.0).toInt().toString(), cardLang)

        // Goal planning: "can I afford laptop by December?" / "saving for laptop 10000" / "laptop 10000 by dec"
        q.contains("by") && (q.contains("dec") || q.contains("jan") || q.contains("feb") || q.contains("mar") ||
            q.contains("apr") || q.contains("may") || q.contains("jun") || q.contains("jul") ||
            q.contains("aug") || q.contains("sep") || q.contains("oct") || q.contains("nov")) ->
            run {
                val targetMonth = when {
                    q.contains("dec") -> java.util.Calendar.DECEMBER
                    q.contains("jan") -> java.util.Calendar.JANUARY
                    q.contains("feb") -> java.util.Calendar.FEBRUARY
                    q.contains("mar") -> java.util.Calendar.MARCH
                    q.contains("apr") -> java.util.Calendar.APRIL
                    q.contains("may") -> java.util.Calendar.MAY
                    q.contains("jun") -> java.util.Calendar.JUNE
                    q.contains("jul") -> java.util.Calendar.JULY
                    q.contains("aug") -> java.util.Calendar.AUGUST
                    q.contains("sep") -> java.util.Calendar.SEPTEMBER
                    q.contains("oct") -> java.util.Calendar.OCTOBER
                    q.contains("nov") -> java.util.Calendar.NOVEMBER
                    else -> java.util.Calendar.DECEMBER
                }
                val price = Regex("(\\d[\\d,]*)").find(q)?.value?.replace(",", "")?.toDoubleOrNull()
                if (price == null || price <= 0) {
                    com.pesaflow.app.ui.buddy.BuddyStrings.goalPrice(cardLang)
                } else {
                    val target = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.MONTH, targetMonth)
                        set(java.util.Calendar.DAY_OF_MONTH, getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
                        set(java.util.Calendar.HOUR_OF_DAY, 23)
                        set(java.util.Calendar.MINUTE, 59)
                        set(java.util.Calendar.SECOND, 59)
                    }.timeInMillis
                    val now = System.currentTimeMillis()
                    val daysLeft = ((target - now) / (24L * 60 * 60 * 1000)).coerceAtLeast(1)
                    val savedAlready = goals.filter { it.targetTimestamp <= target }.sumOf { it.currentAmount }
                    val remaining = (price - savedAlready).coerceAtLeast(0.0)
                    val budgetPool = monthlyBudget?.let { (it - monthExpense).coerceAtLeast(0.0) } ?: freeBalance
                    val safePool = minOf(budgetPool, freeBalance)
                    val surplusPerDay = (safePool / daysLeft).toInt()
                    val neededPerDay = (remaining / daysLeft).toInt()
                    if (remaining <= 0) {
                        com.pesaflow.app.ui.buddy.BuddyStrings.goalSaved(savedAlready.toInt().toString(), price.toInt().toString(), cardLang)
                    } else if (neededPerDay <= surplusPerDay && surplusPerDay > 0) {
                        com.pesaflow.app.ui.buddy.BuddyStrings.goalFits(neededPerDay.toString(), daysLeft.toString(), cardLang)
                    } else if (neededPerDay <= (freeBalance / daysLeft).toInt()) {
                        com.pesaflow.app.ui.buddy.BuddyStrings.goalTight(neededPerDay.toString(), topCat?.key ?: "spending", cardLang)
                    } else {
                        com.pesaflow.app.ui.buddy.BuddyStrings.goalMiss(price.toInt().toString(), java.text.SimpleDateFormat("MMM", java.util.Locale.US).format(java.util.Date(target)), neededPerDay.toString(), surplusPerDay.toString(), cardLang)
                    }
                }
            }

        userInput.lowercase().contains("goal") || userInput.lowercase().contains("target") || userInput.lowercase().contains("saving for") ->
            if (goals.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.goalsNone2(cardLang)
            else {
                val summary = goals.joinToString("; ") { g ->
                    val pct = if (g.targetAmount > 0) (g.currentAmount / g.targetAmount * 100).toInt() else 0
                    "${g.title}: KSh ${g.currentAmount.toInt()}/${g.targetAmount.toInt()} ($pct%)"
                }
                com.pesaflow.app.ui.buddy.BuddyStrings.goalsLine(summary, cardLang)
            }

        userInput.lowercase().contains("salary") || userInput.lowercase().contains("income") ->
            run {
                val topIn = monthTx.filter { it.isEarnedIncome() }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.maxByOrNull { it.value }
                val appSources = viewModel.incomeSources.value
                val expected = appSources.sumOf { com.pesaflow.app.data.income.IncomeSourceStore.budgetedMonthly(it) }
                val declared = appSources.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { it.displayKind() + if (it.expectedAmount > 0) " " + it.expectedAmount.toInt() + " " + it.frequencyLabel() else "" }
                com.pesaflow.app.ui.buddy.BuddyStrings.incomeLine(
                    viewModel.monthlyIncome.value.toInt().toString(),
                    topIn?.let { com.pesaflow.app.ui.buddy.BuddyStrings.incomeTop(it.key, it.value.toInt().toString(), cardLang) } ?: com.pesaflow.app.ui.buddy.BuddyStrings.incomeTopNone(cardLang),
                    if (expected > 0) com.pesaflow.app.ui.buddy.BuddyStrings.incomeDeclared(expected.toInt().toString(), declared ?: "declared sources", cardLang) else "",
                    cardLang
                )
            }

        userInput.lowercase().contains("spend") || userInput.lowercase().contains("burn") ||
            userInput.lowercase().contains("expense") ->
            run {
                val top3 = byCat.entries.sortedByDescending { it.value }.take(3)
                com.pesaflow.app.ui.buddy.BuddyStrings.spendLine(
                    viewModel.monthlyExpenses.value.toInt().toString(),
                    if (top3.isEmpty()) "" else com.pesaflow.app.ui.buddy.BuddyStrings.spendTop(top3.joinToString(", ") { "${it.key} KSh ${it.value.toInt()}" }, cardLang),
                    cardLang
                )
            }

        userInput.lowercase().contains("save") || userInput.lowercase().contains("savings") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.savedLine(viewModel.totalSavings.value.toInt().toString(), viewModel.savingsGoals.value.size.toString(), cardLang)

        userInput.lowercase().contains("food") || userInput.lowercase().contains("kaini") ||
            userInput.lowercase().contains("chakula") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.foodSimple(foodTotal.toInt().toString(), noCook, cardLang)

        userInput.lowercase().contains("transport") || userInput.lowercase().contains("boda") ||
            userInput.lowercase().contains("matatu") ||
            userInput.lowercase().contains("stage") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.transportSimple(transportTotal.toInt().toString(), farCommute, cardLang)

        userInput.lowercase().contains("mpesa") ||
            userInput.lowercase().contains("safaricom") ->
            "M-Pesa transaction parsing is active. SMS-based detection requires permission. " +
            "You can also use Share-to-PesaFlow as a fallback."

        userInput.lowercase().contains("how much") ||
            userInput.lowercase().contains("ngapi") ||
            userInput.lowercase().contains("kitani") ->
            when {
                userInput.lowercase().contains("remaining") ->
                    com.pesaflow.app.ui.buddy.BuddyStrings.howMuchRemaining(viewModel.availableBalance.value.toInt().toString(), cardLang)
                userInput.lowercase().contains("saved") ->
                    com.pesaflow.app.ui.buddy.BuddyStrings.howMuchSaved(viewModel.totalSavings.value.toInt().toString(), viewModel.savingsGoals.value.size.toString(), cardLang)
                else ->
                    com.pesaflow.app.ui.buddy.BuddyStrings.howMuchBase(viewModel.availableBalance.value.toInt().toString(), viewModel.monthlyIncome.value.toInt().toString(), viewModel.monthlyExpenses.value.toInt().toString(), cardLang)
            }

        q.contains("lack") || q.contains("missing") || q.contains("don't have") || q.contains("dont have") || q.contains("need to buy") || q.contains("ninahitaji") || q.contains("what i need") ->
            run {
                val needs = belongings.filter { it.status == "NEED" }.sortedWith(compareBy({ it.priority }, { it.estCost }))
                if (needs.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.lackNone(cardLang)
                else com.pesaflow.app.ui.buddy.BuddyStrings.lackLine(needs.size.toString(), needs.take(5).joinToString("; ") { "${it.name} (~KSh ${it.estCost.toInt()})" }, needs.sumOf { it.estCost }.toInt().toString(), cardLang)
            }

        q.contains("have what") || q.contains("own what") || q.contains("my things") || q.contains("vitu vyangu") ->
            run {
                val haves = belongings.filter { it.status == "HAVE" }
                if (haves.isEmpty() && belongings.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.haveNone(cardLang)
                else if (haves.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.haveNeedsOnly(cardLang)
                else com.pesaflow.app.ui.buddy.BuddyStrings.haveLine(haves.size.toString(), haves.take(6).joinToString(", ") { it.name }, cardLang)
            }

        q.contains("stock") || q.contains("kitchen") || q.contains("cupboard") || q.contains("unga") ->
            if (pantry.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.stockEmpty(cardLang)
            else {
                val ranked = pantry.sortedBy { stockDaysLeft(it) }
                com.pesaflow.app.ui.buddy.BuddyStrings.stockLine(ranked.take(5).joinToString("; ") { "${it.name} ~${stockDaysLeft(it).toInt()}d left" }, cardLang)
            }

        q.contains("how long") || q.contains("itanidumu") || q.contains("itaisha") || q.contains("will it last") ->
            if (pantry.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.howLongNone(cardLang)
            else {
                val lowest = pantry.minByOrNull { stockDaysLeft(it) }
                if (lowest == null) com.pesaflow.app.ui.buddy.BuddyStrings.howLongBare(cardLang)
                else com.pesaflow.app.ui.buddy.BuddyStrings.howLongFirst(lowest.name, stockDaysLeft(lowest).toInt().toString(), stockRefillCost(lowest).toInt().toString(), cardLang)
            }

        q.contains("replenish") || q.contains("restock") || q.contains("refill") || q.contains("jaza") ->
            if (pantry.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.replenishNone(cardLang)
            else {
                val low = pantry.sortedBy { stockDaysLeft(it) }.take(4)
                com.pesaflow.app.ui.buddy.BuddyStrings.replenishLine(low.joinToString("; ") { "${it.name} by ${java.text.SimpleDateFormat("d MMM", java.util.Locale.US).format(java.util.Date(stockReplenishDate(it)))} (KSh ${stockRefillCost(it).toInt()})" }, pantry.sumOf { stockRefillCost(it) }.toInt().toString(), cardLang)
            }

        q.contains("buy first") || q.contains("nini nunue") || q.contains("what first") || q.contains("priorit") ->
            run {
                val needs = belongings.filter { it.status == "NEED" }.sortedWith(compareBy({ it.priority }, { it.estCost }))
                val urgentStock = pantry.filter { stockDaysLeft(it) <= 3 }.sortedBy { stockDaysLeft(it) }
                val first = StringBuilder()
                urgentStock.firstOrNull()?.let { first.append(com.pesaflow.app.ui.buddy.BuddyStrings.buyFirstKitchen(it.name, stockDaysLeft(it).toInt().toString(), cardLang)) }
                needs.firstOrNull()?.let { first.append(com.pesaflow.app.ui.buddy.BuddyStrings.buyFirstBuy(it.name, it.estCost.toInt().toString(), cardLang)) }
                if (first.isEmpty()) com.pesaflow.app.ui.buddy.BuddyStrings.buyFirstCalm(cardLang)
                else first.toString()
            }

        q.contains("rent") || q.contains("hostel") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.rentHostel((byCat["Rent"] ?: 0.0).toInt().toString(), cardLang)

        q.contains("airtime") || q.contains("bundles") || q.contains("data") || q.contains("credit") ->
            com.pesaflow.app.ui.buddy.BuddyStrings.airtimeSpend(((byCat["Airtime"] ?: 0.0) + (byCat["Data"] ?: 0.0)).toInt().toString(), cardLang)

        q.contains("surviv") || q.contains("stretch") || q.contains("make it to") ||
            ((q.contains("until") || q.contains("till")) && (q.contains("friday") || q.contains("saturday") || q.contains("sunday") || q.contains("monday") || q.contains("month") || Regex("\\d+\\s*days?").containsMatchIn(q))) ->
            run {
                val foods = viewModel.mealItems.value.filter { it.source == "Cook" }
                val stock = viewModel.kitchenStock.value
                if (stock.isEmpty()) {
                    com.pesaflow.app.ui.buddy.BuddyStrings.surviveEmpty(cardLang)
                } else {
                    val weekOrder = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
                    val todayIdx = (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
                    val dayNum = Regex("(\\d+)\\s*days?").find(q)?.groupValues?.get(1)?.toIntOrNull()
                    val namedDay = weekOrder.firstOrNull { q.contains(it) }?.let { (weekOrder.indexOf(it) - todayIdx + 7) % 7 }
                    val days = when {
                        dayNum != null -> dayNum.coerceIn(1, 60)
                        namedDay != null -> if (namedDay == 0) 7 else namedDay
                        q.contains("month") -> {
                            val c = java.util.Calendar.getInstance()
                            c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) - c.get(java.util.Calendar.DAY_OF_MONTH) + 1
                        }
                        else -> 7
                    }
                    val plan = com.pesaflow.app.ui.university.planSurvival(stock, foods, days, balance.coerceAtLeast(0.0))
                    when {
                        plan.noFiller && plan.days.size < days -> com.pesaflow.app.ui.buddy.BuddyStrings.surviveGap(plan.days.size.toString(), days.toString(), cardLang)
                        plan.days.all { it.fromStock } -> com.pesaflow.app.ui.buddy.BuddyStrings.surviveStockOnly(days.toString(), cardLang)
                        plan.possible -> com.pesaflow.app.ui.buddy.BuddyStrings.survivePossible(plan.days.count { it.fromStock }.toString(), plan.totalCost.toInt().toString(), plan.shopping.joinToString { "${it.first} ×${it.second}" }, balance.toInt().toString(), cardLang)
                        else -> com.pesaflow.app.ui.buddy.BuddyStrings.surviveTight(plan.totalCost.toInt().toString(), balance.toInt().toString(), plan.shortfall.toInt().toString(), cardLang)
                    }
                }
            }

        q.contains("cooked") || q.contains("nimepika") || q.contains("nimebika") || q.contains("nimechemsha") ||
            (q.contains("used") && pantry.any { q.contains(it.name.lowercase()) }) ->
            run {
                val named = pantry.firstOrNull { q.contains(it.name.lowercase()) }
                if (named != null) {
                    viewModel.logStockUse(named)
                    val left = (named.qtyLeft - named.dailyUse).coerceAtLeast(0.0)
                    val leftStr = if (left == left.toInt().toDouble()) "${left.toInt()}" else String.format(java.util.Locale.US, "%.1f", left)
                    val daysAfter = if (named.dailyUse > 0) ((left / named.dailyUse).toInt()).coerceAtLeast(0) else -1
                    com.pesaflow.app.ui.buddy.BuddyStrings.cookedOne(named.name, leftStr, named.unit, if (daysAfter >= 0) com.pesaflow.app.ui.buddy.BuddyStrings.cookedDays(daysAfter.toString(), cardLang) else "", cardLang)
                } else if (pantry.isEmpty()) {
                    com.pesaflow.app.ui.buddy.BuddyStrings.cookedEmpty(cardLang)
                } else {
                    pantry.forEach { viewModel.logStockUse(it) }
                    com.pesaflow.app.ui.buddy.BuddyStrings.cookedAll(pantry.take(4).joinToString(", ") {
                        "${it.name} ~${((it.qtyLeft - it.dailyUse).coerceAtLeast(0.0) / it.dailyUse).toInt()}d left"
                    }, cardLang)
                }
            }

        else -> com.pesaflow.app.ui.buddy.BuddyStrings.genericFallback(cardLang)
    }

    // Remember a confident intent so entity-only follow-ups resolve next turn.
    BuddyBrain.classify(q).firstOrNull()?.takeIf { it.conf >= 0.5f }?.let { BuddyMemory.lastIntent = it.name }
    // Append, don't wipe: keep the conversation, drop the stale "Thinking...".
    messages.value = (messages.value.filterNot { !it.isUser && it.text == "Thinking..." } +
        listOf(ChatMessage(text = displayInput, isUser = true), ChatMessage(text = response, isUser = false, lang = cardLang))
        ).takeLast(40)
}