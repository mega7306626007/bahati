package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.KitchenStock
import com.pesaflow.app.data.parsers.campusFoods
import com.pesaflow.app.data.parsers.campusSpotsToAdd
import com.pesaflow.app.data.parsers.hostelMonthly
import com.pesaflow.app.data.parsers.matchStockForMeal
import com.pesaflow.app.data.parsers.shouldAskRent
import com.pesaflow.app.data.parsers.shouldAskTransport
import com.pesaflow.app.data.parsers.suggestStarterBudgets
import com.pesaflow.app.ui.dashboard.parseTransportDaily
import org.junit.Assert.*
import org.junit.Test

private fun pile(name: String, left: Double = 2.0, use: Double = 0.5): KitchenStock =
    KitchenStock(name = name, unit = "kg", qtyFull = 5.0, qtyLeft = left, dailyUse = use, pricePerPack = 200.0)

class PlannerGearsTest {
    @Test
    fun `eaten meal matches its stock pile`() {
        val stock = listOf(pile("Unga"), pile("Sukuma"))
        assertEquals("Unga", matchStockForMeal("Ugali + unga", stock)?.name)
        assertEquals("Sukuma", matchStockForMeal("Sukuma wiki", stock)?.name)
    }

    @Test
    fun `empty piles never match`() {
        assertNull(matchStockForMeal("Unga", listOf(pile("Unga", left = 0.0))))
        assertNull(matchStockForMeal("Pizza", listOf(pile("Unga"))))
    }

    @Test
    fun `uon knows clabu smocha 70`() {
        val foods = campusFoods("University of Nairobi")
        val smocha = foods.firstOrNull { it.item.contains("Smocha", ignoreCase = true) }
        assertNotNull(smocha)
        assertEquals(70.0, smocha!!.price, 0.001)
    }

    @Test
    fun `unknown campus falls back to generic spots`() {
        assertTrue(campusFoods("Somewhere College").isNotEmpty())
    }

    @Test
    fun `parents skip rent walkers skip fare`() {
        assertFalse(shouldAskRent("Parents"))
        assertTrue(shouldAskRent("Hostel"))
        assertTrue(shouldAskRent("Rent"))
        assertFalse(shouldAskTransport("Walk"))
        assertTrue(shouldAskTransport("Short"))
        assertTrue(shouldAskTransport("Long"))
    }

    @Test
    fun `starter budgets only include positive envelopes`() {
        val s = suggestStarterBudgets(9000.0, 3000.0, 0.0, 1500.0)
        assertEquals(9000.0, s["ALL"]!!, 0.001)
        assertEquals(3000.0, s["Food"]!!, 0.001)
        assertFalse(s.containsKey("Rent"))
        assertEquals(1500.0, s["Transport"]!!, 0.001)
    }

    @Test
    fun `empty ledger suggests nothing`() {
        assertTrue(suggestStarterBudgets(0.0, 0.0, 0.0, 0.0).isEmpty())
    }

    @Test
    fun `campus spots skip dishes already planned`() {
        val spots = campusSpotsToAdd(listOf("Smocha"), "University of Nairobi")
        assertTrue(spots.none { it.item.equals("Smocha", ignoreCase = true) })
        assertTrue(spots.isNotEmpty())
        // Re-tap after adding everything → nothing left.
        val again = campusSpotsToAdd(
            campusFoods("University of Nairobi").map { it.item }, "University of Nairobi"
        )
        assertTrue(again.isEmpty())
    }

    @Test
    fun `transport daily parses from answers`() {
        assertEquals(150.0, parseTransportDaily("x|transport=150|y=1"), 0.001)
        assertEquals(0.0, parseTransportDaily("x|y=1"), 0.001)
    }

    @Test
    fun `hostel fees follow 2025-26 published rates`() {
        assertEquals(1900.0, hostelMonthly("University of Nairobi")!!, 0.001)
        assertEquals(1400.0, hostelMonthly("Kenyatta University")!!, 0.001)
        assertEquals(1600.0, hostelMonthly("JKUAT")!!, 0.001)
        assertEquals(1200.0, hostelMonthly("Moi University")!!, 0.001)
        assertEquals(1100.0, hostelMonthly("Egerton")!!, 0.001)
        assertEquals(1750.0, hostelMonthly("Maseno")!!, 0.001)
        assertNull(hostelMonthly("Unknown College"))
        assertNull(hostelMonthly(""))
    }
}
