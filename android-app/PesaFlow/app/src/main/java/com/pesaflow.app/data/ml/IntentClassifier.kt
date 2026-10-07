package com.pesaflow.app.data.ml

/**
 * PesaBuddy intent classifier: 14 intents, TF-IDF + NB, lazy-trained.
 * Extracts entities (amount, category, period) with regex slot-filling —
 * deterministic rules on top of the statistical intent label.
 *
 * Intents: BALANCE_QUERY, AFFORDABILITY_QUERY, SPENDING_ANALYSIS,
 * TRANSPORT_COST_QUERY, FOOD_QUERY, BUDGET_QUERY, GOAL_QUERY,
 * TRANSACTION_SEARCH, FINANCIAL_CONSTRAINT_UPDATE, PREFERENCE_UPDATE,
 * SCENARIO_QUERY, GREETING, CORRECTION, UNKNOWN.
 *
 * Model card: INTENT_CLF v1. Low confidence (<0.45) -> ask clarifying
 * question, never guess a financial answer.
 */
object IntentClassifier {
    const val CONFIDENCE_THRESHOLD = 0.45
    private val classifier = TfIdfClassifier()
    private var ready = false

    data class Slots(
        val amount: Double? = null,
        val category: String? = null,
        val period: String? = null,
        val destination: String? = null
    )
    data class Result(val intent: String, val confidence: Double, val slots: Slots, val needsClarification: Boolean)

    private val trainingData: List<TfIdfClassifier.Labeled> = listOf(
        // BALANCE_QUERY
        TfIdfClassifier.Labeled("how much money do I have balance yangu", "BALANCE_QUERY"),
        TfIdfClassifier.Labeled("what is my balance today", "BALANCE_QUERY"),
        TfIdfClassifier.Labeled("pesa ngapi iko", "BALANCE_QUERY", true),
        TfIdfClassifier.Labeled("show my available money", "BALANCE_QUERY", true),
        TfIdfClassifier.Labeled("niko na ngapi saa hii", "BALANCE_QUERY", true),
        TfIdfClassifier.Labeled("safe to spend how much", "BALANCE_QUERY", true),
        // AFFORDABILITY_QUERY
        TfIdfClassifier.Labeled("can I afford lunch for 200", "AFFORDABILITY_QUERY"),
        TfIdfClassifier.Labeled("can I spend 300 on lunch today", "AFFORDABILITY_QUERY"),
        TfIdfClassifier.Labeled("is 500 for shopping okay", "AFFORDABILITY_QUERY", true),
        TfIdfClassifier.Labeled("naeza buy shoes 1500", "AFFORDABILITY_QUERY", true),
        TfIdfClassifier.Labeled("can I afford a laptop this semester", "AFFORDABILITY_QUERY"),
        TfIdfClassifier.Labeled("is it safe to spend 1000 on clothes", "AFFORDABILITY_QUERY", true),
        TfIdfClassifier.Labeled("nisaidie na 200 ya food leo", "AFFORDABILITY_QUERY", true),
        // SPENDING_ANALYSIS
        TfIdfClassifier.Labeled("how much did I spend this week", "SPENDING_ANALYSIS"),
        TfIdfClassifier.Labeled("where did my money go", "SPENDING_ANALYSIS"),
        TfIdfClassifier.Labeled("show my food spending this month", "SPENDING_ANALYSIS"),
        TfIdfClassifier.Labeled("pesa yangu inaenda wapi", "SPENDING_ANALYSIS", true),
        TfIdfClassifier.Labeled("breakdown ya spending yangu", "SPENDING_ANALYSIS", true),
        TfIdfClassifier.Labeled("which category costs me most", "SPENDING_ANALYSIS", true),
        TfIdfClassifier.Labeled("how much transport last month", "SPENDING_ANALYSIS", true),
        // TRANSPORT_COST_QUERY
        TfIdfClassifier.Labeled("how much do I spend getting to school", "TRANSPORT_COST_QUERY"),
        TfIdfClassifier.Labeled("fare yangu ya wiki ni ngapi", "TRANSPORT_COST_QUERY", true),
        TfIdfClassifier.Labeled("matatu cost per month", "TRANSPORT_COST_QUERY"),
        TfIdfClassifier.Labeled("nauli ya kuenda campus na kurudi", "TRANSPORT_COST_QUERY", true),
        TfIdfClassifier.Labeled("weekly fare estimate five days", "TRANSPORT_COST_QUERY", true),
        // FOOD_QUERY
        TfIdfClassifier.Labeled("where should I eat near campus", "FOOD_QUERY"),
        TfIdfClassifier.Labeled("cheap lunch options 150", "FOOD_QUERY", true),
        TfIdfClassifier.Labeled("what is my food budget", "FOOD_QUERY"),
        TfIdfClassifier.Labeled("chakula cheap wapi karibu na chuo", "FOOD_QUERY", true),
        TfIdfClassifier.Labeled("smocha ama mess which is cheaper", "FOOD_QUERY", true),
        TfIdfClassifier.Labeled("what fits my 180 lunch budget", "FOOD_QUERY", true),
        // BUDGET_QUERY
        TfIdfClassifier.Labeled("set budget for transport 800", "BUDGET_QUERY"),
        TfIdfClassifier.Labeled("change my food budget", "BUDGET_QUERY", true),
        TfIdfClassifier.Labeled("niongezee budget ya shopping", "BUDGET_QUERY", true),
        TfIdfClassifier.Labeled("am I over budget on airtime", "BUDGET_QUERY", true),
        TfIdfClassifier.Labeled("create monthly budget 5000 food", "BUDGET_QUERY", true),
        // GOAL_QUERY
        TfIdfClassifier.Labeled("how is my laptop goal going", "GOAL_QUERY"),
        TfIdfClassifier.Labeled("will I reach my savings target", "GOAL_QUERY", true),
        TfIdfClassifier.Labeled("lengo langu la savings liko wapi", "GOAL_QUERY", true),
        TfIdfClassifier.Labeled("how long until emergency fund full", "GOAL_QUERY", true),
        // TRANSACTION_SEARCH
        TfIdfClassifier.Labeled("find my Naivas transaction yesterday", "TRANSACTION_SEARCH"),
        TfIdfClassifier.Labeled("show M-Pesa payments to KPLC", "TRANSACTION_SEARCH", true),
        TfIdfClassifier.Labeled("where is my 500 to mum", "TRANSACTION_SEARCH", true),
        TfIdfClassifier.Labeled("tafuta payment ya rent last month", "TRANSACTION_SEARCH", true),
        // FINANCIAL_CONSTRAINT_UPDATE
        TfIdfClassifier.Labeled("I have only 2000 left until HELB comes", "FINANCIAL_CONSTRAINT_UPDATE"),
        TfIdfClassifier.Labeled("my upkeep is delayed", "FINANCIAL_CONSTRAINT_UPDATE", true),
        TfIdfClassifier.Labeled("helb imechelewa sina pesa", "FINANCIAL_CONSTRAINT_UPDATE", true),
        TfIdfClassifier.Labeled("income yangu itachelewa wiki moja", "FINANCIAL_CONSTRAINT_UPDATE", true),
        // PREFERENCE_UPDATE
        TfIdfClassifier.Labeled("I usually walk I only take matatu when it rains", "PREFERENCE_UPDATE"),
        TfIdfClassifier.Labeled("I cook three days a week", "PREFERENCE_UPDATE", true),
        TfIdfClassifier.Labeled("napenda short explanations", "PREFERENCE_UPDATE", true),
        TfIdfClassifier.Labeled("I prefer cheap meals near gate", "PREFERENCE_UPDATE", true),
        TfIdfClassifier.Labeled("notify me less often", "PREFERENCE_UPDATE", true),
        // SCENARIO_QUERY
        TfIdfClassifier.Labeled("what if I cook three days per week", "SCENARIO_QUERY"),
        TfIdfClassifier.Labeled("can I afford a laptop this semester", "SCENARIO_QUERY"),
        TfIdfClassifier.Labeled("what if my income is delayed", "SCENARIO_QUERY", true),
        TfIdfClassifier.Labeled("what if I walk when practical", "SCENARIO_QUERY", true),
        TfIdfClassifier.Labeled("rent ikipanda 1000 savings itakuaje", "SCENARIO_QUERY", true),
        TfIdfClassifier.Labeled("what if I spend 100 less on lunch", "SCENARIO_QUERY", true),
        // GREETING
        TfIdfClassifier.Labeled("hello hi mambo", "GREETING"),
        TfIdfClassifier.Labeled("niaje pesa buddy", "GREETING", true),
        TfIdfClassifier.Labeled("good morning", "GREETING", true),
        // CORRECTION
        TfIdfClassifier.Labeled("that changed I no longer live near campus", "CORRECTION"),
        TfIdfClassifier.Labeled("no that was food not transport", "CORRECTION", true),
        TfIdfClassifier.Labeled("sihami tena hapo nilihama", "CORRECTION", true),
        TfIdfClassifier.Labeled("wrong category fix it", "CORRECTION", true)
    )

    private fun ensureTrained() {
        if (!ready) {
            classifier.train(trainingData)
            ready = true
        }
    }

    private val amountRegex = Regex("(\\d[\\d,]*\\.?\\d*)")
    private val categoryWords = mapOf(
        "FOOD" to listOf("lunch", "food", "dinner", "supper", "breakfast", "chakula"),
        "TRANSPORT" to listOf("transport", "fare", "matatu", "boda", "nauli"),
        "SHOPPING" to listOf("shopping", "groceries", "nunua"),
        "AIRTIME" to listOf("airtime", "credit"),
        "DATA" to listOf("data", "bundles", "wifi"),
        "RENT" to listOf("rent", "hostel", "nyumba")
    )
    private val periodWords = mapOf(
        "TODAY" to listOf("today", "leo"),
        "WEEK" to listOf("week", "wiki"),
        "MONTH" to listOf("month", "mwezi"),
        "SEMESTER" to listOf("semester")
    )

    fun classify(text: String): Result {
        ensureTrained()
        val top = classifier.predict(text).firstOrNull()
        val q = text.lowercase()
        val amount = amountRegex.find(q.replace(",", ""))?.groupValues?.get(1)?.toDoubleOrNull()
        val category = categoryWords.entries.firstOrNull { (_, ws) -> ws.any { q.contains(it) } }?.key
        val period = periodWords.entries.firstOrNull { (_, ws) -> ws.any { q.contains(it) } }?.key
        val destination = if (q.contains("school") || q.contains("campus") || q.contains("shule")) "USER_INSTITUTION" else null
        val intent = top?.label ?: "UNKNOWN"
        val conf = top?.probability ?: 0.0
        return Result(intent, conf, Slots(amount, category, period, destination), conf < CONFIDENCE_THRESHOLD)
    }
}
