package com.pesaflow.app.data.ledger

import android.content.SharedPreferences


/** Learns merchant → category from the user's own corrections. Checked before
 *  every keyword map, so the engine converges on the user's truth instead of
 *  re-guessing forever. Prefs-backed, capped, zero schema. */
object CategoryMemory {

    private const val KEY = "learned_categories"
    private const val MAX = 200


    fun lookup(prefs: SharedPreferences, merchant: String): String? {
        val want = merchant.trim().lowercase()
        if (want.isEmpty()) return null
        // Legacy single map first, then the triple store.
        read(prefs)[want]?.let { return it }
        return prefs.getString(contactCatKey(merchant), null)?.takeIf { it.isNotBlank() }
    }


    fun learn(prefs: SharedPreferences, merchant: String, category: String) {
        val key = merchant.trim().lowercase()
        if (key.isEmpty() || category.isBlank() || category == "Other") return
        val map = read(prefs).toMutableMap()
        // Most-recent wins; evict oldest past the cap (LinkedHashMap order).
        map.remove(key)
        map[key] = category
        while (map.size > MAX) {
            val oldest = map.keys.first()
            map.remove(oldest)
        }
        prefs.edit().putString(KEY, map.entries.joinToString("|") { "${it.key}=${it.value}" }).apply()
    }


    private fun read(prefs: SharedPreferences): Map<String, String> {
        val raw = prefs.getString(KEY, "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        raw.split("|").forEach { pair ->
            val i = pair.indexOf("=")
            if (i > 0) map[pair.substring(0, i)] = pair.substring(i + 1)
        }
        return map
    }
}


/** Calibrates parse confidence per merchant from your verdicts. Each
 *  approval +1, each rejection −1 (±10 cap); the "approve all sure ones"
 *  bar moves ±0.05 per point. Trusted merchants auto-qualify sooner,
 *  always-rejected ones stop surfacing as sure. Prefs-backed, zero schema. */
object ConfidenceMemory {

    private const val KEY = "merchant_confidence"
    private const val MAX = 200


    fun record(prefs: SharedPreferences, merchant: String, approved: Boolean) {
        val key = merchant.trim().lowercase()
        if (key.isEmpty()) return
        val map = read(prefs).toMutableMap()
        val next = ((map[key] ?: 0) + if (approved) 1 else -1).coerceIn(-10, 10)
        map.remove(key)
        if (next != 0) {
            map[key] = next
            while (map.size > MAX) map.remove(map.keys.first())
        }
        prefs.edit().putString(KEY, map.entries.joinToString("|") { "${it.key}=${it.value}" }).apply()
    }


    fun effective(prefs: SharedPreferences, merchant: String, base: Float): Float {
        val net = read(prefs)[merchant.trim().lowercase()] ?: 0
        return (base + 0.05f * net).coerceIn(0.5f, 0.99f)
    }


    private fun read(prefs: SharedPreferences): Map<String, Int> {
        val raw = prefs.getString(KEY, "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, Int>()
        raw.split("|").forEach { pair ->
            val i = pair.indexOf("=")
            if (i > 0) pair.substring(i + 1).toIntOrNull()?.let { map[pair.substring(0, i)] = it }
        }
        return map
    }
}
