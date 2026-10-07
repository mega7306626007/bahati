package com.pesaflow.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import com.pesaflow.app.ui.theme.chartColorFor
import com.pesaflow.app.ui.theme.DangerRed
import com.pesaflow.app.ui.theme.SuccessGreen
import com.pesaflow.app.ui.theme.WarningAmber
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
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(150.dp)) {
            var currentStartAngle = -90f
            dataPoints.forEach { (key, value) ->
                val sweepAngle = ((value / total) * 360f).toFloat()
                drawArc(
                    color = chartColorFor(key),
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
                val color = chartColorFor(entry.key)
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
    val pct = (fraction.coerceIn(0f, 1f) * 100).toInt()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
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
                color = if (fraction >= 1f) DangerRed else SuccessGreen,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text("$pct%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}


@Composable
fun HistoricalTrendLineChart(points: List<Double>, chartDescription: String? = null) {
    if (points.isEmpty()) return
    val maxVal = points.maxOrNull() ?: 1.0


    var chartModifier = Modifier.fillMaxWidth().height(150.dp).padding(16.dp)
    if (chartDescription != null) {
        chartModifier = chartModifier.semantics { contentDescription = chartDescription }
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