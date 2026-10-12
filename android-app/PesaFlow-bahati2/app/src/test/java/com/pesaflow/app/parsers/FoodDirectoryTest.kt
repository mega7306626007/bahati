package com.pesaflow.app.parsers

import com.pesaflow.app.data.meals.foodSpotHit
import com.pesaflow.app.data.meals.isFoodSpot
import org.junit.Assert.*
import org.junit.Test

class FoodDirectoryTest {

    @Test
    fun `researched mess halls hit`() {
        assertEquals("Nyayo Mess", foodSpotHit("NYAYO MESS")?.displayName)
        assertEquals("Klabu (Club 36)", foodSpotHit("KLABU")?.displayName)
        assertEquals("Mum's Cafe", foodSpotHit("MUM'S CAFE")?.displayName)
        assertEquals("Chui Cafeteria", foodSpotHit("CHUI CAFETERIA")?.displayName)
    }

    @Test
    fun `known plates hit`() {
        assertTrue(isFoodSpot("SMOCHA 50"))
        assertTrue(isFoodSpot("GITHERI"))
        assertTrue(isFoodSpot("OMENA"))
        assertTrue(isFoodSpot("MATUMBO"))
        assertTrue(isFoodSpot("BEANS AND CHAPATI"))
    }

    @Test
    fun `short words never fire inside longer words`() {
        assertFalse(isFoodSpot("SEND MESSAGE"))
        assertFalse(isFoodSpot("EGGHEAD BOOKS"))
    }

    @Test
    fun `bare region names stay out`() {
        // "Mara" alone is a region, not the restaurant — phrase only.
        assertFalse(isFoodSpot("MARA"))
        assertFalse(isFoodSpot("SERENA HOTEL"))
    }

    @Test
    fun `non-food merchants miss`() {
        assertFalse(isFoodSpot("SUPER METRO SACCO"))
        assertFalse(isFoodSpot("KPLC TOKEN"))
        assertFalse(isFoodSpot("ZUKU FIBER"))
    }

    @Test
    fun `mess and cafeteria plates file as food end to end`() {
        assertEquals(
            "Food",
            com.pesaflow.app.data.parsers.MpesaParser.inferCategory(
                "NYAYO MESS",
                com.pesaflow.app.data.models.TransactionType.EXPENSE,
                "lunch"
            )
        )
        assertEquals(
            "Food",
            com.pesaflow.app.data.parsers.MpesaParser.inferCategory(
                "KLABU",
                com.pesaflow.app.data.models.TransactionType.EXPENSE,
                "smocha 50"
            )
        )
    }

    @Test
    fun `sacco sms files transport end to end`() {
        val sms = "QJ8XYZ1234 Confirmed. KSh 80.00 sent to SUPER METRO SACCO on 10/10/26 at 7:15 AM"
        val tx = com.pesaflow.app.data.parsers.MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals("Transport", tx!!.category)
    }
}
