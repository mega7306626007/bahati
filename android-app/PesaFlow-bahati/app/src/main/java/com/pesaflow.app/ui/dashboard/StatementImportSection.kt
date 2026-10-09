package com.pesaflow.app.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.parsers.StatementImporter
import com.pesaflow.app.data.parsers.StatementPdfOpen
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


// First-time Home import card: the same statement pipeline as the welcome
// screen (same parsers, same Pending queue with IDs, same dedupe, same
// fee harvest, same wallet reconciliation) — without the onboarding-only
// extras (budget backfill, sender cards, scan stats), which don't apply
// once onboarding is done. SMS scanning lives in the quick actions above;
// this card owns the two file paths: CSV and password-protected PDF.
@Composable
fun StatementImportSection(viewModel: FinanceViewModel) {
    val appContext = LocalContext.current
    val scanScope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var stage by remember { mutableStateOf<String?>(null) }
    var queuedTotal by remember { mutableStateOf(0) }
    var pendingPdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var statementPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    fun readMemories(): Map<String, com.pesaflow.app.data.ledger.ContactMemory> {
        val prefs = appContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
        return com.pesaflow.app.data.ledger.readContactMemories(
            prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
        )
    }

    fun queueRows(
        rows: List<com.pesaflow.app.data.models.PendingTransaction>,
        latestBalance: Double?,
        kindLabel: String,
        skippedParse: Int,
        fileLabel: String,
        summary: com.pesaflow.app.data.parsers.StatementSummary? = null,
        walletIn: Double = 0.0,
        walletOut: Double = 0.0
    ) {
        if (rows.isEmpty()) {
            busy = false
            msg = "No readable transactions in $fileLabel — check the file and try again."
            return
        }
        progress = 0.9f
        stage = "Queueing ${rows.size} rows for review…"
        latestBalance?.let { com.pesaflow.app.data.parsers.saveMpesaBalance(appContext, it) }
        viewModel.importStatementPending(rows) { queued, skipped ->
            busy = false
            queuedTotal += queued
            val dropped = skipped + skippedParse
            val totals = summary?.takeIf { it.totalIn > 0 || it.totalOut > 0 }?.let {
                val mark = if (kotlin.math.abs(walletIn - it.totalIn) < 1.0 &&
                    kotlin.math.abs(walletOut - it.totalOut) < 1.0
                ) " ✓" else " (gap: $skippedParse skipped)"
                " Statement says in KSh ${it.totalIn.toInt()} · out KSh ${it.totalOut.toInt()}; " +
                    "rows sum in KSh ${walletIn.toInt()} · out KSh ${walletOut.toInt()}$mark."
            } ?: if (walletIn > 0 || walletOut > 0) {
                " Rows sum in KSh ${walletIn.toInt()} · out KSh ${walletOut.toInt()}."
            } else ""
            msg = if (queued > 0) {
                "$queued $kindLabel transaction${if (queued == 1) "" else "s"} queued below for review" +
                    (if (dropped > 0) " ($dropped duplicate${if (dropped == 1) "" else "s"}/unreadable skipped)" else "") +
                    ".$totals Confirm them in Pending 🔔."
            } else {
                "All $kindLabel rows are already in — $dropped duplicate${if (dropped == 1) "" else "s"} skipped."
            }
        }
    }

    fun openPdf(bytes: ByteArray, password: String?, fileLabel: String) {
        busy = true
        msg = null
        passwordError = null
        progress = 0.05f
        stage = "Reading file…"
        scanScope.launch {
            val opened = withContext(Dispatchers.IO) {
                StatementImporter.extractPdfTextPaged(bytes.inputStream(), password) { done, total ->
                    scanScope.launch {
                        progress = 0.05f + 0.65f * done / total.coerceAtLeast(1)
                        stage = "Extracting page $done of $total…"
                    }
                }
            }
            when (opened) {
                is StatementPdfOpen.NeedsPassword -> {
                    busy = false
                    pendingPdfBytes = bytes
                    passwordError =
                        if (!password.isNullOrBlank()) "Wrong password — check the ID used at download and try again." else null
                    showPassword = true
                }
                is StatementPdfOpen.Error -> {
                    busy = false
                    msg = "Could not open that PDF: ${opened.message}"
                }
                is StatementPdfOpen.Ok -> {
                    progress = 0.75f
                    stage = "Parsing rows…"
                    val parsed = withContext(Dispatchers.IO) {
                        StatementImporter.parseStatementPdfText(opened.text, readMemories())
                    }
                    if (parsed.rows.isEmpty()) {
                        busy = false
                        msg = when {
                            parsed.charsRead < 200 ->
                                "That PDF opened but looks empty ($fileLabel, ${parsed.charsRead} characters) — it may be a scanned image. Try the CSV export instead."
                            parsed.candidates > 0 ->
                                "Opened $fileLabel (${parsed.candidates} receipt-like lines) but none parsed as statement rows — this layout differs from the usual M-Pesa format. Try the CSV export instead."
                            else ->
                                "No readable transactions in $fileLabel — it may be a scanned image. Try the CSV export instead."
                        }
                    } else {
                        queueRows(parsed.rows, parsed.latestBalance, "statement", parsed.skipped, fileLabel, parsed.summary, parsed.walletIn, parsed.walletOut)
                    }
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        msg = null
        progress = 0.1f
        stage = "Reading file…"
        scanScope.launch {
            val mime = withContext(Dispatchers.IO) {
                try {
                    appContext.contentResolver.getType(uri)
                } catch (_: Exception) {
                    null
                }
            }
            val name = uri.lastPathSegment.orEmpty().lowercase()
            val fileLabel = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "your file"
            if ((mime?.contains("pdf") == true) || name.endsWith(".pdf")) {
                val bytes = withContext(Dispatchers.IO) {
                    try {
                        appContext.contentResolver.openInputStream(uri)?.readBytes()
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bytes == null || bytes.isEmpty()) {
                    busy = false
                    msg = "Could not read that PDF — try again."
                    return@launch
                }
                openPdf(bytes, statementPassword.ifBlank { null }, fileLabel)
                return@launch
            }
            val text = withContext(Dispatchers.IO) {
                try {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                } catch (_: Exception) {
                    ""
                }
            }
            if (text.isBlank()) {
                busy = false
                msg = "Could not read that file — try again."
                return@launch
            }
            progress = 0.5f
            stage = "Parsing ${text.length / 1000} KB…"
            val parsed = withContext(Dispatchers.IO) {
                StatementImporter.parseStatementCsv(text, readMemories())
            }
            if (parsed.rows.isEmpty()) {
                busy = false
                val headerHint = parsed.headerEcho.takeIf { it.isNotBlank() }?.let {
                    " Found header: \"$it\"."
                }.orEmpty()
                msg = "No readable transactions in $fileLabel " +
                    "(${parsed.fileKind}, ${parsed.charsRead} characters read).$headerHint " +
                    "Expected an M-Pesa statement (Receipt No., date, details, amounts) — check the file and try again."
            } else {
                queueRows(parsed.rows, parsed.latestBalance, "CSV", parsed.skipped, fileLabel, null, parsed.walletIn, parsed.walletOut)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "No SMS to scan? Upload your M-Pesa statement (CSV or PDF) — every row queues for review exactly like a scanned text.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { picker.launch("*/*") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) (stage ?: "Reading statement…") else if (queuedTotal > 0) "Upload another statement" else "Upload M-Pesa statement")
        }
        if (busy) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
        msg?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
        }
    }
    if (showPassword) {
        AlertDialog(
            onDismissRequest = {
                showPassword = false
                pendingPdfBytes = null
                passwordError = null
            },
            title = { Text("Statement password 🔐") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "M-Pesa statements are locked with the ID used at download.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = statementPassword,
                        onValueChange = { statementPassword = it },
                        label = { Text("ID number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    passwordError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPassword = false
                    pendingPdfBytes?.let { bytes -> openPdf(bytes, statementPassword.ifBlank { null }, "your statement") }
                    pendingPdfBytes = null
                }) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { showPassword = false }) { Text("Cancel") } }
        )
    }
}
