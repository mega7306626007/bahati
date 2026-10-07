package com.pesaflow.app.data.ml

/**
 * MlEngine — single entry point for all on-device intelligence.
 *
 * ARCHITECTURE BOUNDARY (Section 25): this engine READS transaction data
 * passed in by callers and returns predictions with confidence + source.
 * It never touches Room, never mutates the ledger, never invents balances.
 * Every financial number shown to the user must come from a deterministic
 * calculation; ML output is suggestion-only and requires user confirmation
 * before any write.
 *
 * Components:
 * - TransactionClassifier (TYPE_CLF v1, trained TF-IDF+NB)
 * - CategoryClassifier (CAT_CLF v1 dict + trained TF-IDF+NB)
 * - MerchantDictionary (BUNDLED_DATASET v1, rules + fuzzy)
 * - AnomalyDetector (STAT, MAD z-score)
 * - RecurringDetector (RULES, interval + amount CV)
 * - Forecaster (STAT, trailing mean ± sd)
 * - IntentClassifier (INTENT_CLF v1, trained TF-IDF+NB + regex slots)
 */
object MlEngine {
    data class CategorySuggestion(
        val category: String,
        val confidence: Double,
        val source: String,
        val needsConfirmation: Boolean = true
    )

    data class TypeSuggestion(val type: String, val confidence: Double, val source: String)

    fun suggestType(smsText: String): TypeSuggestion {
        val top = TransactionClassifier.predictTop(smsText)
        return TypeSuggestion(
            type = top?.label ?: "EXPENSE",
            confidence = top?.probability ?: 0.0,
            source = "TYPE_CLF_v1"
        )
    }

    fun suggestCategory(merchant: String, smsText: String): CategorySuggestion {
        val (cat, conf, src) = CategoryClassifier.classify(merchant, smsText)
        return CategorySuggestion(cat, conf, src, needsConfirmation = conf < 0.90)
    }

    fun checkAnomaly(amount: Double, categoryHistory: List<Double>) =
        AnomalyDetector.verdict(amount, categoryHistory)

    fun findRecurring(transactions: List<Pair<String, Pair<Double, Long>>>) =
        RecurringDetector.detect(transactions)

    fun forecastWeek(dailyTotals: List<Double>) =
        Forecaster.daily(dailyTotals, 7)

    /** 30-day envelope outlook for budget advice (range, never a promise). */
    fun forecastMonth(dailyTotals: List<Double>) =
        Forecaster.daily(dailyTotals, 30)

    fun understand(text: String) = IntentClassifier.classify(text)

    fun projectGoal(title: String, target: Double, current: Double, weekly: List<Double>, obligations: Double = 0.0) =
        GoalForecaster.project(title, target, current, weekly, obligations)

    fun learnPreference(key: String, modalValue: String, occurrences: Int, distinctDays: Int) =
        PreferenceLearner.fromBehavior(key, modalValue, occurrences, distinctDays)

    fun rankOptions(options: List<RecommendationRanker.Option>, budgetKes: Double, maxDistanceM: Double? = null) =
        RecommendationRanker.rank(options, budgetKes, maxDistanceM)

    fun affordableMeals(budgetKes: Double) = LocalKnowledge.mealBudgetOptions(budgetKes)

    fun findPaybill(query: String) = LocalKnowledge.paybills.filter {
        it.owner.lowercase().contains(query.lowercase()) || it.number.contains(query)
    }

    /** Model inventory for diagnostics screen. */
    fun modelCards(): List<String> = listOf(
        "TYPE_CLF v2: TF-IDF uni+bigram + NB, ${TransactionClassifier.trainingData.size} labelled (${TransactionClassifier.trainingData.count { it.synthetic }} synthetic), 11 types",
        "CAT_CLF v2: dict-first + TF-IDF uni+bigram + NB, ${CategoryClassifier.trainingData.size} labelled (${CategoryClassifier.trainingData.count { it.synthetic }} synthetic)",
        "MERCHANT_DICT v2: ${MerchantDictionary.entries.size} canonical merchants incl verified paybills",
        "LOCAL_KNOWLEDGE v1: ${LocalKnowledge.universities.size} universities, ${LocalKnowledge.foodPrices.size} food prices, ${LocalKnowledge.fareBands.size} fare bands, ${LocalKnowledge.paybills.size} paybills",
        "ANOMALY STAT v1: MAD z-score, no training",
        "RECURRING RULES v1: interval+amount CV, no training",
        "FORECAST STAT v1: trailing mean range, no training",
        "GOAL_FORECAST v1: contribution trajectory, no training",
        "PREF_LEARN v1: evidence-graded, no neural net",
        "RANKER v1: weighted multi-criteria, hard constraints first",
        "INTENT_CLF v2: TF-IDF uni+bigram + NB + regex slots"
    )
}
