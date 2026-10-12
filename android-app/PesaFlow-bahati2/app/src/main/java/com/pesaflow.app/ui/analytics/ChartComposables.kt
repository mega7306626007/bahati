package com.pesaflow.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import com.pesaflow.app.ui.theme.DangerRed
import com.pesaflow.app.ui.theme.SuccessGreen
import com.pesaflow.app.ui.theme.WarningAmber
import com.pesaflow.app.ui.theme.categoryChartColor
import com.pesaflow.app.ui.theme.ppColors
import com.pesaflow.app.ui.theme.ppTypography
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricDistributionDonutChart(
    dataPoints: Map<String, Double>,
    selectedCategory: String? = null,
    onSelectCategory: (String?) -> Unit = {}
) {
    val total = dataPoints.values.sum()
    if (total == 0.0) return


    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp).semantics {
            contentDescription = "Spending by category donut chart"
            role = Role.Image
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(150.dp)) {
            var currentStartAngle = -90f
            dataPoints.entries.forEach { entry ->
                val sweepAngle = ((entry.value / total) * 360f).toFloat()
                drawArc(
                    color = categoryChartColor(entry.key),
                    startAngle = currentStartAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 56f)
                )
                currentStartAngle += sweepAngle
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dataPoints.entries.forEach { entry ->
                val pct = (entry.value / total * 100).toInt()
                val color = categoryChartColor(entry.key)
                FilterChip(
                    selected = selectedCategory == entry.key,
                    onClick = { onSelectCategory(entry.key) },
                    label = { Text("${entry.key} $pct% · ${entry.value.toInt()}") },
                    leadingIcon = { Box(modifier = Modifier.size(10.dp).background(color, CircleShape)) }
                )
            }
        }
    }
}


@Composable
fun BudgetRing(fraction: Float, modifier: Modifier = Modifier) {
    val animated: Float by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "budgetRing"
    )
    val pct = (animated * 100).toInt()
    val trackColor = ppColors.border
    Box(modifier = modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = if (fraction >= 1f) ppColors.error else ppColors.gold,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text("$pct%", style = ppTypography.labelLarge, color = ppColors.textPrimary)
    }
}


/**
 * Daily spend series: per-day EXPENSE totals over the last [daysBack] days,
 * oldest → newest, zeros included. Consecutive expenses used to plot at equal
 * spacing no matter the gaps between them, so a rent from last month sat
 * next to today's lunch and the newest point almost always "spiked".
 * Time-uniform buckets fix both the spike and the weirdness. Pure.
 */
fun dailySpendSeries(
    transactions: List<com.pesaflow.app.data.models.Transaction>,
    daysBack: Int,
    nowMs: Long = System.currentTimeMillis()
): List<Double> {
    if (daysBack <= 0) return emptyList()
    val dayMs = 24L * 60 * 60 * 1000
    val start = com.pesaflow.app.data.time.startOfDay(nowMs) - (daysBack - 1) * dayMs
    val perDay = DoubleArray(daysBack)
    for (tx in transactions) {
        if (tx.type != com.pesaflow.app.data.models.TransactionType.EXPENSE || tx.isSample) continue
        if (tx.dateTimestamp < start || tx.dateTimestamp > nowMs) continue
        val idx = ((tx.dateTimestamp - start) / dayMs).toInt().coerceIn(0, daysBack - 1)
        perDay[idx] += tx.amount
    }
    return perDay.toList()
}


@Composable
fun HistoricalTrendLineChart(points: List<Double>, chartDescription: String? = null) {
    if (points.isEmpty()) return
    val maxVal = points.maxOrNull()?.takeIf { it > 0 } ?: 1.0


    var chartModifier = Modifier.fillMaxWidth().height(150.dp).padding(16.dp)
    chartModifier = chartModifier.semantics {
        contentDescription = chartDescription ?: "Spending trend chart, ${points.size} points"
        role = Role.Image
    }
    Canvas(modifier = chartModifier) {
        val distanceX = size.width / (points.size - 1).coerceAtLeast(1)
        var previousOffset: Offset? = null


        points.forEachIndexed { index, currentVal ->
            val x = index * distanceX
            val y = size.height - ((currentVal / maxVal) * size.height).toFloat()
            val currentOffset = Offset(x, y)


            if (previousOffset != null) {
                drawLine(
                    color = SuccessGreen,
                    start = previousOffset!!,
                    end = currentOffset,
                    strokeWidth = 6f
                )
            }
            drawCircle(color = WarningAmber, radius = 8f, center = currentOffset)
            previousOffset = currentOffset
        }
    }
}