package com.pesaflow.app.parsers

import com.pesaflow.app.data.parsers.WelcomeFacts
import com.pesaflow.app.data.parsers.WelcomeQ
import com.pesaflow.app.data.parsers.pickAmount
import com.pesaflow.app.data.parsers.relevantAmounts
import org.junit.Assert.*
import org.junit.Test

class WelcomeQuestionsTest {
    @Test
    fun `scan-rich commuter sees five fields`() {
        val qs = relevantAmounts(
            WelcomeFacts(
                housing = "Parents", commute = "Long",
                scanMonthly = true, scanFood = true, scanTransport = true
            )
        )
        // No SPEND (scan measured the month), no RENT (parents).
        assertEquals(
            listOf(WelcomeQ.INCOME, WelcomeQ.SPONSOR, WelcomeQ.TRANSPORT, WelcomeQ.AIRTIME, WelcomeQ.SAVE),
            qs
        )
    }

    @Test
    fun `hostel walker with no scan sees seven`() {
        val qs = relevantAmounts(WelcomeFacts(housing = "Hostel", commute = "Walk"))
        assertEquals(
            listOf(WelcomeQ.SPEND, WelcomeQ.INCOME, WelcomeQ.SPONSOR, WelcomeQ.RENT, WelcomeQ.AIRTIME, WelcomeQ.SAVE),
            qs
        )
        assertFalse(qs.contains(WelcomeQ.TRANSPORT))
    }

    @Test
    fun `renter commuter sees everything relevant`() {
        val qs = relevantAmounts(WelcomeFacts(housing = "Rent", commute = "Long"))
        assertTrue(qs.containsAll(listOf(WelcomeQ.SPEND, WelcomeQ.RENT, WelcomeQ.TRANSPORT)))
        assertEquals(7, qs.size)
    }

    @Test
    fun `alone lives like renting`() {
        val qs = relevantAmounts(WelcomeFacts(housing = "Alone", commute = "Short"))
        assertTrue(qs.contains(WelcomeQ.RENT))
        assertTrue(qs.contains(WelcomeQ.TRANSPORT))
    }

    @Test
    fun `order is stable spend to save`() {
        val qs = relevantAmounts(WelcomeFacts(housing = "Rent", commute = "Long"))
        assertEquals(WelcomeQ.SPEND, qs.first())
        assertEquals(WelcomeQ.SAVE, qs.last())
    }

    @Test
    fun `typed beats scan beats nothing`() {
        assertEquals(5000.0 to "you", pickAmount(5000.0, 9000.0))
        assertEquals(9000.0 to "scan", pickAmount(null, 9000.0))
        assertEquals(9000.0 to "scan", pickAmount(0.0, 9000.0))
        assertNull(pickAmount(null, null))
        assertNull(pickAmount(0.0, 0.0))
    }
}
