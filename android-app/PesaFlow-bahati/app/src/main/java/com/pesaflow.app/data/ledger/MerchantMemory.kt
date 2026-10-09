package com.pesaflow.app.data.ledger

import android.content.SharedPreferences

/** Merchant aliases: "Lucy" → "Mom". When the user names a sender once,
 *  every future row from that sender displays the name — "who is Lucy?"
 *  is asked exactly once. Saving an alias also teaches CategoryMemory the
 *  row's category, so future texts from them auto-classify too.
 *  Prefs-backed, capped, zero schema (same pattern as CategoryMemory). */
object MerchantMemory {

    private const val KEY = "merchant_aliases"
    private const val MAX = 200
    private const val MAX_LABEL = 40

    data class Alias(val label: String)

    private fun keyOf(merchant: String) =
        merchant.trim().lowercase().replace("|", "").replace("=", "").replace(";", "")

    fun lookup(prefs: SharedPreferences, merchant: String): Alias? {
        val want = keyOf(merchant)
        if (want.isEmpty()) return null
        // Legacy single map first, then the triple store — nothing learned
        // before is ever lost.
        read(prefs)[want]?.let { if (it.isNotBlank()) return Alias(it) }
        return prefs.getString(contactLabelKey(merchant), null)
            ?.takeIf { it.isNotBlank() }?.let { Alias(it) }
    }

    /** Names a sender. Returns false when there is nothing to save. */
    fun learnAlias(prefs: SharedPreferences, merchant: String, label: String): Boolean {
        val key = keyOf(merchant)
        val clean = label.trim().replace("|", "").replace("=", "").replace(";", "").take(MAX_LABEL)
        if (key.isEmpty() || clean.isEmpty()) return false
        val map = read(prefs).toMutableMap()
        // Most-recent wins; evict oldest past the cap (LinkedHashMap order).
        map.remove(key)
        map[key] = clean
        while (map.size > MAX) {
            map.remove(map.keys.first())
        }
        prefs.edit().putString(KEY, map.entries.joinToString("|") { "${it.key}=${it.value}" }).apply()
        return true
    }

    fun clear(prefs: SharedPreferences, merchant: String) {
        val key = keyOf(merchant)
        if (key.isEmpty()) return
        val map = read(prefs).toMutableMap()
        if (map.remove(key) != null) {
            prefs.edit().putString(KEY, map.entries.joinToString("|") { "${it.key}=${it.value}" }).apply()
        }
    }

    fun displayName(prefs: SharedPreferences, merchant: String, fallback: String): String =
        lookup(prefs, merchant)?.label ?: fallback

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
