package com.pesaflow.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Financial number hierarchy: amount dominates, label secondary, meta tertiary.
// Currency stays KSh via Double.toKSh().
@Composable
fun FinancialAmount(
    amountKSh: String,
    label: String,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    large: Boolean = true
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(PesaSpacing.xxs))
        Text(
            text = if (hidden) "KSh ••••" else amountKSh,
            style = if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PesaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f)
                ),
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 1f), blurRadius = 6f)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// Deliberate empty state: WHAT + WHY + WHAT NEXT. Never blank "No data".
@Composable
fun PesaEmptyState(
    title: String,
    explanation: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = PesaRadius.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = PesaElevation.flat)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(PesaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(PesaSpacing.xs))
            Text(
                explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(PesaSpacing.md))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun PesaErrorState(
    title: String = "Something went wrong",
    explanation: String = "Please try again.",
    actionLabel: String = "Try again",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = PesaRadius.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(PesaSpacing.xl)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(PesaSpacing.xs))
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) {
                Spacer(Modifier.height(PesaSpacing.md))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun PesaLoadingRow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(PesaMotion.medium, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
        repeat(3) {
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(PesaRadius.md)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            )
        }
    }
}

// Consistent category treatment: one container style, one size, tinted by theme.
// Emoji content is preserved from existing data (no new asset pipeline, offline-safe).
@Composable
fun CategoryIcon(
    category: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    val container = if (accent) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val content = if (accent) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier.size(40.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = categoryEmoji(category),
            style = MaterialTheme.typography.titleMedium,
            color = content
        )
    }
}

@Composable
fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.semantics { contentDescription = label }.clickable(onClick = onClick),
        shape = PesaRadius.md,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = PesaElevation.card)
    ) {
        Column(
            Modifier.padding(PesaSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PesaSpacing.xs)
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Calm budget progress: healthy blue/green, approaching amber, exceeded restrained red.
// Never conveys status by color alone — percentage text always shown.
@Composable
fun BudgetProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    showPercent: Boolean = true
) {
    val clamped = fraction.coerceIn(0f, 1.25f)
    val bar = (clamped.coerceAtMost(1f))
    val status = budgetStatusFor(fraction)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val progress = when (status) {
        BudgetStatus.HEALTHY -> MaterialTheme.colorScheme.primary
        BudgetStatus.APPROACHING -> if (MaterialTheme.colorScheme.brightness()) PesaWarningDark else PesaWarning
        BudgetStatus.EXCEEDED -> MaterialTheme.colorScheme.error
    }
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { bar },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = progress,
            trackColor = track
        )
        if (showPercent) {
            Spacer(Modifier.height(4.dp))
            val label = when (status) {
                BudgetStatus.HEALTHY -> "${(fraction * 100).toInt()}% used"
                BudgetStatus.APPROACHING -> "${(fraction * 100).toInt()}% used — approaching limit"
                BudgetStatus.EXCEEDED -> "${(fraction * 100).toInt()}% used — over budget"
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Hero financial card: deep blue gradient, single gold accent line, calm hierarchy.
// Available dominates; income/expense/safe-to-spend secondary.
@Composable
fun HeroFinanceCard(
    greeting: String,
    dateLine: String,
    availableLabel: String,
    availableValue: String,
    incomeValue: String,
    expenseValue: String,
    safeToSpendValue: String?,
    // Third stat label. Defaults to the legacy text; Home passes "Wallet"
    // because the value is raw available balance, while true safe-to-spend
    // is computed in SafeToSpendCard — one meaning per number on screen.
    safeToSpendLabel: String = "Safe to spend",
    modifier: Modifier = Modifier,
    onHideToggle: (() -> Unit)? = null,
    hideLabel: String? = null
) {
    val isDark = MaterialTheme.colorScheme.brightness()
    val gradient = if (isDark) {
        Brush.linearGradient(listOf(NavySurface, DeepNavy, PrimaryBlue))
    } else {
        Brush.linearGradient(listOf(PrimaryBlue, RoyalBlue, KeyholeBlue))
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = PesaRadius.xl,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = PesaElevation.hero)
    ) {
        Box(Modifier.background(gradient).fillMaxWidth().padding(PesaSpacing.xl)) {
            Column(verticalArrangement = Arrangement.spacedBy(PesaSpacing.sm)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(dateLine, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                    }
                    if (onHideToggle != null && hideLabel != null) {
                        AssistChip(
                            onClick = onHideToggle,
                            label = { Text(hideLabel) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                labelColor = Color.White
                            )
                        )
                    }
                }
                // Restrained gold accent line — brand moment, not decoration everywhere.
                Box(Modifier.width(48.dp).height(3.dp).clip(CircleShape).background(PrimaryGold))
                Text(availableLabel, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
                Text(
                    availableValue,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(PesaSpacing.md)) {
                    HeroStat("Income", incomeValue, Modifier.weight(1f))
                    HeroStat("Spent", expenseValue, Modifier.weight(1f))
                    if (safeToSpendValue != null) HeroStat(safeToSpendLabel, safeToSpendValue, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Extension: true if current scheme is dark (background luminance heuristic via onBackground).
@Composable
fun androidx.compose.material3.ColorScheme.brightness(): Boolean {
    // PesaFlow dark schemes use near-white onBackground; light uses dark ink.
    return (onBackground.red + onBackground.green + onBackground.blue) / 3f > 0.5f
}

@Composable
fun CenterLoading(modifier: Modifier = Modifier, message: String = "Loading…") {
    Column(modifier.fillMaxWidth().padding(PesaSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(PesaSpacing.sm))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
