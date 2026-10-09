package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.MpesaParser
import org.junit.Assert.*
import org.junit.Test


/**
 * Coverage gate: 105-message multi-provider corpus
 * (app/src/test/resources/parsers/sms_corpus_100.txt — file name historic).
 *
 * - recall >= 96% correct-type parsing across the whole corpus
 * - ZERO false-positives on NULL cases (noise must never book money)
 * - per-provider breakdown printed for diagnostics (no assert — signal
 *   stays on the two gates above)
 */
class CoverageTest {

    private data class Case(
        val sender: String,
        val body: String,
        val expected: String,
        val note: String
    )

    private fun loadCorpus(): List<Case> {
        val stream = javaClass.getResourceAsStream("/parsers/sms_corpus_100.txt")
            ?: error("corpus resource missing: parsers/sms_corpus_100.txt")
        return stream.bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val parts = line.split("|")
                Case(
                    sender = parts.getOrElse(0) { "" },
                    body = parts.getOrElse(1) { "" },
                    expected = parts.getOrElse(2) { "NULL" },
                    note = parts.getOrElse(3) { "" }
                )
            }
    }

    private fun matches(tx: PendingTransaction?, expected: String): Boolean {
        return when (expected) {
            "NULL" -> tx == null
            "INCOME" -> tx?.type == TransactionType.INCOME
            "EXPENSE" -> tx?.type == TransactionType.EXPENSE
            "TRANSFER" -> tx?.type == TransactionType.TRANSFER
            "SAVING" -> tx?.type == TransactionType.SAVING
            else -> false
        }
    }

    private fun describe(tx: PendingTransaction?): String {
        if (tx == null) return "null"
        return "${tx.type} ${tx.amount} '${tx.merchant}'"
    }

    @Test
    fun `recall is at least 96 percent`() {
        val corpus = loadCorpus()
        assertEquals("corpus must hold 105 cases", 105, corpus.size)
        val failures = corpus.mapIndexedNotNull { i, c ->
            val tx = MpesaParser.parseMessage(c.body, c.sender)
            if (matches(tx, c.expected)) null
            else "#$i [${c.note}] expected=${c.expected} got=${describe(tx)} body='${c.body.take(80)}'"
        }
        val recall = (corpus.size - failures.size).toFloat() / corpus.size
        println("Coverage: ${corpus.size - failures.size}/${corpus.size} = $recall (gate >= 0.96)")
        failures.forEach { println("MISS: $it") }
        assertTrue("recall $recall below 96%:\n${failures.joinToString("\n")}", recall >= 0.96f)
    }

    @Test
    fun `zero false positives on noise`() {
        val corpus = loadCorpus()
        val offenders = corpus.mapIndexedNotNull { i, c ->
            if (c.expected != "NULL") return@mapIndexedNotNull null
            val tx = MpesaParser.parseMessage(c.body, c.sender)
            if (tx == null) null
            else "#$i [${c.note}] parsed as ${describe(tx)} body='${c.body.take(80)}'"
        }
        println("Noise false-positives: ${offenders.size}")
        offenders.forEach { println("FP: $it") }
        assertTrue("noise parsed as money:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `provider breakdown printed`() {
        val corpus = loadCorpus()
        val byProvider = corpus.groupBy { c ->
            when (c.sender) {
                "MPESA" -> "M-Pesa"
                "AIRTELMONEY" -> "Airtel"
                "TELKOM" -> "Telkom"
                "KCB", "EQUITY", "CO-OP", "ABSA", "STANBIC", "FAMILY", "DTB", "NCBA", "I&M" -> "Bank"
                "SAFARICOM" -> "SafaricomSvc"
                else -> "NoSender"
            }
        }
        byProvider.forEach { (provider, cases) ->
            val ok = cases.count { c -> matches(MpesaParser.parseMessage(c.body, c.sender), c.expected) }
            println("$provider: $ok/${cases.size} = ${ok.toFloat() / cases.size}")
        }
    }
}
