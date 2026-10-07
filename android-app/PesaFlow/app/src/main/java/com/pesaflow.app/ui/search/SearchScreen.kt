package com.pesaflow.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.R
import com.pesaflow.app.viewmodels.FinanceViewModel
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintSearchTeal


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(viewModel: FinanceViewModel) {
    val transactions by viewModel.allTransactions.collectAsState()

    var searchKeyword by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("") }
    var selectedAmountMin by remember { mutableStateOf("") }
    var selectedAmountMax by remember { mutableStateOf("") }
    var selectedDateRange by remember { mutableStateOf("All time") }

    var appliedKeyword by remember { mutableStateOf("") }
    var appliedCategory by remember { mutableStateOf("") }
    var appliedType by remember { mutableStateOf("") }
    var appliedMin by remember { mutableStateOf<Double?>(null) }
    var appliedMax by remember { mutableStateOf<Double?>(null) }
    var appliedDateRange by remember { mutableStateOf("All time") }
    var hasSearched by remember { mutableStateOf(false) }

    fun dateRangeStart(): Long {
        val cal = java.util.Calendar.getInstance()
        when (appliedDateRange) {
            "Today" -> {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
            }
            "This week" -> {
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.add(java.util.Calendar.DAY_OF_MONTH, -(cal.get(java.util.Calendar.DAY_OF_WEEK) - 2).coerceAtLeast(0))
            }
            "This month" -> {
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
            }
            "Last 30 days" -> cal.add(java.util.Calendar.DAY_OF_MONTH, -30)
            else -> return 0L
        }
        return cal.timeInMillis
    }

    val allCategories = transactions.map { it.category }.distinct().sorted()
    val results = if (!hasSearched) {
        emptyList()
    } else {
        val dateStart = dateRangeStart()
        transactions.filter { tx ->
            (appliedKeyword.isBlank() ||
                tx.merchant.contains(appliedKeyword, ignoreCase = true) ||
                tx.description.contains(appliedKeyword, ignoreCase = true) ||
                tx.category.contains(appliedKeyword, ignoreCase = true)) &&
            (appliedCategory.isBlank() || tx.category.equals(appliedCategory, ignoreCase = true)) &&
            (appliedType.isBlank() || tx.type.name.equals(appliedType, ignoreCase = true)) &&
            (appliedMin == null || tx.amount >= appliedMin!!) &&
            (appliedMax == null || tx.amount <= appliedMax!!) &&
            (dateStart == 0L || tx.dateTimestamp >= dateStart)
        }
    }

    val resultTotal = results.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
    val resultCount = results.size


    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintSearchTeal, bgRes = R.drawable.bg_search_teal)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Search Transactions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    TextButton(onClick = {
                        searchKeyword = ""
                        selectedCategory = ""
                        selectedType = ""
                        selectedAmountMin = ""
                        selectedAmountMax = ""
                        selectedDateRange = "All time"
                        appliedKeyword = ""
                        appliedCategory = ""
                        appliedType = ""
                        appliedMin = null
                        appliedMax = null
                        appliedDateRange = "All time"
                        hasSearched = false
                    }) {
                        Text("Clear", style = MaterialTheme.typography.bodySmall)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            AtmosphereBand(
                workspace = AtmoWorkspace.SEARCH,
                title = "Find anything",
                subtitle = "Keyword · category · dates"
            )
            // Search Filters
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Filter Transactions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = selectedAmountMin,
                        onValueChange = { selectedAmountMin = it },
                        label = { Text("Min Amount (KSh)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = selectedAmountMax,
                        onValueChange = { selectedAmountMax = it },
                        label = { Text("Max Amount (KSh)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = { selectedCategory = it },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Type:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("INCOME", "EXPENSE", "SAVING", "INVESTMENT").forEach { t ->
                            FilterChip(
                                selected = selectedType == t,
                                onClick = {
                                    selectedType = t
                                    appliedType = t
                                    appliedKeyword = searchKeyword.trim()
                                    appliedCategory = selectedCategory.trim()
                                    appliedMin = selectedAmountMin.toDoubleOrNull()
                                    appliedMax = selectedAmountMax.toDoubleOrNull()
                                    appliedDateRange = selectedDateRange
                                    hasSearched = true
                                },
                                label = { Text(t.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = searchKeyword,
                        onValueChange = { searchKeyword = it },
                        label = { Text("Keyword / Merchant") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Try a category:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        allCategories.take(8).forEach { c ->
                            FilterChip(
                                selected = selectedCategory == c,
                                onClick = {
                                    selectedCategory = c
                                    appliedCategory = c
                                    appliedKeyword = searchKeyword.trim()
                                    appliedType = selectedType.trim()
                                    appliedMin = selectedAmountMin.toDoubleOrNull()
                                    appliedMax = selectedAmountMax.toDoubleOrNull()
                                    appliedDateRange = selectedDateRange
                                    hasSearched = true
                                },
                                label = { Text(c) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Date range:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All time", "Today", "This week", "This month", "Last 30 days").forEach { range ->
                            FilterChip(
                                selected = selectedDateRange == range,
                                onClick = {
                                    selectedDateRange = range
                                    appliedDateRange = range
                                    appliedCategory = selectedCategory.trim()
                                    appliedKeyword = searchKeyword.trim()
                                    appliedType = selectedType.trim()
                                    appliedMin = selectedAmountMin.toDoubleOrNull()
                                    appliedMax = selectedAmountMax.toDoubleOrNull()
                                    hasSearched = true
                                },
                                label = { Text(range) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            appliedKeyword = searchKeyword.trim()
                            appliedCategory = selectedCategory.trim()
                            appliedType = selectedType.trim()
                            appliedMin = selectedAmountMin.toDoubleOrNull()
                            appliedMax = selectedAmountMax.toDoubleOrNull()
                            appliedDateRange = selectedDateRange
                            hasSearched = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Apply Filters", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }


            // Results Section
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (hasSearched) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Results ($resultCount)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (resultTotal >= 0) "Net: +KSh ${resultTotal.toInt()}" else "Net: −KSh ${(-resultTotal).toInt()}",
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                                color = if (resultTotal >= 0) Color(0xFF00C853) else Color.Red
                            )
                        }
                    } else {
                        Text("Search Results", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!hasSearched) {
                        Text(
                            "Enter criteria above — cash and M-Pesa records search together.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else if (results.isEmpty()) {
                        Text(
                            "No matches — loosen a filter, or log it from Home (+) if it never happened.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        val dateFormat = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
                        results.take(50).forEach { tx ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.merchant, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                    Row {
                                        Text(tx.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(dateFormat.format(java.util.Date(tx.dateTimestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (tx.source == com.pesaflow.app.data.models.TransactionSource.MPESA_SMS) {
                                            Text(" · M-Pesa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                Text(
                                    text = "${if (tx.type == TransactionType.INCOME) "+" else "−"} KSh ${tx.amount.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.type == TransactionType.INCOME) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (results.size > 50) {
                            Text("Showing 50 of ${results.size} results", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }
}
