package com.pesaflow.app.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

// Unified Layers Canvas Architecture — additive theme kit.
// Procedural ambient art (offline-first, zero assets) on a hardware layer,
// WCAG scrim above it, glass surfaces above that. Light theme is untouched:
// callers opt screens in one by one.

// Per-feature ambient identities (same architecture, distinct feel).
val CinematicBase = Color(0xFF090F0C)
val TintDashboardForest = Color(0xFF0F261B)
val TintSemesterGold = Color(0xFF3A2D15)
val TintBuddyTwilight = Color(0xFF1F1435)
val TintMealsSpice = Color(0xFF3D2C12)
val TintThingsViolet = Color(0xFF2B2440)
val TintKitchenEmber = Color(0xFF4A2413)
val TintBillsSteel = Color(0xFF16233F)
val TintDebtWine = Color(0xFF33151A)
val TintSearchTeal = Color(0xFF1B2A3A)
val TintSettingsNeutral = Color(0xFF232946)
val TintTransactionsLedger = Color(0xFF15241D)
val TintInsightsGraph = Color(0xFF1B2340)
val TintBudgetsJar = Color(0xFF2E2410)
val TintReports = Color(0xFF0F2F3D)
val TintSavingsGrowth = Color(0xFF0E2E22)
val TintIncome = Color(0xFF0F3A2E)

// Volumetric glass: white 7% surface + light-catching 1dp gradient border.
fun Modifier.pesaGlass(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val borderBrush = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.02f)),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    return this
        .clip(PesaRadius.lg)
        .background(Color.White.copy(alpha = 0.07f))
        .border(1.dp, borderBrush, PesaRadius.lg)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = PesaRadius.lg,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = PesaElevation.flat)
    ) {
        Column(Modifier.fillMaxWidth().pesaGlass().padding(PesaSpacing.lg), content = content)
    }
}

private fun Modifier.hardwareBlur(radius: Float): Modifier {
    // RenderEffect.createBlurEffect(radius <= 0) throws IllegalArgumentException
    // on API 31+ and crashes at composition — skip the effect entirely at ~zero.
    if (radius <= 0.01f) return this
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        graphicsLayer {
            renderEffect = RenderEffect
                .createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else {
        blur((radius / 3).dp)
    }
}

@Composable
fun AmbientCanvas(tint: Color, blurRadius: Float, bgRes: Int? = null, modifier: Modifier = Modifier) {
    val animatedBlur by animateFloatAsState(targetValue = blurRadius, label = "atmo-blur")
    Box(
        modifier
            .fillMaxSize()
            .background(CinematicBase)
            .hardwareBlur(animatedBlur)
    ) {
        if (bgRes != null) {
            Image(
                painter = painterResource(id = bgRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(
                brush = Brush.verticalGradient(
                    colors = if (bgRes == null) listOf(tint, CinematicBase) else listOf(Color.Transparent, Color.Transparent),
                    startY = 0f, endY = h
                ),
                size = size
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.38f), Color.Transparent),
                    center = Offset(w * 0.8f, h * 0.18f),
                    radius = w * 0.6f
                ),
                radius = w * 0.6f,
                center = Offset(w * 0.8f, h * 0.18f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(w * 0.15f, h * 0.7f),
                    radius = w * 0.5f
                ),
                radius = w * 0.5f,
                center = Offset(w * 0.15f, h * 0.7f)
            )
        }
    }
}
