package com.pesaflow.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.UserRhythm
import com.pesaflow.app.data.models.RhythmKind

// One card per unconfirmed hypothesis. The user sees
// "I think you spend ~KSh50 on fares daily 7-10am"
// and taps Yes (store + apply) or No (dismiss).
// Confirmed rhythms live in user_rhythms and drive
// fare reserves, rent alerts, payday predictions.

@Composable
fun RhythmConfirmCard(
    hypothesis: RhythmHypothesis,
    onConfirm: (RhythmHypothesis) -> Unit,
    onDismiss: (RhythmHypothesis) -> Unit,
    ) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (hypothesis.kind) {
                RhythmKind.FARE_WINDOW -> Color(0xFFFFF8E1)
                RhythmKind.RENT_DAY -> Color(0xFFE8F5E9)
                RhythmKind.PAYDAY -> Color(0xFFE3F2FD)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (hypothesis.kind) {
                            RhythmKind.FARE_WINDOW -> "Daily fare window 🚌"
                            RhythmKind.RENT_DAY -> "Rent day 🏠"
                            RhythmKind.PAYDAY -> "Payday 💰"
                            RhythmKind.AIRTIME -> "Airtime rhythm 📱"
                            RhythmKind.CUSTOM -> "Money rhythm 🔄"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(hypothesis.hint, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Confidence: ${(hypothesis.confidence * 100).toInt()}% · based on ${hypothesis.supportingCodes.size} observed ${if (hypothesis.supportingCodes.size == 1) "run" else "runs"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        onDismiss(hypothesis)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) { Text("No", color = Color.Gray) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onConfirm(hypothesis) }) { Text("Yes") }
            }
        }
    }
}
