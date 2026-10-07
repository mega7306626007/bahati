package com.pesaflow.app.ui.theme

import androidx.compose.ui.graphics.Color

// PesaFlow / PesaPlanner brand identity — Deep Blue + Royal Blue + Metallic Gold.
// Gold is an accent. Blue establishes trust and structure.

val DeepNavy = Color(0xFF031861)
val PrimaryBlue = Color(0xFF01237A)
val RoyalBlue = Color(0xFF063FB3)
val KeyholeBlue = Color(0xFF01227B)

val PrimaryGold = Color(0xFFFEC32B)
val DarkGold = Color(0xFFFDBD20)
val LightGold = Color(0xFFFFD95A)

// Dark-mode tuned accents (readable on navy, not aggressive)
val RoyalLight = Color(0xFF8AA5FF)
val GoldOnDark = Color(0xFFFFD95A)
val BlueContainerDark = Color(0xFF01237A)
val NavyBackground = Color(0xFF060D2B)
val NavySurface = Color(0xFF0A1542)
val NavySurfaceVariant = Color(0xFF142257)
val NavyOnSurface = Color(0xFFE8ECF8)
val NavyOnVariant = Color(0xFFA9B4D4)

// Light-mode tuned surfaces (clean, calm, not papayawhip)
val LightBackground = Color(0xFFF6F7FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE9EDF6)
val LightOnBackground = Color(0xFF0F1B3D)
val LightOnSurface = Color(0xFF0F1B3D)
val LightOnVariant = Color(0xFF5A6685)
val BlueContainerLight = Color(0xFFDCE6FF)

// Restrained semantic colors — sophisticated, not rainbow.
val PesaSuccess = Color(0xFF15803D)
val PesaSuccessDark = Color(0xFF4ADE80)
val PesaSuccessContainer = Color(0xFFDDF5E5)
val PesaSuccessContainerDark = Color(0xFF0B3D2E)

val PesaWarning = Color(0xFFB45309)
val PesaWarningDark = Color(0xFFFBBF24)
val PesaWarningContainer = Color(0xFFFFF1D6)
val PesaWarningContainerDark = Color(0xFF3A2A08)

val PesaDanger = Color(0xFFB3261E)
val PesaDangerDark = Color(0xFFFF8A80)
val PesaDangerContainer = Color(0xFFFDE7E5)
val PesaDangerContainerDark = Color(0xFF410E0B)

val PesaInfo = Color(0xFF1D4ED8)
val PesaInfoDark = Color(0xFF7AA5FF)

// Budget status — calm even when overspending.
enum class BudgetStatus { HEALTHY, APPROACHING, EXCEEDED }

fun budgetStatusFor(fraction: Float): BudgetStatus = when {
    fraction >= 1f -> BudgetStatus.EXCEEDED
    fraction >= 0.75f -> BudgetStatus.APPROACHING
    else -> BudgetStatus.HEALTHY
}
