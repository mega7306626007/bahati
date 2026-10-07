package com.pesaflow.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * User-defined category rule: "if SMS text contains KEYWORD, assign CATEGORY".
 * Applied in MpesaParser.buildPending AFTER built-in rules but BEFORE ML —
 * user intent always wins. Rules are case-insensitive substring matches.
 */
@Entity(tableName = "category_rules")
data class CategoryRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val keyword: String,
    val category: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
