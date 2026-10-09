package com.pesaflow.app.ui.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.income.IncomeSourceStore


private val KINDS = listOf(
    "HELB_MPESA" to "HELB (M-Pesa)",
    "HELB_BANK" to "HELB (bank)",
    "PARENT" to "Parents",
    "GUARDIAN" to "Guardian",
    "HUSTLE" to "Hustle",
    "JOB" to "Job",
    "SCHOLARSHIP" to "Scholarship",
    "FULIZA" to "Fuliza (borrowed)",
    "OTHER" to "Other"
)

private val BANKS = listOf("KCB", "Equity", "Co-op", "Absa", "Stanbic", "Family", "DTB", "NCBA", "Other")


// Shared income-source manager: used by the onboarding income step AND the
// More → Income page. Room-backed and reactive (Phase 6) — same flow,
// same logic, both stay in sync.
@Composable
fun IncomeSetupBlock(
    viewModel: com.pesaflow.app.viewmodels.FinanceViewModel,
    highlightSelfSponsored: Boolean = false,
    onChanged: () -> Unit = {}
) {
    val sources by viewModel.incomeSources.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    fun persist(next: List<IncomeSource>) {
        viewModel.setIncomeSources(next)
        onChanged()
    }

    if (highlightSelfSponsored) {
        Text(
            "No HELB? No stress — stack hustle, job and parents here. What you add becomes the default everywhere. 💪",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (sources.isEmpty()) {
        Text(
            "No income sources yet — add HELB, parents, hustle, whatever lands. No HELB, no upkeep? Skipping everything is fine — the app works with whatever lands. 🤝",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
    } else {
        sources.forEach { s ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            (s.label.ifBlank { s.displayKind() }) + (if (s.expectedAmount > 0) " · KSh ${s.expectedAmount.toInt()} ${s.frequencyLabel()}" else ""),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            s.displayKind() + (if (s.bank.isNotBlank()) " · ${s.bank}" else "") +
                                (if (s.dayOfMonth in 1..31) " · day ${s.dayOfMonth}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            s.trackingNote(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { persist(sources.filter { it.id != s.id }) }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    Button(
        onClick = { showAdd = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) { Text("Add income source", color = MaterialTheme.colorScheme.onPrimary) }

    if (showAdd) {
        var kind by remember { mutableStateOf("HELB_MPESA") }
        var label by remember { mutableStateOf("") }
        var bank by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var frequency by remember { mutableStateOf("MONTHLY") }
        var day by remember { mutableStateOf("") }
        var useInBudget by remember { mutableStateOf(false) }
        val preview = IncomeSource(kind = kind, label = label, bank = bank).trackingNote()
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New income source") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Where from?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KINDS.forEach { (v, name) ->
                            FilterChip(selected = kind == v, onClick = { kind = v }, label = { Text(name) })
                        }
                    }
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Nickname (e.g. Mum, Jumia hustle)") }, modifier = Modifier.fillMaxWidth())
                    if (kind == "HELB_BANK") {
                        Text("Which bank?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BANKS.forEach { b ->
                                FilterChip(selected = bank == b, onClick = { bank = if (bank == b) "" else b }, label = { Text(b) })
                            }
                        }
                    }
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Expected amount (KSh)") }, modifier = Modifier.fillMaxWidth())
                    Text("How often?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "ONCE" to "One-off").forEach { (v, name) ->
                            FilterChip(selected = frequency == v, onClick = { frequency = v }, label = { Text(name) })
                        }
                    }
                    Text(
                        "Daily money counts too — daily ×30, weekly ×4 into the monthly picture.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("Likely day of month 1-31 (blank = not sure)") }, modifier = Modifier.fillMaxWidth())
                    if (kind == "FULIZA") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = useInBudget,
                                onCheckedChange = { useInBudget = it }
                            )
                            Text(
                                "Use it in budgeting too? Beware: it still reflects as a loan (deni).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        if (kind == "HELB_MPESA") "Fine — I'll parse SMS to see the amounts. ✅"
                        else if (day.toIntOrNull() !in 1..31) "Not sure of the date? No stress — you'll input it manually when it lands. 👍"
                        else preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    persist(
                        sources + IncomeSource(
                            kind = kind,
                            label = label.trim(),
                            bank = if (kind == "HELB_BANK") bank else "",
                            expectedAmount = amount.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0,
                            frequency = frequency,
                            dayOfMonth = day.toIntOrNull()?.takeIf { it in 1..31 } ?: 0,
                            autoTrack = kind == "HELB_MPESA",
                            useInBudget = useInBudget && kind == "FULIZA"
                        )
                    )
                    showAdd = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}
