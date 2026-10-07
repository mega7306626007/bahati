package com.pesaflow.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.pesaflow.app.data.ml.MlEngine
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.MpesaParser
import com.pesaflow.app.ui.theme.PesaSpacing
import com.pesaflow.app.viewmodels.FinanceViewModel

private val QuickCategories = listOf("Food", "Transport", "Rent", "Airtime", "Data", "Shopping", "Health", "School", "Entertainment", "Other")
private val QuickIncomeSources = listOf("Salary", "HELB", "Allowance", "Freelance", "Gift", "Other")
private val QuickAmounts = listOf("100", "200", "500", "1000")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuickAddDialog(
    viewModel: FinanceViewModel,
    defaultType: TransactionType,
    onDismiss: () -> Unit,
    existing: Transaction? = null
) {
    val context = LocalContext.current
    // Tapping any pill commits a choice — drop the keyboard so the sheet
    // stops shoving the view around mid-typing.
    val keyboard = LocalSoftwareKeyboardController.current
    var inputAmount by remember { mutableStateOf(existing?.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var inputMerchant by remember { mutableStateOf(existing?.merchant ?: "") }
    var inputNotes by remember { mutableStateOf(existing?.notes ?: "") }
    var selectedCategory by remember {
        mutableStateOf(
            existing?.category ?: when (defaultType) {
                TransactionType.INCOME -> "Salary"
                TransactionType.SAVING -> "Savings"
                TransactionType.INVESTMENT -> "Investment"
                else -> "Food"
            }
        )
    }
    var entryMode by remember {
        mutableStateOf(
            when (existing?.type) {
                TransactionType.INCOME -> "Received"
                TransactionType.SAVING -> "Saved"
                TransactionType.INVESTMENT -> "Invested"
                else -> when (defaultType) {
                    TransactionType.INCOME -> "Received"
                    TransactionType.SAVING -> "Saved"
                    TransactionType.INVESTMENT -> "Invested"
                    else -> "Spent"
                }
            }
        )
    }
    var selectedDate by remember { mutableStateOf(existing?.dateTimestamp ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
    var selectedMethod by remember {
        val last = context
            .getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
            .getString("last_method", "MPESA")
        mutableStateOf(existing?.paymentMethod ?: runCatching { PaymentMethod.valueOf(last ?: "MPESA") }.getOrDefault(PaymentMethod.MPESA))
    }
    val entryType = when (entryMode) {
        "Received" -> TransactionType.INCOME
        "Saved" -> TransactionType.SAVING
        "Invested" -> TransactionType.INVESTMENT
        else -> TransactionType.EXPENSE
    }
    val amountValid = (inputAmount.toDoubleOrNull() ?: 0.0) > 0

    // On-device ML suggestions (spec §15: suggestion-only, user confirms before any write).
    // Explicit nullable types — avoids the earlier Map<String, Any?> inference break.
    val mlTypeSuggestion: MlEngine.TypeSuggestion? = remember(inputMerchant) {
        if (inputMerchant.isBlank()) null
        else runCatching { MlEngine.suggestType(inputMerchant) }.getOrNull()
    }
    val mlCategorySuggestion: MlEngine.CategorySuggestion? = remember(inputMerchant, inputNotes) {
        if (inputMerchant.isBlank()) null
        else runCatching { MlEngine.suggestCategory(inputMerchant, inputNotes) }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add transaction" else "Edit transaction", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PesaSpacing.sm)) {
                // Four-way type: money out, in, parked (saving), grown (invest)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                    listOf(
                        "Spent" to "− Spent",
                        "Received" to "+ Received",
                        "Saved" to "↓ Saved",
                        "Invested" to "↑ Invested"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = entryMode == mode,
                            onClick = {
                                keyboard?.hide()
                                entryMode = mode
                                selectedCategory = when (mode) {
                                    "Received" -> if (selectedCategory !in QuickIncomeSources) "Salary" else selectedCategory
                                    "Spent" -> if (selectedCategory in QuickIncomeSources) "Food" else selectedCategory
                                    "Saved" -> "Savings"
                                    else -> "Investment"
                                }
                            },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Amount dominates — calculator-style, numeric first
                OutlinedTextField(
                    value = inputAmount,
                    onValueChange = { v -> if (v.matches(Regex("^\\d*(\\.\\d{0,2})?\$"))) inputAmount = v },
                    label = { Text("Amount · KSh") },
                    placeholder = { Text("0", style = MaterialTheme.typography.displaySmall) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Start),
                    modifier = Modifier.fillMaxWidth()
                )
                // Quick amounts: one tap for the common notes
                if (entryMode == "Spent" || entryMode == "Received") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                        QuickAmounts.forEach { amt ->
                            FilterChip(
                                selected = inputAmount == amt,
                                onClick = { keyboard?.hide(); inputAmount = amt },
                                label = { Text(amt) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = inputMerchant,
                    onValueChange = { inputMerchant = it },
                    label = {
                        Text(
                            when (entryMode) {
                                "Received" -> "From who? (e.g. HELB)"
                                "Saved" -> "Where to? (e.g. M-Shwari, Chama)"
                                "Invested" -> "Where to? (e.g. Stima, Shares)"
                                else -> "Where? (e.g. Kibanda, Java House)"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // ML type hint (suggestion-only — never auto-applies).
                val mlType = mlTypeSuggestion
                if (mlType != null && inputMerchant.isNotBlank()) {
                    Text(
                        "ML suggests type: ${mlType.type} (${(mlType.confidence * 100).toInt()}%, ${mlType.source})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Date: today is the default, but a weekend purchase can be
                // back-dated — money math only works when the day is right.
                Text("When?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                    val today = com.pesaflow.app.data.academic.dayStart(System.currentTimeMillis())
                    val selDay = com.pesaflow.app.data.academic.dayStart(selectedDate)
                    FilterChip(
                        selected = selDay == today,
                        onClick = { selectedDate = System.currentTimeMillis() },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = selDay == today - 24L * 60 * 60 * 1000,
                        onClick = { selectedDate = System.currentTimeMillis() - 24L * 60 * 60 * 1000 },
                        label = { Text("Yesterday") }
                    )
                    FilterChip(
                        selected = selDay != today && selDay != today - 24L * 60 * 60 * 1000,
                        onClick = { showDatePicker = true },
                        label = {
                            Text(
                                if (selDay == today || selDay == today - 24L * 60 * 60 * 1000) "Other date…"
                                else android.text.format.DateFormat.format("d MMM yyyy", selectedDate).toString()
                            )
                        }
                    )
                }
                // Elegant category chips — consistent treatment, no wall of fields
                Text(if (entryMode == "Received") "Source" else "Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs), verticalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                    (if (entryMode == "Received") QuickIncomeSources else QuickCategories).forEach { c ->
                        FilterChip(selected = selectedCategory == c, onClick = { keyboard?.hide(); selectedCategory = c }, label = { Text(c) })
                    }
                }
                val suggestedCat = remember(inputMerchant, entryMode) {
                    if (inputMerchant.isBlank()) null
                    else MpesaParser.inferCategory(inputMerchant, entryType).takeIf { it != "Other" }
                }
                if (suggestedCat != null && selectedCategory != suggestedCat) {
                    TextButton(onClick = { keyboard?.hide(); selectedCategory = suggestedCat }) {
                        Text("Use suggested: $suggestedCat?")
                    }
                }
                // ML category suggestion with confirm path (spec §15).
                val mlCat = mlCategorySuggestion
                if (mlCat != null && selectedCategory != mlCat.category) {
                    TextButton(onClick = { keyboard?.hide(); selectedCategory = mlCat.category }) {
                        Text("Use ML category: ${mlCat.category} (${(mlCat.confidence * 100).toInt()}%)?")
                    }
                }
                OutlinedTextField(
                    value = inputNotes,
                    onValueChange = { inputNotes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Payment method", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                    listOf(PaymentMethod.MPESA, PaymentMethod.CASH, PaymentMethod.BANK_TRANSFER, PaymentMethod.AIRTIME).forEach { m ->
                        FilterChip(
                            selected = selectedMethod == m,
                            onClick = { keyboard?.hide(); selectedMethod = m },
                            label = {
                                Text(
                                    when (m) {
                                        PaymentMethod.MPESA -> "M-Pesa"
                                        PaymentMethod.CASH -> "Cash"
                                        PaymentMethod.BANK_TRANSFER -> "Bank"
                                        PaymentMethod.AIRTIME -> "Airtime"
                                        PaymentMethod.OTHER -> "Other"
                                    }
                                )
                            }
                        )
                    }
                }
                if (existing == null) {
                    Text(
                        "Tip: type \"nimebuy lunch 250\" in Insights → Parse and confirm — Food KSh 250.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!amountValid) {
                    Text(
                        "Enter an amount above zero to save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = amountValid,
                onClick = {
                    val amt = inputAmount.toDoubleOrNull() ?: return@Button
                    if (amt <= 0) return@Button
                    val fallbackCategory = if (entryType == TransactionType.INCOME) "Salary" else "Food"
                    val finalCategory = selectedCategory.ifBlank { fallbackCategory }
                    val finalMerchant = inputMerchant.ifBlank { "General" }
                    // Record ML confirm/correct exactly once per save (spec §15 & §18).
                    val mlCat = mlCategorySuggestion
                    val mlTyp = mlTypeSuggestion
                    if (existing == null && mlCat != null) {
                        val accepted = (finalCategory == mlCat.category)
                        if (accepted) {
                            viewModel.confirmMlSuggestion(
                                merchant = finalMerchant,
                                smsText = inputNotes.trim(),
                                suggestedType = mlTyp?.type ?: entryType.name,
                                suggestedCategory = mlCat.category,
                                confidence = mlCat.confidence,
                                source = mlCat.source
                            )
                        } else {
                            viewModel.correctMlSuggestion(
                                merchant = finalMerchant,
                                smsText = inputNotes.trim(),
                                suggestedType = mlTyp?.type ?: entryType.name,
                                suggestedCategory = mlCat.category,
                                confidence = mlCat.confidence,
                                source = mlCat.source,
                                finalType = entryType.name,
                                finalCategory = finalCategory
                            )
                        }
                    }
                    if (existing == null) {
                        viewModel.addManualTransaction(
                            amt,
                            entryType,
                            finalCategory,
                            finalMerchant,
                            selectedMethod,
                            date = selectedDate,
                            note = inputNotes.trim()
                        )
                        // Sticky method: cash users stay on Cash, M-Pesa users on M-Pesa.
                        context
                            .getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putString("last_method", selectedMethod.name).apply()
                    } else {
                        viewModel.editTransaction(
                            existing.copy(
                                amount = amt,
                                type = entryType,
                                category = finalCategory,
                                merchant = finalMerchant,
                                paymentMethod = selectedMethod,
                                dateTimestamp = selectedDate,
                                notes = inputNotes.trim(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    onDismiss()
                }
            ) {
                Text(if (existing == null) "Save" else "Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMidnight ->
                        // DatePicker hands back UTC midnight; dayStart lands it on
                        // the same local day (UTC+3 keeps the day intact).
                        selectedDate = com.pesaflow.app.data.academic.dayStart(utcMidnight)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
