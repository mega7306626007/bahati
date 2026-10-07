package com.pesaflow.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import kotlinx.coroutines.launch
import com.pesaflow.app.R
import com.pesaflow.app.ui.dashboard.QuickAddDialog
import com.pesaflow.app.ui.theme.CategoryIcon
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.PesaEmptyState
import com.pesaflow.app.ui.theme.PesaSectionHeader
import com.pesaflow.app.ui.theme.PesaSpacing
import com.pesaflow.app.ui.theme.TintTransactionsLedger
import com.pesaflow.app.ui.theme.toKSh
import com.pesaflow.app.viewmodels.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun dayStartOf(ts: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun groupLabel(dayStart: Long, now: Long): String {
    val fmt = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
    return when (dayStart) {
        dayStartOf(now) -> "TODAY"
        dayStartOf(now) - 24L * 60 * 60 * 1000 -> "YESTERDAY"
        else -> fmt.format(Date(dayStart)).uppercase(Locale.getDefault())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: FinanceViewModel,
    onQuickAdd: (TransactionType) -> Unit = {}
) {
    val transactions by viewModel.allTransactions.collectAsState()
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var confirmDelete by remember { mutableStateOf<Transaction?>(null) }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()
    val sorted = remember(transactions, typeFilter) {
        transactions.filter { typeFilter == null || it.type.name == typeFilter }.sortedByDescending { it.dateTimestamp }
    }
    val groups = remember(sorted) {
        sorted.groupBy { dayStartOf(it.dateTimestamp) }.toSortedMap(compareByDescending { it })
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintTransactionsLedger, bgRes = R.drawable.bg_transactions_ledger)
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text("Transactions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { inner ->
        if (sorted.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(inner).padding(PesaSpacing.md)) {
                val tf = typeFilter
                PesaEmptyState(
                    title = if (tf == null) "No transactions yet" else "No ${tf.lowercase()} transactions",
                    explanation = if (tf == null) "Your spending will appear here as you add transactions or import M-Pesa messages."
                    else "Nothing in this type — clear the filter or log one.",
                    actionLabel = if (tf == null) "Add your first expense" else "Clear filter",
                    onAction = if (tf == null) { { onQuickAdd(TransactionType.EXPENSE) } } else { { typeFilter = null } }
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = PesaSpacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = typeFilter == null,
                        onClick = { typeFilter = null },
                        label = { Text("All") }
                    )
                    listOf("INCOME" to "In", "EXPENSE" to "Out", "SAVING" to "Saved", "INVESTMENT" to "Grown").forEach { (v, label) ->
                        FilterChip(
                            selected = typeFilter == v,
                            onClick = { typeFilter = if (typeFilter == v) null else v },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
                Modifier.fillMaxSize().weight(1f),
                verticalArrangement = Arrangement.spacedBy(PesaSpacing.xs)
            ) {
                item { Spacer(Modifier.height(PesaSpacing.xs)) }
                groups.forEach { (day, txs) ->
                    item(key = "h-$day") {
                        val dayNet = txs.sumOf {
                            when (it.type) {
                                TransactionType.INCOME -> it.amount
                                else -> -it.amount
                            }
                        }
                        PesaSectionHeader(
                            title = groupLabel(day, now),
                            subtitle = "${txs.size} item(s) · " + (if (dayNet >= 0) "+" else "−") + " KSh " + kotlin.math.abs(dayNet).toInt()
                        )
                    }
                    items(txs, key = { it.id }) { tx ->
                        TransactionRow(
                            tx = tx,
                            onEdit = { editingTx = tx },
                            onDelete = { confirmDelete = tx }
                        )
                    }
                    item { Spacer(Modifier.height(PesaSpacing.sm)) }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
        }
    }
    }

    editingTx?.let {
        QuickAddDialog(viewModel = viewModel, defaultType = it.type, onDismiss = { editingTx = null }, existing = it)
    }

    confirmDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this transaction?") },
            text = { Text("${tx.merchant} · KSh ${tx.amount.toInt()} — you can undo right after.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransactionWithUndo(tx)
                    confirmDelete = null
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
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } }
        )
    }
}

@Composable
fun TransactionRow(
    tx: Transaction,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    showActions: Boolean = true
) {
    val isIncome = tx.type == TransactionType.INCOME
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(PesaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PesaSpacing.sm)
        ) {
            CategoryIcon(category = tx.category)
            Column(Modifier.weight(1f)) {
                Text(tx.merchant.ifBlank { tx.category }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${tx.category} · ${tx.paymentMethod.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                (if (isIncome) "+ " else "− ") + tx.amount.toKSh().removePrefix("KSh "),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when (tx.type) {
                    TransactionType.INCOME -> Color(0xFF00C853)
                    TransactionType.SAVING -> Color(0xFF00BFA5)
                    TransactionType.INVESTMENT -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1
            )
            if (showActions) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit transaction") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete transaction", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun Double.toKShTrim(): String = this.toKSh()
