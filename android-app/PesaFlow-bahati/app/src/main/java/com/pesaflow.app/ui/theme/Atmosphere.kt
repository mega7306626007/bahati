package com.pesaflow.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


// Cinematic Atmosphere engine: procedural (zero-asset, offline-safe) artwork
// per workspace, always behind scrim + blur, never raw. Applied as hero bands
// so light-mode content keeps full contrast (see each call site).
enum class AtmoWorkspace {
    ANALYTICS, OASIS, STAGE,
    LEDGER, BUDGET, CAMPUS, MEALS, THINGS, KITCHEN,
    BILLS, DEBT, SEARCH, VAULT, INSIGHTS, BUDDY
}


private enum class AtmoMotif { GRID, SUN_HILLS, BEAMS, WAVES, RINGS, DOTS }


private data class AtmoSpec(
    val top: Color,
    val bottom: Color,
    val glow: Color,
    val gx: Float,
    val gy: Float,
    val motif: AtmoMotif,
    val blur: Dp
)


// One art direction per window: no two screens share palette + motif.
private fun spec(w: AtmoWorkspace): AtmoSpec = when (w) {
    AtmoWorkspace.ANALYTICS -> AtmoSpec(Color(0xFF101512), Color(0xFF0B0E0C), Color(0xFF00C853), 0.82f, 0.18f, AtmoMotif.GRID, 28.dp)
    AtmoWorkspace.OASIS -> AtmoSpec(Color(0xFFF5B942), Color(0xFF3A2418), Color(0xFFFFF3D6), 0.78f, 0.30f, AtmoMotif.SUN_HILLS, 12.dp)
    AtmoWorkspace.STAGE -> AtmoSpec(Color(0xFF1A1D21), Color(0xFF0C0E11), Color(0xFF00C853), 0.50f, 0.45f, AtmoMotif.BEAMS, 40.dp)
    AtmoWorkspace.LEDGER -> AtmoSpec(Color(0xFF0B3D2E), Color(0xFF071210), Color(0xFF2DFF8F), 0.15f, 0.20f, AtmoMotif.RINGS, 20.dp)
    AtmoWorkspace.BUDGET -> AtmoSpec(Color(0xFF123524), Color(0xFF0A0F0B), Color(0xFFFFCA28), 0.85f, 0.25f, AtmoMotif.GRID, 24.dp)
    AtmoWorkspace.CAMPUS -> AtmoSpec(Color(0xFF232946), Color(0xFF12141F), Color(0xFFB8C6FF), 0.76f, 0.28f, AtmoMotif.SUN_HILLS, 14.dp)
    AtmoWorkspace.MEALS -> AtmoSpec(Color(0xFF3D2C12), Color(0xFF171006), Color(0xFFFFB74D), 0.50f, 0.30f, AtmoMotif.WAVES, 16.dp)
    AtmoWorkspace.THINGS -> AtmoSpec(Color(0xFF2B2440), Color(0xFF14111D), Color(0xFFB39DDB), 0.20f, 0.25f, AtmoMotif.DOTS, 18.dp)
    AtmoWorkspace.KITCHEN -> AtmoSpec(Color(0xFF4A2413), Color(0xFF1C0E07), Color(0xFFFF8A50), 0.80f, 0.30f, AtmoMotif.WAVES, 14.dp)
    AtmoWorkspace.BILLS -> AtmoSpec(Color(0xFF16233F), Color(0xFF0A0E18), Color(0xFFFF8A65), 0.15f, 0.75f, AtmoMotif.GRID, 22.dp)
    AtmoWorkspace.DEBT -> AtmoSpec(Color(0xFF33151A), Color(0xFF120809), Color(0xFFEF9A9A), 0.85f, 0.70f, AtmoMotif.RINGS, 26.dp)
    AtmoWorkspace.SEARCH -> AtmoSpec(Color(0xFF1B2A3A), Color(0xFF0B0F16), Color(0xFF4DD0E1), 0.50f, 0.20f, AtmoMotif.DOTS, 20.dp)
    AtmoWorkspace.VAULT -> AtmoSpec(Color(0xFF241D4D), Color(0xFF100C22), Color(0xFFFFD54F), 0.50f, 0.30f, AtmoMotif.RINGS, 24.dp)
    AtmoWorkspace.INSIGHTS -> AtmoSpec(Color(0xFF123B3B), Color(0xFF081314), Color(0xFF64FFDA), 0.18f, 0.72f, AtmoMotif.WAVES, 26.dp)
    AtmoWorkspace.BUDDY -> AtmoSpec(Color(0xFF3A1F3D), Color(0xFF150C18), Color(0xFFF48FB1), 0.82f, 0.24f, AtmoMotif.DOTS, 16.dp)
}


private fun workspaceBlur(w: AtmoWorkspace): Dp = spec(w).blur


private fun DrawScope.paintAtmo(s: AtmoSpec, size: Size) {
    val w = size.width
    val h = size.height
    drawRect(brush = Brush.verticalGradient(listOf(s.top, s.bottom)), size = size)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(s.glow.copy(alpha = 0.38f), Color.Transparent),
            center = Offset(w * s.gx, h * s.gy),
            radius = w * 0.55f
        ),
        radius = w * 0.55f,
        center = Offset(w * s.gx, h * s.gy)
    )
    when (s.motif) {
        AtmoMotif.GRID -> {
            var x = 0f
            while (x < w) {
                drawLine(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                x += 46f
            }
            var y = 0f
            while (y < h) {
                drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                y += 46f
            }
        }
        AtmoMotif.SUN_HILLS -> {
            drawCircle(color = s.glow.copy(alpha = 0.85f), radius = h * 0.30f, center = Offset(w * s.gx, h * s.gy))
            drawCircle(color = s.bottom.copy(alpha = 0.55f), radius = w * 0.75f, center = Offset(w * 0.15f, h * 1.55f))
            drawCircle(color = s.bottom.copy(alpha = 0.75f), radius = w * 0.7f, center = Offset(w * 0.9f, h * 1.7f))
        }
        AtmoMotif.BEAMS -> {
            val beam = Path().apply {
                moveTo(w * 0.55f, -20f)
                lineTo(w * 0.85f, -20f)
                lineTo(w * 0.45f, h + 20f)
                lineTo(w * 0.15f, h + 20f)
                close()
            }
            drawPath(beam, Color.White.copy(alpha = 0.05f))
            val beam2 = Path().apply {
                moveTo(w * 0.05f, -20f)
                lineTo(w * 0.2f, -20f)
                lineTo(w * -0.2f, h + 20f)
                lineTo(w * -0.35f, h + 20f)
                close()
            }
            drawPath(beam2, Color.White.copy(alpha = 0.04f))
        }
        AtmoMotif.WAVES -> {
            for (i in 0..2) {
                val baseY = h * (0.35f + i * 0.22f)
                val path = Path()
                var x = -20f
                path.moveTo(x, baseY)
                while (x < w + 20f) {
                    path.quadraticBezierTo(x + 40f, baseY - 22f, x + 80f, baseY)
                    path.quadraticBezierTo(x + 120f, baseY + 22f, x + 160f, baseY)
                    x += 160f
                }
                drawPath(path, Color.White.copy(alpha = 0.07f - i * 0.015f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            }
        }
        AtmoMotif.RINGS -> {
            val center = Offset(w * s.gx, h * (s.gy + 0.55f))
            for (i in 1..4) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f - i * 0.012f),
                    radius = w * (0.14f + i * 0.11f),
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
            }
        }
        AtmoMotif.DOTS -> {
            var x = 24f
            while (x < w) {
                var y = 20f
                while (y < h) {
                    drawCircle(Color.White.copy(alpha = 0.07f), radius = 2.5f, center = Offset(x, y))
                    y += 34f
                }
                x += 38f
            }
        }
    }
}


// Type roles. Inter/Poppins are mapped to system weights on purpose:
// downloadable fonts need connectivity and binary blobs, both banned in an
// offline-first app with no asset pipeline.
object AtmoType {
    val hero = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.5).sp
    )
    val sub = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    val figure = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = (-1).sp
    )
}


// Frosted-glass surface: translucent fill + light-catching border.
// NOTE: true back-blur needs API 31+; below that the fill + border still read
// as glass over the dimmed art. No crash on any minSdk.
@Composable
fun Modifier.glass(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    fillAlpha: Float = 0.55f,
    borderAlpha: Float = 0.14f
): Modifier {
    return this
        .background(MaterialTheme.colorScheme.surface.copy(alpha = fillAlpha), shape)
        .border(1.dp, Color.White.copy(alpha = borderAlpha), shape)
}


@Composable
fun AtmosphereBand(
    workspace: AtmoWorkspace,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(172.dp)
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "atmo-fade"
    )
    Box(modifier = modifier) {
        // Layer 1: artwork, faded in, hardware-blurred (no-op below API 31).
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(workspaceBlur(workspace))
                .background(Color.Transparent)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha }
            ) {
                paintAtmo(spec(workspace), size)
            }
        }
        // Layer 2: gradient scrim — text never sits on raw art.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.10f),
                            Color.Black.copy(alpha = 0.62f)
                        )
                    )
                )
        )
        // Layer 3: high-contrast type floating above.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text(title, style = AtmoType.hero, color = Color.White)
            Text(subtitle, style = AtmoType.sub, color = Color.White.copy(alpha = 0.85f))
        }
    }
}


private fun DrawScope.drawStage(size: Size) {
    paintAtmo(
        AtmoSpec(Color(0xFF1A1D21), Color(0xFF0C0E11), Color(0xFF00C853), 0.50f, 0.45f, AtmoMotif.BEAMS, 40.dp),
        size
    )
}
