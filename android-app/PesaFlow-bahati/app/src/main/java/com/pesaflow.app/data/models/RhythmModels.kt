package com.pesaflow.app.data.models

import androidx.room.*

// Hypotheses PesaFlow proposes about your money rhythm.
// The user confirms or dismisses each — confirmed ones drive
// fare windows, rent-day alerts, payday predictions and insights.
// (Best-of both trees: ported from PesaFlow main.)
enum class RhythmKind(val short: String) {
    FARE_WINDOW("fare"),
    RENT_DAY("rent"),
    PAYDAY("payday"),
    AIRTIME("airtime"),
    CUSTOM("custom")
}

@Entity(tableName = "user_rhythms")
data class UserRhythm(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val kind: String,              // RhythmKind.name
    val category: String,          // "Transport", "Rent", "Income", etc.
    val confidence: Float,         // 0.5f-0.95f
    val hint: String,              // "Every day 7-10am ~KSh50"
    val dayOfMonth: Int,           // -1 = any day / day-of-week
    val amount: Double = 0.0,
    val sourceCode: String = "",   // fingerprint backing this hypothesis
    val confirmed: Boolean = false,
    val dismissed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
