package com.pesaflow.app.ui.reports

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.ChartPurple
import com.pesaflow.app.ui.theme.LinkOptionCard
import com.pesaflow.app.ui.theme.PesaEmptyState
import com.pesaflow.app.ui.theme.PesaRadius
import com.pesaflow.app.ui.theme.PesaSpacing
import com.pesaflow.app.ui.theme.SkinAccentLine
import com.pesaflow.app.ui.theme.SkinCard
import com.pesaflow.app.ui.theme.SkinVault
import com.pesaflow.app.ui.theme.brightness
import com.pesaflow.app.ui.theme.toKSh
import com.pesaflow.app.viewmodels.FinanceViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(viewModel: FinanceViewModel, onLogIncome: () -> Unit = {}) {
    val transactions by viewModel.allTransactions.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val profile by viewModel.universityProfile.collectAsState()

    val opening = profile?.startingFunding ?: 0.0
    val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val spent = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val saved = transactions.filter { it.type == TransactionType.SAVING }.sumOf { it.amount }
    val invested = transactions.filter { it.type == TransactionType.INVESTMENT }.sumOf { it.amount }
    val owed = debts.filter { it.status != "PAID" }.sumOf { it.amount }
    val cash = opening + income - spent - saved - invested
    val netWorth = cash + saved + invested - owed
    val scale = maxOf(kotlin.math.abs(cash), saved, invested, owed, 1.0)

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = SkinVault.tint, bgRes = R.drawable.bg_networth_vault)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Net Worth", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            if (transactions.isEmpty() && opening <= 0) {
                Column(Modifier.fillMaxSize().padding(innerPadding).padding(PesaSpacing.md)) {
                    PesaEmptyState(
                        title = "No fortune to measure yet",
                        explanation = "Log income and spending and this vault fills itself: cash, savings, investments, debts.",
                        actionLabel = "Log opening income",
                        onAction = onLogIncome
                    )
                    Spacer(modifier = Modifier.height(96.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = PesaSpacing.md)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PesaSpacing.md)
                ) {
                    // Vault hero: the single number that matters, in gold.
                    SkinCard(skin = SkinVault) {
                        Text("Total Net Worth", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(PesaSpacing.xxs))
                        SkinAccentLine(SkinVault.accent)
                        Spacer(Modifier.height(PesaSpacing.xs))
                        Text(
                            if (netWorth < 0) "- " + (-netWorth).toKSh() else netWorth.toKSh(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (netWorth < 0) MaterialTheme.colorScheme.error else SkinVault.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(PesaSpacing.xs))
                        Text(
                            "Cash + savings + investments minus money owed. Updates with every transaction.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(PesaSpacing.sm))
                        Text(
                            if (netWorth < 0) "Negative right now - every shilling of debt you clear lifts this number."
                            else "Positive and tracking - keep the savings habit.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (netWorth < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    VaultRow(label = "Cash in hand", value = cash, fraction = (cash / scale).toFloat(), color = MaterialTheme.colorScheme.primary, skin = SkinVault)
                    VaultRow(label = "Savings", value = saved, fraction = (saved / scale).toFloat(), color = SkinVault.accent, skin = SkinVault)
                    VaultRow(label = "Investments", value = invested, fraction = (invested / scale).toFloat(), color = ChartPurple, skin = SkinVault)
                    VaultRow(label = "Money owed", value = -owed, fraction = (owed / scale).toFloat(), color = MaterialTheme.colorScheme.error, skin = SkinVault)
                    // M-Pesa wallet from the last SMS: display-only, never counted twice.
                    val walletCtx = LocalContext.current
                    com.pesaflow.app.data.parsers.readMpesaBalance(walletCtx)?.let { (wamt, wat) ->
                        VaultRow(label = "M-Pesa wallet", value = wamt, fraction = (wamt / scale).toFloat(), color = MaterialTheme.colorScheme.tertiary, skin = SkinVault)
                        Text(
                            "SMS reading " + com.pesaflow.app.data.parsers.balanceAgeText(wat, System.currentTimeMillis()) + " · shown, not added — cash already counts it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Balance history: every transaction SMS carries a
                        // balance tail, so the ring shows the wallet's trend —
                        // and a reconciliation hook against the ledger.
                        val history = com.pesaflow.app.data.parsers.readMpesaBalanceHistory(walletCtx)
                        if (history.size >= 2) {
                            val first = history.first()
                            val last = history.last()
                            val trend = last.second - first.second
                            val firstDate = android.text.format.DateFormat.format("d MMM", java.util.Date(first.first)).toString()
                            Text(
                                "${history.size} SMS readings since $firstDate · ${if (trend >= 0) "up" else "down"} ${kotlin.math.abs(trend).toKSh()} over the window",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // The app as a unit: surplus becomes savings ONLY on confirm.
                    if (cash > 0) {
                        LinkOptionCard(
                            skin = SkinVault,
                            title = "Put KSh ${cash.toInt()} surplus to work?",
                            body = "Move idle cash into a savings goal. Your ledger keeps a full trail - nothing vanishes.",
                            actionLabel = "Save surplus",
                            confirmTitle = "Save KSh ${cash.toInt()} as a goal?",
                            confirmBody = "A 90-day savings goal is created for the full cash surplus. Approve to continue.",
                            confirmLabel = "Create goal",
                            onConfirm = { viewModel.addSavingsGoal("Surplus savings", cash, 90) }
                        )
                    }
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
    }
}


@Composable
private fun VaultRow(
    label: String,
    value: Double,
    fraction: Float,
    color: Color,
    skin: com.pesaflow.app.ui.theme.FeatureSkin
) {
    val animated by animateFloatAsState(targetValue = fraction.coerceIn(0f, 1f), label = "vault-$label")
    SkinCard(skin = skin) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                (if (value < 0) "-" else "") + kotlin.math.abs(value).toKSh(),
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(PesaSpacing.sm))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(PesaRadius.xs),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
