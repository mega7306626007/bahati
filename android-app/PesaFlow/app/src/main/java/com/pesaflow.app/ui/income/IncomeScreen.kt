package com.pesaflow.app.ui.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.income.IncomeSourceStore
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintIncome
import com.pesaflow.app.viewmodels.FinanceViewModel


// More → Income: the one page for "where does my money come from".
// Anything set here auto-updates the budget calculator base, Buddy's income
// answers and the onboarding review — single store, whole system.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeScreen(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintIncome, bgRes = R.drawable.bg_income)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Income", fontWeight = FontWeight.Bold) },
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "HELB, parents, hustle, job — declare it here once. Expected totals feed the budget calculator and Buddy automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IncomeSetupBlock(
                    highlightSelfSponsored = false,
                    onChanged = {}
                )
                val total = IncomeSourceStore.totalExpected(context)
                if (total > 0) {
                    Text(
                        "Expected: KSh ${total.toInt()}/month across your sources.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                ReconcileCard(viewModel = viewModel)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Reconcile wizard: bulk SMS imports land as expenses with no opening row,
 * so the ledger can read negative while the wallet holds cash. Enter what
 * M-Pesa says and one tap posts a single compensating OPENING row for the
 * gap — idempotent per day, excluded from income statistics by source tag.
 */
@Composable
private fun ReconcileCard(viewModel: FinanceViewModel) {
    val balance by viewModel.availableBalance.collectAsState()
    var walletInput by remember { mutableStateOf("") }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val wallet = walletInput.toDoubleOrNull()
    val diff = if (wallet != null) wallet - balance else null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Reconcile wallet 🤝", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Ledger says KSh ${balance.toInt()}. If M-Pesa says otherwise, enter it — one tap posts the difference as an opening row.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = walletInput,
                onValueChange = { v -> if (v.matches(Regex("^\\d*(\\.\\d{0,2})?\$"))) walletInput = v },
                label = { Text("M-Pesa balance · KSh") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            if (diff != null && kotlin.math.abs(diff) >= 1.0) {
                Text(
                    "Gap: KSh ${diff.toInt()} " + if (diff > 0) "(wallet holds more — books income)" else "(ledger holds more — books expense)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(
                enabled = wallet != null && kotlin.math.abs((diff ?: 0.0)) >= 1.0,
                onClick = {
                    keyboard?.hide()
                    val w = wallet ?: return@Button
                    viewModel.reconcileToWallet(w) { adjusted ->
                        resultMsg = if (adjusted == 0.0) "Already reconciled today — nothing posted. ✅"
                        else "Posted KSh ${adjusted.toInt()} opening row. Ledger matches wallet. ✅"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Reconcile") }
            resultMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
