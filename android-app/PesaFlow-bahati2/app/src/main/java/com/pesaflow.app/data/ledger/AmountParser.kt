package com.pesaflow.app.data.ledger


/** One money reader for every input: QuickAdd, NLP, CSV, SMS fallback.
 *  Understands "1,250", "2k", "1.5K", "KSh 250", "250.00". Returns null junk-free. */
object AmountParser {

    fun parse(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
            .replace("KSh", "", ignoreCase = true)
            .replace("KES", "", ignoreCase = true)
            .replace("bob", "", ignoreCase = true)
            .replace(",", "")
            .replace("/-", "")
            .trim()
            .trimEnd('.') // sentence punctuation, not decimals: "KSh99.00."
            .trim()
        if (s.isEmpty()) return null
        var mult = 1.0
        if (s.endsWith("k", ignoreCase = true)) {
            mult = 1000.0
            s = s.dropLast(1)
        } else if (s.endsWith("m", ignoreCase = true)) {
            mult = 1_000_000.0
            s = s.dropLast(1)
        }
        val v = s.toDoubleOrNull() ?: return null
        if (!v.isFinite() || v <= 0 || v > 1_000_000_000) return null
        // Round to cents — money has no fractions of a cent.
        return kotlin.math.round(v * mult * 100) / 100.0
    }


    /** Simple left-to-right expressions: "250+80", "1000-200", "2k+500".
     *  Falls back to single-value parsing when no operator is present. */
    fun parseExpression(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        if (!raw.any { it == '+' || it == '-' }) return parse(raw)
        if (!raw.matches(Regex("^[0-9.,kKmM+\\-\\s]+\$"))) return null
        return try {
            val tokens = raw.replace(" ", "")
                .split(Regex("(?=[+-])|(?<=[+-])"))
                .filter { it.isNotBlank() }
            var total: Double? = null
            var op = '+'
            for (t in tokens) {
                if (t == "+" || t == "-") { op = t[0]; continue }
                val v = parse(t) ?: return null
                total = if (total == null) v else if (op == '+') total + v else total - v
            }
            total?.let { if (it > 0 && it <= 1_000_000_000) kotlin.math.round(it * 100) / 100.0 else null }
        } catch (e: Exception) {
            null
        }
    }
    /** Last parseable token wins ("2 chapo 100" → 100, not 2). */
    fun parseLast(input: String): Double? {
        val tokens = input.trim().split("\\s+".toRegex())
        for (t in tokens.asReversed()) {
            parse(t)?.let { return it }
        }
        return null
    }
}
