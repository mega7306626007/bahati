package com.pesaflow.app.data.meals

// Food quantity words: the top-up field accepts what comrades actually
// type - "nusu", "robo", "quarter", "1/2" - not just digits. Numeric text
// still parses first, so "2" and "0.5" behave exactly as before.
fun parseFoodQuantity(raw: String): Double? {
    val q = raw.trim().lowercase()
    if (q.isEmpty()) return null
    q.toDoubleOrNull()?.let { return it }
    Regex("^(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)$").find(q)?.let { m ->
        val num = m.groupValues[1].toDoubleOrNull()
        val den = m.groupValues[2].toDoubleOrNull()
        if (num != null && den != null && den != 0.0) return num / den
    }
    if ("three quarter" in q) return 0.75
    if ("quarter" in q || "robo" in q) return 0.25
    if ("half" in q || "nusu" in q) return 0.5
    if ("whole" in q || "full" in q) return 1.0
    return null
}
