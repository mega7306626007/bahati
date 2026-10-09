package com.pesaflow.app.ui.theme

import androidx.compose.ui.graphics.Color

// PesaFlow / PesaPlanner brand identity — Deep Blue + Royal Blue + Metallic Gold.
// Values follow the approved token sheet (ppColors in PpTokens.kt is the
// reader-friendly mirror). Legacy names are kept: screens reference these.
val DeepNavy = Color(0xFF071A33)
val PrimaryBlue = Color(0xFF1557B0)
val RoyalBlue = Color(0xFF1557B0)
val KeyholeBlue = Color(0xFF0B2342)

val PrimaryGold = Color(0xFFD4A72C)
val DarkGold = Color(0xFFA77B12)
val LightGold = Color(0xFFF0C75E)

// Dark-mode tuned accents (readable on navy, not aggressive)
val RoyalLight = Color(0xFF2878D4)
val GoldOnDark = Color(0xFFF0C75E)
val BlueContainerDark = Color(0xFF14365D)
val NavyBackground = Color(0xFF071A33)
val NavySurface = Color(0xFF102B4D)
val NavySurfaceVariant = Color(0xFF14365D)
val NavyOnSurface = Color(0xFFFFFFFF)
val NavyOnVariant = Color(0xFFC9D5E3)

// Light-mode tuned surfaces (clean, calm, not papayawhip)
val LightBackground = Color(0xFFF6F7FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE9EDF6)
val LightOnBackground = Color(0xFF0F1B3D)
val LightOnSurface = Color(0xFF0F1B3D)
val LightOnVariant = Color(0xFF5A6685)
val BlueContainerLight = Color(0xFFDCE6FF)

// Restrained semantic colors — token hues, sophisticated, not rainbow.
val PesaSuccess = Color(0xFF2FBF71)
val PesaSuccessDark = Color(0xFF2FBF71)
val PesaSuccessContainer = Color(0xFFDDF5E5)
val PesaSuccessContainerDark = Color(0xFF173F32)

val PesaWarning = Color(0xFFE8A92E)
val PesaWarningDark = Color(0xFFE8A92E)
val PesaWarningContainer = Color(0xFFFFF1D6)
val PesaWarningContainerDark = Color(0xFF493918)

val PesaDanger = Color(0xFFE05252)
val PesaDangerDark = Color(0xFFE05252)
val PesaDangerContainer = Color(0xFFFDE7E5)
val PesaDangerContainerDark = Color(0xFF47252A)

val PesaInfo = Color(0xFF3D8BEA)
val PesaInfoDark = Color(0xFF3D8BEA)

// Budget status — calm even when overspending.
enum class BudgetStatus { HEALTHY, APPROACHING, EXCEEDED }

fun budgetStatusFor(fraction: Float): BudgetStatus = when {
    fraction >= 1f -> BudgetStatus.EXCEEDED
    fraction >= 0.75f -> BudgetStatus.APPROACHING
    else -> BudgetStatus.HEALTHY
}
