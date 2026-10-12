package com.pesaflow.app.parsers

import com.pesaflow.app.data.ledger.mergeTransportTerms
import org.junit.Assert.*
import org.junit.Test

class TransportCardTest {

    @Test
    fun `fresh names merge onto empty card`() {
        val merged = mergeTransportTerms(emptyList(), listOf("SUPER METRO", "Boda Stage"))
        assertEquals(listOf("SUPER METRO", "Boda Stage"), merged)
    }

    @Test
    fun `dedupes case-insensitively keeping first casing`() {
        val merged = mergeTransportTerms(
            listOf("Super Metro"),
            listOf("SUPER METRO", "super metro ", "City Shuttle")
        )
        assertEquals(listOf("Super Metro", "City Shuttle"), merged)
    }

    @Test
    fun `drops blanks and the card name itself`() {
        val merged = mergeTransportTerms(
            listOf("  ", "Transport", "transport"),
            listOf("", "  ", "TRANSPORT", "Rongai Shuttle")
        )
        assertEquals(listOf("Rongai Shuttle"), merged)
    }

    @Test
    fun `collapses inner whitespace and caps length`() {
        val merged = mergeTransportTerms(emptyList(), listOf("Nairobi   West  Shuttle"))
        assertEquals(listOf("Nairobi West Shuttle"), merged)
        assertTrue(merged.all { it.length <= 60 })
    }
}
