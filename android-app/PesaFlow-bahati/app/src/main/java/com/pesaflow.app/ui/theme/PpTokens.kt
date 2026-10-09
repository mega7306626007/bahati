package com.pesaflow.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// PesaPlanner design tokens — single source of truth for the UI.
// Values follow the approved token sheet exactly. Do not invent parallel
// palettes, spacings, radii or type sizes elsewhere.
// Type: Inter (bundled offline) throughout the token system; financial
// numbers request tabular numerals via tnum where the font supports them.

object ppColors {
    // Brand
    val primaryNavy = Color(0xFF071A33)
    val navySurface = Color(0xFF0B2342)
    val royalBlue = Color(0xFF1557B0)
    val brightBlue = Color(0xFF2878D4)
    val gold = Color(0xFFD4A72C)
    val goldLight = Color(0xFFF0C75E)
    val goldDark = Color(0xFFA77B12)

    // Backgrounds
    val appBackground = Color(0xFF071A33)
    val secondaryBackground = Color(0xFF0B2342)
    val surface = Color(0xFF102B4D)
    val surfaceElevated = Color(0xFF14365D)
    val surfaceSoft = Color(0xFF183D66)

    // Text
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFC9D5E3)
    val textTertiary = Color(0xFF91A4BA)
    val textDisabled = Color(0xFF62758C)
    val textOnGold = Color(0xFF071A33)
    val textOnBlue = Color(0xFFFFFFFF)

    // Borders
    val border = Color(0xFF274463)
    val borderStrong = Color(0xFF3B5B7D)
    val borderGold = Color(0xFFD4A72C)

    // Semantic
    val success = Color(0xFF2FBF71)
    val successSoft = Color(0xFF173F32)
    val warning = Color(0xFFE8A92E)
    val warningSoft = Color(0xFF493918)
    val error = Color(0xFFE05252)
    val errorSoft = Color(0xFF47252A)
    val info = Color(0xFF3D8BEA)
    val infoSoft = Color(0xFF173A5E)

    // Financial
    val income = Color(0xFF3BCB7A)
    val expense = Color(0xFFE96A6A)
    val neutralFinancial = Color(0xFFC9D5E3)
    val balancePositive = Color(0xFFD4A72C)
}

object ppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val giant = 40.dp
    val huge = 48.dp
    val massive = 64.dp

    const val screenHorizontal = 16
    const val screenHorizontalLarge = 24
    const val contentMaxWidth = 720
    const val topContent = 16
    const val sectionGap = 24
    const val majorSectionGap = 32
    const val bottomClearance = 88
}

object ppTypography {
    val displayLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp)
    val displayMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp)
    val h1 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp)
    val h2 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)
    val h3 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp)
    val labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
    val labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
    val labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp)

    // Financial figures: tabular numerals keep columns/hero numbers stable.
    val financialHero = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp,
        fontFeatureSettings = "tnum"
    )
    val financialLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp,
        fontFeatureSettings = "tnum"
    )
    val financialMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp,
        fontFeatureSettings = "tnum"
    )
    val financialSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp,
        fontFeatureSettings = "tnum"
    )
}

object ppShapes {
    val card = RoundedCornerShape(20.dp)
    val cardLarge = RoundedCornerShape(24.dp)
    val cardCompact = RoundedCornerShape(16.dp)
    val hero = RoundedCornerShape(24.dp)
    val button = RoundedCornerShape(16.dp)
    val buttonSecondary = RoundedCornerShape(14.dp)
    val input = RoundedCornerShape(14.dp)
    val progress = RoundedCornerShape(4.dp)
}

object ppMotion {
    const val micro = 120
    const val standard = 200
    const val screen = 250
    const val emphasis = 300
}

// Photographic overlay tokens: navy scrims that keep photos recognizable.
object ppOverlays {
    val photoBase = Color(0xFF071A33)
    const val photoAlpha = 0.35f
    const val textStrongAlpha = 0.60f
    const val navAlpha = 0.85f
    const val heroAlpha = 0.45f
}
