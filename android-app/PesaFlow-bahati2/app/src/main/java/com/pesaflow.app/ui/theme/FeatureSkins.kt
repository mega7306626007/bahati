package com.pesaflow.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R

// Per-feature visual identities: each feature owns a background image, an
// ambient tint, a dark card tone and an accent. Same architecture everywhere,
// completely different look per function. Light theme falls back to the clean
// system surfaces (skins are a dark-mode identity system).
data class FeatureSkin(
    val bgRes: Int? = null,
    val tint: Color,
    val card: Color,
    val accent: Color
)

val SkinDashboard = FeatureSkin(R.drawable.bg_dashboard_forest, TintDashboardForest, NavySurface.copy(alpha = 0.6f), PrimaryGold)
val SkinSemester = FeatureSkin(R.drawable.bg_semester_gold, TintSemesterGold, Color(0xFF2A2110).copy(alpha = 0.6f), LightGold)
val SkinBuddy = FeatureSkin(R.drawable.bg_buddy_twilight, TintBuddyTwilight, Color(0xFF221741).copy(alpha = 0.6f), RoyalLight)
val SkinMeals = FeatureSkin(R.drawable.bg_meals_spice, TintMealsSpice, Color(0xFF2E2110).copy(alpha = 0.6f), Color(0xFFFFB74D).copy(alpha = 0.6f))
val SkinThings = FeatureSkin(R.drawable.bg_things_carry, TintThingsViolet, Color(0xFF241F3D).copy(alpha = 0.6f), Color(0xFFB39DDB).copy(alpha = 0.6f))
val SkinKitchen = FeatureSkin(R.drawable.bg_kitchen_ember, TintKitchenEmber, Color(0xFF331C0E).copy(alpha = 0.6f), Color(0xFFFF8A50).copy(alpha = 0.6f))
val SkinBills = FeatureSkin(R.drawable.bg_bills_steel, TintBillsSteel, Color(0xFF122036).copy(alpha = 0.6f), Color(0xFFFF8A65).copy(alpha = 0.6f))
val SkinDebt = FeatureSkin(R.drawable.bg_debt_wine, TintDebtWine, Color(0xFF2E1218).copy(alpha = 0.6f), Color(0xFFEF9A9A).copy(alpha = 0.6f))
val SkinSearch = FeatureSkin(R.drawable.bg_search_teal, TintSearchTeal, Color(0xFF14242F).copy(alpha = 0.6f), Color(0xFF4DD0E1).copy(alpha = 0.6f))
val SkinSettings = FeatureSkin(R.drawable.bg_settings_neutral, TintSettingsNeutral, Color(0xFF1B2340).copy(alpha = 0.6f), Color(0xFFB8C6FF).copy(alpha = 0.6f))
val SkinVault = FeatureSkin(R.drawable.bg_networth_vault, Color(0xFF14301F).copy(alpha = 0.6f), Color(0xFF101D33).copy(alpha = 0.6f), PrimaryGold)
val SkinCampus = FeatureSkin(R.drawable.bg_university_campus, Color(0xFF232946).copy(alpha = 0.6f), Color(0xFF1A2140).copy(alpha = 0.6f), Color(0xFFB8C6FF).copy(alpha = 0.6f))
val SkinSavings = FeatureSkin(R.drawable.bg_savings, TintSavingsGrowth, Color(0xFF10281E).copy(alpha = 0.6f), Color(0xFF7BFFB2).copy(alpha = 0.9f))
val SkinIncome = FeatureSkin(R.drawable.bg_income, TintIncome, Color(0xFF12271F).copy(alpha = 0.6f), PrimaryGold)

@Composable
fun skinCardColor(skin: FeatureSkin): Color =
    if (MaterialTheme.colorScheme.brightness()) skin.card else MaterialTheme.colorScheme.surface

// Signature card of a feature: own card tone, own accent line, own voice.
@Composable
fun SkinCard(
    skin: FeatureSkin,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = PesaRadius.xl,
        colors = CardDefaults.cardColors(containerColor = skinCardColor(skin)),
        elevation = CardDefaults.cardElevation(defaultElevation = PesaElevation.card)
    ) {
        Column(Modifier.fillMaxWidth().padding(PesaSpacing.xl), content = content)
    }
}

@Composable
fun SkinAccentLine(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.width(48.dp).height(3.dp).clip(PesaRadius.xs).background(color))
}

// Cross-feature linking primitive: the app works as a unit, but every
// cross-feature action is an OPTION the user confirms first. Nothing moves
// money silently: title + honest body + [action] -> confirm dialog -> effect.
@Composable
fun LinkOptionCard(
    skin: FeatureSkin,
    title: String,
    body: String,
    actionLabel: String,
    confirmTitle: String,
    confirmBody: String,
    confirmLabel: String = "Confirm",
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirm by remember { mutableStateOf(false) }
    SkinCard(skin = skin, modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(PesaSpacing.xs))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(PesaSpacing.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = { showConfirm = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text(actionLabel) }
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(confirmTitle, fontWeight = FontWeight.Bold) },
            text = { Text(confirmBody) },
            confirmButton = {
                Button(onClick = { showConfirm = false; onConfirm() }) { Text(confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SkinSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    // Soft drop shadow keeps headers legible over the lighter scrim + photo.
    val titleStyle = MaterialTheme.typography.titleLarge.copy(
        shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f)
    )
    val subtitleStyle = MaterialTheme.typography.bodySmall.copy(
        shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 1f), blurRadius = 6f)
    )
    Column(modifier.fillMaxWidth()) {
        Text(title, style = titleStyle, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = subtitleStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
