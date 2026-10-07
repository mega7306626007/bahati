package com.pesaflow.app.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

// Full-bleed backdrop for screens that keep their own Scaffold/scroll container
// (Dashboard, Semester, Buddy). Layers: charcoal base → procedural ambient canvas
// (hardware blur, version-guarded) → 12%-to-62% protective scrim. Content draws
// above with transparent scaffold/container colors. Light theme keeps the same
// photo, treated bright: soft alpha + white scrim, text stays readable.
@Composable
fun CinematicBackdrop(
    workspaceTint: Color,
    // Preview mode: ~zero blur so the photo shows sharp. Restore 14f to re-soften.
    blurRadius: Float = 0f,
    bgRes: Int? = null,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.brightness()
    if (!dark) {
        // Light cinematic: same photo, treated bright — photos never vanish.
        Box(modifier.fillMaxSize().background(LightBackground)) {
            if (bgRes != null) {
                Image(
                    painter = painterResource(id = bgRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.20f
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            LightBackground.copy(alpha = 0.94f)
                        )
                    )
                )
            )
        }
        return
    }
    Box(modifier.fillMaxSize().background(CinematicBase)) {
        AmbientCanvas(tint = workspaceTint, blurRadius = blurRadius, bgRes = bgRes)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        workspaceTint.copy(alpha = 0.12f),
                        CinematicBase.copy(alpha = 0.62f)
                    )
                )
            )
        )
    }
}
