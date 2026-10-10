package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.isTransportOperator
import com.pesaflow.app.data.finance.transportOperatorHit
import org.junit.Assert.*
import org.junit.Test

class TransportDirectoryTest {

    @Test
    fun `matatu saccos hit`() {
        assertEquals("Super Metro", transportOperatorHit("SUPER METRO")?.displayName)
        assertEquals("City Shuttle", transportOperatorHit("Paid CITY SHUTTLE 80")?.displayName)
        assertEquals("Kenya Mpya", transportOperatorHit("KENYA MPYA")?.displayName)
        assertEquals("Embassava", transportOperatorHit("EMBASSAVA SACCO")?.displayName)
    }

    @Test
    fun `coaches and shuttles hit`() {
        assertEquals("Easy Coach", transportOperatorHit("EASY COACH NAIROBI")?.displayName)
        assertEquals("ENA Coach", transportOperatorHit("ENA COACH booking")?.displayName)
        assertEquals("Modern Coast", transportOperatorHit("MODERN COAST")?.displayName)
        assertEquals("North Rift Shuttle", transportOperatorHit("NORTH RIFT SHUTTLE")?.displayName)
    }

    @Test
    fun `ride hail and generic ride words hit`() {
        assertEquals("Bolt", transportOperatorHit("BOLT trip")?.displayName)
        assertTrue(isTransportOperator("boda to stage"))
        assertTrue(isTransportOperator("NDUTHI 50"))
        // Bare "sacco" is savings territory, never transport.
        assertFalse(isTransportOperator("SACCO dues"))
        assertFalse(isTransportOperator("my chama sacco"))
    }

    @Test
    fun `short names never fire inside longer words`() {
        assertNull(transportOperatorHit("Rogue Salon"))
        assertNull(transportOperatorHit("General Shop"))
        assertNull(transportOperatorHit("Missouri Boutique"))
        assertNull(transportOperatorHit("Mashed Potatoes"))
    }

    @Test
    fun `longest keyword wins over generic`() {
        // "rembo shuttle" beats the bare "shuttle" generic.
        assertEquals("Rembo Shuttle", transportOperatorHit("REMBO SHUTTLE")?.displayName)
    }

    @Test
    fun `non-transport merchants miss`() {
        assertNull(transportOperatorHit("NAIVAS SUPERMARKET"))
        assertNull(transportOperatorHit("KPLC TOKEN"))
        assertNull(transportOperatorHit("HELB DISBURSEMENT"))
    }

    @Test
    fun `named matatu sacco beats the savings sacco rule`() {
        assertEquals(
            "Transport",
            com.pesaflow.app.data.parsers.MpesaParser.inferCategory(
                "SUPER METRO SACCO",
                com.pesaflow.app.data.models.TransactionType.EXPENSE,
                "fare paid"
            )
        )
        assertEquals(
            "Savings",
            com.pesaflow.app.data.parsers.MpesaParser.inferCategory(
                "CHAMA SACCO",
                com.pesaflow.app.data.models.TransactionType.EXPENSE,
                "monthly contribution"
            )
        )
    }
}
