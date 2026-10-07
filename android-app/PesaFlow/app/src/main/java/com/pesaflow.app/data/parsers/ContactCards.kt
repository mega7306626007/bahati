package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionType

// Contact-card confirmation: instead of approving 700 SMS rows one by one,
// the inbox is grouped by who you paid / who paid you ("Nancy", "Naivas").
// One card = one person/merchant: count, total, date range, top category.
// The user confirms the card ("Nancy is my roommate, Food") and every row
// under it enters the ledger with that label.
data class ContactCard(
    val name: String,
    val count: Int,
    val total: Double,
    val firstSeen: Long,
    val lastSeen: Long,
    val topCategory: String,
    val members: List<PendingTransaction>
)

fun normalizeContact(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").lowercase()
        .split(" ").joinToString(" ") { w ->
            w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

fun buildContactCards(
    pending: List<PendingTransaction>,
    minCount: Int = 1
): List<ContactCard> {
    if (pending.isEmpty()) return emptyList()
    return pending.groupBy { normalizeContact(it.merchant.ifBlank { "Unknown" }) }
        .map { (name, rows) ->
            val sorted = rows.sortedBy { it.dateTimestamp }
            val topCat = rows.groupingBy { it.category.ifBlank { "Unknown" } }.eachCount()
                .maxByOrNull { it.value }?.key ?: "Unknown"
            ContactCard(
                name = name,
                count = rows.size,
                total = rows.sumOf { it.amount },
                firstSeen = sorted.first().dateTimestamp,
                lastSeen = sorted.last().dateTimestamp,
                topCategory = topCat,
                members = sorted
            )
        }
        .filter { it.count >= minCount }
        .sortedByDescending { it.count }
}

// Frequent-contact signal ("Nancy appears 14x — who is she to you?").
// Surfaces after a scan so the user labels the person once, not 14 rows.
// Either bar earns a card: 17+ texts OR KSh 1,500+ moved — a landlord with
// 3 big rent texts matters as much as a friend with 20 small ones.
fun frequentContacts(
    pending: List<PendingTransaction>,
    minCount: Int = 3,
    limit: Int = 5,
    minTotal: Double = 1500.0
): List<ContactCard> = buildContactCards(pending, minCount = 1)
    .filter { it.count >= minCount || it.total >= minTotal }
    .sortedWith(compareByDescending<ContactCard> { it.count }.thenByDescending { it.total })
    .take(limit)

// Face memory: labels saved in the first scan ("Nancy" → "Mother") are
// re-applied on every later scan and every live SMS, so the user only ever
// labels NEW people. Matching is on the normalized name — case-proof.
fun contactLabelKey(name: String): String = "contact_label_" + normalizeContact(name)
fun contactRelationKey(name: String): String = "contact_rel_" + normalizeContact(name)

// UI: allows the user to set a free‑form relation (e.g. "Friend", "Roommate").
// Stored alongside the label so it survives rescreens.
fun contactRelationVal(key: String): String = key.removePrefix("contact_rel_").trim()

fun applyContactLabels(
    pending: List<PendingTransaction>,
    labels: Map<String, String>
): List<PendingTransaction> {
    if (pending.isEmpty() || labels.isEmpty()) return pending
    val norm = labels.entries.associate { normalizeContact(it.key) to it.value.trim() }
        .filterValues { it.isNotEmpty() }
    if (norm.isEmpty()) return pending
    return pending.map { p ->
        val hit = norm[normalizeContact(p.merchant.ifBlank { "Unknown" })]
        if (hit == null || p.displayMerchant.isNotBlank()) p
        else p.copy(displayMerchant = p.merchant.trim() + " · " + hit)
    }
}

// Flexible memory: each person carries a relation ("Mother"), a usual
// category ("Upkeep", "Food" — blank means "ask me each time") and a scope
// (IN = money from them, OUT = money to them, BOTH). Category overrides
// apply only inside their scope, so "Mother" can mean Upkeep-IN and still
// leave support-OUT alone.
data class ContactMemory(
    val label: String = "",
    val category: String = "",
    val scope: String = "BOTH",
    val relation: String = ""
)

fun contactCatKey(name: String): String = "contact_cat_" + normalizeContact(name)

fun contactScopeKey(name: String): String = "contact_scope_" + normalizeContact(name)

fun readContactMemories(all: Map<String, *>): Map<String, ContactMemory> {
    val labels = all.entries
        .filter { it.key.startsWith("contact_label_") }
        .associate { it.key.removePrefix("contact_label_") to (it.value as? String).orEmpty() }
    val cats = all.entries
        .filter { it.key.startsWith("contact_cat_") }
        .associate { it.key.removePrefix("contact_cat_") to (it.value as? String).orEmpty() }
    val scopes = all.entries
        .filter { it.key.startsWith("contact_scope_") }
        .associate { it.key.removePrefix("contact_scope_") to (it.value as? String).orEmpty() }
    val rels = all.entries
        .filter { it.key.startsWith("contact_rel_") }
        .associate { it.key.removePrefix("contact_rel_") to (it.value as? String).orEmpty() }
    val names = (labels.keys + cats.keys + rels.keys).map { normalizeContact(it) }.toSet()
    return names.associateWith { n ->
        ContactMemory(
            label = labels.entries.firstOrNull { normalizeContact(it.key) == n }?.value.orEmpty().trim(),
            category = cats.entries.firstOrNull { normalizeContact(it.key) == n }?.value.orEmpty().trim(),
            scope = scopes.entries.firstOrNull { normalizeContact(it.key) == n }?.value
                .orEmpty().trim().uppercase().takeIf { it == "IN" || it == "OUT" } ?: "BOTH",
            relation = rels.entries.firstOrNull { normalizeContact(it.key) == n }?.value.orEmpty().trim()
        )
    }.filterValues { it.label.isNotBlank() || it.category.isNotBlank() || it.relation.isNotBlank() }
}

fun applyContactMemory(
    pending: List<PendingTransaction>,
    memories: Map<String, ContactMemory>
): List<PendingTransaction> {
    if (pending.isEmpty() || memories.isEmpty()) return pending
    val norm = memories.entries.associate { normalizeContact(it.key) to it.value }
    if (norm.isEmpty()) return pending
    return pending.map { p ->
        val mem = norm[normalizeContact(p.merchant.ifBlank { "Unknown" })] ?: return@map p
        var q = p
        if (mem.label.isNotBlank() && q.displayMerchant.isBlank()) {
            q = q.copy(displayMerchant = p.merchant.trim() + " · " + mem.label)
        }
        if (mem.relation.isNotBlank() && q.displayMerchant.isBlank()) {
            q = q.copy(displayMerchant = p.merchant.trim() + " · " + mem.relation)
        }
        if (mem.category.isNotBlank()) {
            val applies = when (mem.scope) {
                "IN" -> q.type == TransactionType.INCOME
                "OUT" -> q.type != TransactionType.INCOME
                else -> true
            }
            if (applies) q = q.copy(category = mem.category)
        }
        q
    }
}

// Auto-suggest: typing "Mother" pre-fills Upkeep + Money-in; typing
// "Matatu" pre-fills Transport + Money-out. Typing "Friend" pre-fills the
// relation so Samson → Friend is one tap. Never overrides what the user
// already typed or saved.
fun suggestMemory(text: String): ContactMemory {
    val t = text.lowercase()
    fun has(vararg words: String) = words.any { it in t }
    return when {
        has("mum", "mother", "mom", "dad", "father", "parent", "guardian", "sponsor") ->
            ContactMemory("", "Upkeep", "IN")
        has("friend", "bestie", "buddy", "jamaa", "roommate", "flatmate", "housemate") ->
            ContactMemory("", "", "BOTH", "Friend")
        has("boss", "employer", "salary") -> ContactMemory("", "Income", "IN")
        has("helb") -> ContactMemory("", "Income", "IN")
        has("landlord", "caretaker", "house agent") -> ContactMemory("", "Rent", "OUT")
        has("school", "university", "college", "registrar", "exam", "fees") ->
            ContactMemory("", "School", "OUT")
        has("kplc", "token", "nairobi water", " water", "wifi") -> ContactMemory("", "Bills", "OUT")
        // Vehicle words beat "sacco": a matatu SACCO is transport, a
        // savings SACCO has no vehicle words and falls through below.
        has("boda", "matatu", "bolt", "uber", "stage", "fare", "shuttle") ->
            ContactMemory("", "Transport", "OUT")
        has("kibanda", "mboga", "grocery", "naivas", "carrefour", "quickmart", "chips", "smocha", "restaurant", "kiosk", "butchery") ->
            ContactMemory("", "Food", "OUT")
        has("airtime", "bundle", "data") -> ContactMemory("", "Airtime", "OUT")
        has("pochi") -> ContactMemory("", "Shopping", "OUT")
        has("chama", "sacco") -> ContactMemory("", "Savings", "OUT")
        else -> ContactMemory()
    }
}

fun monthKey(ts: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    return "%04d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1)
}

fun bucketByMonth(
    stamps: List<Long>
): Map<String, Int> = stamps.groupingBy { monthKey(it) }.eachCount().toSortedMap()

/// UI: updates the in‑memory relation map for a contact name.
/// The caller is responsible for persisting the map (e.g. to SharedPreferences or Room).
fun updateContactRelation(map: MutableMap<String, String>, name: String, relation: String) {
    map[normalizeContact(name)] = relation.trim()
}

/// UI: clears the saved relation for a contact name.
fun clearContactRelation(map: MutableMap<String, String>, name: String) {
    map.remove(normalizeContact(name))
}

/**
 * Retro-active pass: apply (or re-apply) every saved memory to the FULL
 * ledger, not just the fresh scan batch. Call this right after the user
 * saves a relation ("Samson → Friend") so historic SMS rows are instantly
 * labelled too. Same logic as applyContactMemory, different call site.
 */
fun applyContactMemoryToAll(
    allPending: List<PendingTransaction>,
    allMemories: Map<String, ContactMemory>
): List<PendingTransaction> = applyContactMemory(allPending, allMemories)

/**
 * Search the whole history for one person, ignoring case and spacing.
 * "joan", "Joan", "jOAn", "JOAN" all resolve to the same rows because both
 * the query and every merchant go through normalizeContact.
 */
fun searchTransactionsByContact(
    transactions: List<PendingTransaction>,
    contactName: String
): List<PendingTransaction> {
    val normQuery = normalizeContact(contactName)
    if (normQuery.isEmpty()) return emptyList()
    return transactions.filter { p ->
        normalizeContact(p.merchant.ifBlank { "Unknown" }) == normQuery
    }
}

/**
 * Search + label in one shot: filter by contact name, then bake any saved
 * label/relation/category into displayMerchant so the UI can show rows
 * immediately (e.g. "Samson · Friend").
 */
fun List<PendingTransaction>.searchAndLabel(
    contactName: String,
    memories: Map<String, ContactMemory>
): List<PendingTransaction> =
    applyContactMemory(searchTransactionsByContact(this, contactName), memories)

// Persistence: relations survive restarts as one prefs string
// "Samson|Friend;Nancy|Mother". Bad rows are skipped, never fatal.
fun encodeRelations(map: Map<String, String>): String =
    map.entries.joinToString(";") { (k, v) ->
        normalizeContact(k).replace(";", ",") + "|" + v.trim().replace(";", ",")
    }

fun decodeRelations(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(";").mapNotNull { row ->
        val parts = row.split("|")
        if (parts.size != 2) return@mapNotNull null
        val name = normalizeContact(parts[0])
        val rel = parts[1].trim()
        if (name.isEmpty() || rel.isEmpty()) null else name to rel
    }.toMap()
}
