package com.pesaflow.app.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintBillsSteel
import java.util.Calendar


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(viewModel: FinanceViewModel) {
    val bills by viewModel.bills.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    // Proof ticks: brief ✓ that reverts so actions stay tappable.
    val ackScope = rememberCoroutineScope()
    var acked by remember { mutableStateOf(setOf<String>()) }
    fun ack(key: String) {
        acked = acked + key
        ackScope.launch { kotlinx.coroutines.delay(2000); acked = acked - key }
    }
    val repeats = remember(transactions) { mergeRepeats(transactions) }
    // Ledger-backed payment hints: "this bill looks paid already".
    val billMatches = remember(transactions, bills) { matchBillPayments(bills, transactions) }

    val upcoming = bills.filter { it.status != "PAID" }
    val recurring = bills.filter { it.frequency != "ONE_TIME" && it.status != "PAID" }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintBillsSteel, bgRes = R.drawable.bg_bills_steel)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Bills & Reminders", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("Add Bill", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            AtmosphereBand(
                workspace = AtmoWorkspace.BILLS,
                title = "Never surprised",
                subtitle = "Due · repeats · paid"
            )
            // Detected repeats: same charge on a rhythm — one tap to track as a bill
            if (repeats.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Detected Repeats 🔁", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Same charge, regular rhythm — track it so it never surprises you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        repeats.take(5).forEach { r ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(r.label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                    Text(
                                        "KSh ${r.amount.toInt()} · ~every ${r.intervalDays}d · ${r.times}× seen",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    viewModel.addBill(
                                        r.label,
                                        r.amount,
                                        System.currentTimeMillis() + r.intervalDays * 24L * 60 * 60 * 1000,
                                        r.category,
                                        if (r.monthly) "MONTHLY" else "WEEKLY"
                                    )
                                    ack("${r.label}|${r.amount}|${r.intervalDays}")
                                }) { Text(if ("${r.label}|${r.amount}|${r.intervalDays}" in acked) "Tracked ✓" else "+ Bill", color = MaterialTheme.colorScheme.primary) }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }


            // Section: Upcoming Bills
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Upcoming Bills (${upcoming.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // Wallet cover check: can M-Pesa clear what's due?
                    val billsCtx = LocalContext.current
                    val upTotal = upcoming.sumOf { it.amount }
                    com.pesaflow.app.data.parsers.readMpesaBalance(billsCtx)?.let { (wamt, wat) ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (upcoming.isEmpty()) "Nothing due — wallet untouched. 🎉"
                            else if (wamt >= upTotal) "Wallet covers all KSh ${upTotal.toInt()} ✓ (" + com.pesaflow.app.data.parsers.balanceAgeText(wat, System.currentTimeMillis()) + ")"
                            else "Wallet holds KSh ${wamt.toInt()} of KSh ${upTotal.toInt()} due — gap KSh ${(upTotal - wamt).toInt()}.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (upcoming.isNotEmpty() && wamt < upTotal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (upcoming.isEmpty()) {
                        Text(
                            "No upcoming bills. Add " + (if (com.pesaflow.app.ui.budgets.parseLiving(viewModel.getOnboardingAnswers()).hasRent) "rent, " else "hostel fees, ") + "subscriptions or repayments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        upcoming.forEach { bill ->
                            val match = billMatches[bill.id]
                            BillCard(
                                bill = bill,
                                onPaid = { viewModel.markBillPaid(bill.id) },
                                onDelete = { viewModel.deleteBill(bill.id) },
                                matchText = match?.let {
                                    "Matches KSh ${it.amount.toInt()} paid ${formatDate(it.paidAt)} — tap Mark Paid ✓"
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }


            // Section: Recurring Bills
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Recurring Bills (${recurring.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (recurring.isEmpty()) {
                        Text(
                            "No recurring bills.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recurring.forEach { bill ->
                            val match = billMatches[bill.id]
                            BillCard(
                                bill = bill,
                                onPaid = { viewModel.markBillPaid(bill.id) },
                                onDelete = { viewModel.deleteBill(bill.id) },
                                matchText = match?.let {
                                    "Matches KSh ${it.amount.toInt()} paid ${formatDate(it.paidAt)} — tap Mark Paid ✓"
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }


            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Add New Bill", color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(if (com.pesaflow.app.ui.budgets.parseLiving(viewModel.getOnboardingAnswers()).hasRent) "Rent" else "School") }
        var daysUntilDue by remember { mutableStateOf("7") }
        var frequency by remember { mutableStateOf("ONE_TIME") }
        val frequencies = listOf("ONE_TIME", "WEEKLY", "MONTHLY")

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Bill") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Bill name") })
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (KSh)") })
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") })
                    OutlinedTextField(value = daysUntilDue, onValueChange = { daysUntilDue = it }, label = { Text("Due in (days)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        frequencies.forEach { f ->
                            FilterChip(
                                selected = frequency == f,
                                onClick = { frequency = f },
                                label = { Text(if (f == "ONE_TIME") "Once" else f.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amount.toDoubleOrNull()
                    val days = daysUntilDue.toIntOrNull()
                    if (name.isNotBlank() && amt != null && amt > 0 && days != null) {
                        val due = System.currentTimeMillis() + days.coerceAtLeast(0) * 24L * 60 * 60 * 1000
                        viewModel.addBill(name.trim(), amt, due, category.trim().ifEmpty { "Other" }, frequency)
                        showAddDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}


@Composable
fun BillCard(bill: Bill, onPaid: () -> Unit, onDelete: () -> Unit, matchText: String? = null) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = if (isExpanded) Arrangement.spacedBy(8.dp) else Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        bill.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "KSh ${bill.amount.toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bill.amount > 1000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Due: ${formatDate(bill.dueDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (bill.status == "PAID") {
                        Text("PAID ✓", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    } else {
                        TextButton(onClick = onPaid) {
                            Text("Mark Paid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (matchText != null && bill.status != "PAID") {
                        Text(
                            matchText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2
                        )
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailRowItem(
                        icon = Icons.Default.DateRange,
                        label = "Due Date",
                        value = formatDate(bill.dueDate)
                    )
                    DetailRowItem(
                        icon = Icons.Default.Person,
                        label = "Category",
                        value = bill.category
                    )
                    DetailRowItem(
                        icon = Icons.Default.Refresh,
                        label = "Frequency",
                        value = bill.frequency
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(0.dp, 0.dp, 8.dp, 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRowItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatDate(millis: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = millis
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.get(Calendar.MONTH) + 1
    val year = calendar.get(Calendar.YEAR)
    return "$day/$month/$year"
}


private data class RecurringHit(
    val label: String,
    val amount: Double,
    val category: String,
    val intervalDays: Long,
    val times: Int,
    val monthly: Boolean
)


/**
 * Union of the rule detector below and the statistical RecurringDetector
 * (spec §10: interval CV + amount CV). The ML pass groups by merchant only,
 * so it catches repeats whose category the user edited month to month —
 * exactly what the (merchant, category) rule key misses. ML-only hits carry
 * an MlEngine-suggested category; rule hits keep their ledger category.
 * User still taps + Bill — nothing auto-tracks.
 */
private fun mergeRepeats(txs: List<Transaction>): List<RecurringHit> {
    val ruleHits = detectRepeats(txs)
    val seen = ruleHits.map { it.label.trim().lowercase() }.toSet()
    val mlOnly = runCatching {
        val rows = txs.filter { it.type == TransactionType.EXPENSE }
            .map { it.merchant to (it.amount to it.dateTimestamp) }
        com.pesaflow.app.data.ml.MlEngine.findRecurring(rows)
            .filter { it.merchant !in seen }
            .mapNotNull { c ->
                val original = txs.filter { it.type == TransactionType.EXPENSE }
                    .filter { it.merchant.trim().lowercase() == c.merchant }
                    .maxByOrNull { it.dateTimestamp }?.merchant ?: c.merchant
                val category = runCatching {
                    com.pesaflow.app.data.ml.MlEngine.suggestCategory(c.merchant, "").category
                }.getOrDefault("Other")
                RecurringHit(
                    label = original,
                    amount = c.medianAmount,
                    category = category,
                    intervalDays = c.medianIntervalDays.toLong().coerceAtLeast(1),
                    times = c.occurrences,
                    monthly = c.medianIntervalDays in 25.0..35.0
                )
            }
    }.getOrDefault(emptyList())
    return (ruleHits + mlOnly).sortedByDescending { it.times }
}

/**
 * Bill auto-match suggestions (no silent writes): an UNPAID bill whose name
 * and amount match a real ledger EXPENSE likely got paid off-app. Returns one
 * best match per bill; the UI shows "matches … — tap Mark Paid" and the user
 * confirms. Sample/seeded rows never match (one counting rule).
 */
internal data class BillMatch(val billId: String, val txId: String, val amount: Double, val paidAt: Long)

internal fun matchBillPayments(
    bills: List<Bill>,
    txs: List<Transaction>,
    nowMs: Long = System.currentTimeMillis()
): Map<String, BillMatch> {
    val day = 24L * 60 * 60 * 1000
    val out = mutableMapOf<String, BillMatch>()
    bills.filter { it.status != "PAID" }.forEach { bill ->
        val key = bill.name.trim().lowercase()
        if (key.isEmpty() || bill.amount <= 0) return@forEach
        val tolerance = (bill.amount * 0.05).coerceAtLeast(1.0)
        val best = txs.filter {
            it.type == TransactionType.EXPENSE &&
                it.source !in com.pesaflow.app.data.money.NON_STAT_SOURCES &&
                it.dateTimestamp in (bill.dueDate - 45 * day)..(nowMs + 1) &&
                it.dateTimestamp >= nowMs - 120 * day &&
                (it.merchant.trim().lowercase() == key ||
                    it.merchant.trim().lowercase().contains(key) ||
                    key.contains(it.merchant.trim().lowercase())) &&
                kotlin.math.abs(it.amount - bill.amount) <= tolerance
        }.minWithOrNull(compareBy({ kotlin.math.abs(it.amount - bill.amount) }, { -it.dateTimestamp }))
        if (best != null) out[bill.id] = BillMatch(bill.id, best.id, best.amount, best.dateTimestamp)
    }
    return out
}

private fun detectRepeats(txs: List<Transaction>): List<RecurringHit> {
    val day = 24L * 60 * 60 * 1000
    return txs.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.merchant.trim().lowercase() to it.category }
        .mapNotNull { (key, list) ->
            if (list.size < 3) return@mapNotNull null
            val sorted = list.map { it.dateTimestamp }.sorted()
            val gaps = sorted.zipWithNext { a, b -> (b - a) / day }
            if (gaps.isEmpty()) return@mapNotNull null
            val median = gaps.sorted()[gaps.size / 2]
            if (median < 5 || median > 40) return@mapNotNull null
            val avg = list.map { it.amount }.average()
            if (list.any { kotlin.math.abs(it.amount - avg) > avg * 0.35 + 1 }) return@mapNotNull null
            RecurringHit(
                label = list.maxByOrNull { it.dateTimestamp }?.merchant ?: key.first,
                amount = avg,
                category = key.second,
                intervalDays = median,
                times = list.size,
                monthly = median >= 25
            )
        }
        .sortedByDescending { it.times }
}
