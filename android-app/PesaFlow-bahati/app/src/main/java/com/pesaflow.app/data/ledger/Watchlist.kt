package com.pesaflow.app.data.ledger

import android.content.SharedPreferences

// Category watchlist: pinned envelopes report first in Smart Insights,
// budget or not. Prefs-backed set of lowercase names, zero schema.
object Watchlist {
    private const val KEY = "watched_categories"

    fun read(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().map { it.lowercase() }.toSet()

    fun toggle(prefs: SharedPreferences, category: String): Set<String> {
        val key = category.trim().lowercase()
        if (key.isEmpty()) return read(prefs)
        val next = read(prefs).toMutableSet()
        if (!next.add(key)) next.remove(key)
        prefs.edit().putStringSet(KEY, next).apply()
        return next
    }
}
