package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.*
import com.pesaflow.app.data.models.*
import org.junit.Assert.*
import org.junit.Test

class BillerDirectoryTest {

    private var clock = 1_700_000_000_000L

    private fun tx(
        merchant: String,
        amount: Double,
        dayGap: Int = 30,
        description: String = ""
    ): Transaction {
        clock += dayGap * 24L * 60 * 60 * 1000
        return Transaction(
            amount = amount, type = TransactionType.EXPENSE, category = "Bills",
            merchant = merchant, description = description,
            dateTimestamp = clock
        )
    }

    @Test
    fun `zuku paybill rows suggest zuku fiber`() {
        val txs = listOf(
            tx("ZUKU", 1100.0, 0, "Confirmed Ksh 1100 sent to ZUKU FIBER for account 12345. Paybill 320320"),
            tx("ZUKU", 1100.0, 30, "Confirmed Ksh 1100 sent to ZUKU FIBER for account 12345. Paybill 320320"),
            tx("ZUKU", 1100.0, 30, "Confirmed Ksh 1100 sent to ZUKU FIBER for account 12345. Paybill 320320")
        )
        val hits = suggestBillsFromDirectory(txs)
        assertEquals(1, hits.size)
        assertEquals("Zuku Fiber", hits[0].biller.displayName)
        assertTrue(hits[0].viaPaybill)
        assertTrue(hits[0].monthly)
        assertEquals(1100.0, hits[0].medianAmount, 0.001)
        assertTrue(hits[0].evidence.contains("320320"))
    }

    @Test
    fun `any-name merchants never suggest`() {
        val txs = listOf(
            tx("Kibanda Mama Njoroge", 150.0, 0),
            tx("Kibanda Mama Njoroge", 150.0, 7),
            tx("Kibanda Mama Njoroge", 150.0, 7)
        )
        assertTrue(suggestBillsFromDirectory(txs).isEmpty())
    }

    @Test
    fun `rent matches home names and rent rows`() {
        val txs = listOf(
            tx("Sent to GREENVIEW COURT", 8000.0, 0, "Rent payment"),
            tx("Sent to GREENVIEW COURT", 8000.0, 31),
            tx("Sent to GREENVIEW COURT", 8000.0, 30)
        )
        val hits = suggestBillsFromDirectory(txs, homeNames = listOf("Greenview Court"))
        assertEquals(1, hits.size)
        assertEquals("Rent", hits[0].biller.displayName)
        assertTrue(hits[0].monthly)
    }

    @Test
    fun `home name inside a confident non-rent filing never suggests rent`() {
        // "MASENO BOOKSHOP" filed as School: the home-name keyword fires,
        // but the category gate keeps it out of the Rent bucket.
        val txs = listOf(
            Transaction(amount = 200.0, type = TransactionType.EXPENSE, category = "School", merchant = "MASENO BOOKSHOP", description = "", dateTimestamp = clock + 0),
            Transaction(amount = 200.0, type = TransactionType.EXPENSE, category = "School", merchant = "MASENO BOOKSHOP", description = "", dateTimestamp = clock + 30 * 24L * 60 * 60 * 1000),
            Transaction(amount = 200.0, type = TransactionType.EXPENSE, category = "School", merchant = "MASENO BOOKSHOP", description = "", dateTimestamp = clock + 60 * 24L * 60 * 60 * 1000)
        )
        val hits = suggestBillsFromDirectory(txs, homeNames = listOf("Maseno"))
        assertTrue(hits.none { it.biller.displayName == "Rent" })
    }

    @Test
    fun `irregular amounts are rejected`() {
        val txs = listOf(
            tx("ZUKU", 1100.0, 0, "Paybill 320320"),
            tx("ZUKU", 5000.0, 30, "Paybill 320320"),
            tx("ZUKU", 200.0, 30, "Paybill 320320")
        )
        assertTrue(suggestBillsFromDirectory(txs).isEmpty())
    }

    @Test
    fun `one row pays at most one bill`() {
        val txs = listOf(
            tx("ZUKU FIBER", 1100.0, 0, "Paybill 320320"),
            tx("ZUKU FIBER", 1100.0, 30, "Paybill 320320"),
            tx("ZUKU FIBER", 1100.0, 30, "Paybill 320320")
        )
        val hits = suggestBillsFromDirectory(txs)
        assertEquals(1, hits.size)
    }

    @Test
    fun `median resists one-off spikes`() {
        val txs = listOf(
            tx("POA INTERNET", 1000.0, 0, "Paybill 7769384"),
            tx("POA INTERNET", 1000.0, 30, "Paybill 7769384"),
            tx("POA INTERNET", 1050.0, 30, "Paybill 7769384")
        )
        val hits = suggestBillsFromDirectory(txs)
        assertEquals(1, hits.size)
        assertEquals("Poa Internet", hits[0].biller.displayName)
        assertEquals(1000.0, hits[0].medianAmount, 0.001)
    }

    @Test
    fun `directory is large and sourced`() {
        assertTrue(BILLER_DIRECTORY.size >= 20)
        assertTrue(BILLER_DIRECTORY.all { it.keywords.isNotEmpty() && it.source.isNotBlank() })
        assertTrue(BILLER_DIRECTORY.any { it.paybills.isNotEmpty() })
    }
}
