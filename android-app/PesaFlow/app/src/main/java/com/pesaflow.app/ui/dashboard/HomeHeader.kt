package com.pesaflow.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.viewmodels.FinanceViewModel


@Composable
fun HomeHeader(
    viewModel: FinanceViewModel,
    userName: String,
    onQuickAdd: (TransactionType) -> Unit
) {
    Column {
        // Daily voice: nickname first, full name only as fallback.
        val nickname by viewModel.nickname.collectAsState()
        val dailyName = nickname.ifBlank { userName }
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
            if (dailyName.isNotBlank()) "$greet $dailyName" else greet,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            java.text.SimpleDateFormat("EEEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onQuickAdd(TransactionType.EXPENSE) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Text(viewModel.getLocalizedString("expense_btn"))
            }
            Button(
                onClick = { onQuickAdd(TransactionType.INCOME) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text(viewModel.getLocalizedString("income_btn"))
            }
        }
    }
}
