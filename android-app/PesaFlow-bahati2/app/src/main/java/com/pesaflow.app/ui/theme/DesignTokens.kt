package com.pesaflow.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Centralized design tokens — single source of truth for spacing, shape, elevation, motion.
// Spacing scale: 4 / 8 / 12 / 16 / 20 / 24 / 32
object PesaSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object PesaRadius {
    val xs = RoundedCornerShape(8.dp)
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(24.dp)
    val xxl = RoundedCornerShape(32.dp)
}

object PesaElevation {
    val flat = 0.dp
    val card = 1.dp
    val raised = 4.dp
    val hero = 8.dp
}

// Material 3 motion principles: fast, smooth, purposeful. No bounce.
object PesaMotion {
    const val fast = 150
    const val medium = 300
    const val slow = 500
}
