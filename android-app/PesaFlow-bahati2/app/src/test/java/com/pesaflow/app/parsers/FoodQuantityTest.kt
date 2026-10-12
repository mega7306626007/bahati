package com.pesaflow.app.parsers

import com.pesaflow.app.data.meals.parseFoodQuantity
import org.junit.Assert.*
import org.junit.Test

class FoodQuantityTest {

    @Test
    fun `digits still parse`() {
        assertEquals(2.0, parseFoodQuantity("2")!!, 0.001)
        assertEquals(0.5, parseFoodQuantity("0.5")!!, 0.001)
    }

    @Test
    fun `swahili and english quantity words parse`() {
        assertEquals(0.25, parseFoodQuantity("quarter")!!, 0.001)
        assertEquals(0.25, parseFoodQuantity("robo")!!, 0.001)
        assertEquals(0.5, parseFoodQuantity("nusu")!!, 0.001)
        assertEquals(0.5, parseFoodQuantity("half")!!, 0.001)
        assertEquals(0.75, parseFoodQuantity("three quarters")!!, 0.001)
        assertEquals(1.0, parseFoodQuantity("whole")!!, 0.001)
    }

    @Test
    fun `fractions and case vary freely`() {
        assertEquals(0.5, parseFoodQuantity("1/2")!!, 0.001)
        assertEquals(0.25, parseFoodQuantity("1 / 4")!!, 0.001)
        assertEquals(0.5, parseFoodQuantity("NUSU")!!, 0.001)
        assertEquals(0.25, parseFoodQuantity("  robo  ")!!, 0.001)
    }

    @Test
    fun `gibberish stays null`() {
        assertNull(parseFoodQuantity(""))
        assertNull(parseFoodQuantity("abc"))
        assertNull(parseFoodQuantity("1/0"))
    }
}
