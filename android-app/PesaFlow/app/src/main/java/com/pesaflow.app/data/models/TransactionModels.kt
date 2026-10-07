package com.pesaflow.app.data.models

import androidx.room.*
import java.util.UUID


enum class TransactionType { INCOME, EXPENSE, SAVING, INVESTMENT }


enum class TransactionSource { MANUAL, MPESA_SMS, NOTIFICATION, SHARE_TO_APP, RECEIPT_OCR, CSV_IMPORT, NLP, OPENING, SAMPLE }


enum class PaymentMethod { CASH, MPESA, BANK_TRANSFER, AIRTIME, OTHER }


enum class BudgetType { DAILY, WEEKLY, MONTHLY, SEMESTER, ANNUAL }

// Hypotheses PesaFlow proposes about your money rhythm.
// The user confirms or dismisses each — confirmed ones drive
// fare windows, rent-day alerts, payday predictions and insights.
enum class RhythmKind(val short: String) {
    FARE_WINDOW("fare"),
    RENT_DAY("rent"),
    PAYDAY("payday"),
    AIRTIME("airtime"),
    CUSTOM("custom")
}

@Entity(tableName = "user_rhythms")
data class UserRhythm(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
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


enum class AppLanguage { ENGLISH, KISWAHILI, SHENG, MIXED }


enum class AppTheme { SYSTEM, LIGHT, DARK, AMOLED }


@Entity(
    tableName = "transactions",
    indices = [
        Index("dateTimestamp"),
        Index("sourceTransactionId"),
        Index("category"),
        Index("type")
    ]
)
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val subcategory: String = "",
    val dateTimestamp: Long, // Epoch timestamp in milliseconds
    val merchant: String,
    val description: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,
    val source: TransactionSource = TransactionSource.MANUAL,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val recurring: Boolean = false,
    val confirmed: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceTransactionId: String? = null, // e.g., M-Pesa transaction code
    val receiptImagePath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)


@Entity(
    tableName = "pending_transactions",
    indices = [Index("sourceTransactionId")]
)
data class PendingTransaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val subcategory: String = "",
    val merchant: String,
    val dateTimestamp: Long,
    val paymentMethod: PaymentMethod,
    val source: TransactionSource,
    val sourceTransactionId: String?,
    val rawText: String,
    val confidenceScore: Float = 1.0f,
    val displayCategory: String = "",
    val displayMerchant: String = ""
)


@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val category: String = "ALL", // "ALL" for global wallet budget
    val limitAmount: Double,
    val type: BudgetType,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val sharedWith: String = "" // comma-separated household names sharing this envelope
)


@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val targetTimestamp: Long = 0L
)


@Entity(tableName = "university_profiles")
data class UniversityProfile(
    @PrimaryKey val id: String = "SINGLETON_USER_PROFILE",
    val universityName: String = "",
    val campus: String = "",
    val currentSemester: Int = 1,
    val academicYear: String = "",
    val semesterStartTimestamp: Long = 0L,
    val semesterEndTimestamp: Long = 0L,
    val startingFunding: Double = 0.0,
    val helbExpected: Double = 0.0,
    val feesAmount: Double = 0.0,
    val feesDueDate: Long = 0L,
    val fundingSource: String = "HELB" // HELB, SELF, BOTH
)


@Entity(tableName = "chama_groups")
data class ChamaGroup(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val contribution: Double,
    val members: String = "", // comma-separated rotation order
    val cycleDays: Int = 30,
    val startTimestamp: Long = System.currentTimeMillis(),
    val paidCycles: Int = 0
)


@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Double,
    val dueDate: Long, // Epoch timestamp in milliseconds
    val category: String,
    val frequency: String = "ONE_TIME", // ONE_TIME, MONTHLY, WEEKLY, CUSTOM
    val status: String = "UNPAID", // UNPAID, PAID
    val reminderEnabled: Boolean = false,
    val reminderLeadDays: Int = 3
)


@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val person: String,
    val amount: Double,
    val dateBorrowed: Long, // Epoch timestamp in milliseconds
    val dueDate: Long, // Epoch timestamp in milliseconds
    val description: String = "",
    val status: String = "OWING", // OWING, PAID, OVERDUE
    val direction: String = "THEY_OWE", // THEY_OWE (they owe me) or I_OWE (I owe them)
    val reminderEnabled: Boolean = false,
    val reminderLeadDays: Int = 3
)


@Entity(tableName = "meal_items")
data class MealItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mealType: String = "Lunch", // Breakfast, Lunch, Supper, Snack
    val price: Double,
    val component: String = "Complete", // Starch, Mboga, Protein, Complete, Other
    val source: String = "Buy" // Cook (raw, you cook) or Buy (ready cooked)
)


@Entity(tableName = "belongings")
data class Belonging(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String = "Other", // Clothes, Books, Shoes, Electronics, Other
    val status: String = "NEED", // HAVE, NEED
    val estCost: Double = 0.0,
    val priority: Int = 2, // 1 must-have, 2 nice, 3 dream
    val notes: String = ""
)


@Entity(tableName = "kitchen_stock")
data class KitchenStock(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String, // unga, oil, sukuma...
    val unit: String = "kg",
    val qtyFull: Double = 1.0, // pack size you buy
    val qtyLeft: Double = 1.0, // what's on the shelf now
    val dailyUse: Double = 0.25, // what a cooking day consumes
    val pricePerPack: Double = 0.0,
    val expiryTimestamp: Long = 0L, // 0 = not perishable; else eat-by date
    val eatByDays: Int = 0, // 0 = no priority; else eat-within-N-days target (1 or 5)
    val updatedAt: Long = System.currentTimeMillis()
)


// Pure stock math (kept here so unit tests cover it, not UI code).
fun stockDaysLeft(s: KitchenStock): Double =
    if (s.dailyUse > 0) (s.qtyLeft / s.dailyUse).coerceAtLeast(0.0) else Double.MAX_VALUE


fun stockRefillCost(s: KitchenStock): Double {
    if (s.qtyFull <= 0 || s.pricePerPack <= 0) return 0.0
    val fraction = ((s.qtyFull - s.qtyLeft) / s.qtyFull).coerceIn(0.0, 1.0)
    return s.pricePerPack * fraction
}


fun stockReplenishDate(s: KitchenStock, now: Long = System.currentTimeMillis()): Long {
    val days = stockDaysLeft(s).toLong().coerceAtMost(3650)
    return now + days * 24L * 60 * 60 * 1000
}


data class TransactionSearchFilter(
    var merchant: String = "",
    var category: String = "",
    var minAmount: Double? = null,
    var maxAmount: Double? = null,
    var startDate: Long? = null,
    var endDate: Long? = null,
    var paymentMethod: PaymentMethod? = null,
    var transactionType: TransactionType? = null,
    var keyword: String = ""
)


data class TransactionSummary(
    val category: String,
    val amount: Double,
    val count: Int
)