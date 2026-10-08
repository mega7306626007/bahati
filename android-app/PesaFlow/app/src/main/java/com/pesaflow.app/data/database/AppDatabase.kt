package com.pesaflow.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import com.pesaflow.app.data.models.*


@Database(
    entities = [Transaction::class, PendingTransaction::class, Budget::class, SavingsGoal::class, UniversityProfile::class, Bill::class, Debt::class, MealItem::class, ChamaGroup::class, Belonging::class, KitchenStock::class, UserRhythm::class, ContextFact::class, ModelFeedback::class, CategoryRule::class],
    version = 17,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun universityProfileDao(): UniversityProfileDao
    abstract fun billDao(): BillDao
    abstract fun debtDao(): DebtDao
    abstract fun mealDao(): MealDao
    abstract fun chamaDao(): ChamaDao
    abstract fun belongingDao(): BelongingDao
    abstract fun kitchenStockDao(): KitchenStockDao
    abstract fun userRhythmDao(): UserRhythmDao
    abstract fun contextFactDao(): ContextFactDao
    abstract fun modelFeedbackDao(): ModelFeedbackDao
    abstract fun categoryRuleDao(): CategoryRuleDao


    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kitchen_stock ADD COLUMN expiryTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE kitchen_stock ADD COLUMN eatByDays INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN direction TEXT NOT NULL DEFAULT 'THEY_OWE'")
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS user_rhythms (" +
                    "id TEXT PRIMARY KEY NOT NULL, " +
                    "kind TEXT NOT NULL, category TEXT NOT NULL, " +
                    "confidence REAL NOT NULL, hint TEXT NOT NULL, " +
                    "dayOfMonth INTEGER NOT NULL DEFAULT -1, amount REAL NOT NULL DEFAULT 0, " +
                    "sourceCode TEXT NOT NULL DEFAULT '', " +
                    "confirmed INTEGER NOT NULL DEFAULT 0, " +
                    "dismissed INTEGER NOT NULL DEFAULT 0, " +
                    "createdAt INTEGER NOT NULL DEFAULT 0)")
            }
        }
        // Indices: every date-range, category, type and source-code lookup was
        // a full table scan — findBySourceCode runs on EVERY incoming SMS.
        // Names must match Room's generated index_<table>_<col> convention.
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_dateTimestamp ON transactions(dateTimestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_sourceTransactionId ON transactions(sourceTransactionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_category ON transactions(category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type ON transactions(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_transactions_sourceTransactionId ON pending_transactions(sourceTransactionId)")
            }
        }
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema is unchanged from 13 — version bump only.
            }
        }
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS context_facts (" +
                    "id TEXT PRIMARY KEY NOT NULL, " +
                    "key TEXT NOT NULL, " +
                    "value TEXT NOT NULL, " +
                    "source TEXT NOT NULL DEFAULT 'USER_ENTERED', " +
                    "confidence REAL NOT NULL DEFAULT 0.5, " +
                    "createdAt INTEGER NOT NULL DEFAULT 0, " +
                    "updatedAt INTEGER NOT NULL DEFAULT 0, " +
                    "expiresAt INTEGER NOT NULL DEFAULT 0, " +
                    "userConfirmed INTEGER NOT NULL DEFAULT 0, " +
                    "evidenceLevel TEXT NOT NULL DEFAULT 'TENTATIVE')" )
            }
        }
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS model_feedback (" +
                    "id TEXT PRIMARY KEY NOT NULL, " +
                    "merchant TEXT NOT NULL DEFAULT '', " +
                    "smsText TEXT NOT NULL DEFAULT '', " +
                    "suggestedType TEXT NOT NULL DEFAULT '', " +
                    "suggestedCategory TEXT NOT NULL DEFAULT '', " +
                    "suggestedConfidence REAL NOT NULL DEFAULT 0.0, " +
                    "suggestedSource TEXT NOT NULL DEFAULT '', " +
                    "finalType TEXT NOT NULL DEFAULT '', " +
                    "finalCategory TEXT NOT NULL DEFAULT '', " +
                    "accepted INTEGER NOT NULL DEFAULT 0, " +
                    "createdAt INTEGER NOT NULL DEFAULT 0)")
            }
        }
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS category_rules (" +
                    "id TEXT PRIMARY KEY NOT NULL, " +
                    "keyword TEXT NOT NULL, " +
                    "category TEXT NOT NULL, " +
                    "enabled INTEGER NOT NULL DEFAULT 1, " +
                    "createdAt INTEGER NOT NULL DEFAULT 0)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null


        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pesaflow_secure_db"
                // Explicit migrations only — no destructive fallback. A missing
                // migration must crash loudly, never silently wipe user data.
                ).addMigrations(
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17
                ).build()
                INSTANCE = instance
                instance
            }
        }

    }
}