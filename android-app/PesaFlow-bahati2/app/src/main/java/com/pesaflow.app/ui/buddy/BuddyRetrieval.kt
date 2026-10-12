package com.pesaflow.app.ui.buddy

import com.pesaflow.app.data.models.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// PesaBuddy's RAG retrieval layer: one ranked pass over every local
// knowledge source - ledger rows, campus guide, food guide, transport
// atlas - before the composer writes a word. No embeddings, no network:
// the ledger is the corpus and token overlap is the ranker. Every chunk
// carries its source so answers can cite where each fact came from.
// Pure Kotlin, unit-tested.
data class RetrievedChunk(val source: String, val label: String, val score: Double)

object BuddyRetrieval {

    fun retrieve(query: String, txs: List<Transaction>, maxChunks: Int = 5): List<RetrievedChunk> {
        if (query.isBlank()) return emptyList()
        val out = mutableListOf<RetrievedChunk>()
        // Ledger first: the Ziidi quarantine lives inside SearchEngine, so
        // product rows never leak into name queries from this path either.
        val dayFmt = SimpleDateFormat("d MMM", Locale.getDefault())
        com.pesaflow.app.data.search.SearchEngine().search(
            txs.filter { !it.isSample }, query
        ).hits.sortedByDescending { it.score }.take(3).forEach { hit ->
            val tx = hit.transaction
            out.add(
                RetrievedChunk(
                    source = "ledger",
                    label = "${tx.merchant} KSh ${tx.amount.toInt()} (${dayFmt.format(Date(tx.dateTimestamp))})",
                    score = hit.score
                )
            )
        }
        // Guides: campus, food, transport. Fixed mid scores - context, not hits.
        com.pesaflow.app.data.campus.campusHabitsFor(query)?.let { habit ->
            out.add(RetrievedChunk("campus guide", habit.campus, 0.5))
        }
        com.pesaflow.app.data.meals.spotsFor(query).take(2).forEach { spot ->
            out.add(RetrievedChunk("food guide", "${spot.spot}: ${spot.item}", 0.4))
        }
        com.pesaflow.app.data.finance.transportFor(query)?.let { atlas ->
            out.add(RetrievedChunk("transport atlas", atlas.campus, 0.4))
        }
        return out.sortedByDescending { it.score }.take(maxChunks)
    }

    // "Sources: ledger (2 rows) - campus guide (UoN Kabete)".
    fun sourcesLine(chunks: List<RetrievedChunk>): String {
        if (chunks.isEmpty()) return ""
        val ledger = chunks.filter { it.source == "ledger" }
        val guides = chunks.filter { it.source != "ledger" }
        val parts = mutableListOf<String>()
        if (ledger.isNotEmpty()) {
            parts.add("ledger (${ledger.size} row${if (ledger.size == 1) "" else "s"})")
        }
        guides.forEach { parts.add("${it.source} (${it.label})") }
        return "Sources: " + parts.joinToString(" - ")
    }
}
