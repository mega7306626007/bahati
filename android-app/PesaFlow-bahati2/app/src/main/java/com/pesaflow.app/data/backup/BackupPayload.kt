package com.pesaflow.app.data.backup

import com.pesaflow.app.data.models.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


/** Backup v2: every table, every field, compiler-checked. Adding a column to
 *  an entity automatically joins the backup — parity by construction, not
 *  by remembering to update two hand-written converters. */
@Serializable
data class BackupPayload(
    val version: Int = 2,
    val exportedAt: Long = 0L,
    val transactions: List<Transaction> = emptyList(),
    val pending: List<PendingTransaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val profile: UniversityProfile? = null,
    val bills: List<Bill> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val meals: List<MealItem> = emptyList(),
    val chamas: List<ChamaGroup> = emptyList(),
    val belongings: List<Belonging> = emptyList(),
    val kitchenStock: List<KitchenStock> = emptyList()
)


val BackupJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
