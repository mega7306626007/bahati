package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.Transaction

// Duplicate cleaner (user-initiated, after any scan): double scans and
// double imports leave the same event twice. Two rules, both conservative:
//  1. Same M-Pesa/source code twice = same SMS, certain.
//  2. Same person + same amount + same minute = same event. A daily
//     smocha at the same kibanda lands on different minutes/days, so real
//     repeats survive. Newest row of each group is kept, the rest go.
fun findDuplicateGroups(txs: List<Transaction>): List<List<Transaction>> {
    if (txs.size < 2) return emptyList()
    val groups = mutableListOf<List<Transaction>>()
    txs.filter { !it.sourceTransactionId.isNullOrBlank() }
        .groupBy { it.sourceTransactionId!! }
        .values.filter { it.size > 1 }
        .forEach { groups.add(it) }
    txs.groupBy {
        normalizeContact(it.merchant.ifBlank { "Unknown" }) + "|" +
            it.amount + "|" + it.type.name + "|" + (it.dateTimestamp / 60000)
    }.values.filter { it.size > 1 }
        .forEach { groups.add(it) }
    // Merge groups sharing a row (a triple-dupe hits both rules).
    val merged = mutableListOf<MutableSet<String>>()
    val byId = txs.associateBy { it.id }
    groups.map { g -> g.map { it.id }.toSet() }.forEach { ids ->
        val hit = merged.firstOrNull { m -> m.any { it in ids } }
        if (hit == null) merged.add(ids.toMutableSet()) else hit.addAll(ids)
    }
    return merged.mapNotNull { ids ->
        val rows = ids.mapNotNull { byId[it] }
        if (rows.size > 1) rows else null
    }
}

// Ids to drop: everything except the newest row per group.
fun duplicateIdsToRemove(groups: List<List<Transaction>>): List<String> =
    groups.flatMap { g ->
        val keep = g.maxByOrNull { it.dateTimestamp }?.id
        g.map { it.id }.filter { it != keep }
    }

// Pending-inbox twin: same two conservative rules, so dupes can be dropped
// BEFORE "Confirm all" bakes them into the ledger. Newest row per group is
// kept, the rest go.
fun findDuplicatePendingGroups(pending: List<PendingTransaction>): List<List<PendingTransaction>> {
    if (pending.size < 2) return emptyList()
    val groups = mutableListOf<List<PendingTransaction>>()
    pending.filter { !it.sourceTransactionId.isNullOrBlank() }
        .groupBy { it.sourceTransactionId!! }
        .values.filter { it.size > 1 }
        .forEach { groups.add(it) }
    pending.groupBy {
        normalizeContact(it.merchant.ifBlank { "Unknown" }) + "|" +
            it.amount + "|" + it.type.name + "|" + (it.dateTimestamp / 60000)
    }.values.filter { it.size > 1 }
        .forEach { groups.add(it) }
    // Merge groups sharing a row (a triple-dupe hits both rules).
    val merged = mutableListOf<MutableSet<String>>()
    val byId = pending.associateBy { it.id }
    groups.map { g -> g.map { it.id }.toSet() }.forEach { ids ->
        val hit = merged.firstOrNull { m -> m.any { it in ids } }
        if (hit == null) merged.add(ids.toMutableSet()) else hit.addAll(ids)
    }
    return merged.mapNotNull { ids ->
        val rows = ids.mapNotNull { byId[it] }
        if (rows.size > 1) rows else null
    }
}

// Pending ids to drop: everything except the newest row per group.
fun pendingIdsToRemove(groups: List<List<PendingTransaction>>): List<String> =
    groups.flatMap { g ->
        val keep = g.maxByOrNull { it.dateTimestamp }?.id
        g.map { it.id }.filter { it != keep }
    }
