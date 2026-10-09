package com.pesaflow.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import com.pesaflow.app.ui.theme.PpQuickAction
import com.pesaflow.app.ui.theme.ppSpacing
import com.pesaflow.app.ui.theme.ppTypography
import com.pesaflow.app.ui.theme.ppColors


// Personalized header: greeting first, actions after the hero.
@Composable
fun HomeGreeting(userName: String) {
    Column {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val part = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else -> "night"
        }
        // Greetings stay in plain English in every language mode —
        // a wrong Sheng greeting is worse than no Sheng at all.
        val greet = when (part) {
            "morning" -> "Good morning ☀️"
            "afternoon" -> "Good afternoon 🌤️"
            "evening" -> "Good evening 🌙"
            else -> "Burning the midnight oil 🦉"
        }
        Text(
            if (userName.isNotBlank()) "$greet, $userName" else greet,
            style = ppTypography.h2,
            color = ppColors.textPrimary
        )
        Spacer(modifier = Modifier.height(ppSpacing.xs))
        Text(
            "Here's your financial picture today",
            style = ppTypography.bodySmall,
            color = ppColors.textTertiary
        )
        Text(
            java.text.SimpleDateFormat("EEEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date()),
            style = ppTypography.bodySmall,
            color = ppColors.textTertiary
        )
    }
}

@Composable
fun HomeQuickActions(
    pendingCount: Int,
    scanning: Boolean,
    onScanToday: () -> Unit,
    onReviewPending: () -> Unit
) {
    // Scan-only: money enters through SMS scans and statement imports, never
    // typed forms. Scan fills Pending below; review confirms it to the ledger.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ppSpacing.md)) {
        PpQuickAction(
            label = if (scanning) "Scanning…" else "Scan today",
            icon = Icons.Filled.Search,
            onClick = onScanToday,
            modifier = Modifier.weight(1f)
        )
        PpQuickAction(
            label = if (pendingCount > 0) "Pending ($pendingCount)" else "Pending",
            icon = Icons.Filled.Notifications,
            onClick = onReviewPending,
            modifier = Modifier.weight(1f)
        )
    }
}
