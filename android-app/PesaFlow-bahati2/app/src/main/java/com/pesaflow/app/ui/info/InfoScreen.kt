package com.pesaflow.app.ui.info

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoScreen() {
    var expandedSection by remember { mutableStateOf<String?>(null) }

    fun toggleSection(section: String) {
        expandedSection = if (expandedSection == section) null else section
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Header Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column {
                            Text(
                                "PesaFlow App Guide",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Smart, Offline-First Personal Finance",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Text(
                        "PesaFlow helps students and young professionals master money management through automatic SMS ledger parsing, intelligent budget pacing, and offline mathematical models.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Section 1: Overview & Key Features
        item {
            InfoExpandableCard(
                title = "1. About PesaFlow & Mission",
                subtitle = "Offline-first, 100% private financial assistant",
                icon = Icons.Filled.Security,
                isExpanded = expandedSection == "about",
                onToggle = { toggleSection("about") }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureDetailItem(
                        title = "100% On-Device & Private",
                        description = "Your financial data never leaves your phone. All transaction parsing, database storage, and budget calculations happen locally on your device."
                    )
                    FeatureDetailItem(
                        title = "M-Pesa & SMS Automation",
                        description = "Automatically detects M-Pesa, Airtel Money, and bank SMS notifications. Categorizes spending instantly using smart rules and merchant memory."
                    )
                    FeatureDetailItem(
                        title = "Student-Centric Intelligence",
                        description = "Built specifically with features like Semester Runway, Kitchen Stock tracking, Meal Planner, and Campus Food Guides."
                    )
                }
            }
        }

        // Section 2: How to Use the App
        item {
            InfoExpandableCard(
                title = "2. How to Use PesaFlow",
                subtitle = "Guide to tabs, quick add, SMS import, and settings",
                icon = Icons.Filled.Lightbulb,
                isExpanded = expandedSection == "usage",
                onToggle = { toggleSection("usage") }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    UsageStepItem(
                        step = "Home Dashboard",
                        icon = Icons.Filled.AccountBalanceWallet,
                        text = "View your real-time Safe-to-Spend balance, daily budget pace, quick shortcuts, and smart financial tips."
                    )
                    UsageStepItem(
                        step = "Plan Tab",
                        icon = Icons.Filled.AccountBalance,
                        text = "Set daily, weekly, monthly, and semester budget limits per category. Keep track of active budget velocity."
                    )
                    UsageStepItem(
                        step = "Money Tab",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        text = "Inspect all transactions, pending SMS rows for approval, search by merchant, filter by type/category, or manually add transactions."
                    )
                    UsageStepItem(
                        step = "Insight Tab",
                        icon = Icons.Filled.Lightbulb,
                        text = "Analyze spending trends, view category breakdown charts, and parse unformatted financial text using Natural Language Processing (NLP)."
                    )
                    UsageStepItem(
                        step = "You (More) Section",
                        icon = Icons.Filled.Settings,
                        text = "Access Student tools (Semester, Meal Planner, Kitchen, Things), Contact Book, Bills, Debt Tracking, Net Worth, Backup/Export, and App Settings."
                    )
                }
            }
        }

        // Section 3: Financial Formulas & Math Engine
        item {
            InfoExpandableCard(
                title = "3. Financial Formulas & Math Engine",
                subtitle = "Exact calculations powering Safe-to-Spend, Pace & Runway",
                icon = Icons.Filled.CheckCircle,
                isExpanded = expandedSection == "formulas",
                onToggle = { toggleSection("formulas") }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormulaCard(
                        name = "Safe-to-Spend Formula",
                        formula = "Safe Daily = (Liquid Balance - Reserved Obligations - Savings) / Days Left",
                        explanation = "Calculates exact daily spending cash after deducting upcoming bills due within the window and savings targets, preventing accidental overspending."
                    )
                    FormulaCard(
                        name = "Weekday Pace Factor",
                        formula = "Adjusted Daily Target = Base Daily Target × Weekday Weight Factor",
                        explanation = "Adjusts daily recommendations based on historical spending habits (e.g. higher budget weight for weekends vs weekdays)."
                    )
                    FormulaCard(
                        name = "Semester Runway (Days)",
                        formula = "Runway = Net Liquid Cash / Average Daily Burn Rate",
                        explanation = "Projects how many days your current semester allowance and liquid cash will last at your current daily burn rate."
                    )
                    FormulaCard(
                        name = "Net Worth",
                        formula = "Net Worth = (Cash + Savings + Belongings) - (Debts + Unpaid Bills)",
                        explanation = "Calculates total financial health by balancing current liquid/fixed assets against all outstanding liabilities."
                    )
                    FormulaCard(
                        name = "Budget Velocity / Pace %",
                        formula = "Velocity % = (Spent / Total Budget) ÷ (Elapsed Days / Total Days) × 100",
                        explanation = "Measures whether spending speed is ahead of, on track with, or behind calendar days elapsed."
                    )
                }
            }
        }

        // Section 4: What to Expect & Best Practices
        item {
            InfoExpandableCard(
                title = "4. What to Expect & Tips",
                subtitle = "Permissions, SMS detection, backup & export",
                icon = Icons.AutoMirrored.Filled.Help,
                isExpanded = expandedSection == "expectations",
                onToggle = { toggleSection("expectations") }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TipItem(
                        header = "SMS Permissions",
                        detail = "PesaFlow requests READ_SMS solely to scan transaction messages locally. It does not send SMS or transmit data outside your device."
                    )
                    TipItem(
                        header = "Pending vs Confirmed Ledger",
                        detail = "Shared text or incoming SMS entries land in the Pending queue so you can confirm or recategorize before touching permanent records."
                    )
                    TipItem(
                        header = "Backup & Export",
                        detail = "Use the Export & Backup tool in More to generate full JSON database backups or export transaction history to CSV format anytime."
                    )
                    TipItem(
                        header = "Demo Mode",
                        detail = "You can toggle Demo Mode in Settings to test features with sample data without altering your actual financial records."
                    )
                }
            }
        }

        // Bottom padding
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoExpandableCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun FeatureDetailItem(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UsageStepItem(step: String, icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Column {
            Text(step, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FormulaCard(name: String, formula: String, explanation: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            ) {
                Text(
                    formula,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TipItem(header: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(header, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp)
        )
    }
}
