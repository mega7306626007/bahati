package com.pesaflow.app.ui.debt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.Debt
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.PesaEmptyState
import com.pesaflow.app.ui.theme.PesaRadius
import com.pesaflow.app.ui.theme.PesaSpacing
import com.pesaflow.app.ui.theme.SkinAccentLine
import com.pesaflow.app.ui.theme.SkinCard
import com.pesaflow.app.ui.theme.SkinDebt
import com.pesaflow.app.ui.theme.toKSh
import com.pesaflow.app.viewmodels.FinanceViewModel
import java.util.Calendar

private const val DAY_MS = 24L * 60 * 60 * 1000

private fun dueLabel(dueDate: Long, now: Long): String {
    val days = ((dueDate - now) / DAY_MS).toInt()
    return when {
        days < 0 -> "Overdue " + (-days) + "d"
        days == 0 -> "Due today"
        days == 1 -> "Due tomorrow"
        else -> "Due in " + days + "d"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtTrackingScreen(viewModel: FinanceViewModel) {
    val debts by viewModel.debts.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("In") }
    var personQuery by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current

    val now = System.currentTimeMillis()
    val active = debts.filter { it.status != "PAID" }
    val owedToMe = active.filter { it.direction != "I_OWE" }
    val iOwe = active.filter { it.direction == "I_OWE" }
    val paid = debts.filter { it.status == "PAID" }
    val overdue = active.filter { it.dueDate in 1..now }
    val shown = when (tab) {
        "Out" -> iOwe
        "Done" -> paid
        else -> owedToMe
    }.filter { personQuery.isBlank() || it.person.contains(personQuery, ignoreCase = true) }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = SkinDebt.tint, bgRes = R.drawable.bg_debt_wine)
        Scaffold(
            containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = { Text("Debt Tracking", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = PesaSpacing.md)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PesaSpacing.md)
            ) {
                // Wine hero: what is open, what is late.
                SkinCard(skin = SkinDebt) {
                    Text("Outstanding", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(PesaSpacing.xxs))
                    SkinAccentLine(SkinDebt.accent)
                    Spacer(Modifier.height(PesaSpacing.xs))
                    Text(
                        owedToMe.sumOf { it.amount }.toKSh() + " owed to you  ·  " + iOwe.sumOf { it.amount }.toKSh() + " you owe",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (overdue.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(PesaSpacing.xs))
                        Text(
                            overdue.size.toString() + " overdue (" + overdue.joinToString(", ") { it.person } + ") - chase or pay today.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    // Payoff order: most urgent you-owe first, else biggest chase.
                    val payFirst = iOwe.minByOrNull { it.dueDate }
                    val chaseFirst = owedToMe.maxByOrNull { it.amount }
                    if (payFirst != null || chaseFirst != null) {
                        Spacer(modifier = Modifier.height(PesaSpacing.xs))
                        Text(
                            if (payFirst != null) "First move: pay ${payFirst.person} KSh ${payFirst.amount.toInt()} (" + dueLabel(payFirst.dueDate, System.currentTimeMillis()) + ")."
                            else "First move: chase ${chaseFirst!!.person} KSh ${chaseFirst.amount.toInt()}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Fuliza transparency: the deni rows above already include
                    // Fuliza borrowing, and the ledger breaks it down here —
                    // borrowed vs repaid vs outstanding, charges observed vs
                    // the 1% access estimate, plus your carrier fee bleed.
                    val fuliza = remember(transactions) { com.pesaflow.app.data.finance.fulizaTotals(transactions) }
                    val fulizaRows = debts.count {
                        it.person.contains("Fuliza", ignoreCase = true)
                    }
                    if (fuliza.borrowCount > 0 || fulizaRows > 0) {
                        Spacer(modifier = Modifier.height(PesaSpacing.xs))
                        SkinAccentLine(SkinDebt.accent)
                        Spacer(modifier = Modifier.height(PesaSpacing.xs))
                        Text(
                            "Includes Fuliza: borrowed KSh ${fuliza.borrowed.toInt()} · repaid KSh ${fuliza.repaid.toInt()} · outstanding KSh ${fuliza.outstanding.toInt()} ($fulizaRows deni rows).",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val feeCtx = LocalContext.current
                        val monthFees = remember { com.pesaflow.app.data.parsers.readMonthFees(feeCtx) }
                        val lifeFees = remember(transactions) { com.pesaflow.app.data.finance.lifetimeFeeTotal(transactions) }
                        val rate = com.pesaflow.app.data.finance.dailyRateFor(fuliza.outstanding)
                        Text(
                            "Fuliza charges observed KSh ${fuliza.chargesObserved.toInt()} · ~1% access ≈ KSh ${fuliza.accessEstimate.toInt()} (estimate) · running ~KSh ${rate.toInt()}/day at this balance. Carrier fees: KSh ${monthFees.toInt()} this month · KSh ${lifeFees.toInt()} lifetime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val toMeTotal = owedToMe.sumOf { it.amount }
                val iOweTotal = iOwe.sumOf { it.amount }
                // Payoff planner: earliest-due first, one monthly number spills
                // across every debt you owe. Pure math, live on your data.
                if (iOwe.isNotEmpty()) {
                    var payoffInput by remember(iOweTotal) {
                        mutableStateOf((iOweTotal / 3).toInt().coerceAtLeast(100).toString())
                    }
                    val payoffMonthly = payoffInput.toDoubleOrNull()?.takeIf { it > 0 }
                    val plan = remember(iOwe, payoffMonthly) {
                        payoffMonthly?.let { com.pesaflow.app.data.finance.planPayoff(iOwe, it) }
                    }
                    SkinCard(skin = SkinDebt) {
                        Text(
                            "Payoff plan 🧹",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(PesaSpacing.xs))
                        OutlinedTextField(
                            value = payoffInput,
                            onValueChange = { v -> if (v.matches(Regex("^[0-9.,]*$"))) payoffInput = v },
                            label = { Text("Monthly payoff (KSh)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (plan != null && plan.steps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(PesaSpacing.xs))
                            plan.steps.forEachIndexed { i, s ->
                                Text(
                                    "${i + 1}. ${s.person} — KSh ${s.amount.toInt()} · clears month ${s.clearMonth}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(PesaSpacing.xs))
                            Text(
                                "Debt-free in ${plan.totalMonths} month(s) at KSh ${payoffMonthly!!.toInt()}/mo.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Text(
                    "Owed to you KSh ${toMeTotal.toInt()} · You owe KSh ${iOweTotal.toInt()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = personQuery,
                    onValueChange = { personQuery = it },
                    label = { Text("Search person") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                    FilterChip(selected = tab == "In", onClick = { tab = "In" },
                        label = { Text("Owed to me (${owedToMe.size})") })
                    FilterChip(selected = tab == "Out", onClick = { tab = "Out" },
                        label = { Text("I owe (${iOwe.size})") })
                    FilterChip(selected = tab == "Done", onClick = { tab = "Done" },
                        label = { Text("Settled (${paid.size})") })
                }

                if (shown.isEmpty()) {
                    PesaEmptyState(
                        title = when (tab) {
                            "Out" -> "You owe nobody"
                            "Done" -> "Nothing settled yet"
                            else -> "Nobody owes you"
                        },
                        explanation = when (tab) {
                            "Out" -> "Money you borrow appears here with its due date and countdown."
                            "Done" -> "Settled debts land here with a ledger trail when you log them."
                            else -> "Add someone who owes you - due dates and reminders keep it honest."
                        },
                        actionLabel = if (tab == "Done") null else "Add debt entry",
                        onAction = if (tab == "Done") null else ({ showAddDialog = true })
                    )
                } else {
                    // Most urgent first: overdue, then nearest due date.
                    shown.sortedWith(compareBy({ it.status == "PAID" }, { it.dueDate })).forEach { debt ->
                        // Ledger trail: Debt-category rows naming this person.
                        val trail = remember(debt.id, transactions) {
                            transactions.filter {
                                it.category == "Debt" && it.merchant.contains(debt.person, ignoreCase = true)
                            }.sortedByDescending { it.dateTimestamp }.take(3)
                        }
                        DebtCard(
                            debt = debt,
                            now = now,
                            history = trail,
                            onSettleLog = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (debt.direction == "I_OWE") {
                                    viewModel.addManualTransaction(debt.amount, TransactionType.EXPENSE, "Debt", "Repaid " + debt.person, PaymentMethod.CASH)
                                } else {
                                    viewModel.addManualTransaction(debt.amount, TransactionType.INCOME, "Debt", "Collected from " + debt.person, PaymentMethod.CASH)
                                }
                                viewModel.markDebtPaid(debt.id)
                            },
                            onSettlePart = { amt, method ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.settleDebtPartial(debt, amt, method)
                            },
                            onDelete = { viewModel.deleteDebt(debt.id) }
                        )
                    }
                }

                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = PesaRadius.md,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Add Debt Entry", color = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.height(96.dp))
            }
        }
    }

    if (showAddDialog) {
        var person by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var daysUntilDue by remember { mutableStateOf("14") }
        var description by remember { mutableStateOf("") }
        var direction by remember { mutableStateOf("THEY_OWE") }
        val amountValid = (amount.toDoubleOrNull() ?: 0.0) > 0

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New debt entry", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(PesaSpacing.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                        FilterChip(
                            selected = direction == "THEY_OWE",
                            onClick = { direction = "THEY_OWE" },
                            label = { Text("They owe me") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = direction == "I_OWE",
                            onClick = { direction = "I_OWE" },
                            label = { Text("I owe") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text("Person") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { v -> if (v.matches(Regex("^\\d*(\\.\\d{0,2})?\$"))) amount = v },
                        label = { Text("Amount (KSh)") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = daysUntilDue, onValueChange = { daysUntilDue = it }, label = { Text("Due in (days)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
                    if (!amountValid) {
                        Text("Enter an amount above zero to save.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = amountValid && person.isNotBlank() && daysUntilDue.toIntOrNull() != null,
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: return@Button
                        val days = daysUntilDue.toIntOrNull() ?: return@Button
                        val due = System.currentTimeMillis() + days.coerceAtLeast(0) * DAY_MS
                        viewModel.addDebt(person.trim(), amt, due, description.trim(), direction)
                        showAddDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}


@Composable
fun DebtCard(
    debt: Debt,
    now: Long,
    onSettleLog: () -> Unit,
    onDelete: () -> Unit,
    onSettlePart: ((Double, PaymentMethod) -> Unit)? = null,
    history: List<Transaction> = emptyList()
) {
    var showSettle by remember { mutableStateOf(false) }
    val debtShareCtx = LocalContext.current
    // Partial payment: defaults to the full balance, trim to whatever moved.
    var settleAmt by remember(debt.id) {
        mutableStateOf(if (debt.amount % 1.0 == 0.0) debt.amount.toInt().toString() else debt.amount.toString())
    }
    var settleMethod by remember(debt.id) { mutableStateOf(PaymentMethod.MPESA) }
    val settled = debt.status == "PAID"
    val late = !settled && debt.dueDate in 1..now
    SkinCard(skin = SkinDebt) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(debt.person, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (debt.direction == "I_OWE") "You owe" else "Owes you") +
                        if (settled) " · settled" else " · " + dueLabel(debt.dueDate, now),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        settled -> MaterialTheme.colorScheme.primary
                        late -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                )
            }
            Text(
                debt.amount.toKSh(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (debt.description.isNotBlank()) {
            Spacer(Modifier.height(PesaSpacing.xs))
            Text(debt.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(PesaSpacing.xs))
        Text(
            "Borrowed " + formatDebtDate(debt.dateBorrowed) + " · Due " + formatDebtDate(debt.dueDate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(PesaSpacing.sm))
        if (!settled && debt.dueDate > now) {
            val daysLeftDebt = ((debt.dueDate - now) / (24L * 60 * 60 * 1000)).coerceAtLeast(1)
            Text(
                "Clear it: KSh ${(debt.amount / daysLeftDebt).toInt()}/day for $daysLeftDebt day(s)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(PesaSpacing.xs))
            Text(
                "Ledger trail:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            history.forEach { h ->
                Text(
                    h.merchant + " - KSh " + h.amount.toInt() + " (" + formatDebtDate(h.dateTimestamp) + ")",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!settled) {
                TextButton(onClick = { showSettle = true }) { Text("Settle & log") }
            }
            TextButton(onClick = {
                val verb = if (debt.direction == "I_OWE") "I owe" else "owes me"
                val text = "${debt.person} $verb KSh ${debt.amount.toInt()} (due ${formatDebtDate(debt.dueDate)}). Tracked in PesaPlanner."
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                debtShareCtx.startActivity(android.content.Intent.createChooser(intent, "Share IOU"))
            }) { Text("Share", style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onDelete) {
                Text("Delete", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (showSettle) {
        val settleValid = (settleAmt.toDoubleOrNull() ?: 0.0) > 0
        AlertDialog(
            onDismissRequest = { showSettle = false },
            title = { Text("Settle " + debt.person + "?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(PesaSpacing.sm)) {
                    Text(
                        "Full balance is " + debt.amount.toKSh() + ". Enter less for a part-payment — the remainder stays open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = settleAmt,
                        onValueChange = { v -> if (v.matches(Regex("^\\d*(\\.\\d{0,2})?\$"))) settleAmt = v },
                        label = { Text("Amount paid (KSh)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                        listOf(PaymentMethod.MPESA to "M-Pesa", PaymentMethod.CASH to "Cash", PaymentMethod.BANK_TRANSFER to "Bank").forEach { (m, label) ->
                            FilterChip(selected = settleMethod == m, onClick = { settleMethod = m }, label = { Text(label) })
                        }
                    }
                    if (!settleValid) {
                        Text("Enter an amount above zero.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = settleValid,
                    onClick = {
                        val amt = settleAmt.toDoubleOrNull() ?: return@Button
                        showSettle = false
                        if (onSettlePart != null) onSettlePart(amt, settleMethod)
                        else onSettleLog()
                    }
                ) { Text("Log payment") }
            },
            dismissButton = { TextButton(onClick = { showSettle = false }) { Text("Cancel") } }
        )
    }
}

private fun formatDebtDate(millis: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = millis
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.get(Calendar.MONTH) + 1
    val year = calendar.get(Calendar.YEAR)
    return "$day/$month/$year"
}
