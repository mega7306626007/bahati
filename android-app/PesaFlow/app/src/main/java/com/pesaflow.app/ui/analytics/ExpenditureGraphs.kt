package com.pesaflow.app.ui.analytics

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.theme.PesaEmptyState
import com.pesaflow.app.ui.theme.PesaRadius
import com.pesaflow.app.ui.theme.PesaSectionHeader
import com.pesaflow.app.ui.theme.PesaSpacing
import com.pesaflow.app.ui.theme.PrimaryGold
import com.pesaflow.app.ui.theme.toKSh
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class ExpenditureRange(val title: String) {
    HOURLY("Hourly"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    SEMESTER("Semester"),
    ANNUAL("Annual")
}

data class ExpenditureBin(val label: String, val amount: Double)

private const val DAY_MS = 24L * 60 * 60 * 1000

private fun dayStartOf(now: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/**
 * Real-data aggregation — EXPENSE transactions only, never invented.
 * Hourly: 24 buckets for today. Daily: last 14 days. Weekly: last 12 weeks.
 * Monthly: last 12 months. Semester: last 6 months (semester window).
 * Annual: last 5 years.
 */
fun aggregateExpenditure(
    transactions: List<Transaction>,
    range: ExpenditureRange,
    now: Long = System.currentTimeMillis()
): List<ExpenditureBin> {
    val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
    if (expenses.isEmpty()) return emptyList()
    return when (range) {
        ExpenditureRange.HOURLY -> {
            val start = dayStartOf(now)
            val cal = Calendar.getInstance()
            (0..23).map { h ->
                val b0 = start + h * 60L * 60 * 1000
                val b1 = b0 + 60L * 60 * 1000
                val total = expenses.filter { it.dateTimestamp >= b0 && it.dateTimestamp < b1 }.sumOf { it.amount }
                val label = when (h) {
                    0 -> "12a"; 12 -> "12p"
                    in 1..11 -> "${h}a"
                    else -> "${h - 12}p"
                }
                ExpenditureBin(label, total)
            }
        }
        ExpenditureRange.DAILY -> {
            val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
            val start = dayStartOf(now)
            (13 downTo 0).map { back ->
                val d0 = start - back * DAY_MS
                val total = expenses.filter { it.dateTimestamp >= d0 && it.dateTimestamp < d0 + DAY_MS }.sumOf { it.amount }
                ExpenditureBin(fmt.format(java.util.Date(d0)), total)
            }
        }
        ExpenditureRange.WEEKLY -> {
            // Monday-anchored calendar weeks from the shared timeline — the
            // old rolling 7-day windows drifted a day every day, so "this
            // week" here never matched "this week" on the dashboard.
            val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
            com.pesaflow.app.data.academic.weekBuckets(12, now).map { (w0, w1) ->
                val total = expenses.filter { it.dateTimestamp >= w0 && it.dateTimestamp < w1 }.sumOf { it.amount }
                ExpenditureBin(fmt.format(java.util.Date(w0)), total)
            }
        }
        ExpenditureRange.MONTHLY -> {
            val fmt = SimpleDateFormat("MMM yy", Locale.getDefault())
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            (11 downTo 0).map { back ->
                val c = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -back) }
                val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH)
                val total = expenses.filter {
                    val t = Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }
                    t.get(Calendar.YEAR) == y && t.get(Calendar.MONTH) == m
                }.sumOf { it.amount }
                ExpenditureBin(fmt.format(c.time), total)
            }
        }
        ExpenditureRange.SEMESTER -> {
            // Semester window: current month + previous 5 months, per-month bins.
            val fmt = SimpleDateFormat("MMM", Locale.getDefault())
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            (5 downTo 0).map { back ->
                val c = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -back) }
                val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH)
                val total = expenses.filter {
                    val t = Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }
                    t.get(Calendar.YEAR) == y && t.get(Calendar.MONTH) == m
                }.sumOf { it.amount }
                ExpenditureBin(fmt.format(c.time), total)
            }
        }
        ExpenditureRange.ANNUAL -> {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val thisYear = cal.get(Calendar.YEAR)
            (4 downTo 0).map { back ->
                val y = thisYear - back
                val total = expenses.filter {
                    Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }.get(Calendar.YEAR) == y
                }.sumOf { it.amount }
                ExpenditureBin(y.toString(), total)
            }
        }
    }
}

@Composable
fun ExpenditureBarChart(
    bins: List<ExpenditureBin>,
    modifier: Modifier = Modifier
) {
    if (bins.isEmpty()) return
    val max = bins.maxOf { it.amount }.coerceAtLeast(1.0)
    var selected by remember(bins) { mutableStateOf<Int?>(null) }
    val gold = PrimaryGold
    val barColor = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier.fillMaxWidth()) {
        // Bars — accessible tap targets, no Canvas hit-testing, no clipping on small phones.
        Row(
            Modifier.fillMaxWidth().height(148.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bins.forEachIndexed { i, bin ->
                val frac = (bin.amount / max).toFloat().coerceIn(0f, 1f)
                val isPeak = bin.amount == max && max > 0
                val barHeight by animateDpAsState(targetValue = (8 + frac * 100).dp, label = "bar-$i")
                Column(
                    Modifier.weight(1f).semantics { contentDescription = "${bin.label}: ${bin.amount.toKSh()}" }
                        .clickable { selected = if (selected == i) null else i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (selected == i && bin.amount > 0) {
                        Text(
                            bin.amount.toKSh(), style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Spacer(Modifier.height(16.dp))
                    }
                    Box(
                        Modifier.fillMaxWidth().weight(1f, fill = false)
                            .height(barHeight)
                            .clip(PesaRadius.xs)
                            .background(if (isPeak) gold else if (selected == i) barColor.copy(alpha = 0.55f) else barColor.copy(alpha = 0.85f))
                    )
                }
            }
        }
        Spacer(Modifier.height(PesaSpacing.xs))
        // X labels — subset to avoid crowding (max ~6).
        val step = maxOf(1, bins.size / 6)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            bins.forEachIndexed { i, bin ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % step == 0 || i == bins.lastIndex) {
                        Text(
                            bin.label, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        // Hidden track reference for theme consistency (keeps surfaceVariant in hierarchy).
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(track.copy(alpha = 0.4f)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenditureGraphsCard(
    transactions: List<Transaction>,
    modifier: Modifier = Modifier
) {
    var range by remember { mutableStateOf(ExpenditureRange.MONTHLY) }
    val bins = remember(transactions, range) { aggregateExpenditure(transactions, range) }
    val total = bins.sumOf { it.amount }
    val avg = if (bins.isNotEmpty()) total / bins.size else 0.0
    val peak = bins.maxByOrNull { it.amount }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = PesaRadius.xl,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(PesaSpacing.lg), verticalArrangement = Arrangement.spacedBy(PesaSpacing.sm)) {
            PesaSectionHeader(
                title = "Expenditure",
                subtitle = when (range) {
                    ExpenditureRange.HOURLY -> "Today, hour by hour — when does money leave?"
                    ExpenditureRange.DAILY -> "Last 14 days — how has spending changed?"
                    ExpenditureRange.WEEKLY -> "Last 12 weeks — weekly rhythm"
                    ExpenditureRange.MONTHLY -> "Last 12 months — where did it go?"
                    ExpenditureRange.SEMESTER -> "Semester window — last 6 months"
                    ExpenditureRange.ANNUAL -> "Last 5 years — the long view"
                }
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(PesaSpacing.xs)
            ) {
                ExpenditureRange.entries.forEach { r ->
                    FilterChip(
                        selected = range == r,
                        onClick = { range = r },
                        label = { Text(r.title) }
                    )
                }
            }
            if (bins.isEmpty() || total == 0.0) {
                PesaEmptyState(
                    title = "No spending ${range.title.lowercase()} yet",
                    explanation = "Your ${range.title.lowercase()} expenditure graph will appear here once you log expenses.",
                    actionLabel = null, onAction = null
                )
            } else {
                ExpenditureBarChart(bins = bins)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PesaSpacing.md)) {
                    Column(Modifier.weight(1f)) {
                        Text("Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(total.toKSh(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Average", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(avg.toKSh(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Peak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (peak != null && peak.amount > 0) "${peak.label} · ${peak.amount.toKSh()}" else "—",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
