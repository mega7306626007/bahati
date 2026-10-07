package com.pesaflow.app.ui.debt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.Debt
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
    var showAddDialog by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("In") }

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
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = SkinDebt.tint, bgRes = R.drawable.bg_debt_wine)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Debt Tracking", fontWeight = FontWeight.Bold) },
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
                        Spacer(Modifier.height(PesaSpacing.xs))
                        Text(
                            overdue.size.toString() + " overdue (" + overdue.joinToString(", ") { it.person } + ") - chase or pay today.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                    FilterChip(selected = tab == "In", onClick = { tab = "In" }, label = { Text("Owed to me (" + owedToMe.size + ")") })
                    FilterChip(selected = tab == "Out", onClick = { tab = "Out" }, label = { Text("I owe (" + iOwe.size + ")") })
                    FilterChip(selected = tab == "Done", onClick = { tab = "Done" }, label = { Text("Settled (" + paid.size + ")") })
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
                        DebtCard(
                            debt = debt,
                            now = now,
                            onSettleLog = {
                                if (debt.direction == "I_OWE") {
                                    viewModel.addManualTransaction(debt.amount, TransactionType.EXPENSE, "Debt", "Repaid " + debt.person, PaymentMethod.CASH)
                                } else {
                                    viewModel.addManualTransaction(debt.amount, TransactionType.INCOME, "Debt", "Collected from " + debt.person, PaymentMethod.CASH)
                                }
                                viewModel.markDebtPaid(debt.id)
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
    onDelete: () -> Unit
) {
    var showSettle by remember { mutableStateOf(false) }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!settled) {
                TextButton(onClick = { showSettle = true }) { Text("Settle & log") }
            }
            TextButton(onClick = onDelete) {
                Text("Delete", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (showSettle) {
        AlertDialog(
            onDismissRequest = { showSettle = false },
            title = { Text("Settle " + debt.amount.toKSh() + "?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (debt.direction == "I_OWE") "Marks paid and logs a KSh " + debt.amount.toInt() + " Debt expense, so your ledger matches reality."
                    else "Marks paid and logs a KSh " + debt.amount.toInt() + " Debt income, so your ledger matches reality."
                )
            },
            confirmButton = {
                Button(onClick = { showSettle = false; onSettleLog() }) { Text("Settle") }
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
