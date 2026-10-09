package com.pesaflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.AppTheme


// Bundled offline-first type: Poppins headers (friendly curves), Inter metrics/body.
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)


// Core brand palette (legacy aliases kept: other screens reference these — do not remove)
val DeepTealGreen = Color(0xFF004D40)
val FreshMint = Color(0xFF00C853)
val DarkCharcoal = Color(0xFF121212)
val CardBackgroundSurface = Color(0xFF1E1E1E)

// Extended semantic palette (legacy aliases — prefer Pesa* tokens in Color.kt for new code)
val SuccessGreen = Color(0xFF00C853)
val WarningAmber = Color(0xFFFFCA28)
val DangerRed = Color(0xFFEF5350)
val InfoBlue = Color(0xFF29B6F6)
val MutedGray = Color(0xFF9E9E9E)
val ChartPurple = Color(0xFFAB47BC)
// Donut/chart slices draw from the token system — navy-compatible hues,
 // never neon. Gold appears once; income/expense read instantly.
val ChartPalette = listOf(
    ppColors.brightBlue,
    ppColors.income,
    ppColors.gold,
    ppColors.info,
    ppColors.warning,
    ppColors.expense
)
val SoftCardSurface = Color(0xFF252525)


private val PesaFlowDarkColors = darkColorScheme(
    primary = PrimaryGold,
    onPrimary = Color(0xFF1A1300),
    secondary = RoyalLight,
    onSecondary = Color(0xFF060D2B),
    tertiary = GoldOnDark,
    background = NavyBackground,
    onBackground = NavyOnSurface,
    surface = NavySurface,
    onSurface = NavyOnSurface,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = NavyOnVariant,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = GoldOnDark,
    secondaryContainer = NavySurfaceVariant,
    onSecondaryContainer = NavyOnSurface,
    tertiaryContainer = Color(0xFF3A2A08),
    onTertiaryContainer = GoldOnDark,
    error = PesaDangerDark,
    onError = Color(0xFF1A0B0B),
    errorContainer = PesaDangerContainerDark,
    onErrorContainer = Color(0xFFFFDAD6)
)


private val PesaFlowAmoledColors = darkColorScheme(
    primary = PrimaryGold,
    onPrimary = Color(0xFF1A1300),
    secondary = RoyalLight,
    onSecondary = Color.Black,
    tertiary = GoldOnDark,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFFE0E0E0),
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = GoldOnDark,
    error = PesaDangerDark,
    onError = Color.Black,
    errorContainer = PesaDangerContainerDark,
    onErrorContainer = Color(0xFFFFDAD6)
)


private val PesaFlowLightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    secondary = RoyalBlue,
    onSecondary = Color.White,
    tertiary = Color(0xFF8A6D00),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnVariant,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = PrimaryBlue,
    secondaryContainer = Color(0xFFFFF1D6),
    onSecondaryContainer = Color(0xFF5A4300),
    error = PesaDanger,
    onError = Color.White,
    errorContainer = PesaDangerContainer,
    onErrorContainer = Color(0xFF410E0B)
)


private val PesaFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)


private val PesaFlowTypography = Typography(
    displayLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
)


@Composable
fun PesaFlowTheme(mode: AppTheme = AppTheme.DARK, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val scheme = when (mode) {
        AppTheme.AMOLED -> PesaFlowAmoledColors
        AppTheme.DARK -> PesaFlowDarkColors
        AppTheme.LIGHT -> PesaFlowLightColors
        AppTheme.SYSTEM -> if (systemDark) PesaFlowDarkColors else PesaFlowLightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = PesaFlowShapes,
        typography = PesaFlowTypography,
        content = content
    )
}
