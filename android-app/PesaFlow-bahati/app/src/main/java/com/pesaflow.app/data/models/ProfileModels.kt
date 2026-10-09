package com.pesaflow.app.data.models

import androidx.room.*

// Multidimensional financial profile (§12): independent dimensions instead
// of one archetype. SINGLETON row — one user, one declared truth.
// Declared facts outrank inference; observation may only SUGGEST (§13).
@Entity(tableName = "financial_profile")
data class FinancialProfile(
    @PrimaryKey val id: String = "SINGLETON_PROFILE",
    val housing: String = "HOSTEL", // PARENTS, RENTAL, SHARED_RENT, HOSTEL
    val commute: String = "SHORT", // WALK, SHORT, LONG, MIXED, IRREGULAR
    val food: String = "MIXED", // HOME_FED, COOK, BUY, MIXED
    val household: String = "NONE", // NONE, LOW, MEDIUM, HIGH contribution
    val incomeStability: String = "MIXED", // FIXED, VARIABLE, MIXED, NONE
    val incomeKindsCsv: String = "", // HELB,PARENT,HUSTLE,JOB,...
    val academic: String = "SEMESTER", // FIRST_YEAR, RETURNING, SEMESTER, BREAK, EXAM_PERIOD
    val debtLevel: String = "NONE", // NONE, LOW, MEDIUM, HIGH
    val savingsPressure: String = "LOW", // LOW, MEDIUM, HIGH
    val risk: String = "STANDARD", // CONSERVATIVE, STANDARD, FLEXIBLE
    val roommates: Int = 0,
    val rentShare: Double = 0.0, // fraction of rent this user pays (1.0 = all)
    val utilityShare: Double = 0.0,
    val commuteDays: Int = 0, // declared campus days per week (0 = unknown)
    val cookingDays: Int = 0, // home-cooked dinners per week (0 = unknown)
    val dependants: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
