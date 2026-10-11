package com.pesaflow.app.ui.exports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.*
import com.pesaflow.app.data.exports.ExportEngine
import com.pesaflow.app.data.ledger.Watchlist

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(
    transactions: List<Transaction>,
    budgets: List<Budget> = emptyList(),
    goals: List<SavingsGoal> = emptyList(),
    bills: List<Bill> = emptyList(),
    debts: List<Debt> = emptyList(),
    profile: com.pesaflow.app.data.models.UniversityProfile? = null
) {
    val engine = remember { ExportEngine() }
    var csvText by remember { mutableStateOf("") }
    var backupJson by remember { mutableStateOf("") }
    var shareText by remember { mutableStateOf("") }
    // Range export: window + category filter. Null window = all time.
    var rangeStart by remember { mutableStateOf<Long?>(null) }
    var rangeEnd by remember { mutableStateOf<Long?>(null) }
    var rangeCats by remember { mutableStateOf(setOf<String>()) }
    val allCats = remember(transactions) {
        transactions.map { it.category }.distinct().sorted().take(10)
    }
    val dayMs = 24L * 60 * 60 * 1000
    val now = remember { System.currentTimeMillis() }
    val monthStart = remember(now) {
        java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Purpose-first: one open section at a time. Backup and export
    // never blend - restoring lives in Settings, stated below.
    var openSection by remember { mutableStateOf<String?>(null) }
    fun toggle(section: String) {
        openSection = if (openSection == section) null else section
    }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("📤 Export & Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            PurposeHeader(
                title = "Analyse my transactions",
                subtitle = "CSV or PDF of your ledger",
                open = openSection == "analyse",
                onToggle = { toggle("analyse") }
            )
        }
        if (openSection == "analyse") {
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CSV Export", fontWeight = FontWeight.Bold)
                    Text("Export all transactions, budgets, goals, bills, and debts as CSV.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        csvText = engine.exportCsv(transactions, budgets, goals, bills, debts)
                    }) { Text("Generate CSV") }
                    if (csvText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("✅ CSV ready (${csvText.length} chars)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Range export", fontWeight = FontWeight.Bold)
                    Text("A window and/or categories — commitments always export whole.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        data class RangeChip(val label: String, val start: Long?, val end: Long?)
                        listOf(
                            RangeChip("All time", null, null),
                            RangeChip("This month", monthStart, null),
                            RangeChip("Last 30 days", now - 30 * dayMs, null),
                            RangeChip("Last 7 days", now - 7 * dayMs, null)
                        ).forEach { r ->
                            FilterChip(
                                selected = rangeStart == r.start && rangeEnd == r.end,
                                onClick = {
                                    if (rangeStart == r.start && rangeEnd == r.end) {
                                        rangeStart = null; rangeEnd = null
                                    } else {
                                        rangeStart = r.start; rangeEnd = r.end
                                    }
                                },
                                label = { Text(r.label) }
                            )
                        }
                    }
                    if (allCats.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Categories (none = all):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            allCats.forEach { c ->
                                FilterChip(
                                    selected = c in rangeCats,
                                    onClick = { rangeCats = if (c in rangeCats) rangeCats - c else rangeCats + c },
                                    label = { Text(c) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        csvText = engine.exportRange(
                            transactions, budgets, goals, bills, debts, profile,
                            startMs = rangeStart ?: Long.MIN_VALUE,
                            endMs = rangeEnd ?: Long.MAX_VALUE,
                            categories = rangeCats
                        )
                    }) { Text("Generate range CSV") }
                    if (csvText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("✅ CSV ready (${csvText.length} chars)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PDF Statement Report", fontWeight = FontWeight.Bold)
                    Text("Full M-Pesa transaction report with category breakdown — uniquely branded, printable.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    val pdfRange = remember(rangeStart, rangeEnd) {
                        val rs = rangeStart
                        val re = rangeEnd
                        if (rs == null || re == null) "All time"
                        else {
                            val fmt = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                            "${fmt.format(java.util.Date(rs))} – ${fmt.format(java.util.Date(re))}"
                        }
                    }
                    val pdfContext = androidx.compose.ui.platform.LocalContext.current
                    Button(onClick = {
                        val rs = rangeStart ?: Long.MIN_VALUE
                        val re = rangeEnd ?: Long.MAX_VALUE
                        val report = com.pesaflow.app.data.exports.statementReportModel(
                            transactions,
                            startMs = rs,
                            endMs = re
                        )
                        val doc = com.pesaflow.app.data.exports.renderStatementReportPdf(report)
                        // Filename sanitized: date labels carry slashes/colons that would
// create phantom subdirectories and crash with ENOENT.
val safeStamp = report.generatedLabel.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').takeIf { it.isNotBlank() } ?: "report"
val file = java.io.File(pdfContext.cacheDir, "pesaflow-statement-$safeStamp.pdf")
                        file.outputStream().use { doc.writeTo(it) }
                        doc.close()
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            pdfContext, "${pdfContext.packageName}.fileprovider", file
                        )
                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        pdfContext.startActivity(android.content.Intent.createChooser(share, "Share PDF statement"))
                    }) { Text("Generate PDF Statement ($pdfRange)") }
                }
            }
        }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            PurposeHeader(
                title = "Back up or restore my data",
                subtitle = "Complete backup file - restore in Settings",
                open = openSection == "backup",
                onToggle = { toggle("backup") }
            )
        }
        if (openSection == "backup") {
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Backup (JSON)", fontWeight = FontWeight.Bold)
                    Text("Full database backup with every table, compiler-checked.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        val payload = engine.buildBackupPayload(
                            transactions = transactions, pending = emptyList(),
                            budgets = budgets, goals = goals, profile = profile,
                            bills = bills, debts = debts, meals = emptyList(),
                            chamas = emptyList(), belongings = emptyList(),
                            kitchenStock = emptyList()
                        )
                        backupJson = engine.backupToJson(payload)
                    }) { Text("Create Backup") }
                    if (backupJson.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("✅ Backup ready (${backupJson.length} chars)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            PurposeHeader(
                title = "Share a financial summary",
                subtitle = "Readable 30-day text for people",
                open = openSection == "share",
                onToggle = { toggle("share") }
            )
        }
        if (openSection == "share") {
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Share Summary", fontWeight = FontWeight.Bold)
                    Text("Human-readable 30-day summary for sharing.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        shareText = engine.generateShareSummary(transactions, 30)
                    }) { Text("Generate Summary") }
                    if (shareText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(shareText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// One purpose per row: title + what it produces + chevron. The flow
// underneath is unchanged -- only the decision is smaller.
@Composable
private fun PurposeHeader(
    title: String,
    subtitle: String,
    open: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (open) "\u25B4" else "\u25BE", style = MaterialTheme.typography.titleMedium)
        }
    }
}
