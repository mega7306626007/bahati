package com.pesaflow.app.ui.dashboard

data class BuddyMemoryState(
    val lastTopic: String? = null,
    val previousTopic: String? = null,
    val entities: Map<String, String> = emptyMap(),
    val timeWindow: String? = null,
    val turnCount: Int = 0,
    val lastUserInput: String? = null,
    val lastResponse: String? = null
)

object BuddyMemory {
    @Volatile
    private var state = BuddyMemoryState()

    @Synchronized
    fun snapshot(): BuddyMemoryState = state.copy(entities = state.entities.toMap())

    @Synchronized
    fun clear() {
        state = BuddyMemoryState()
    }

    @Synchronized
    fun remember(
        userInput: String,
        response: String,
        topic: String?,
        entities: Map<String, String> = emptyMap(),
        timeWindow: String? = null
    ) {
        state = state.copy(
            previousTopic = state.lastTopic,
            lastTopic = topic ?: state.lastTopic,
            entities = entities.toMap(),
            timeWindow = timeWindow ?: state.timeWindow,
            turnCount = state.turnCount + 1,
            lastUserInput = userInput,
            lastResponse = response
        )
    }
}

object BuddyFollowUpResolver {
    private val topicTerms = linkedMapOf(
        "food" to listOf("food", "chakula", "kula", "lunch", "supper", "breakfast", "munch", "kibanda"),
        "transport" to listOf("transport", "fare", "nauli", "matatu", "boda", "mathree"),
        "budget" to listOf("budget", "bajeti"),
        "bills" to listOf("bill", "bills", "due", "lipia"),
        "debts" to listOf("debt", "deni", "madeni", "owe", "borrow"),
        "savings" to listOf("saving", "savings", "saved", "akiba"),
        "income" to listOf("income", "salary", "mshahara", "paycheck"),
        "balance" to listOf("balance", "baki", "salio", "remaining"),
        "spending" to listOf("spend", "spending", "expense", "expenses", "matumizi", "gharama")
    )

    private val timeTerms = linkedMapOf(
        "TODAY" to listOf("today", "leo"),
        "YESTERDAY" to listOf("yesterday", "jana"),
        "THIS_WEEK" to listOf("this week", "week", "wiki", "7 days"),
        "LAST_WEEK" to listOf("last week", "wiki iliyopita"),
        "THIS_MONTH" to listOf("this month", "mwezi huu"),
        "LAST_MONTH" to listOf("last month", "mwezi uliopita")
    )

    private val referencePattern =
        Regex("^(and|na|what about|how about|hii|hiyo|it|that|this|same)\\b")

    fun detectTopic(text: String): String? {
        val q = text.lowercase()
        return topicTerms.entries.firstOrNull { (_, terms) -> terms.any { q.contains(it) } }?.key
    }

    fun detectTimeWindow(text: String): String? {
        val q = text.lowercase()
        return timeTerms.entries.firstOrNull { (_, terms) -> terms.any { q.contains(it) } }?.key
    }

    fun extractEntities(text: String): Map<String, String> {
        val q = text.lowercase()
        val result = linkedMapOf<String, String>()
        detectTopic(q)?.let { result["topic"] = it }
        detectTimeWindow(q)?.let { result["timeWindow"] = it }
        Regex("(\\d[\\d,]*)(?:\\.\\d+)?").find(q)?.value
            ?.replace(",", "")
            ?.let { result["amount"] = it }
        return result
    }

    fun resolve(raw: String, memory: BuddyMemoryState): String? {
        val q = raw.trim().lowercase()
        val topic = memory.lastTopic ?: return null
        if (topic !in topicTerms.keys && topic !in setOf("today", "yesterday", "week", "summary")) return null

        val explicitTopic = detectTopic(q)
        if (explicitTopic != null) return null

        val explicitWindow = detectTimeWindow(q)
        val reference = referencePattern.containsMatchIn(q) || q.contains(" it ") || q == "it" || q == "that"
        if (!reference && explicitWindow == null) return null

        val topicPhrase = when (topic) {
            "food" -> "food spending"
            "transport" -> "transport spending"
            "spending", "spend", "week", "summary" -> "spending"
            "balance" -> "balance"
            "budget" -> "budget"
            "bills" -> "bills"
            "debts" -> "debts"
            "savings" -> "savings"
            "income" -> "income"
            "today", "yesterday" -> "spending"
            else -> topic
        }

        val window = explicitWindow ?: memory.timeWindow
        return listOfNotNull(topicPhrase, windowToPhrase(window)).joinToString(" ")
    }

    private fun windowToPhrase(window: String?): String? = when (window) {
        "TODAY" -> "today"
        "YESTERDAY" -> "yesterday"
        "THIS_WEEK" -> "this week"
        "LAST_WEEK" -> "last week"
        "THIS_MONTH" -> "this month"
        "LAST_MONTH" -> "last month"
        else -> null
    }
}

object BuddyHumor {
    private val greetings = listOf(
        "Ready to put the wallet under investigation? 😭",
        "PesaBuddy reporting for duty. Your wallet has been notified. 🫡",
        "Hey! Let’s see what those coins have been plotting. 👀"
    )

    private val thanks = listOf(
        "Anytime. Someone has to keep the wallet accountable. 😌",
        "Karibu sana. The ledger and I got you. 🤝",
        "You're welcome — now go spend wisely before I start judging. 😂"
    )

    private val errors = listOf(
        "That request tripped over its own shoelaces. Try again. 😭",
        "My brain just missed a matatu. Rephrase that one. 🚌",
        "I couldn't make that one land cleanly. Give me another shot."
    )

    private val quips = listOf(
        "Your wallet remains under active investigation. 🕵️",
        "The numbers are talking. Loudly.",
        "Pesa has entered the chat. 💸",
        "At least the ledger is keeping receipts. 😭"
    )

    fun greeting(turn: Int): String = greetings[turn % greetings.size]
    fun thanks(turn: Int): String = thanks[turn % thanks.size]
    fun error(turn: Int): String = errors[turn % errors.size]

    fun decorate(response: String, intent: String?, turn: Int): String {
        if (response.isBlank()) return response
        if (intent in setOf("greeting", "thanks", "bye")) return response
        if (turn > 0 && turn % 5 == 0 && response.length > 24) {
            val quip = quips[(turn / 5 - 1) % quips.size]
            return response + " " + quip
        }
        return response
    }
}
