package com.pesaflow.app.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pesaflow.app.data.academic.budgetWindow
import com.pesaflow.app.data.database.AppDatabase
import com.pesaflow.app.data.models.*
import com.pesaflow.app.data.parsers.NaturalLanguageParser
import com.pesaflow.app.data.repositories.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class FinanceViewModel(application: Application) : AndroidViewModel(application) {


    private val database: AppDatabase = AppDatabase.getDatabase(application)
    internal val repository: FinanceRepository = FinanceRepository(database)


    // StateFlows for UI
    val allTransactions: StateFlow<List<Transaction>> = repository.allTransactions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    // Room speaks quickly but not instantly â€” until it has, the dashboard
    // must show a skeleton, not "Nothing yet" (which reads as "you have no
    // data" when the DB is still opening).
    val ledgerLoaded: StateFlow<Boolean> = repository.allTransactions
        .map { true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val pendingTransactions: StateFlow<List<PendingTransaction>> = repository.pendingTransactions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val budgets: StateFlow<List<Budget>> = repository.budgets.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val savingsGoals: StateFlow<List<SavingsGoal>> = repository.savingsGoals.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val universityProfile: StateFlow<UniversityProfile?> = repository.universityProfile.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )
    val bills: StateFlow<List<Bill>> = repository.allBills.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val debts: StateFlow<List<Debt>> = repository.allDebts.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val mealItems: StateFlow<List<MealItem>> = repository.allMealItems.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val belongings: StateFlow<List<Belonging>> = repository.allBelongings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val kitchenStock: StateFlow<List<KitchenStock>> = repository.allKitchenStock.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )


    // Runtime state bindings
    val currentLanguage = MutableStateFlow(AppLanguage.MIXED)
    val nlpInputText = MutableStateFlow("")
    val extractedNlpTransaction = MutableStateFlow<PendingTransaction?>(null)
    val userName = MutableStateFlow("")
    val nickname = MutableStateFlow("")
    val themeMode = MutableStateFlow(AppTheme.DARK)
    val hideBalances = MutableStateFlow(false)
    val hiddenSections = MutableStateFlow(setOf<String>())
    // Training data opt-in (spec §18): default OFF. Only accepted feedback
    // leaves the device, anonymized, and only after this toggle.
    val trainingOptIn = MutableStateFlow(false)

    // Event-driven notifications: the reminder engine is all clock-driven;
    // these fire the moment money moves. MainActivity collects and posts.
    private val _transactionEvents = MutableSharedFlow<Transaction>(extraBufferCapacity = 8)
    val transactionEvents: SharedFlow<Transaction> = _transactionEvents

    // Goal reached: the moment a contribution completes a savings goal.
    data class GoalReached(val goal: SavingsGoal)
    private val _goalEvents = MutableSharedFlow<GoalReached>(extraBufferCapacity = 4)
    val goalEvents: SharedFlow<GoalReached> = _goalEvents

    // Low balance: fires once per crossing (above → below threshold), not on
    // every emission while below. Threshold is a pref, default KSh 500.
    private val _lowBalanceEvents = MutableSharedFlow<Double>(extraBufferCapacity = 4)
    val lowBalanceEvents: SharedFlow<Double> = _lowBalanceEvents
    private var lastBalanceSeen = Double.MAX_VALUE


    init {
        // Restore persisted identity + preferences (no new dependencies: SharedPreferences only).
        // NOTE: this block must stay AFTER the StateFlow declarations above:
        // init blocks run in textual order.
        val prefs = getApplication<Application>().getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
        userName.value = prefs.getString("user_name", "") ?: ""
        nickname.value = prefs.getString("user_nickname", "") ?: ""
        currentLanguage.value = try {
            AppLanguage.valueOf(prefs.getString("app_language", "MIXED") ?: "MIXED")
        } catch (e: Exception) {
            AppLanguage.MIXED
        }
        themeMode.value = try {
            AppTheme.valueOf(prefs.getString("app_theme", "DARK") ?: "DARK")
        } catch (e: Exception) {
            AppTheme.DARK
        }
        hideBalances.value = prefs.getBoolean("hide_balances", false)
        hiddenSections.value = (prefs.getString("hidden_sections", "") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        trainingOptIn.value = prefs.getBoolean("training_opt_in", false)

        // Low-balance watcher: fires once per crossing (above → below), not
        // on every emission while below. Threshold is a pref (default 500).
        viewModelScope.launch {
            availableBalance.collect { bal ->
                val threshold = prefs().getFloat("low_balance_threshold", 500f).toDouble()
                if (threshold > 0 && bal < threshold && lastBalanceSeen >= threshold) {
                    _lowBalanceEvents.tryEmit(bal)
                }
                lastBalanceSeen = bal
            }
        }
    }


    private fun prefs() = getApplication<Application>().getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)


    // Computed metrics â€” every figure routes through MoneyMath, the single
    // home for hero money math, so no screen can count the ledger its own way.
    val availableBalance: StateFlow<Double> = allTransactions.map { txs ->
        com.pesaflow.app.data.money.ledgerBalance(txs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    val monthlyIncome: StateFlow<Double> = allTransactions.map { txs ->
        com.pesaflow.app.data.money.monthScopedTotal(txs, TransactionType.INCOME)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    val monthlyExpenses: StateFlow<Double> = allTransactions.map { txs ->
        com.pesaflow.app.data.money.monthScopedTotal(txs, TransactionType.EXPENSE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    val totalSavings: StateFlow<Double> = allTransactions.map { txs ->
        txs.filter { it.type == TransactionType.SAVING && it.source !in com.pesaflow.app.data.money.NON_STAT_SOURCES }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    val ziidiSaved: StateFlow<Double> = allTransactions.map { txs ->
        com.pesaflow.app.data.money.ziidiHoldings(txs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    // User-curated money-rhythm hypotheses confirmed on Home.
    val userRhythms: StateFlow<List<UserRhythm>> = repository.userRhythms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val confirmedRhythms: StateFlow<List<UserRhythm>> = repository.confirmedRhythms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Structured user context (spec §3 & §13). Observed by UI; never written by ML.
    val contextFacts: StateFlow<List<ContextFact>> = repository.contextFacts.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val modelFeedback: StateFlow<List<ModelFeedback>> = repository.modelFeedback.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val categoryRules: StateFlow<List<CategoryRule>> = repository.categoryRules.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    init {
        // Keep the parser's rule cache in sync — rules apply on next SMS.
        viewModelScope.launch {
            repository.categoryRules.collect { rules ->
                com.pesaflow.app.data.parsers.MpesaParser.setCachedRules(rules)
            }
        }
    }

    // Forecast invalidation (spec §15): bumped whenever a confirmed fact is
    // corrected or an ML suggestion is rejected. Forecast UI collects this
    // version and recomputes trailing-mean ranges — never serves stale bands.
    private val _forecastVersion = MutableStateFlow(0)
    val forecastVersion: StateFlow<Int> = _forecastVersion

    fun saveUserContextFact(
        key: String,
        value: String,
        source: String = "USER_ENTERED",
        confidence: Double = 1.0,
        evidenceLevel: String = "CONFIRMED"
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.saveContextFact(
                ContextFact(
                    key = key,
                    value = value,
                    source = source,
                    confidence = confidence,
                    createdAt = now,
                    updatedAt = now,
                    expiresAt = 0L,
                    userConfirmed = true,
                    evidenceLevel = evidenceLevel
                )
            )
        }
    }

    /** User accepted an ML suggestion as-is. Records feedback; no invalidation needed. */
    fun confirmMlSuggestion(
        merchant: String,
        smsText: String,
        suggestedType: String,
        suggestedCategory: String,
        confidence: Double,
        source: String
    ) {
        viewModelScope.launch {
            repository.recordModelFeedback(
                ModelFeedback(
                    merchant = merchant,
                    smsText = smsText,
                    suggestedType = suggestedType,
                    suggestedCategory = suggestedCategory,
                    suggestedConfidence = confidence,
                    suggestedSource = source,
                    finalType = suggestedType,
                    finalCategory = suggestedCategory,
                    accepted = true
                )
            )
        }
    }

    /**
     * User corrected an ML suggestion. Records feedback AND invalidates
     * dependent forecasts (spec §15: correction → recalculate cached results).
     * Conservative: any type/category correction bumps the version — cheap
     * recompute beats a stale band shown as truth.
     */
    fun correctMlSuggestion(
        merchant: String,
        smsText: String,
        suggestedType: String,
        suggestedCategory: String,
        confidence: Double,
        source: String,
        finalType: String,
        finalCategory: String
    ) {
        viewModelScope.launch {
            repository.recordModelFeedback(
                ModelFeedback(
                    merchant = merchant,
                    smsText = smsText,
                    suggestedType = suggestedType,
                    suggestedCategory = suggestedCategory,
                    suggestedConfidence = confidence,
                    suggestedSource = source,
                    finalType = finalType,
                    finalCategory = finalCategory,
                    accepted = false
                )
            )
            if (suggestedType != finalType || suggestedCategory != finalCategory) {
                _forecastVersion.value = _forecastVersion.value + 1
            }
        }
    }

    fun invalidateForecasts() {
        _forecastVersion.value = _forecastVersion.value + 1
    }

    // Category rules: user-defined keyword → category overrides.
    fun addCategoryRule(keyword: String, category: String) {
        val kw = keyword.trim()
        if (kw.isEmpty() || category.trim().isEmpty()) return
        viewModelScope.launch {
            repository.insertCategoryRule(CategoryRule(keyword = kw, category = category.trim()))
        }
    }

    fun deleteCategoryRule(id: String) {
        viewModelScope.launch { repository.deleteCategoryRule(id) }
    }

    fun toggleCategoryRule(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setCategoryRuleEnabled(id, enabled) }
    }


    // UI actions
    // date/note have defaults so meal/kitchen callers keep logging "now";
    // QuickAdd passes an explicit date (back-dating) and note.
    fun addManualTransaction(
        amount: Double,
        type: TransactionType,
        category: String,
        merchant: String,
        method: PaymentMethod,
        date: Long = System.currentTimeMillis(),
        note: String = ""
    ) {
        viewModelScope.launch {
            val tx = Transaction(
                amount = amount,
                type = type,
                category = category,
                merchant = merchant,
                description = "Manual Input Record Entry",
                paymentMethod = method,
                source = TransactionSource.MANUAL,
                dateTimestamp = date,
                notes = note,
                createdAt = date
            )
            repository.insertTransaction(tx)
            _transactionEvents.tryEmit(tx)
        }
    }


    fun parseAndProcessNlp() {
        val parsed = NaturalLanguageParser.parse(nlpInputText.value)
        extractedNlpTransaction.value = parsed
    }


    fun commitExtractedNlp() {
        val tx = extractedNlpTransaction.value ?: return
        viewModelScope.launch {
            val inserted = Transaction(
                amount = tx.amount,
                type = tx.type,
                category = tx.category,
                merchant = tx.merchant,
                description = tx.rawText,
                paymentMethod = tx.paymentMethod,
                source = TransactionSource.NLP,
                dateTimestamp = tx.dateTimestamp
            )
            repository.insertTransaction(inserted)
            _transactionEvents.tryEmit(inserted)
            extractedNlpTransaction.value = null
            nlpInputText.value = ""
        }
    }


    sealed interface Undoable {
        data class Approved(val pending: PendingTransaction, val txId: String) : Undoable
        data class Rejected(val pending: PendingTransaction) : Undoable
        data class Deleted(val tx: Transaction) : Undoable
    }

    private var lastUndo: Undoable? = null


    fun approvePending(pending: PendingTransaction, finalCategory: String) {
        viewModelScope.launch {
            val tx = repository.approvePendingTransaction(pending, finalCategory)
            lastUndo = Undoable.Approved(pending, tx.id)
            _transactionEvents.tryEmit(tx)
        }
    }


    // Bulk confirm for a full inbox scan: every pending SMS enters the
    // ledger with its own suggested category. One tap, no per-item typing.
    fun approveAllPending() {
        viewModelScope.launch {
            pendingTransactions.value.forEach { repository.approvePendingTransaction(it, it.category) }
        }
    }


    fun rejectPending(pending: PendingTransaction) {
        viewModelScope.launch {
            repository.rejectPendingTransaction(pending.id)
            lastUndo = Undoable.Rejected(pending)
        }
    }

    // Rhythm hypotheses: confirm, dismiss, upsert.
    fun confirmAllRhythms() {
        viewModelScope.launch {
            val confirmed = repository.confirmedRhythms.first()
            confirmed.forEach { repository.confirmRhythm(it.id) }
        }
    }
    fun upsertRhythm(rhythm: UserRhythm) = viewModelScope.launch {
        repository.upsertRhythm(rhythm)
    }
    fun upsertRhythms(rhythms: List<UserRhythm>) = viewModelScope.launch {
        repository.upsertRhythms(rhythms)
    }
    fun dismissRhythm(id: String) = viewModelScope.launch {
        repository.dismissRhythm(id)
    }


    fun deleteTransactionWithUndo(tx: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(tx.id)
            lastUndo = Undoable.Deleted(tx)
        }
    }


    // User-initiated duplicate clean (after any scan): drops every dupe row
    // except the newest per group. Reports how many went; 0 means clean.
    fun cleanDuplicateTransactions(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val groups = com.pesaflow.app.data.parsers.findDuplicateGroups(allTransactions.value)
            val ids = com.pesaflow.app.data.parsers.duplicateIdsToRemove(groups)
            ids.forEach { repository.deleteTransaction(it) }
            onDone(ids.size)
        }
    }

    // Inbox twin: drop dupe PENDING rows before they reach the ledger, so
    // "Confirm all" can never bake doubles in. Newest per group survives.
    fun cleanDuplicatePending(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val groups = com.pesaflow.app.data.parsers.findDuplicatePendingGroups(pendingTransactions.value)
            val ids = com.pesaflow.app.data.parsers.pendingIdsToRemove(groups)
            ids.forEach { repository.rejectPendingTransaction(it) }
            onDone(ids.size)
        }
    }


    // True edit: the row keeps its id (undo, source links, references survive).
    // The old replaceTransaction was delete+insert with a fresh UUID.
    fun editTransaction(tx: Transaction) {
        viewModelScope.launch { repository.updateTransaction(tx) }
    }


    fun replaceTransaction(oldId: String, tx: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(oldId)
            repository.insertTransaction(tx)
        }
    }


    fun undoLast() {
        val undone = lastUndo ?: return
        lastUndo = null
        viewModelScope.launch {
            when (undone) {
                is Undoable.Approved -> {
                    repository.deleteTransaction(undone.txId)
                    repository.insertPendingTransaction(undone.pending)
                }
                is Undoable.Rejected -> repository.insertPendingTransaction(undone.pending)
                is Undoable.Deleted -> repository.insertTransaction(undone.tx)
            }
        }
    }


    fun queueSharedTransaction(pending: PendingTransaction) {
        viewModelScope.launch { repository.insertPendingTransaction(pending) }
    }


    // Suspend variant for inbox scans that need to know inserted vs duplicate.
    suspend fun tryQueuePending(pending: PendingTransaction): Boolean =
        repository.insertPendingTransaction(pending)


    fun queueSharedText(text: String) {
        val parsed = NaturalLanguageParser.parse(text)
        if (parsed != null) {
            viewModelScope.launch { repository.insertPendingTransaction(parsed) }
        }
        // Unparseable text is ignored: no junk rows ever reach the ledger.
    }


    fun addBudget(category: String, limitAmount: Double, type: BudgetType, sharedWith: String = "") {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val (windowStart, windowEnd) = budgetWindow(type, now)
            repository.insertBudget(
                Budget(category = category, limitAmount = limitAmount, type = type, startTimestamp = windowStart, endTimestamp = windowEnd, sharedWith = sharedWith)
            )
        }
    }


    // Upsert: onboarding re-runs must UPDATE the same category+type row, never
    // stack duplicates â€” every reader takes firstOrNull, so dupes freeze stale values.
    // Single coroutine (no nested launch): delete-then-insert is atomic from the caller's view.
    fun upsertBudget(category: String, limitAmount: Double, type: BudgetType, sharedWith: String = "") {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val (windowStart, windowEnd) = budgetWindow(type, now)
            repository.budgets.first()
                .filter { it.type == type && it.category.equals(category, ignoreCase = true) }
                .forEach { repository.deleteBudget(it.id) }
            repository.insertBudget(
                Budget(category = category, limitAmount = limitAmount, type = type, startTimestamp = windowStart, endTimestamp = windowEnd, sharedWith = sharedWith)
            )
        }
    }


    // Opening money: pocket cash + monthly upkeep become real ledger INCOME rows
    // (guarded by merchant tag, so re-onboarding never double-logs). Tagged
    // OPENING: they count in the balance (cash is cash) but never in income
    // statistics, savings rates or verdicts â€” seeded cash was never earned.
    fun seedOpeningMoney(pocket: Double, monthlyUpkeep: Double) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = repository.allTransactions.first()
            if (pocket > 0 && existing.none { it.merchant == "Opening balance" && it.type == TransactionType.INCOME }) {
                repository.insertTransaction(
                    Transaction(
                        amount = pocket,
                        type = TransactionType.INCOME,
                        category = "Income",
                        dateTimestamp = now,
                        merchant = "Opening balance",
                        description = "Pocket cash from onboarding",
                        paymentMethod = PaymentMethod.CASH,
                        source = TransactionSource.OPENING
                    )
                )
            }
            if (monthlyUpkeep > 0 && existing.none { it.merchant == "Monthly upkeep" && it.type == TransactionType.INCOME }) {
                repository.insertTransaction(
                    Transaction(
                        amount = monthlyUpkeep,
                        type = TransactionType.INCOME,
                        category = "Income",
                        dateTimestamp = now,
                        merchant = "Monthly upkeep",
                        description = "Home/sponsor monthly upkeep, in hand",
                        paymentMethod = PaymentMethod.CASH,
                        source = TransactionSource.OPENING
                    )
                )
            }
        }
    }


    // Reconcile wizard: the ledger drifts from the real wallet when bulk SMS
    // land as expenses with no opening row (negative ledger vs positive
    // M-Pesa). One tap writes a single compensating OPENING row for the gap.
    // Idempotent per calendar day via the notes key — re-tapping never stacks.
    // Tagged OPENING: counts in the balance, never in income statistics.
    fun reconcileToWallet(walletCash: Double, onDone: (adjusted: Double) -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val dayStart = com.pesaflow.app.data.academic.dayStart(now)
            val key = "reconcile:$dayStart"
            val existing = repository.allTransactions.first()
            if (existing.any { it.notes == key }) {
                onDone(0.0)
                return@launch
            }
            val ledger = com.pesaflow.app.data.money.ledgerBalance(existing)
            val diff = walletCash - ledger
            if (kotlin.math.abs(diff) < 1.0) {
                onDone(0.0)
                return@launch
            }
            val tx = if (diff > 0) {
                Transaction(
                    amount = diff,
                    type = TransactionType.INCOME,
                    category = "Income",
                    dateTimestamp = now,
                    merchant = "Opening balance",
                    description = "Reconciled to wallet cash",
                    paymentMethod = PaymentMethod.CASH,
                    source = TransactionSource.OPENING,
                    notes = key
                )
            } else {
                Transaction(
                    amount = -diff,
                    type = TransactionType.EXPENSE,
                    category = "Other",
                    dateTimestamp = now,
                    merchant = "Opening adjustment",
                    description = "Reconciled to wallet cash",
                    paymentMethod = PaymentMethod.CASH,
                    source = TransactionSource.OPENING,
                    notes = key
                )
            }
            repository.insertTransaction(tx)
            _transactionEvents.tryEmit(tx)
            onDone(diff)
        }
    }


    fun shareBudget(id: String, names: String) {
        viewModelScope.launch { repository.updateBudgetShared(id, names) }
    }


    fun deleteBudget(id: String) {
        viewModelScope.launch { repository.deleteBudget(id) }
    }


    fun applyCalculatedBudgets(rows: List<Pair<String, Double>>, type: BudgetType) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val (windowStart, windowEnd) = budgetWindow(type, now)
            // Merge with manual budgets: only replace same-type budgets whose
            // category is in the new plan â€” hand-made ones for other
            // categories survive an Apply instead of being wiped.
            val incoming = rows.map { it.first.trim().lowercase() }.toSet()
            repository.budgets.first()
                .filter { it.type == type && incoming.contains(it.category.trim().lowercase()) }
                .forEach { repository.deleteBudget(it.id) }
            rows.forEach { (category, amount) ->
                if (amount > 0) {
                    repository.insertBudget(
                        Budget(
                            category = category,
                            limitAmount = amount,
                            type = type,
                            startTimestamp = windowStart,
                            endTimestamp = windowEnd
                        )
                    )
                }
            }
        }
    }


    fun addSavingsGoal(title: String, targetAmount: Double, daysFromNow: Int) {
        viewModelScope.launch {
            val target = System.currentTimeMillis() + daysFromNow.coerceAtLeast(1) * 24L * 60 * 60 * 1000
            repository.insertSavingsGoal(
                SavingsGoal(title = title, targetAmount = targetAmount, currentAmount = 0.0, targetTimestamp = target)
            )
        }
    }


    fun deleteSavingsGoal(id: String) {
        viewModelScope.launch { repository.deleteSavingsGoal(id) }
    }


    // Contribute logs a real SAVING ledger row AND bumps the goal, so net worth,
    // balance, reports and planners all move together. One tap = new money in.
    // The bump is an atomic SQL increment â€” concurrent taps can never lose an
    // update to a stale read-modify-write.
    fun contributeToSavingsGoal(goal: SavingsGoal, amount: Double) {
        viewModelScope.launch {
            if (amount <= 0) return@launch
            repository.insertTransaction(
                Transaction(
                    amount = amount,
                    type = TransactionType.SAVING,
                    category = "Savings",
                    dateTimestamp = System.currentTimeMillis(),
                    merchant = goal.title,
                    description = "Saved toward ${goal.title}",
                    paymentMethod = PaymentMethod.CASH
                )
            )
            repository.bumpGoal(goal.id, amount)
            // Goal reached? The bump is atomic SQL — read back and check.
            val updated = repository.getSavingsGoal(goal.id)
            if (updated != null && updated.currentAmount >= updated.targetAmount && goal.currentAmount < goal.targetAmount) {
                _goalEvents.tryEmit(GoalReached(updated))
            }
        }
    }


    // One-tap banking of a free-day dividend: the first goal wins; with no
    // goal yet a "Fare jar" is created first (ids are client UUIDs, so the
    // same object inserts and receives the contribution) so the tap never
    // dead-ends. Logs a real SAVING row via contributeToSavingsGoal.
    // Persisted in prefs so rotation can't lose the idempotency guard.
    fun bankDividend(amount: Double, goals: List<SavingsGoal>, goalTitle: String = "Fare jar", onBanked: (String) -> Unit = {}) {
        viewModelScope.launch {
            if (amount <= 0) return@launch
            val key = "banked_div_${goalTitle}"
            if (prefs().getBoolean(key, false)) return@launch
            val g = goals.firstOrNull() ?: SavingsGoal(
                title = goalTitle,
                targetAmount = amount * 4,
                targetTimestamp = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
            ).also { repository.insertSavingsGoal(it) }
            contributeToSavingsGoal(g, amount)
            prefs().edit().putBoolean(key, true).apply()
            onBanked(g.title)
        }
    }


    fun saveUniversityProfile(profile: UniversityProfile) {
        viewModelScope.launch { repository.saveUniversityProfile(profile) }
    }


    fun addBill(name: String, amount: Double, dueDate: Long, category: String, frequency: String) {
        viewModelScope.launch {
            repository.insertBill(
                Bill(name = name, amount = amount, dueDate = dueDate, category = category, frequency = frequency)
            )
            // Bills drive budgets: a recurring bill adjusts (never duplicates) its monthly budget
            if (frequency != "ONE_TIME" && category.isNotBlank()) {
                val existing = repository.budgets.first().firstOrNull {
                    it.type == BudgetType.MONTHLY && it.category.equals(category, ignoreCase = true)
                }
                if (existing == null) {
                    val now = System.currentTimeMillis()
                    val (windowStart, windowEnd) = budgetWindow(BudgetType.MONTHLY, now)
                    repository.insertBudget(
                        Budget(
                            category = category,
                            limitAmount = amount,
                            type = BudgetType.MONTHLY,
                            startTimestamp = windowStart,
                            endTimestamp = windowEnd
                        )
                    )
                } else if (amount > existing.limitAmount) {
                    repository.updateBudgetAmount(existing.id, amount)
                }
            }
        }
    }


    // Paying a bill moves real money: flag the bill AND write the ledger
    // expense, so the balance drops the day it's paid â€” not just a checkmark.
    // Idempotent via the notes key "bill:<id>": re-tapping never double-spends.
    fun markBillPaid(id: String) {
        viewModelScope.launch {
            val bill = repository.allBills.first().firstOrNull { it.id == id } ?: return@launch
            repository.markBillPaid(id)
            if (allTransactions.value.any { it.notes == "bill:$id" }) return@launch
            val tx = Transaction(
                amount = bill.amount,
                type = TransactionType.EXPENSE,
                category = bill.category,
                merchant = bill.name,
                description = "Bill payment",
                paymentMethod = PaymentMethod.MPESA,
                source = TransactionSource.MANUAL,
                dateTimestamp = System.currentTimeMillis(),
                notes = "bill:$id"
            )
            repository.insertTransaction(tx)
            _transactionEvents.tryEmit(tx)
        }
    }


    fun deleteBill(id: String) {
        viewModelScope.launch { repository.deleteBill(id) }
    }


    fun addDebt(person: String, amount: Double, dueDate: Long, description: String, direction: String = "THEY_OWE") {
        viewModelScope.launch {
            repository.insertDebt(
                Debt(person = person, amount = amount, dateBorrowed = System.currentTimeMillis(), direction = direction, dueDate = dueDate, description = description)
            )
        }
    }


    // Settling a debt moves money too: I_OWE paid back = money out (EXPENSE),
    // THEY_OWE collected = money in (INCOME). Idempotent via "debt:<id>".
    fun markDebtPaid(id: String) {
        viewModelScope.launch {
            val debt = repository.allDebts.first().firstOrNull { it.id == id } ?: return@launch
            repository.markDebtPaid(id)
            if (allTransactions.value.any { it.notes == "debt:$id" }) return@launch
            val iOwe = debt.direction == "I_OWE"
            val tx = Transaction(
                amount = debt.amount,
                type = if (iOwe) TransactionType.EXPENSE else TransactionType.INCOME,
                category = "Debt",
                merchant = debt.person,
                description = if (iOwe) "Debt repaid" else "Debt collected",
                paymentMethod = PaymentMethod.MPESA,
                source = TransactionSource.MANUAL,
                dateTimestamp = System.currentTimeMillis(),
                notes = "debt:$id"
            )
            repository.insertTransaction(tx)
            _transactionEvents.tryEmit(tx)
        }
    }


    fun deleteDebt(id: String) {
        viewModelScope.launch { repository.deleteDebt(id) }
    }


    fun deleteTransaction(id: String) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }


    fun hasUndo(): Boolean = lastUndo != null


    fun deleteAllTransactions() {
        viewModelScope.launch { repository.deleteAllTransactions() }
    }


    fun importTransactions(transactions: List<Transaction>) {
        viewModelScope.launch { repository.insertTransactions(transactions) }
    }


    fun restoreBackup(json: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val root = org.json.JSONObject(json)
                if (root.optInt("version", 0) != 1) {
                    onDone(false)
                    return@launch
                }
                fun arr(key: String) = root.optJSONArray(key) ?: org.json.JSONArray()
                val txs = mutableListOf<Transaction>()
                val ta = arr("transactions")
                for (i in 0 until ta.length()) {
                    val o = ta.getJSONObject(i)
                    txs.add(
                        Transaction(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            amount = o.optDouble("amount", 0.0),
                            type = try { TransactionType.valueOf(o.optString("type", "EXPENSE")) } catch (e: Exception) { TransactionType.EXPENSE },
                            category = o.optString("category", "Other"),
                            dateTimestamp = o.optLong("dateTimestamp", System.currentTimeMillis()),
                            merchant = o.optString("merchant", ""),
                            description = o.optString("description", ""),
                            paymentMethod = try { PaymentMethod.valueOf(o.optString("paymentMethod", "OTHER")) } catch (e: Exception) { PaymentMethod.OTHER },
                            source = try { TransactionSource.valueOf(o.optString("source", "MANUAL")) } catch (e: Exception) { TransactionSource.MANUAL },
                            sourceTransactionId = o.optString("sourceTransactionId").ifBlank { null }
                        )
                    )
                }
                repository.insertTransactions(txs)
                val ba = arr("budgets")
                for (i in 0 until ba.length()) {
                    val o = ba.getJSONObject(i)
                    repository.insertBudget(
                        Budget(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            category = o.optString("category", "Other"),
                            limitAmount = o.optDouble("limitAmount", 0.0),
                            type = try { BudgetType.valueOf(o.optString("type", "MONTHLY")) } catch (e: Exception) { BudgetType.MONTHLY },
                            startTimestamp = o.optLong("startTimestamp", 0L),
                            endTimestamp = o.optLong("endTimestamp", 0L),
                            sharedWith = o.optString("sharedWith", "")
                        )
                    )
                }
                val ga = arr("goals")
                for (i in 0 until ga.length()) {
                    val o = ga.getJSONObject(i)
                    repository.insertSavingsGoal(
                        SavingsGoal(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            title = o.optString("title", "Goal"),
                            targetAmount = o.optDouble("targetAmount", 0.0),
                            currentAmount = o.optDouble("currentAmount", 0.0),
                            targetTimestamp = o.optLong("targetTimestamp", 0L)
                        )
                    )
                }
                val pa = arr("profile")
                if (pa.length() > 0) {
                    val o = pa.getJSONObject(0)
                    repository.saveUniversityProfile(
                        UniversityProfile(
                            universityName = o.optString("universityName", ""),
                            campus = o.optString("campus", ""),
                            currentSemester = o.optInt("currentSemester", 1),
                            academicYear = o.optString("academicYear", ""),
                            semesterStartTimestamp = o.optLong("semesterStartTimestamp", 0L),
                            semesterEndTimestamp = o.optLong("semesterEndTimestamp", 0L),
                            startingFunding = o.optDouble("startingFunding", 0.0),
                            helbExpected = o.optDouble("helbExpected", 0.0),
                            fundingSource = o.optString("fundingSource", "HELB"),
                            feesAmount = o.optDouble("feesAmount", 0.0),
                            feesDueDate = o.optLong("feesDueDate", 0L)
                        )
                    )
                }
                val la = arr("bills")
                for (i in 0 until la.length()) {
                    val o = la.getJSONObject(i)
                    repository.insertBill(
                        Bill(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            name = o.optString("name", "Bill"),
                            amount = o.optDouble("amount", 0.0),
                            dueDate = o.optLong("dueDate", 0L),
                            category = o.optString("category", "Other"),
                            frequency = o.optString("frequency", "ONE_TIME"),
                            status = o.optString("status", "UNPAID")
                        )
                    )
                }
                val da = arr("debts")
                for (i in 0 until da.length()) {
                    val o = da.getJSONObject(i)
                    repository.insertDebt(
                        Debt(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            person = o.optString("person", ""),
                            amount = o.optDouble("amount", 0.0),
                            dateBorrowed = o.optLong("dateBorrowed", 0L),
                            dueDate = o.optLong("dueDate", 0L),
                            description = o.optString("description", ""),
                            status = o.optString("status", "OWING")
                        )
                    )
                }
                val ma = arr("meals")
                for (i in 0 until ma.length()) {
                    val o = ma.getJSONObject(i)
                    repository.insertMealItem(
                        MealItem(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            name = o.optString("name", ""),
                            mealType = o.optString("mealType", "Lunch"),
                            price = o.optDouble("price", 0.0),
                            component = o.optString("component", "Complete"),
                            source = o.optString("source", "Buy")
                        )
                    )
                }
                val ca = arr("chamas")
                for (i in 0 until ca.length()) {
                    val o = ca.getJSONObject(i)
                    repository.insertChamaGroup(
                        ChamaGroup(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            name = o.optString("name", ""),
                            contribution = o.optDouble("contribution", 0.0),
                            members = o.optString("members", ""),
                            cycleDays = o.optInt("cycleDays", 30),
                            startTimestamp = o.optLong("startTimestamp", System.currentTimeMillis()),
                            paidCycles = o.optInt("paidCycles", 0)
                        )
                    )
                }
                onDone(true)
            } catch (e: Exception) {
                onDone(false)
            }
        }
    }


    fun addMealItem(name: String, mealType: String, price: Double, component: String = "Complete", source: String = "Buy") {
        viewModelScope.launch { repository.insertMealItem(MealItem(name = name, mealType = mealType, price = price, component = component, source = source)) }
    }


    fun deleteMealItem(id: String) {
        viewModelScope.launch { repository.deleteMealItem(id) }
    }


    fun addBelonging(name: String, category: String, estCost: Double, priority: Int, status: String = "NEED", notes: String = "") {
        viewModelScope.launch {
            repository.insertBelonging(
                Belonging(name = name.trim(), category = category, status = status, estCost = estCost, priority = priority, notes = notes.trim())
            )
        }
    }


    fun markBelonging(item: Belonging, status: String) {
        viewModelScope.launch { repository.updateBelonging(item.copy(status = status)) }
    }


    fun deleteBelonging(id: String) {
        viewModelScope.launch { repository.deleteBelonging(id) }
    }


    fun addKitchenStock(name: String, unit: String, qtyFull: Double, qtyLeft: Double, dailyUse: Double, pricePerPack: Double, expiryTimestamp: Long = 0L, eatByDays: Int = 0) {
        viewModelScope.launch {
            repository.insertKitchenStock(
                KitchenStock(name = name.trim(), unit = unit.trim().ifEmpty { "kg" }, qtyFull = qtyFull, qtyLeft = qtyLeft, dailyUse = dailyUse, pricePerPack = pricePerPack, expiryTimestamp = expiryTimestamp, eatByDays = eatByDays)
            )
        }
    }


    fun logStockUse(item: KitchenStock, days: Double = 1.0) {
        viewModelScope.launch {
            repository.updateKitchenStock(
                item.copy(qtyLeft = (item.qtyLeft - item.dailyUse * days).coerceAtLeast(0.0), updatedAt = System.currentTimeMillis())
            )
        }
    }


    // One gear: "I ate this" writes the Food expense AND burns one
    // dailyUse of the matching stock pile (Cook meals). Menu, stock,
    // ledger, budgets and insights all move together.
    fun logMealEaten(item: MealItem) {
        viewModelScope.launch {
            repository.insertTransaction(
                Transaction(
                    amount = item.price,
                    type = TransactionType.EXPENSE,
                    category = "Food",
                    merchant = item.name,
                    description = "Ate: " + item.name + " (" + item.source + ")",
                    paymentMethod = PaymentMethod.CASH,
                    source = TransactionSource.MANUAL,
                    dateTimestamp = System.currentTimeMillis()
                )
            )
            if (item.source == "Cook") {
                val pile = com.pesaflow.app.data.parsers.matchStockForMeal(
                    item.name, repository.allKitchenStock.first()
                )
                if (pile != null) {
                    repository.updateKitchenStock(
                        pile.copy(
                            qtyLeft = (pile.qtyLeft - pile.dailyUse).coerceAtLeast(0.0),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }


    fun setStockPriority(item: KitchenStock, days: Int) {
        viewModelScope.launch {
            repository.updateKitchenStock(item.copy(eatByDays = days, updatedAt = System.currentTimeMillis()))
        }
    }


    fun setStockExpiry(item: KitchenStock, timestamp: Long) {
        viewModelScope.launch {
            repository.updateKitchenStock(item.copy(expiryTimestamp = timestamp, updatedAt = System.currentTimeMillis()))
        }
    }


    fun restockKitchen(item: KitchenStock) {
        viewModelScope.launch {
            repository.updateKitchenStock(item.copy(qtyLeft = item.qtyFull, updatedAt = System.currentTimeMillis()))
        }
    }


    fun deleteKitchenStock(id: String) {
        viewModelScope.launch { repository.deleteKitchenStock(id) }
    }


    fun clearMealItems() {
        viewModelScope.launch { repository.clearMealItems() }
    }


    val chamaGroups: StateFlow<List<ChamaGroup>> = repository.allChamaGroups.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )


    fun addChamaGroup(name: String, contribution: Double, members: String, cycleDays: Int) {
        viewModelScope.launch {
            repository.insertChamaGroup(
                ChamaGroup(name = name, contribution = contribution, members = members, cycleDays = cycleDays)
            )
        }
    }


    fun deleteChamaGroup(id: String) {
        viewModelScope.launch { repository.deleteChamaGroup(id) }
    }


    fun advanceChama(group: ChamaGroup) {
        viewModelScope.launch { repository.advanceChama(group.id, group.paidCycles + 1) }
    }


    // Sample data is one-shot and clearly fake: guarded by a prefs flag AND a
    // non-empty ledger check, so double-taps and re-entry can never pollute
    // real records or stack duplicate budgets. Watermarked as SAMPLE so
    // balances stay real but rates, verdicts and averages stay honest.
    fun seedSampleData() {
        viewModelScope.launch {
            val p = prefs()
            if (p.getBoolean("sample_seeded", false)) return@launch
            if (repository.allTransactions.first().isNotEmpty() || repository.budgets.first().isNotEmpty()) {
                p.edit().putBoolean("sample_seeded", true).apply()
                return@launch
            }
            val now = System.currentTimeMillis()
            val day = 24L * 60 * 60 * 1000
            listOf(
                Transaction(amount = 20000.0, type = TransactionType.INCOME, category = "Salary", dateTimestamp = now - 2 * day, merchant = "HELB", description = "Sample semester upkeep", paymentMethod = PaymentMethod.MPESA, source = TransactionSource.SAMPLE),
                Transaction(amount = 250.0, type = TransactionType.EXPENSE, category = "Food", dateTimestamp = now - 2 * day, merchant = "Kibanda", description = "Sample lunch", paymentMethod = PaymentMethod.CASH, source = TransactionSource.SAMPLE),
                Transaction(amount = 100.0, type = TransactionType.EXPENSE, category = "Transport", dateTimestamp = now - 1 * day, merchant = "Matatu Stage", description = "Sample fare", paymentMethod = PaymentMethod.CASH, source = TransactionSource.SAMPLE),
                Transaction(amount = 150.0, type = TransactionType.EXPENSE, category = "Airtime", dateTimestamp = now - 1 * day, merchant = "Safaricom", description = "Sample bundles", paymentMethod = PaymentMethod.MPESA, source = TransactionSource.SAMPLE),
                Transaction(amount = 8000.0, type = TransactionType.EXPENSE, category = "Rent", dateTimestamp = now - 5 * day, merchant = "Hostel Caretaker", description = "Sample rent", paymentMethod = PaymentMethod.MPESA, source = TransactionSource.SAMPLE)
            ).forEach { repository.insertTransaction(it) }
            val (sampleWindowStart, sampleWindowEnd) = budgetWindow(BudgetType.MONTHLY, now)
            repository.insertBudget(Budget(category = "ALL", limitAmount = 25000.0, type = BudgetType.MONTHLY, startTimestamp = sampleWindowStart, endTimestamp = sampleWindowEnd))
            repository.insertBudget(Budget(category = "Food", limitAmount = 6000.0, type = BudgetType.MONTHLY, startTimestamp = sampleWindowStart, endTimestamp = sampleWindowEnd))
            repository.insertSavingsGoal(SavingsGoal(title = "Laptop", targetAmount = 80000.0, currentAmount = 5000.0, targetTimestamp = now + 180 * day))
            p.edit().putBoolean("sample_seeded", true).apply()
        }
    }


    // Wipe every sample row (source = SAMPLE) from the ledger so the
    // ledger can go fully real. Keeps budgets, goals and prefs intact.
    fun wipeSampleData() {
        viewModelScope.launch {
            repository.deleteBySource(TransactionSource.SAMPLE.name)
            prefs().edit().putBoolean("sample_seeded", false).apply()
        }
    }


    fun setLanguage(lang: AppLanguage) {
        currentLanguage.value = lang
        prefs().edit().putString("app_language", lang.name).apply()
    }


    fun setUserName(name: String) {
        userName.value = name.trim()
        prefs().edit().putString("user_name", userName.value).apply()
    }


    fun setNickname(name: String) {
        nickname.value = name.trim()
        prefs().edit().putString("user_nickname", nickname.value).apply()
    }


    // Daily voice uses the nickname; extreme warnings use the full name to sound serious.
    fun displayName(): String = nickname.value.ifBlank { userName.value }

    fun seriousName(): String = userName.value.ifBlank { nickname.value }


    // Onboarding "tell us about you" answers, kept as JSON for future
    // personalization (spending baselines, first-run advice).
    fun saveOnboardingAnswers(json: String) {
        prefs().edit().putString("onboarding_answers", json).apply()
    }


    fun getOnboardingAnswers(): String =
        prefs().getString("onboarding_answers", "").orEmpty()


    fun setThemeMode(mode: AppTheme) {
        themeMode.value = mode
        prefs().edit().putString("app_theme", mode.name).apply()
    }


    fun setHideBalances(hidden: Boolean) {
        hideBalances.value = hidden
        prefs().edit().putBoolean("hide_balances", hidden).apply()
    }


    fun toggleSection(key: String) {
        val updated = hiddenSections.value.toMutableSet()
        if (!updated.add(key)) updated.remove(key)
        hiddenSections.value = updated
        prefs().edit().putString("hidden_sections", updated.joinToString(",")).apply()
    }


    fun setTrainingOptIn(enabled: Boolean) {
        trainingOptIn.value = enabled
        prefs().edit().putBoolean("training_opt_in", enabled).apply()
    }

    /**
     * Anonymized training export (spec §18). Returns null unless opted in —
     * callers must treat null as "no export", never fall back to raw rows.
     */
    suspend fun exportTrainingJson(): String? {
        if (!trainingOptIn.value) return null
        val rows = com.pesaflow.app.data.ml.TrainingExport.toRows(modelFeedback.value)
        return com.pesaflow.app.data.ml.TrainingExport.toJson(rows)
    }

    /**
     * Versioned weight import (spec §18 retrain path). Returns false on bad
     * blob and keeps bundled weights — never half-loads one classifier.
     */
    fun importModelWeights(typeBlob: String, categoryBlob: String): Boolean {
        return try {
            com.pesaflow.app.data.ml.TransactionClassifier.loadWeights(typeBlob)
            com.pesaflow.app.data.ml.CategoryClassifier.loadWeights(categoryBlob)
            invalidateForecasts()
            prefs().edit().putLong("model_weights_imported_at", System.currentTimeMillis()).apply()
            true
        } catch (e: Exception) {
            false
        }
    }


    // Localized strings â€” single source is AppCopy (dashboard section); this
    // function stays as the compatibility delegate so every caller upgrades at once.
    fun getLocalizedString(key: String, arg: String = "", balance: Double = availableBalance.value): String {
        val lang = currentLanguage.value
        if (key == "dashboard_status") {
            return com.pesaflow.app.ui.language.dashStatus(displayName(), seriousName(), balance, lang)
        }
        return com.pesaflow.app.ui.language.dashKey(key, arg, lang)
    }


    private fun isCurrentMonth(timestamp: Long): Boolean {
        val cal = java.util.Calendar.getInstance()
        val txCal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(java.util.Calendar.YEAR) == txCal.get(java.util.Calendar.YEAR) &&
            cal.get(java.util.Calendar.MONTH) == txCal.get(java.util.Calendar.MONTH)
    }
}
