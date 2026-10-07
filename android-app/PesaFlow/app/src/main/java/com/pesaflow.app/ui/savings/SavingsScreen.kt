package com.pesaflow.app.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.SavingsGoal
import com.pesaflow.app.ui.language.Copy4
import com.pesaflow.app.ui.language.addCashBtn
import com.pesaflow.app.ui.language.goalOverdueLine
import com.pesaflow.app.ui.language.goalPaceLine
import com.pesaflow.app.ui.language.moneySpoken
import com.pesaflow.app.ui.language.newGoalBtn
import com.pesaflow.app.ui.language.savingsEmptyBody
import com.pesaflow.app.ui.language.savingsEmptyTitle
import com.pesaflow.app.ui.language.savingsTitle
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.PesaEmptyState
import com.pesaflow.app.ui.theme.SkinAccentLine
import com.pesaflow.app.ui.theme.SkinCard
import com.pesaflow.app.ui.theme.SkinSavings
import com.pesaflow.app.ui.theme.TintSavingsGrowth
import com.pesaflow.app.viewmodels.FinanceViewModel


private fun pick4(en: String, sw: String, sh: String, mix: String, lang: AppLanguage) =
    Copy4(en, sw, sh, mix).pick(lang)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(viewModel: FinanceViewModel) {
    val goals by viewModel.savingsGoals.collectAsState()
    val lang by viewModel.currentLanguage.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var contributeTo by remember { mutableStateOf<SavingsGoal?>(null) }
    var deleteGoal by remember { mutableStateOf<SavingsGoal?>(null) }

    val now = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    val totalSaved = goals.sumOf { it.currentAmount }
    val totalTarget = goals.sumOf { it.targetAmount }
    val pct = if (totalTarget > 0) (totalSaved / totalTarget).coerceIn(0.0, 1.0) else 0.0
    val nearest = goals.filter { it.currentAmount < it.targetAmount }.minByOrNull { it.targetTimestamp }
    fun t(en: String, sw: String, sh: String, mix: String, useLang: AppLanguage = lang) = Copy4(en, sw, sh, mix).pick(useLang)

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintSavingsGrowth, bgRes = R.drawable.bg_savings)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(savingsTitle(lang), fontWeight = FontWeight.Bold) },
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
                // Hero: the whole mission in one ring.
                SkinCard(skin = SkinSavings) {
                    Text(
                        t("Growth, one coin at a time", "Ukuaji, coin moja", "Growth, coin moja", "Growth, coin moja", lang),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SkinAccentLine(SkinSavings.accent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(
                            progress = { pct.toFloat() },
                            modifier = Modifier.size(84.dp),
                            color = SkinSavings.accent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 9.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                moneySpoken(totalSaved, lang),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                t("of", "kati ya", "of", "of", lang) + " " + moneySpoken(totalTarget, lang) +
                                    " · ${(pct * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            nearest?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                val d = ((it.targetTimestamp - now) / dayMs).coerceAtLeast(0)
                                Text(
                                    t("Next:", "Ifuatayo:", "Next:", "Next:", lang) + " ${it.title} · $d d",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SkinSavings.accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        t(
                            "Every addition lands in your ledger too — net worth, balance and reports move together.",
                            "Kila nyongeza inaingia pia — net worth na reports zinafuata.",
                            "Kila unaweka inaingia ledger pia — net worth na repoti zinafuata.",
                            "Kila unaweka inaingia ledger pia — everything moves together."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // At-home students pay no rent: that missing bill is a savings
                // superpower — say it plainly until a goal exists.
                if (goals.isEmpty() && !com.pesaflow.app.ui.budgets.parseLiving(viewModel.getOnboardingAnswers()).hasRent) {
                    SkinCard(skin = SkinSavings) {
                        Text(
                            t(
                                "No rent at home — your first KSh 1,000/mo saved beats most renters. Start below. 🏠",
                                "Huna rent home — KSh 1,000 ya kwanza monthly inashinda wapangaji wengi. Anza hapa. 🏠",
                                "Huna kodi nyumbani — KSh 1,000 ya kwanza kila mwezi inawashinda wapangaji. Anza. 🏠",
                                "No rent at home — first 1k monthly beats most renters. Start below. 🏠"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (goals.isEmpty()) {
                    PesaEmptyState(
                        title = savingsEmptyTitle(lang),
                        explanation = savingsEmptyBody(lang),
                        actionLabel = newGoalBtn(lang),
                        onAction = { showAdd = true }
                    )
                } else {
                    goals.sortedBy { it.targetTimestamp }.forEach { g ->
                        GoalCard(
                            goal = g,
                            lang = lang,
                            now = now,
                            onAddCash = { contributeTo = g },
                            onDelete = { deleteGoal = g }
                        )
                    }
                }

                Button(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text(newGoalBtn(lang), color = MaterialTheme.colorScheme.onPrimary) }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("") }
        var days by remember { mutableStateOf("90") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(newGoalBtn(lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(t("Goal name (e.g. Laptop)", "Jina la lengo", "Jina ya goal", "Goal name", lang)) })
                    OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text(t("Target (KSh)", "Lengo (KSh)", "Target (KSh)", "Target (KSh)", lang)) })
                    OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text(t("Days to get there", "Siku", "Siku", "Days", lang)) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = target.toDoubleOrNull()
                    val d = days.toIntOrNull() ?: 90
                    if (title.isNotBlank() && amt != null && amt > 0) {
                        viewModel.addSavingsGoal(title.trim(), amt, d.coerceAtLeast(1))
                        showAdd = false
                    }
                }) { Text(t("Save goal", "Weka lengo", "Weka goal", "Save goal", lang)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(t("Cancel", "Ghairi", "Cancel", "Cancel", lang)) } }
        )
    }

    contributeTo?.let { g ->
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { contributeTo = null },
            title = { Text("${addCashBtn(lang)} · ${g.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        t("Goes to the goal AND your ledger — net worth moves too.", "Inaingia goal NA ledger.", "Inaingia goal NA ledger.", "Goal NA ledger — both move.", lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    com.pesaflow.app.ui.language.walletText(LocalContext.current)?.let { wt ->
                        Text(wt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(t("Amount (KSh)", "Kiasi (KSh)", "Amount (KSh)", "Amount (KSh)", lang)) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amount.toDoubleOrNull()
                    if (amt != null && amt > 0) {
                        viewModel.contributeToSavingsGoal(g, amt)
                        contributeTo = null
                    }
                }) { Text(addCashBtn(lang)) }
            },
            dismissButton = { TextButton(onClick = { contributeTo = null }) { Text(t("Cancel", "Ghairi", "Cancel", "Cancel", lang)) } }
        )
    }

    deleteGoal?.let { g ->
        AlertDialog(
            onDismissRequest = { deleteGoal = null },
            title = { Text(t("Delete this goal?", "Futa lengo?", "Delete hii goal?", "Delete goal?", lang)) },
            text = { Text(t("Saved coins stay in your ledger — only the target goes.", "Coins zilizowekwa zinabaki.", "Coins ziko ledger zinabaki.", "Ledger coins stay — target only.", lang)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSavingsGoal(g.id)
                    deleteGoal = null
                }) { Text(t("Delete", "Futa", "Delete", "Delete", lang), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteGoal = null }) { Text(t("Keep", "Wacha", "Keep", "Keep", lang)) } }
        )
    }
}


@Composable
private fun GoalCard(
    goal: SavingsGoal,
    lang: AppLanguage,
    now: Long,
    onAddCash: () -> Unit,
    onDelete: () -> Unit
) {
    val dayMs = 24L * 60 * 60 * 1000
    fun t(en: String, sw: String, sh: String, mix: String, useLang: AppLanguage = lang) = Copy4(en, sw, sh, mix).pick(useLang)
    val pct = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).coerceIn(0.0, 1.0) else 0.0
    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                progress = { pct.toFloat() },
                modifier = Modifier.size(60.dp),
                color = SkinSavings.accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 7.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(goal.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    moneySpoken(goal.currentAmount, lang) + " / " + moneySpoken(goal.targetAmount, lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (remaining <= 0) {
                    Text(
                        t("Done — hongera! 🎉", "Imekamilika — hongera! 🎉", "Done — fiti sana! 🎉", "Done — hongera! 🎉", lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = SkinSavings.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (goal.targetTimestamp < now) {
                    val od = ((now - goal.targetTimestamp) / dayMs).toInt()
                    Text(goalOverdueLine(od.toLong(), lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    val d = ((goal.targetTimestamp - now) / dayMs).coerceAtLeast(1)
                    val perDay = remaining / d
                    Text(goalPaceLine(d, perDay, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAddCash,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text(addCashBtn(lang), color = MaterialTheme.colorScheme.onPrimary) }
                    TextButton(onClick = onDelete) {
                        Text(t("Delete", "Futa", "Delete", "Delete", lang), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
