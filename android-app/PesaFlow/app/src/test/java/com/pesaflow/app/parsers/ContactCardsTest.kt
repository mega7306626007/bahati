package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.ContactMemory
import com.pesaflow.app.data.parsers.applyContactLabels
import com.pesaflow.app.data.parsers.applyContactMemory
import com.pesaflow.app.data.parsers.buildContactCards
import com.pesaflow.app.data.parsers.applyContactMemoryToAll
import com.pesaflow.app.data.parsers.contactLabelKey
import com.pesaflow.app.data.parsers.decodeRelations
import com.pesaflow.app.data.parsers.encodeRelations
import com.pesaflow.app.data.parsers.frequentContacts
import com.pesaflow.app.data.parsers.normalizeContact
import com.pesaflow.app.data.parsers.readContactMemories
import com.pesaflow.app.data.parsers.searchAndLabel
import com.pesaflow.app.data.parsers.searchTransactionsByContact
import com.pesaflow.app.data.parsers.suggestMemory
import com.pesaflow.app.data.parsers.updateContactRelation
import org.junit.Assert.*
import org.junit.Test

private fun pend(merchant: String, amount: Double, day: Long, category: String = "Food"): PendingTransaction =
    PendingTransaction(
        amount = amount,
        type = TransactionType.EXPENSE,
        category = category,
        merchant = merchant,
        dateTimestamp = day,
        paymentMethod = PaymentMethod.MPESA,
        source = TransactionSource.MPESA_SMS,
        sourceTransactionId = null,
        rawText = "$merchant $amount"
    )

class ContactCardsTest {
    @Test
    fun `nancy rows collapse into one card`() {
        val rows = listOf(
            pend("Nancy", 200.0, 1700000000000L),
            pend("nancy ", 150.0, 1700086400000L),
            pend("NANCY", 300.0, 1700172800000L),
            pend("Naivas", 500.0, 1700000000000L)
        )
        val cards = buildContactCards(rows)
        assertEquals(2, cards.size)
        val nancy = cards.first()
        assertEquals("Nancy", nancy.name)
        assertEquals(3, nancy.count)
        assertEquals(650.0, nancy.total, 0.001)
        assertEquals(1700000000000L, nancy.firstSeen)
        assertEquals(1700172800000L, nancy.lastSeen)
    }

    @Test
    fun `frequent contacts surface repeat senders`() {
        val rows = (1..4).map { pend("Nancy", 100.0, 1700000000000L + it * 86400000L) } +
            listOf(pend("Once", 50.0, 1700000000000L))
        val freq = frequentContacts(rows, minCount = 3)
        assertEquals(1, freq.size)
        assertEquals("Nancy", freq.first().name)
    }

    @Test
    fun `only heavy regulars earn a who-is-this card`() {
        val rows = (1..17).map { pend("Nancy", 100.0, 1700000000000L + it * 86400000L) } +
            (1..16).map { pend("Kevin", 50.0, 1700000000000L + it * 86400000L) }
        val freq = frequentContacts(rows, minCount = 17, minTotal = 1e9)
        assertEquals(1, freq.size)
        assertEquals("Nancy", freq.first().name)
    }

    @Test
    fun `big movers earn a card below the text count`() {
        val rows = (1..3).map { pend("Landlord", 8000.0, 1700000000000L + it * 86400000L) } +
            (1..16).map { pend("Kevin", 50.0, 1700000000000L + it * 86400000L) }
        val freq = frequentContacts(rows, minCount = 17, minTotal = 1500.0)
        assertEquals(1, freq.size)
        assertEquals("Landlord", freq.first().name)
    }

    @Test
    fun `normalize trims and title-cases`() {
        assertEquals("Nancy", normalizeContact("  nancy  "))
        assertEquals("Naivas Moi Avenue", normalizeContact("naivas  moi avenue"))
    }

    @Test
    fun `remembered faces arrive pre-named`() {
        val rows = listOf(
            pend("Nancy", 200.0, 1700000000000L),
            pend("NANCY", 150.0, 1700086400000L),
            pend("Stranger", 50.0, 1700000000000L)
        )
        val out = applyContactLabels(rows, mapOf("nancy" to "Mother"))
        assertEquals("Nancy · Mother", out[0].displayMerchant)
        assertEquals("NANCY · Mother", out[1].displayMerchant)
        assertEquals("", out[2].displayMerchant)
    }

    @Test
    fun `empty labels leave rows untouched`() {
        val rows = listOf(pend("Nancy", 200.0, 1700000000000L))
        assertEquals(rows, applyContactLabels(rows, emptyMap()))
    }

    @Test
    fun `label keys are normalized and stable`() {
        assertEquals(contactLabelKey("Nancy"), contactLabelKey("  nancy "))
    }

    @Test
    fun `memory stamps label and usual category`() {
        val rows = listOf(pend("Nancy", 200.0, 1700000000000L, category = "Unknown"))
        val out = applyContactMemory(rows, mapOf("nancy" to ContactMemory("Mother", "Upkeep", "BOTH")))
        assertEquals("Nancy · Mother", out[0].displayMerchant)
        assertEquals("Upkeep", out[0].category)
    }

    @Test
    fun `scope IN leaves money-out alone`() {
        val outP = pend("Mum", 500.0, 1700000000000L, category = "Food")
        val out = applyContactMemory(
            listOf(outP.copy(type = TransactionType.EXPENSE)),
            mapOf("mum" to ContactMemory("Mother", "Upkeep", "IN"))
        )
        assertEquals("Food", out[0].category)
        assertEquals("Mum · Mother", out[0].displayMerchant)
    }

    @Test
    fun `scope OUT leaves money-in alone`() {
        val inp = pend("Mum", 1000.0, 1700000000000L, category = "Income")
            .copy(type = TransactionType.INCOME)
        val out = applyContactMemory(
            listOf(inp),
            mapOf("mum" to ContactMemory("Mother", "Food", "OUT"))
        )
        assertEquals("Income", out[0].category)
    }

    @Test
    fun `blank category means ask me`() {
        val rows = listOf(pend("Nancy", 200.0, 1700000000000L, category = "Transport"))
        val out = applyContactMemory(rows, mapOf("nancy" to ContactMemory("Friend", "", "BOTH")))
        assertEquals("Transport", out[0].category)
        assertEquals("Nancy · Friend", out[0].displayMerchant)
    }

    @Test
    fun `mother suggests upkeep in`() {
        val s = suggestMemory("Mother")
        assertEquals("Upkeep", s.category)
        assertEquals("IN", s.scope)
    }

    @Test
    fun `landlord suggests rent out`() {
        val s = suggestMemory("My landlord")
        assertEquals("Rent", s.category)
        assertEquals("OUT", s.scope)
    }

    @Test
    fun `matatu and naivas suggest their lanes`() {
        assertEquals("Transport", suggestMemory("Matatu sacco").category)
        assertEquals("Food", suggestMemory("Naivas").category)
        assertEquals("Airtime", suggestMemory("Safaricom airtime").category)
    }

    @Test
    fun `friends stay blank aka ask me`() {
        val s = suggestMemory("Kevin roommate")
        assertEquals("", s.category)
        assertEquals("BOTH", s.scope)
    }

    @Test
    fun `memories read back from prefs map`() {
        val all = mapOf(
            "contact_label_Nancy" to "Mother",
            "contact_cat_Nancy" to "Upkeep",
            "contact_scope_Nancy" to "in",
            "other_key" to "x"
        )
        val mem = readContactMemories(all)
        assertEquals(1, mem.size)
        val nancy = mem["Nancy"] ?: mem.values.first()
        assertEquals("Mother", nancy.label)
        assertEquals("Upkeep", nancy.category)
        assertEquals("IN", nancy.scope)
    }

    @Test
    fun `relation alone survives the read filter`() {
        val mem = readContactMemories(mapOf("contact_rel_Samson" to "Friend"))
        assertEquals(1, mem.size)
        assertEquals("Friend", mem.values.first().relation)
    }

    @Test
    fun `friend words suggest friend relation`() {
        val s = suggestMemory("Kevin roommate")
        assertEquals("Friend", s.relation)
        assertEquals("", s.category)
    }

    @Test
    fun `relation labels past and future rows`() {
        val rows = listOf(
            pend("Samson", 200.0, 1700000000000L),
            pend("SAMSON", 150.0, 1700086400000L)
        )
        val mem = mapOf("Samson" to ContactMemory(relation = "Friend"))
        val out = applyContactMemoryToAll(rows, mem)
        assertTrue(out.all { it.displayMerchant.contains("Friend") })
    }

    @Test
    fun `search finds every casing of joan`() {
        val rows = listOf(
            pend("joan", 100.0, 1700000000000L),
            pend("Joan", 200.0, 1700086400000L),
            pend("jOAn", 300.0, 1700172800000L),
            pend("JOAN", 400.0, 1700259200000L),
            pend("Nancy", 500.0, 1700000000000L)
        )
        val found = searchTransactionsByContact(rows, "Joan")
        assertEquals(4, found.size)
        assertEquals(1000.0, found.sumOf { it.amount }, 0.001)
    }

    @Test
    fun `search and label shows friend in one shot`() {
        val rows = listOf(pend("samson", 250.0, 1700000000000L))
        val mem = mapOf("Samson" to ContactMemory(relation = "Friend"))
        val out = rows.searchAndLabel("SAMSON", mem)
        assertEquals(1, out.size)
        assertTrue(out.first().displayMerchant.contains("Friend"))
    }

    @Test
    fun `relations encode round trip and skip bad rows`() {
        val map = mutableMapOf<String, String>()
        updateContactRelation(map, "Samson", "Friend")
        val enc = encodeRelations(map)
        assertEquals(mapOf("Samson" to "Friend"), decodeRelations(enc))
        assertTrue(decodeRelations("junk-without-pipe;Samson|Friend").containsKey("Samson"))
        assertTrue(decodeRelations(null).isEmpty())
    }
}
