package com.pesaflow.app.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.ledger.ContactBook
import com.pesaflow.app.data.ledger.ContactEntry
import com.pesaflow.app.data.ledger.normalizeContact

// TODO(REMOVE_BEFORE_PRODUCTION): testing-only contact dump/restore lives
// behind this flag so reinstalls don't wipe the test setup. Delete the flag,
// the testing card below, and the MainActivity handlers before any release.
const val TEST_CONTACT_DUMP_ENABLED = true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactBookScreen(
    contacts: List<ContactEntry>,
    transactions: List<com.pesaflow.app.data.models.Transaction> = emptyList(),
    seedMerchant: String = "",
    onSeedConsumed: () -> Unit = {},
    reprocessStatus: String? = null,
    rescanActive: Boolean = false,
    onCancelRescan: () -> Unit = {},
    onSave: (name: String, display: String, rel: String, cat: String, scope: String, notes: String, matchTerms: String) -> Unit,
    onDelete: (name: String) -> Unit,
    onScanToday: () -> Unit = {},
    testingToolsEnabled: Boolean = false,
    onExportContacts: () -> String = { "[]" },
    onImportContacts: (String) -> Int = { 0 }
) {
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ContactEntry?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var seededName by remember { mutableStateOf("") }
    // Deep link from spending footprint: capture the merchant locally, open
    // the naming dialog prefilled, then consume the seed so plain visits
    // stay plain. Local capture wins the race with the parent clearing it.
    LaunchedEffect(seedMerchant) {
        if (seedMerchant.isNotBlank()) {
            seededName = seedMerchant
            showAdd = true
            onSeedConsumed()
        }
    }

    val filtered = remember(contacts, search) {
        if (search.isBlank()) contacts
        else contacts.filter {
            it.displayName.contains(search, ignoreCase = true) ||
                it.relationship.contains(search, ignoreCase = true) ||
                it.category.contains(search, ignoreCase = true)
        }
    }.sortedByDescending { it.lastSeen }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Contact Book") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add contact")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Set a relationship and optional matching names here. Saving a rule immediately rescans your SMS history and updates matching transaction categories.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (testingToolsEnabled) {
                var dumpMsg by remember { mutableStateOf<String?>(null) }
                val dumpCtx = androidx.compose.ui.platform.LocalContext.current
                val exportPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    try {
                        dumpCtx.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(onExportContacts().toByteArray(Charsets.UTF_8))
                        }
                        dumpMsg = "Contacts saved to file ✓ — load it back after a reinstall."
                    } catch (e: Exception) {
                        dumpMsg = "Export failed: ${e.message}"
                    }
                }
                val importPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    try {
                        val text = dumpCtx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
                        val n = onImportContacts(text)
                        dumpMsg = if (n >= 0) "Restored $n contact(s) ✓" else "That file is not a contacts backup."
                    } catch (e: Exception) {
                        dumpMsg = "Import failed: ${e.message}"
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "🧪 Testing tools — REMOVE BEFORE PRODUCTION",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Save/load contact rules so reinstalls don't wipe the test setup. Counts rebuild themselves from new SMS.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { exportPicker.launch("pesaflow-contacts-test.json") }) {
                                Text("Save contacts")
                            }
                            OutlinedButton(onClick = { importPicker.launch(arrayOf("application/json")) }) {
                                Text("Load contacts")
                            }
                        }
                        dumpMsg?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            reprocessStatus?.let {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rescanActive) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (rescanActive) {
                        TextButton(onClick = onCancelRescan) { Text("Cancel") }
                    }
                }
            }
            // Quick action: scan today's SMS for new transactions
            OutlinedButton(
                onClick = onScanToday,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("Scan today's SMS")
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search contacts...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
            Text(
                "${filtered.size} contacts",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(filtered, key = { it.id.ifBlank { "contact-${it.name}" } }) { c ->
                    ContactRow(
                        entry = c,
                        transactions = transactions,
                        onEdit = { editing = c },
                        onDelete = { onDelete(c.name) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        ContactEditDialog(
            initial = seededName.takeIf { it.isNotBlank() }
                ?.let { com.pesaflow.app.data.ledger.ContactEntry(name = it, displayName = it) },
            onDismiss = { showAdd = false; seededName = "" },
            onSave = { name, display, rel, cat, scope, notes, matchTerms ->
                onSave(name, display, rel, cat, scope, notes, matchTerms)
                showAdd = false
                seededName = ""
            }
        )
    }
    editing?.let { e ->
        ContactEditDialog(
            initial = e,
            onDismiss = { editing = null },
            onSave = { name, display, rel, cat, scope, notes, matchTerms ->
                onSave(name, display, rel, cat, scope, notes, matchTerms)
                editing = null
            }
        )
    }
}

@Composable
private fun ContactRow(
    entry: ContactEntry,
    transactions: List<com.pesaflow.app.data.models.Transaction> = emptyList(),
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val members = remember(entry) {
        com.pesaflow.app.data.ledger.splitUserList(entry.matchTerms)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.displayName, fontWeight = FontWeight.Bold)
                if (entry.relationship.isNotBlank()) {
                    Text(entry.relationship, style = MaterialTheme.typography.bodySmall)
                }
                if (entry.category.isNotBlank()) {
                    Text("→ ${entry.category}", style = MaterialTheme.typography.bodySmall)
                }
                if (entry.matchTerms.isNotBlank()) {
                    Text("Also matches: ${entry.matchTerms}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "${entry.transactionCount} tx · last ${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(entry.lastSeen))}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
                Text(if (expanded) "-" else "+", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
        if (expanded) {
            // Lazy: the regex sweep over the ledger runs only for the
            // opened card, not for every row on every recomposition.
            val myTxns = remember(entry, transactions) {
                transactions.filter { com.pesaflow.app.data.ledger.ContactBook.matchesContact(it.merchant, entry) }
                    .sortedByDescending { it.dateTimestamp }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (members.isNotEmpty()) {
                Text(
                    "Group members (" + members.size + ") - each opens its own messages:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                members.forEach { term ->
                    MemberRow(term = term, transactions = transactions)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (myTxns.isEmpty()) {
                Text(
                    "No messages recorded under this name yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Messages (" + myTxns.size + "):",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                myTxns.take(20).forEach { tx ->
                    Text(
                        tx.merchant + " - KSh " + tx.amount.toInt() + " (" +
                            java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(tx.dateTimestamp)) + ")",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (myTxns.size > 20) {
                    Text(
                        "+" + (myTxns.size - 20) + " more in Transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun MemberRow(term: String, transactions: List<com.pesaflow.app.data.models.Transaction>) {
    var open by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp)) {
        TextButton(onClick = { open = !open }) {
            Text((if (open) "- " else "+ ") + term, style = MaterialTheme.typography.bodySmall)
        }
        if (open) {
            val termTxns = remember(term, transactions) {
                transactions.filter { com.pesaflow.app.data.ledger.ContactBook.matchesContact(it.merchant, term) }
                    .sortedByDescending { it.dateTimestamp }
            }
            Text(
                "Messages (" + termTxns.size + "):",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            if (termTxns.isEmpty()) {
                Text("None yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            termTxns.take(10).forEach { tx ->
                Text(
                    tx.merchant + " - KSh " + tx.amount.toInt(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactEditDialog(
    initial: ContactEntry?,
    onDismiss: () -> Unit,
    onSave: (name: String, display: String, rel: String, cat: String, scope: String, notes: String, matchTerms: String) -> Unit
) {
    var display by remember { mutableStateOf(initial?.displayName ?: "") }
    var rel by remember { mutableStateOf(initial?.relationship ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: "") }
    var scope by remember { mutableStateOf(initial?.scope ?: "BOTH") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var matchTerms by remember { mutableStateOf(initial?.matchTerms ?: "") }
    var relExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Contact" else "Edit Contact") },
        text = {
            Column {
                OutlinedTextField(
                    value = display,
                    onValueChange = { display = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = relExpanded,
                    onExpandedChange = { relExpanded = it }
                ) {
                    OutlinedTextField(
                        value = rel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Relationship") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = relExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = relExpanded,
                        onDismissRequest = { relExpanded = false }
                    ) {
                        ContactBook.relationships().forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r) },
                                onClick = { rel = r; relExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = matchTerms,
                    onValueChange = { matchTerms = it },
                    label = { Text("Also match parsed names") },
                    placeholder = { Text("Nancy, Daniel Mayhvjh") },
                    supportingText = { Text("Comma-separated names or phrases; matched as whole words after parsing.") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cat,
                    onValueChange = { cat = it },
                    label = { Text("Auto-category (e.g. Food, Rent, Upkeep)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    listOf("IN", "OUT", "BOTH").forEach { s ->
                        FilterChip(
                            selected = scope == s,
                            onClick = { scope = s },
                            label = { Text(s) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = if (initial != null) initial.name else normalizeContact(display)
                    onSave(n, display, rel, cat, scope, notes, matchTerms)
                },
                enabled = display.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
