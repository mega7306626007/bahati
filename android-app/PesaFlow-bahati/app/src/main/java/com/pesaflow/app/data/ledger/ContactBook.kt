package com.pesaflow.app.data.ledger

import android.content.SharedPreferences
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class ContactEntry(
    // Stable identity: assigned once at insert, never rewritten on update —
    // renames keep the id, so UI keys and future references stay put.
    // Blank on legacy data; backfilled to the normalized name on read.
    val id: String = "",
    val name: String,
    val displayName: String,
    val relationship: String = "",
    val category: String = "",
    val scope: String = "BOTH",
    val notes: String = "",
    val transactionCount: Int = 0,
    val lastSeen: Long = 0L,
    val totalIn: Double = 0.0,
    val totalOut: Double = 0.0,
    val matchTerms: String = ""
)

object ContactBook {

    private const val KEY = "contact_book"
    private const val MAX = 300
    private const val MAX_NOTES = 200

    private val RELATIONSHIPS = listOf(
        "Friend", "Brother", "Sister", "Family", "Landlord", "Boss", "Business", "School", "Other"
    )

    fun relationships(): List<String> = RELATIONSHIPS

    private fun keyOf(name: String) =
        name.trim().lowercase().replace("|", "").replace("=", "").replace(";", "")

    // Entries persist as |-separated lines: a literal pipe inside any field
    // would shift every column after it on the next read, and the numeric
    // tail (count, totals) would collapse to 0 / 0.0 defaults. Neutralize
    // pipes (and line breaks) up front so a round-trip can never corrupt.
    private fun cleanField(raw: String, max: Int) =
        raw.replace("|", " ").replace(Regex("\\s+"), " ").trim().take(max)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun readAll(prefs: SharedPreferences): List<ContactEntry> {
        val raw = prefs.getString(KEY, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        val entries = if (raw.trimStart().startsWith("[")) {
            try {
                json.decodeFromString(ListSerializer(ContactEntry.serializer()), raw)
            } catch (_: Exception) {
                return emptyList()
            }
        } else {
            parseLegacy(raw)
        }
        // Backfill: legacy and pre-id entries resolve to their normalized
        // name — the same value insert assigns, so keys never shift again.
        return entries.map { e ->
            if (e.id.isBlank()) e.copy(id = normalizeContact(e.name).ifBlank { e.name }) else e
        }
    }

    // Legacy pipe format: entries were joined with "||" — which ALSO appears
    // wherever a field is empty — so fragment-splitting corrupted every
    // multi-entry book (ghost "0"/"0.0" rows, silently dropped contacts).
    // Re-chunking on raw pipes is exact instead: each entry is 11 cells plus
    // one separator cell, so boundaries can never drift. Ghost numeric tails
    // from the old parser are recognized and dropped.
    internal fun parseLegacy(raw: String): List<ContactEntry> {
        val cells = raw.split("|")
        val out = mutableListOf<ContactEntry>()
        var start = 0
        while (start + 11 <= cells.size) {
            val c = cells.subList(start, start + 11)
            start += 12
            val name = c[0]
            if (name.isBlank()) continue
            // Ghost tail, not a contact: numeric name with numeric-or-blank
            // relationship/category (e.g. name "0", relationship "0.0").
            if (name.matches(Regex("^\\d+(\\.\\d+)?$")) &&
                c[2].all { it.isDigit() || it == '.' } &&
                c[3].all { it.isDigit() || it == '.' }
            ) continue
            out.add(
                ContactEntry(
                    name = name,
                    displayName = c[1],
                    relationship = c[2],
                    category = c[3],
                    scope = c[4].takeIf { it == "IN" || it == "OUT" } ?: "BOTH",
                    notes = c[5],
                    transactionCount = c[6].toIntOrNull() ?: 0,
                    lastSeen = c[7].toLongOrNull() ?: 0L,
                    totalIn = c[8].toDoubleOrNull() ?: 0.0,
                    totalOut = c[9].toDoubleOrNull() ?: 0.0,
                    matchTerms = c[10]
                )
            )
        }
        return out
    }

    fun save(
        prefs: SharedPreferences,
        name: String,
        displayName: String,
        relationship: String,
        category: String,
        scope: String,
        notes: String,
        matchTerms: String = ""
    ): Boolean {
        val key = keyOf(name)
        if (key.isEmpty() || displayName.isBlank()) return false
        val cleanDisplay = cleanField(displayName, 40)
        if (cleanDisplay.isBlank()) return false
        val cleanRel = cleanField(relationship, 20)
        val cleanCat = cleanField(category, 30)
        val cleanScope = scope.trim().uppercase().takeIf { it == "IN" || it == "OUT" } ?: "BOTH"
        val cleanNotes = cleanField(notes, MAX_NOTES)
        val cleanMatches = splitUserList(matchTerms)
            .map { cleanField(it.replace("=", "").replace(";", ""), 60) }
            .filter { it.isNotBlank() }.distinct().take(20).joinToString(",")
        val all = readAll(prefs).toMutableList()
        val idx = all.indexOfFirst { it.name == key }
        if (idx >= 0) {
            val old = all[idx]
            all[idx] = old.copy(
                // Identity survives renames: the id is assigned once and only
                // backfilled here for rows predating it.
                id = old.id.ifBlank { key },
                displayName = cleanDisplay,
                relationship = cleanRel,
                category = cleanCat,
                scope = cleanScope,
                notes = cleanNotes,
                matchTerms = cleanMatches
            )
        } else {
            all.add(
                ContactEntry(
                    id = key,
                    name = key,
                    displayName = cleanDisplay,
                    relationship = cleanRel,
                    category = cleanCat,
                    scope = cleanScope,
                    notes = cleanNotes,
                    matchTerms = cleanMatches
                )
            )
        }
        while (all.size > MAX) all.removeAt(0)
        writeAll(prefs, all)
        return true
    }

    fun delete(prefs: SharedPreferences, name: String) {
        val key = keyOf(name)
        if (key.isEmpty()) return
        val all = readAll(prefs).filter { it.name != key }
        writeAll(prefs, all)
    }

    fun lookup(prefs: SharedPreferences, name: String): ContactEntry? {
        val key = keyOf(name)
        if (key.isEmpty()) return null
        return readAll(prefs).firstOrNull { it.name == key }
    }

    // Whole-word match: "Nancy" tallies "NANCY WANJIKU" but never "Nancys".
    // Same rule hasContactMemory uses, so stats and categorization agree.
    // Match terms count too: every name on the Transport card tallies into
    // it, so per-operator spend accumulates without one contact per SACCO.
    internal fun matchesContact(merchant: String, contactName: String): Boolean =
        matchesContact(merchant, ContactEntry(name = contactName, displayName = contactName))

    internal fun matchesContact(merchant: String, contact: ContactEntry): Boolean {
        if (matchesName(merchant, contact.name)) return true
        return splitUserList(contact.matchTerms).any { matchesName(merchant, it) }
    }

    private fun matchesName(merchant: String, term: String): Boolean {
        val norm = normalizeContact(term)
        if (norm.isEmpty()) return false
        if (normalizeContact(merchant) == norm) return true
        return Regex(
            "(?<![\\p{L}\\p{N}])${Regex.escape(norm)}(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(merchant)
    }

    // Tally one ledger movement into every matching KNOWN contact. Never
    // creates entries — unknown merchants stay out of the book until the
    // user names them; otherwise every till ever approved would flood it.
    fun recordTransaction(prefs: SharedPreferences, merchant: String, type: TransactionType, amount: Double, ts: Long) {
        if (merchant.isBlank() || amount <= 0 || !amount.isFinite()) return
        val all = readAll(prefs).toMutableList()
        var touched = false
        all.indices.forEach { i ->
            val old = all[i]
            if (!matchesContact(merchant, old)) return@forEach
            all[i] = old.copy(
                transactionCount = old.transactionCount + 1,
                lastSeen = maxOf(old.lastSeen, ts),
                totalIn = if (type == TransactionType.INCOME) old.totalIn + amount else old.totalIn,
                totalOut = if (type != TransactionType.INCOME) old.totalOut + amount else old.totalOut
            )
            touched = true
        }
        if (touched) writeAll(prefs, all)
    }

    // One-time repair + new-contact catch-up: zero every counter, then replay
    // the full ledger. Called after imports and contact saves so the book
    // always agrees with search. Cheap (contacts × transactions, in-memory).
    fun rebuildStats(prefs: SharedPreferences, transactions: List<Transaction>) {
        val zeroed = readAll(prefs).map {
            it.copy(transactionCount = 0, lastSeen = 0L, totalIn = 0.0, totalOut = 0.0)
        }.toMutableList()
        for (tx in transactions) {
            if (tx.amount <= 0 || !tx.amount.isFinite() || tx.merchant.isBlank()) continue
            for (i in zeroed.indices) {
                val old = zeroed[i]
                if (!matchesContact(tx.merchant, old)) continue
                zeroed[i] = old.copy(
                    transactionCount = old.transactionCount + 1,
                    lastSeen = maxOf(old.lastSeen, tx.dateTimestamp),
                    totalIn = if (tx.type == TransactionType.INCOME) old.totalIn + tx.amount else old.totalIn,
                    totalOut = if (tx.type != TransactionType.INCOME) old.totalOut + tx.amount else old.totalOut
                )
            }
        }
        writeAll(prefs, zeroed)
    }

    fun knownNames(prefs: SharedPreferences): Set<String> =
        readAll(prefs).map { it.name }.toSet()

    // JSON array: field contents can never collide with the framing, so
    // multi-entry books survive every round-trip. First save after this
    // version transparently migrates legacy pipe data (see parseLegacy).
    private fun writeAll(prefs: SharedPreferences, entries: List<ContactEntry>) {
        val raw = json.encodeToString(ListSerializer(ContactEntry.serializer()), entries)
        prefs.edit().putString(KEY, raw).apply()
    }
}
