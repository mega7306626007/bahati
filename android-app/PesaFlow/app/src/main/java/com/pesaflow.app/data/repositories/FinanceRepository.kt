package com.pesaflow.app.data.repositories

import com.pesaflow.app.data.database.AppDatabase
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


class FinanceRepository(private val database: AppDatabase) {


    val allTransactions: Flow<List<Transaction>> = database.transactionDao().getAllTransactions()
    val pendingTransactions: Flow<List<PendingTransaction>> = database.pendingTransactionDao().getAllPendingTransactions()
    val userRhythms: Flow<List<UserRhythm>> = database.userRhythmDao().getAll()
    val confirmedRhythms: Flow<List<UserRhythm>> = database.userRhythmDao().getConfirmed()
    val budgets: Flow<List<Budget>> = database.budgetDao().getAllBudgets()
    val savingsGoals: Flow<List<SavingsGoal>> = database.savingsGoalDao().getAllSavingsGoals()
    val universityProfile: Flow<UniversityProfile?> = database.universityProfileDao().getUniversityProfile()
    val allBills: Flow<List<Bill>> = database.billDao().getAllBills()
    val allDebts: Flow<List<Debt>> = database.debtDao().getAllDebts()
    val allMealItems: Flow<List<MealItem>> = database.mealDao().getAllMealItems()
    val allBelongings: Flow<List<Belonging>> = database.belongingDao().getAllBelongings()
    val allKitchenStock: Flow<List<KitchenStock>> = database.kitchenStockDao().getAllStock()
    val contextFacts: Flow<List<ContextFact>> = database.contextFactDao().getAll()
    val modelFeedback: Flow<List<ModelFeedback>> = database.modelFeedbackDao().getAll()
    val categoryRules: Flow<List<CategoryRule>> = database.categoryRuleDao().getAll()


    suspend fun insertMealItem(item: MealItem) = database.mealDao().insertMealItem(item)


    suspend fun deleteMealItem(id: String) = database.mealDao().deleteMealItem(id)


    suspend fun insertBelonging(item: Belonging) = database.belongingDao().insertBelonging(item)


    suspend fun updateBelonging(item: Belonging) = database.belongingDao().updateBelonging(item)


    suspend fun deleteBelonging(id: String) = database.belongingDao().deleteBelonging(id)


    suspend fun insertKitchenStock(item: KitchenStock) = database.kitchenStockDao().insertStock(item)


    suspend fun updateKitchenStock(item: KitchenStock) = database.kitchenStockDao().updateStock(item)


    suspend fun deleteKitchenStock(id: String) = database.kitchenStockDao().deleteStock(id)


    suspend fun clearMealItems() = database.mealDao().clearMealItems()


    val allChamaGroups: Flow<List<ChamaGroup>> = database.chamaDao().getAllChamaGroups()


    suspend fun insertChamaGroup(group: ChamaGroup) = database.chamaDao().insertChamaGroup(group)


    suspend fun deleteChamaGroup(id: String) = database.chamaDao().deleteChamaGroup(id)


    suspend fun advanceChama(id: String, cycles: Int) = database.chamaDao().updatePaidCycles(id, cycles)


    fun getTransactionsInTimeframe(start: Long, end: Long): Flow<List<Transaction>> =
        database.transactionDao().getTransactionsInTimeframe(start, end)


    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> =
        database.transactionDao().getTransactionsByType(type)


    fun getTransactionsByCategory(category: String): Flow<List<Transaction>> =
        database.transactionDao().getTransactionsByCategory(category)


    fun getTransactionsByCategoryInTimeframe(start: Long, end: Long): Flow<List<TransactionSummary>> =
        database.transactionDao().getTransactionsByCategoryInTimeframe(start, end)


    fun getConfirmedTransactions(): Flow<List<Transaction>> =
        database.transactionDao().getConfirmedTransactions()


    suspend fun insertTransaction(transaction: Transaction) {
        database.transactionDao().insertTransaction(transaction)
    }


    suspend fun updateTransaction(transaction: Transaction) {
        database.transactionDao().updateTransaction(transaction)
    }


    suspend fun insertTransactions(transactions: List<Transaction>) {
        database.transactionDao().insertTransactions(transactions)
    }


    suspend fun deleteTransaction(id: String) {
        database.transactionDao().deleteTransaction(id)
    }


    suspend fun deleteAllTransactions() {
        database.transactionDao().deleteAllTransactions()
    }


    suspend fun deleteBySource(source: String) {
        database.transactionDao().deleteBySource(source)
    }


    suspend fun updateConfirmation(id: String, confirmed: Boolean) {
        database.transactionDao().updateConfirmation(id, confirmed)
    }


    suspend fun insertPendingTransaction(pending: PendingTransaction): Boolean {
        // Check for duplicates before inserting (pending + confirmed ledgers).
        // Returns false when skipped as a duplicate so callers can say so honestly.
        val code = pending.sourceTransactionId ?: ""
        if (code.isNotEmpty()) {
            if (database.pendingTransactionDao().findBySourceCode(code) != null) return false
            if (database.transactionDao().findBySourceCode(code) != null) return false
        }
        database.pendingTransactionDao().insertPendingTransaction(pending)
        return true
    }


    suspend fun insertPendingTransactions(pending: List<PendingTransaction>) {
        for (p in pending) {
            insertPendingTransaction(p)
        }
    }


    suspend fun deletePendingTransaction(id: String) {
        database.pendingTransactionDao().deletePendingTransaction(id)
    }

    // ContextFacts: structured user context (spec §3 & §13). One row per
    // key — save replaces any prior value so readers never see stale dupes.
    // Model-inferred rows are never auto-promoted to user-confirmed.
    suspend fun saveContextFact(fact: ContextFact) {
        database.contextFactDao().deleteByKey(fact.key)
        database.contextFactDao().insert(fact)
    }

    suspend fun getContextFact(key: String): ContextFact? =
        database.contextFactDao().getLatestByKey(key)

    fun observeContextFact(key: String): Flow<List<ContextFact>> =
        database.contextFactDao().getByKey(key)

    // ModelFeedback: confirm/correct events for ML suggestions (spec §15 & §18).
    // Accepted rows are future training candidates (exported only on opt-in).
    suspend fun recordModelFeedback(feedback: ModelFeedback) =
        database.modelFeedbackDao().insert(feedback)

    // Category rules: user-defined keyword → category overrides (applied in
    // MpesaParser.buildPending after built-in rules, before ML).
    suspend fun insertCategoryRule(rule: CategoryRule) = database.categoryRuleDao().insert(rule)
    suspend fun deleteCategoryRule(id: String) = database.categoryRuleDao().delete(id)
    suspend fun setCategoryRuleEnabled(id: String, enabled: Boolean) = database.categoryRuleDao().setEnabled(id, enabled)

    suspend fun modelFeedbackCount(): Int = database.modelFeedbackDao().count()

    // User-rhythm confirmations: hypotheses you verified,
    // stored for fare windows, rent-day alerts and payday predictions.
    suspend fun confirmRhythm(id: String) = database.userRhythmDao().confirm(id)
    suspend fun dismissRhythm(id: String) = database.userRhythmDao().dismiss(id)
    suspend fun upsertRhythm(rhythm: UserRhythm) = database.userRhythmDao().insert(rhythm)
    suspend fun upsertRhythms(rhythms: List<UserRhythm>) = database.userRhythmDao().insertAll(rhythms)


    // Atomic: a crash between insert and delete used to leave the row in both
    // tables — a duplicate the next scan would catch too late.
    // Fully-qualified: androidx.room.Transaction would shadow the model class.
    @androidx.room.Transaction
    suspend fun approvePendingTransaction(pending: PendingTransaction, customizedCategory: String): Transaction {
        val transaction = Transaction(
            amount = pending.amount,
            type = pending.type,
            category = customizedCategory,
            dateTimestamp = pending.dateTimestamp,
            merchant = pending.merchant,
            description = pending.rawText,
            paymentMethod = pending.paymentMethod,
            source = pending.source,
            sourceTransactionId = pending.sourceTransactionId,
            confirmed = true
        )
        insertTransaction(transaction)
        deletePendingTransaction(pending.id)
        return transaction
    }


    suspend fun rejectPendingTransaction(id: String) {
        database.pendingTransactionDao().deletePendingTransaction(id)
    }


    // Budget operations
    suspend fun insertBudget(budget: Budget) = database.budgetDao().insertBudget(budget)


    suspend fun deleteBudget(id: String) = database.budgetDao().deleteBudget(id)


    suspend fun updateBudgetAmount(id: String, amount: Double) = database.budgetDao().updateBudgetAmount(id, amount)


    suspend fun deleteBudgetsByType(type: BudgetType) = database.budgetDao().deleteBudgetsByType(type)


    suspend fun updateBudgetShared(id: String, names: String) = database.budgetDao().updateSharedWith(id, names)


    fun getActiveBudgets(start: Long, end: Long): Flow<List<Budget>> =
        database.budgetDao().getActiveBudgets(start, end)


    // Savings Goals operations
    suspend fun insertSavingsGoal(goal: SavingsGoal) = database.savingsGoalDao().insertSavingsGoal(goal)


    suspend fun deleteSavingsGoal(id: String) = database.savingsGoalDao().deleteSavingsGoal(id)


    suspend fun contributeToSavingsGoal(id: String, newAmount: Double) =
        database.savingsGoalDao().updateSavingsGoalCurrent(id, newAmount)


    suspend fun updateSavingsGoalCurrent(id: String, current: Double) =
        database.savingsGoalDao().updateSavingsGoalCurrent(id, current)


    suspend fun getSavingsGoal(id: String): SavingsGoal? =
        database.savingsGoalDao().getSavingsGoal(id)


    // Atomic increment: concurrent contributes can never lose an update to a
    // stale read-modify-write.
    suspend fun bumpGoal(id: String, amount: Double) =
        database.savingsGoalDao().bumpGoal(id, amount)


    fun getGoalsWithRemaining(minCurrent: Double): Flow<List<SavingsGoal>> =
        database.savingsGoalDao().getGoalsWithRemaining(minCurrent)


    fun getCompletedGoals(): Flow<List<SavingsGoal>> =
        database.savingsGoalDao().getCompletedGoals()


    // University Profile operations
    suspend fun saveUniversityProfile(profile: UniversityProfile) =
        database.universityProfileDao().saveUniversityProfile(profile)


    // Bill operations
    suspend fun insertBill(bill: Bill) = database.billDao().insertBill(bill)


    suspend fun deleteBill(id: String) = database.billDao().deleteBill(id)


    suspend fun updateBillAmount(id: String, amount: Double, dueDate: Long, category: String, frequency: String, reminder: Boolean, leadDays: Int) =
        database.billDao().updateBill(id, amount, dueDate, category, frequency, reminder, leadDays)


    suspend fun markBillPaid(id: String) = database.billDao().markBillPaid(id)


    fun getBillsInTimeframe(start: Long, end: Long): Flow<List<Bill>> =
        database.billDao().getBillsInTimeframe(start, end)


    fun getOverdueBills(now: Long): Flow<List<Bill>> =
        database.billDao().getOverdueBills(now)


    // Debt operations
    suspend fun insertDebt(debt: Debt) = database.debtDao().insertDebt(debt)


    suspend fun deleteDebt(id: String) = database.debtDao().deleteDebt(id)


    suspend fun updateDebtAmount(id: String, amount: Double, dueDate: Long, description: String, status: String, reminder: Boolean, leadDays: Int) =
        database.debtDao().updateDebt(id, amount, dueDate, description, status, reminder, leadDays)


    suspend fun markDebtPaid(id: String) = database.debtDao().markDebtPaid(id)


    fun getOverdueDebts(now: Long): Flow<List<Debt>> =
        database.debtDao().getOverdueDebts(now)


    fun totalOwing(): Double = database.debtDao().totalOwing()


    fun totalOverdue(): Double = database.debtDao().totalOverdue()
}