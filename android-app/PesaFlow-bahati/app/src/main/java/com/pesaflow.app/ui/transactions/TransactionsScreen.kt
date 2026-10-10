package com.pesaflow.app.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.ledger.CategoryMemory
import com.pesaflow.app.data.ledger.ConfidenceMemory
import com.pesaflow.app.data.ledger.MerchantMemory
import com.pesaflow.app.data.parsers.PendingPolicy
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.time.addDays
import com.pesaflow.app.data.time.rollingDays
import com.pesaflow.app.data.time.startOfDay
import com.pesaflow.app.viewmodels.exactDuplicateGroups
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun groupLabel(dayStart: Long, now: Long, lang: com.pesaflow.app.data.models.AppLanguage = com.pesaflow.app.data.models.AppLanguage.ENGLISH): String {
    val fmt = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
    return when (dayStart) {
        startOfDay(now) -> com.pesaflow.app.ui.language.txnT("day_today", lang)
        addDays(startOfDay(now), -1) -> com.pesaflow.app.ui.language.txnT("day_yesterday", lang)
        else -> fmt.format(Date(dayStart)).uppercase(Locale.getDefault())
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    viewModel: FinanceViewModel,
    onOpenSearch: () -> Unit = {},
    onAskBuddy: () -> Unit = {}
) {
    val transactions by viewModel.allTransactions.collectAsState()
    val pendings by viewModel.pendingTransactions.collectAsState()
    val lang by viewModel.currentLanguage.collectAsState()
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var confirmDelete by remember { mutableStateOf<Transaction?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shareContext = LocalContext.current
    var typeFilter by remember { mutableStateOf<String?>(null) }
    // Dual-SIM filter: null = both lines, 0 = SIM 1, 1 = SIM 2.
    var simFilter by remember { mutableStateOf<Int?>(null) }
    var merchantQuery by remember { mutableStateOf("") }
    var oldestFirst by remember { mutableStateOf(false) }
    // Bulk mode: multi-select rows for share/delete. Range: quick time windows.
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var rangeDays by remember { mutableStateOf<Int?>(null) }
    // SMS scan: today up front, longer ranges behind one disclosure.
    var confirmBulk by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanFound by remember { mutableStateOf(0) }
    var scanMsg by remember { mutableStateOf<String?>(null) }
    var showMoreScan by remember { mutableStateOf(false) }
    fun runScan(days: Int, label: String) {
        scanning = true
        scanFound = 0
        scanMsg = null
        viewModel.scanInboxDays(
            daysBack = days,
            maxRows = if (days <= 1) 150 else 500,
            onProgress = { f, _ -> scanFound = f },
            onDone = { found, queued, feeTotal, error ->
                scanning = false
                val rangeName = when (days) {
                    1 -> com.pesaflow.app.ui.language.txnT("today", lang)
                    7 -> com.pesaflow.app.ui.language.txnT("days7", lang)
                    30 -> com.pesaflow.app.ui.language.txnT("days30", lang)
                    else -> label
                }
                scanMsg = if (error != null) com.pesaflow.app.ui.language.txnT("scan_fail", lang, error)
                else com.pesaflow.app.ui.language.txnT("scan_done", lang, found.toString(), rangeName, queued.toString()) +
                    (if (feeTotal > 0) com.pesaflow.app.ui.language.txnT("scan_fees", lang, feeTotal.toInt().toString()) else "") + " ✅"
            }
        )
    }
    val now = System.currentTimeMillis()
    val sorted = remember(transactions, typeFilter, merchantQuery, rangeDays, simFilter) {
        // Range chips cover whole calendar days: the old now - n*24h cutoff
        // sliced the earliest day at the current time-of-day.
        val cutoff = when (rangeDays) {
            0 -> startOfDay(System.currentTimeMillis())
            null -> 0L
            else -> rollingDays(System.currentTimeMillis(), rangeDays!!).startInclusive
        }
        transactions
            .filter { typeFilter == null || it.type.name == typeFilter }
            .filter { simFilter == null || it.simSlot == simFilter }
            .filter { merchantQuery.isBlank() || it.merchant.contains(merchantQuery, ignoreCase = true) || it.category.contains(merchantQuery, ignoreCase = true) }
            .filter { it.dateTimestamp >= cutoff }
            .sortedByDescending { it.dateTimestamp }
    }
    val orderedGroups = remember(sorted, oldestFirst) {
        val g = sorted.groupBy { startOfDay(it.dateTimestamp) }.toSortedMap(compareByDescending { it })
        if (oldestFirst) g.toSortedMap(compareBy { it }) else g
    }
    // Pagination: first 100 rows render instantly on big histories (2k SMS
    // imports grouped/sorted on every keystroke); the rest load on tap.
    var visibleLimit by remember(sorted) { mutableStateOf(100) }
    val groups = remember(orderedGroups, visibleLimit, oldestFirst) {
        var shown = 0
        val out = LinkedHashMap<Long, List<Transaction>>()
        for ((day, txs) in orderedGroups) {
            if (shown >= visibleLimit) break
            val take = txs.take((visibleLimit - shown).coerceAtLeast(0))
            if (take.isNotEmpty()) out[day] = take
            shown += take.size
        }
        if (oldestFirst) out.toSortedMap(compareBy { it }) else out
    }
    val visibleCount = groups.values.sumOf { it.size }
    // One share engine: Nairobi dates, samples never export. The footer
    // shares the FULL ledger (filters only narrow the view, never the
    // export); the bulk bar shares exactly the user's picked selection.
    // The subject always states which of the two it is.
    fun shareTxs(list: List<Transaction>, scope: String) {
        val full = list.filter { !it.isSample }.sortedByDescending { it.dateTimestamp }
        if (full.isEmpty()) return
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = com.pesaflow.app.data.time.KenyaTime.timeZone
        }
        val rows = full.map { tx ->
            "${fmt.format(java.util.Date(tx.dateTimestamp))},\"${tx.merchant.replace("\"", "")}\",${tx.category},${tx.type},${tx.amount},${tx.paymentMethod}"
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "PesaPlanner transactions ($scope, ${full.size} rows)")
            putExtra(android.content.Intent.EXTRA_TEXT, (listOf("date,merchant,category,type,amount,method") + rows).joinToString("\n"))
        }
        shareContext.startActivity(android.content.Intent.createChooser(intent, "Share transactions"))
    }
    // Chronological running balance (oldest → newest, samples excluded)
    // regardless of the view's sort direction.
    val runningById = remember(transactions) {
        var run = 0.0
        val map = LinkedHashMap<String, Double>()
        transactions.filter { !it.isSample }.sortedBy { it.dateTimestamp }.forEach { tx ->
            run += when (tx.type) {
                TransactionType.INCOME -> tx.amount
                TransactionType.EXPENSE -> -tx.amount
                TransactionType.SAVING -> -tx.amount
                TransactionType.INVESTMENT -> -tx.amount
                TransactionType.TRANSFER -> 0.0
            }
            map[tx.id] = run
        }
        map
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintTransactionsLedger, bgRes = R.drawable.bg_transactions_ledger)
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                TopAppBar(
                    title = { Text(com.pesaflow.app.ui.language.txnT("title", lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
                    actions = {
                        TextButton(onClick = onAskBuddy) {
                            Text(com.pesaflow.app.ui.language.txnT("ask_buddy", lang))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { inner ->
        if (transactions.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(inner).padding(PesaSpacing.md)) {
                // Scanned rows wait in Pending for confirm — the ledger stays
                // empty until then. Say so out loud instead of pretending
                // nothing was found, and never funnel to manual entry.
                val txPrefs = shareContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                val sure = pendings.filter {
                    ConfidenceMemory.effective(
                        txPrefs,
                        it.merchant, it.confidenceScore
                    ) >= PendingPolicy.SURE_CONFIDENCE
                }
                if (pendings.isNotEmpty()) {
                    PesaEmptyState(
                        title = com.pesaflow.app.ui.language.txnT("empty_pending_title", lang, pendings.size.toString()),
                        explanation = com.pesaflow.app.ui.language.txnT("empty_pending_body", lang),
                        actionLabel = if (sure.isNotEmpty()) com.pesaflow.app.ui.language.txnT("confirm_sure", lang, sure.size.toString()) else com.pesaflow.app.ui.language.txnT("scan_sms_btn", lang),
                        onAction = {
                            if (sure.isNotEmpty()) {
                                viewModel.approveAllPending(sure)
                                scope.launch {
                                    snackbar.showSnackbar(com.pesaflow.app.ui.language.txnT("confirmed_msg", lang, sure.size.toString()), duration = SnackbarDuration.Short)
                                }
                            } else runScan(30, "30 days")
                        }
                    )
                } else {
                    PesaEmptyState(
                        title = com.pesaflow.app.ui.language.txnT("no_tx_title", lang),
                        explanation = com.pesaflow.app.ui.language.txnT("no_tx_body", lang),
                        actionLabel = if (scanning) com.pesaflow.app.ui.language.txnT("scanning_found", lang, scanFound.toString()) else com.pesaflow.app.ui.language.txnT("scan_sms_btn", lang),
                        onAction = { runScan(30, "30 days") }
                    )
                    scanMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = PesaSpacing.md)) {
                OutlinedTextField(
                    value = merchantQuery,
                    onValueChange = { merchantQuery = it },
                    label = { Text(com.pesaflow.app.ui.language.txnT("search_label", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onOpenSearch) { Text(com.pesaflow.app.ui.language.txnT("search_everything", lang)) }
                }
                Spacer(modifier = Modifier.height(4.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = typeFilter == null,
                        onClick = { typeFilter = null },
                        label = { Text(com.pesaflow.app.ui.language.txnT("t_all", lang)) }
                    )
                    listOf("INCOME" to com.pesaflow.app.ui.language.txnT("t_in", lang), "EXPENSE" to com.pesaflow.app.ui.language.txnT("t_out", lang), "SAVING" to com.pesaflow.app.ui.language.txnT("t_saved", lang), "INVESTMENT" to com.pesaflow.app.ui.language.txnT("t_grown", lang), "TRANSFER" to com.pesaflow.app.ui.language.txnT("t_moved", lang)).forEach { (v, label) ->
                        FilterChip(
                            selected = typeFilter == v,
                            onClick = { typeFilter = if (typeFilter == v) null else v },
                            label = { Text(label) }
                        )
                    }
                    // Dual-SIM: each line keeps its own history.
                    FilterChip(
                        selected = simFilter == 0,
                        onClick = { simFilter = if (simFilter == 0) null else 0 },
                        label = { Text("SIM 1") }
                    )
                    FilterChip(
                        selected = simFilter == 1,
                        onClick = { simFilter = if (simFilter == 1) null else 1 },
                        label = { Text("SIM 2") }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = !oldestFirst, onClick = { oldestFirst = false }, label = { Text(com.pesaflow.app.ui.language.txnT("newest", lang)) })
                    FilterChip(selected = oldestFirst, onClick = { oldestFirst = true }, label = { Text(com.pesaflow.app.ui.language.txnT("oldest", lang)) })
                    FilterChip(
                        selected = selecting,
                        onClick = {
                            selecting = !selecting
                            if (!selecting) selection = emptySet()
                        },
                        label = { Text(if (selecting) com.pesaflow.app.ui.language.txnT("done_sel", lang) else com.pesaflow.app.ui.language.txnT("select", lang)) }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = rangeDays == null, onClick = { rangeDays = null }, label = { Text(com.pesaflow.app.ui.language.txnT("all_time", lang)) })
                    FilterChip(selected = rangeDays == 0, onClick = { rangeDays = if (rangeDays == 0) null else 0 }, label = { Text(com.pesaflow.app.ui.language.txnT("today", lang)) })
                    FilterChip(selected = rangeDays == 7, onClick = { rangeDays = if (rangeDays == 7) null else 7 }, label = { Text(com.pesaflow.app.ui.language.txnT("days7", lang)) })
                    FilterChip(selected = rangeDays == 30, onClick = { rangeDays = if (rangeDays == 30) null else 30 }, label = { Text(com.pesaflow.app.ui.language.txnT("days30", lang)) })
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Scan row: today is one tap; week/month/history live behind
                // a disclosure with plain info about what each one does.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { runScan(1, "today") }, enabled = !scanning) {
                        Text(if (scanning) com.pesaflow.app.ui.language.txnT("scanning_found", lang, scanFound.toString()) else com.pesaflow.app.ui.language.txnT("scan_today", lang))
                    }
                    TextButton(onClick = { showMoreScan = !showMoreScan }) {
                        Text(if (showMoreScan) com.pesaflow.app.ui.language.txnT("less", lang) else com.pesaflow.app.ui.language.txnT("more_scan", lang))
                    }
                }
                if (showMoreScan) {
                    Text(
                        com.pesaflow.app.ui.language.txnT("scan_explainer", lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { runScan(7, "7 days") }, enabled = !scanning) { Text(com.pesaflow.app.ui.language.txnT("scan7", lang)) }
                        TextButton(onClick = { runScan(30, "30 days") }, enabled = !scanning) { Text(com.pesaflow.app.ui.language.txnT("scan30", lang)) }
                        TextButton(onClick = { runScan(150, "5 months") }, enabled = !scanning) { Text(com.pesaflow.app.ui.language.txnT("scan5m", lang)) }
                    }
                }
                scanMsg?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            val viewIn = sorted.filter { it.type == TransactionType.INCOME && !it.isSample && !it.isOpening }.sumOf { it.amount }
            val viewOut = sorted.filter { it.type == TransactionType.EXPENSE && !it.isSample }.sumOf { it.amount }
                val dupClusters = remember(sorted) {
                    sorted.filter { !it.isSample }.groupBy {
                        "${it.amount}|${it.merchant.trim().lowercase()}"
                    }.filter { it.value.size > 1 }.values.toList()
                }
                var mergeGroup by remember { mutableStateOf<List<Transaction>?>(null) }
                // Auto-remove: exact matches only (same amount + merchant +
                // day + method, minutes apart). Keeps the earliest, undoable.
                val autoGroups = remember(sorted) { exactDuplicateGroups(sorted) }
                val autoCount = autoGroups.sumOf { it.size - 1 }
                var confirmAuto by remember { mutableStateOf(false) }
                var autoResult by remember { mutableStateOf<String?>(null) }
                val dayMs = 24L * 60 * 60 * 1000
                val last14 = remember(sorted) {
                    val now = System.currentTimeMillis()
                    // Strict [day, next-day) buckets: the old ..(d0 + dayMs)
                    // range double-counted any row stamped exactly at midnight.
                    rollingDays(now, 14).days().map { d0 ->
                        sorted.filter {
                            it.type == TransactionType.EXPENSE && !it.isSample &&
                                it.dateTimestamp >= d0 && it.dateTimestamp < d0 + dayMs
                        }.sumOf { it.amount }
                    }
                }
                val peak14 = (last14.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
                val topCats = remember(sorted) {
                    sorted.filter { it.type == TransactionType.EXPENSE && !it.isSample }
                        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                        .entries.sortedByDescending { it.value }.take(5)
                }
            LazyColumn(
                Modifier.fillMaxSize().weight(1f),
                verticalArrangement = Arrangement.spacedBy(PesaSpacing.xs)
            ) {
                if (sorted.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            PesaEmptyState(
                                title = "No matching transactions",
                                explanation = "Change your search or filters, or clear them to see your full history.",
                                actionLabel = "Clear search and filters",
                                onAction = {
                                    merchantQuery = ""
                                    typeFilter = null
                                    simFilter = null
                                    rangeDays = null
                                    oldestFirst = false
                                    selecting = false
                                    selection = emptySet()
                                }
                            )
                        }
                    }
                } else {
                    item { Spacer(Modifier.height(PesaSpacing.xs)) }
                    groups.forEach { (day, txs) ->
                        item(key = "h-$day") {
                            val dayNet = txs.filter { !it.isSample }.sumOf {
                                when (it.type) {
                                    TransactionType.INCOME -> it.amount
                                    TransactionType.EXPENSE -> -it.amount
                                    TransactionType.SAVING -> -it.amount
                                    TransactionType.INVESTMENT -> -it.amount
                                    TransactionType.TRANSFER -> 0.0
                                }
                            }
                            val moved = txs.count { it.type == TransactionType.TRANSFER }
                            PesaSectionHeader(
                                title = groupLabel(day, now, lang),
                                subtitle = com.pesaflow.app.ui.language.txnT("day_items", lang, txs.size.toString()) + (if (dayNet >= 0) "+" else "−") + " KSh " + kotlin.math.abs(dayNet).toInt() + (if (moved > 0) com.pesaflow.app.ui.language.txnT("day_moved", lang, moved.toString()) else "")
                            )
                        }
                        items(txs, key = { it.id }) { tx ->
                            Box(Modifier) {
                                TransactionRow(
                                    tx = tx,
                                    onEdit = { editingTx = tx },
                                    onDelete = { confirmDelete = tx },
                                    runningBalance = runningById[tx.id],
                                    selected = tx.id in selection,
                                    onToggleSelect = if (selecting) ({
                                        selection = if (tx.id in selection) selection - tx.id else selection + tx.id
                                    }) else null,
                                    lang = lang
                                )
                            }
                        }
                        item { Spacer(Modifier.height(PesaSpacing.sm)) }
                    }
                }
                if (sorted.isNotEmpty()) {
                item(key = "footer") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    com.pesaflow.app.ui.language.txnT("showing", lang, visibleCount.toString(), sorted.size.toString(), viewIn.toInt().toString(), viewOut.toInt().toString()) + com.pesaflow.app.ui.language.txnT("net", lang) + (if (viewIn - viewOut >= 0) "+" else "−") + "KSh ${kotlin.math.abs(viewIn - viewOut).toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                    TextButton(onClick = { shareTxs(transactions, "all") }) { Text(com.pesaflow.app.ui.language.txnT("share_all", lang)) }
                }
                if (visibleCount < sorted.size) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { visibleLimit += 100 }) { Text(com.pesaflow.app.ui.language.txnT("show_more", lang)) }
                        TextButton(onClick = { visibleLimit = sorted.size }) { Text(com.pesaflow.app.ui.language.txnT("show_all", lang, sorted.size.toString())) }
                    }
                }
                }
                item(key = "bulk") {
                if (selecting && selection.isNotEmpty()) {
                    val picked = remember(selection, transactions) { transactions.filter { it.id in selection } }
                    val pickedTotal = picked.sumOf { it.amount }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            com.pesaflow.app.ui.language.txnT("picked", lang, selection.size.toString(), pickedTotal.toInt().toString()),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { shareTxs(picked, "picked") }) { Text(com.pesaflow.app.ui.language.txnT("share", lang)) }
                        TextButton(onClick = { confirmBulk = true }) {
                            Text(com.pesaflow.app.ui.language.txnT("delete", lang), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                }
                item(key = "dups") {
                if (dupClusters.isNotEmpty()) {
                    com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.LARGE) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.xs)
                        ) {
                            com.pesaflow.app.ui.theme.PpSectionHeader(
                                title = com.pesaflow.app.ui.language.txnT("dup_title", lang, dupClusters.size.toString()),
                                subtitle = com.pesaflow.app.ui.language.txnT("dup_sub", lang)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (autoCount > 0) {
                                TextButton(onClick = { confirmAuto = true }) {
                                    Text(
                                        com.pesaflow.app.ui.language.txnT("auto_remove", lang, autoCount.toString()),
                                        style = com.pesaflow.app.ui.theme.ppTypography.labelLarge,
                                        color = com.pesaflow.app.ui.theme.ppColors.gold
                                    )
                                }
                            }
                            autoResult?.let { msg ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.undoAutoDedupe { n -> autoResult = com.pesaflow.app.ui.language.txnT("restored", lang, n.toString()) }
                                    }) { Text(com.pesaflow.app.ui.language.txnT("undo", lang)) }
                                }
                            }
                            dupClusters.take(5).forEach { g ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${g.first().merchant} · KSh ${g.first().amount.toInt()} (${g.size}×)",
                                        style = com.pesaflow.app.ui.theme.ppTypography.bodyMedium,
                                        color = com.pesaflow.app.ui.theme.ppColors.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { mergeGroup = g }) {
                                        Text(
                                            com.pesaflow.app.ui.language.txnT("review_btn", lang),
                                            style = com.pesaflow.app.ui.theme.ppTypography.labelMedium,
                                            color = com.pesaflow.app.ui.theme.ppColors.gold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
                item(key = "last14") {
                com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.LARGE) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.xs)
                    ) {
                        com.pesaflow.app.ui.theme.PpSectionHeader(
                            title = "Last 14 days",
                            subtitle = "Daily spend, oldest to newest"
                        )
                        last14.forEachIndexed { i, v ->
                            val bars = "█".repeat(((v / peak14) * 12).toInt().coerceIn(0, 12)).ifEmpty { "·" }
                            Text(
                                "D-${13 - i} $bars KSh ${v.toInt()}",
                                style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                                color = com.pesaflow.app.ui.theme.ppColors.textTertiary,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
                }
                item(key = "topcats") {
                if (topCats.isNotEmpty()) {
                    com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.LARGE) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.sm)
                        ) {
                            com.pesaflow.app.ui.theme.PpSectionHeader(title = "Top in view")
                            val topMax = topCats.first().value.coerceAtLeast(1.0)
                            topCats.forEach { e ->
                                Column(verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.xs)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            e.key,
                                            style = com.pesaflow.app.ui.theme.ppTypography.labelLarge,
                                            color = com.pesaflow.app.ui.theme.ppColors.textPrimary
                                        )
                                        Text(
                                            "KSh ${e.value.toInt()}",
                                            style = com.pesaflow.app.ui.theme.ppTypography.financialSmall,
                                            color = com.pesaflow.app.ui.theme.ppColors.textPrimary
                                        )
                                    }
                                    com.pesaflow.app.ui.theme.PpProgress(fraction = (e.value / topMax).toFloat())
                                }
                            }
                        }
                    }
                }
                }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
                if (confirmAuto) {
                    AlertDialog(
                        onDismissRequest = { confirmAuto = false },
                        title = { Text(com.pesaflow.app.ui.language.txnT("dedupe_title", lang, autoCount.toString())) },
                        text = { Text(com.pesaflow.app.ui.language.txnT("dedupe_body", lang)) },
                        confirmButton = {
                            TextButton(onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.autoRemoveExactDuplicates(autoGroups) { n ->
                                    confirmAuto = false
                                    autoResult = com.pesaflow.app.ui.language.txnT("removed_dupes", lang, n.toString())
                                }
                            }) { Text(com.pesaflow.app.ui.language.txnT("remove", lang)) }
                        },
                        dismissButton = { TextButton(onClick = { confirmAuto = false }) { Text(com.pesaflow.app.ui.language.txnT("keep", lang)) } }
                    )
                }
                mergeGroup?.let { g ->
                    AlertDialog(
                        onDismissRequest = { mergeGroup = null },
                        title = { Text(com.pesaflow.app.ui.language.txnT("merge_title", lang, g.size.toString())) },
                        text = { Text(com.pesaflow.app.ui.language.txnT("merge_body", lang, g.first().merchant, g.first().amount.toInt().toString(), (g.size - 1).toString())) },
                        confirmButton = {
                            TextButton(onClick = {
                                val keep = g.maxByOrNull { it.dateTimestamp }?.id
                                // Batched: one statement, one emission — N single
                                // deletes recompose per row and ANR big merges.
                                viewModel.deleteTransactions(g.filter { it.id != keep }.map { it.id }) {
                                    mergeGroup = null
                                }
                            }) { Text(com.pesaflow.app.ui.language.txnT("merge_btn", lang), color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { mergeGroup = null }) { Text(com.pesaflow.app.ui.language.txnT("keep_all", lang)) } }
                    )
                }
            }
        }
    }
    }

    editingTx?.let {
        QuickAddDialog(viewModel = viewModel, defaultType = it.type, onDismiss = { editingTx = null }, existing = it)
    }

    if (confirmBulk) {
        val picked = transactions.filter { it.id in selection }
        AlertDialog(
            onDismissRequest = { confirmBulk = false },
            title = { Text(com.pesaflow.app.ui.language.txnT("del_bulk_title", lang, picked.size.toString())) },
            text = { Text(com.pesaflow.app.ui.language.txnT("del_bulk_body", lang, picked.sumOf { it.amount }.toInt().toString())) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransactions(picked.map { it.id }) {
                        selection = emptySet()
                        selecting = false
                        confirmBulk = false
                    }
                }) { Text(com.pesaflow.app.ui.language.txnT("del_all_btn", lang), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmBulk = false }) { Text(com.pesaflow.app.ui.language.txnT("keep", lang)) } }
        )
    }

    confirmDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(com.pesaflow.app.ui.language.txnT("del_title", lang)) },
            text = { Text(com.pesaflow.app.ui.language.txnT("del_body", lang, tx.merchant, tx.amount.toInt().toString())) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.deleteTransactionWithUndo(tx)
                    confirmDelete = null
                    scope.launch {
                        val r = snackbar.showSnackbar(com.pesaflow.app.ui.language.txnT("deleted_merchant", lang, tx.merchant), com.pesaflow.app.ui.language.txnT("undo", lang), duration = SnackbarDuration.Long)
                        if (r == SnackbarResult.ActionPerformed) viewModel.undoLast()
                    }
                }) { Text(com.pesaflow.app.ui.language.txnT("delete", lang), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(com.pesaflow.app.ui.language.txnT("keep", lang)) } }
        )
    }
}

@Composable
fun TransactionRow(
    tx: Transaction,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    showActions: Boolean = true,
    runningBalance: Double? = null,
    selected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    lang: com.pesaflow.app.data.models.AppLanguage = com.pesaflow.app.data.models.AppLanguage.ENGLISH
) {
    val isIncome = tx.type == TransactionType.INCOME
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE) }
    var aliasTick by remember { mutableStateOf(0) }
    val alias = remember(tx.merchant, aliasTick) { MerchantMemory.lookup(prefs, tx.merchant) }
    var showAlias by remember { mutableStateOf(false) }
    var aliasInput by remember(tx.merchant, showAlias) { mutableStateOf(alias?.label.orEmpty()) }
    Card(
        onClick = { onToggleSelect?.invoke() },
        enabled = onToggleSelect != null,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
        shape = com.pesaflow.app.ui.theme.ppShapes.cardCompact,
        colors = CardDefaults.cardColors(containerColor = com.pesaflow.app.ui.theme.ppColors.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) com.pesaflow.app.ui.theme.ppColors.borderGold else com.pesaflow.app.ui.theme.ppColors.border
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = com.pesaflow.app.ui.theme.ppSpacing.lg,
                vertical = com.pesaflow.app.ui.theme.ppSpacing.md
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.md)
        ) {
            CategoryIcon(category = tx.category)
            Column(Modifier.weight(1f)) {
                Text(
                    alias?.label ?: tx.merchant.ifBlank { tx.category },
                    style = com.pesaflow.app.ui.theme.ppTypography.labelLarge,
                    color = com.pesaflow.app.ui.theme.ppColors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    (if (alias != null && tx.merchant.isNotBlank()) tx.merchant + " · " else "") + "${tx.category} · ${tx.paymentMethod.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercase() }}" +
                        (if (tx.simSlot >= 0) " · SIM ${tx.simSlot + 1}" else ""),
                    style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                    color = com.pesaflow.app.ui.theme.ppColors.textTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (when (tx.type) {
                        TransactionType.INCOME -> "+ "
                        TransactionType.TRANSFER -> "↔ "
                        else -> "− "
                    }) + tx.amount.toKSh().removePrefix("KSh "),
                    style = com.pesaflow.app.ui.theme.ppTypography.financialSmall,
                    color = when (tx.type) {
                        TransactionType.INCOME -> com.pesaflow.app.ui.theme.ppColors.income
                        TransactionType.SAVING -> com.pesaflow.app.ui.theme.ppColors.gold
                        TransactionType.INVESTMENT -> com.pesaflow.app.ui.theme.ppColors.brightBlue
                        TransactionType.TRANSFER -> com.pesaflow.app.ui.theme.ppColors.textTertiary
                        else -> com.pesaflow.app.ui.theme.ppColors.expense
                    },
                    maxLines = 1
                )
                if (runningBalance != null) {
                    Text(
                        com.pesaflow.app.ui.language.txnT("bal", lang) + (if (runningBalance < 0) "−" else "") + "KSh " + kotlin.math.abs(runningBalance).toInt(),
                        style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                        color = com.pesaflow.app.ui.theme.ppColors.textTertiary,
                        maxLines = 1
                    )
                }
            }
            if (showActions) {
                IconButton(onClick = { showAlias = true }) { Icon(Icons.Filled.Person, contentDescription = com.pesaflow.app.ui.language.txnT("name_sender", lang), tint = com.pesaflow.app.ui.theme.ppColors.textTertiary) }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = com.pesaflow.app.ui.language.txnT("edit_txn", lang), tint = com.pesaflow.app.ui.theme.ppColors.textTertiary) }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = com.pesaflow.app.ui.language.txnT("del_txn", lang), tint = com.pesaflow.app.ui.theme.ppColors.error)
                }
            }
        }
    }
    if (showAlias) {
        AlertDialog(
            onDismissRequest = { showAlias = false },
            title = { Text(com.pesaflow.app.ui.language.txnT("alias_title", lang, tx.merchant.ifBlank { tx.category })) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        com.pesaflow.app.ui.language.txnT("alias_body", lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text(com.pesaflow.app.ui.language.txnT("alias_label", lang)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (alias != null) {
                        Text(
                            com.pesaflow.app.ui.language.txnT("alias_showing", lang, alias.label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (MerchantMemory.learnAlias(prefs, tx.merchant, aliasInput)) {
                        CategoryMemory.learn(prefs, tx.merchant, tx.category)
                        aliasTick++
                    }
                    showAlias = false
                }) { Text(com.pesaflow.app.ui.language.txnT("save", lang)) }
            },
            dismissButton = {
                Row {
                    if (alias != null) {
                        TextButton(onClick = {
                            MerchantMemory.clear(prefs, tx.merchant)
                            aliasTick++
                            showAlias = false
                        }) { Text(com.pesaflow.app.ui.language.txnT("forget", lang)) }
                    }
                    TextButton(onClick = { showAlias = false }) { Text(com.pesaflow.app.ui.language.txnT("cancel", lang)) }
                }
            }
        )
    }
}

private fun Double.toKShTrim(): String = this.toKSh()
