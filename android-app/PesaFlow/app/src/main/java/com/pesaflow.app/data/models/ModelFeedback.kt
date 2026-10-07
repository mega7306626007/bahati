package com.pesaflow.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * ModelFeedback — user confirm/correct events for ML suggestions (spec §15 & §18).
 *
 * Every ML suggestion shown in UI must be confirmable/correctable.
 * Accepted rows become future training candidates (exported only with
 * explicit user opt-in, anonymized). Rejected rows drive dictionary
 * overrides and forecast invalidation — never silent retraining.
 */
@Entity(tableName = "model_feedback")
data class ModelFeedback(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val merchant: String = "",
    val smsText: String = "",
    val suggestedType: String = "",
    val suggestedCategory: String = "",
    val suggestedConfidence: Double = 0.0,
    val suggestedSource: String = "",
    val finalType: String = "",
    val finalCategory: String = "",
    val accepted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
