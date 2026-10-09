package com.pesaflow.app.ui.buddy

import com.pesaflow.app.ui.dashboard.BuddyBrain

// Flexible input understanding: punctuation collapse, word-order freedom
// (callers already match order-free), and typo tolerance. Correction only
// ever rewrites a token into a KNOWN command keyword — names, merchants and
// amounts are never touched — and only when there is exactly one close
// candidate, so "brian" can never become something else. Pure, unit-tested.
object BuddyTextNorm {

    // Command vocabulary: every word the router can act on. Anything outside
    // this set (names, places, slang) passes through unchanged. Swahili and
    // Sheng domain words ride here too: without them a typo like "wekka"
    // has no correct target, and worse, exact words like "helb" sit one edit
    // from an English keyword ("help") and get corrupted into it.
    val VOCAB: Set<String> by lazy {
        buildSet {
            addAll(
                listOf(
                    "hello", "hey", "habari", "help", "thanks", "balance", "spend", "today",
                    "yesterday", "week", "budget", "safe", "afford", "bills", "debt", "meals",
                    "food", "transport", "summary", "compare", "savings", "goals", "income",
                    "survive", "stock", "belongings", "undo", "review", "confirm", "ignore",
                    "categorize", "open", "show", "find", "list", "search", "add", "create",
                    "change", "delete", "remove", "mark", "move", "set", "hide", "dark",
                    "light", "theme", "language", "remind", "reminder", "export", "backup",
                    "restore", "plan", "simulate", "compare", "briefing", "semester",
                    "transactions", "transaction", "spending", "payment", "budgets",
                    "savings", "settings", "notifications", "contacts", "reports",
                    "analytics", "insights", "income", "semester", "university", "meals",
                    "kitchen", "things", "reports", "goals", "search", "review", "home",
                    "tomorrow", "monday", "friday", "rent", "bill", "debt", "goal",
                    "month", "months", "week", "weeks", "days", "what", "much", "spend",
                    "survive", "stretch", "prepare", "morning", "changed", "versus",
                    // Kiswahili money + command + query words.
                    "salio", "baki", "deni", "madeni", "bajeti", "akiba", "matumizi",
                    "mapato", "gharama", "shilingi", "lipa", "lipia", "kopa",
                    "tumia", "ongeza", "weka", "tuma", "kumbusha", "angalia",
                    "onyesha", "nisaidie", "saidia", "nataka", "naomba",
                    "ninahitaji", "niko", "yangu", "yako", "yetu", "asante",
                    "tafadhali", "lini", "wapi", "nini", "kiasi", "ngapi",
                    "leo", "jana", "kesho", "wiki", "mwezi", "siku", "chakula",
                    "nauli", "kula", "kunywa", "posho", "mshahara", "helb",
                    "salary", "sasa", "pole", "karibu", "nzuri", "kidogo",
                    "nyingi", "tarehe", "fungua", "jaza", "unga", "mboga",
                    // Sheng money + greeting words.
                    "niaje", "mambo", "mullah", "ganji", "cheddar", "fiti",
                    "noma", "maze", "form", "wasee", "buda", "mdosi", "mbesha"
                )
            )
            addAll(BuddyBrain.BUDDY_CATEGORIES.map { it.lowercase() })
        }
    }

    // Legitimate words the corrector must never touch — real English,
    // domain nouns and plurals. Without this, "save" becomes "safe" and
    // "mode" becomes "move". Correction is for typos, not vocabulary.
    private val STOPLIST = setOf(
        "a", "about", "after", "again", "all", "also", "always", "amount", "an",
        "and", "any", "apply", "are", "around", "as", "at", "away", "back", "be",
        "because", "been", "before", "being", "below", "between", "both", "but",
        "by", "can", "cancel", "cannot", "could", "daily", "date", "day", "did",
        "do", "does", "doing", "done", "down", "during", "each", "every", "far",
        "few", "first", "for", "forward", "from", "get", "getting", "go", "goes",
        "going", "good", "got", "had", "has", "have", "having", "here", "how",
        "into", "is", "it", "its", "just", "keep", "kind", "know", "last",
        "later", "least", "left", "less", "like", "long", "made", "make", "many",
        "me", "mean", "money", "more", "most", "much", "my", "name", "near",
        "never", "new", "next", "no", "not", "now", "number", "of", "off",
        "often", "on", "once", "one", "only", "or", "other", "our", "out",
        "over", "own", "per", "please", "put", "same", "save", "second", "see",
        "set", "should", "show", "since", "so", "some", "still", "such", "sure",
        "take", "than", "that", "then", "there", "these", "they", "thing",
        "third", "this", "those", "though", "through", "to", "together", "too",
        "turn", "two", "under", "until", "up", "us", "use", "used", "very",
        "was", "we", "were", "what", "when", "where", "which", "while", "who",
        "why", "will", "with", "within", "without", "would", "yes", "yet",
        "you", "your", "mode", "every", "reminders", "cancel", "title", "time",
        "times", "way", "okay", "sawa", "yebo", "ksh", "kes", "bob", "via",
        "balances", "bills", "budgets", "transactions", "savings", "debts",
        "goals", "contacts", "reports", "settings", "expenses", "payments",
        "notifications", "incomes", "earnings", "belongings"
    )

    // "open...budgets!!!", "add, 500, transport" → "open budgets".
    fun collapse(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    // Capped Levenshtein: bail out past maxDist (fast on short tokens).
    fun editDistance(a: String, b: String, maxDist: Int = 2): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > maxDist) return maxDist + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > maxDist) return maxDist + 1
            prev = cur
        }
        return prev[b.length]
    }

    // Correct a single token: unique close keyword wins; ties, digits,
    // short tokens, stoplisted words and protected names stay as typed.
    // Never invents words outside VOCAB.
    fun correctToken(token: String, protected: Set<String> = emptySet()): String {
        if (token.length < 4 || token.any { it.isDigit() }) return token
        if (token in protected) return token
        if (token in VOCAB || token in STOPLIST) return token
        val threshold = if (token.length >= 7) 2 else 1
        var best: String? = null
        var bestDist = threshold + 1
        var tied = false
        for (w in VOCAB) {
            if (w.length < 4) continue
            val d = editDistance(token, w, threshold)
            if (d < bestDist) {
                bestDist = d
                best = w
                tied = false
            } else if (d == bestDist) {
                tied = true
            }
        }
        return if (!tied && bestDist <= threshold && best != null) best else token
    }

    // Two-letter command verbs can't fuzzy-match safely ("ad" could be
    // anything), so the only common short typos are pinned explicitly.
    private val SHORT_FIXES = mapOf("ad" to "add", "opn" to "open", "opne" to "open")

    fun correct(normalized: String, protected: Set<String> = emptySet()): String =
        normalized.split(" ").joinToString(" ") { tok ->
            SHORT_FIXES[tok] ?: correctToken(tok, protected)
        }

    fun normalize(raw: String, protected: Set<String> = emptySet()): String =
        correct(BuddyBrain.normalize(collapse(raw)), protected)

    // Word-boundary verb check: "mark" must not fire inside "remark", and
    // trailing verbs ("500 transport add") count the same as leading ones.
    fun hasWord(haystack: String, word: String): Boolean =
        Regex("(?<![a-z0-9])${Regex.escape(word)}(?![a-z0-9])").containsMatchIn(haystack)
}
