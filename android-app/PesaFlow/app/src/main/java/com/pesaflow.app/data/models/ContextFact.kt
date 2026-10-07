package com.pesaflow.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * ContextFact — structured user context, spec §3 & §13.
 *
 * Relationships rather than flat questionnaire. Each fact has provenance
 * (source), confidence, timestamps, and user-confirmation status.
 * Model-inferred fields are never promoted to confirmed user facts.
 *
 * Sources: USER_SELECTED, USER_ENTERED, USER_CONFIRMED,
 * MODEL_INFERRED, IMPORTED_TRANSACTION, CALCULATED
 *
 * Evidence levels: INSUFFICIENT < TENTATIVE < OBSERVED < CONFIRMED
 */
@Entity(tableName = "context_facts")
data class ContextFact(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val key: String,
    val value: String,
    val source: String = "USER_ENTERED", // USER_SELECTED | USER_ENTERED | USER_CONFIRMED | MODEL_INFERRED | IMPORTED_TRANSACTION | CALCULATED
    val confidence: Double = 0.5, // 0.0-1.0, never treat inferred as confirmed
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0L, // 0 = never expires
    val userConfirmed: Boolean = false,
    val evidenceLevel: String = "TENTATIVE" // INSUFFICIENT | TENTATIVE | OBSERVED | CONFIRMED
)