package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.SenderCard
import com.pesaflow.app.data.parsers.groupSenderCards
import com.pesaflow.app.data.parsers.isKnownEntity
import com.pesaflow.app.data.parsers.mergeSenderCards
import org.junit.Assert.*
import org.junit.Test


class SenderCardsTest {

    private val day = 24L * 60 * 60 * 1000
    private val base = 1_700_000_000_000L

    private fun tx(merchant: String, amount: Double, type: TransactionType, daysAgo: Long, category: String = "Food") =
        PendingTransaction(
            amount = amount, type = type, category = category, merchant = merchant,
            dateTimestamp = base - daysAgo * day, paymentMethod = PaymentMethod.MPESA,
            source = TransactionSource.MPESA_SMS, sourceTransactionId = null, rawText = ""
        )

    @Test
    fun `rows group into one card per sender busiest first`() {
        val rows = listOf(
            tx("Nancy", 200.0, TransactionType.EXPENSE, 1),
            tx("Nancy", 150.0, TransactionType.EXPENSE, 5),
            tx("Nancy", 1000.0, TransactionType.INCOME, 9),
            tx("Kevin", 50.0, TransactionType.EXPENSE, 2)
        )
        val cards = groupSenderCards(rows, minTransactions = 0)
        assertEquals(2, cards.size)
        assertEquals("Nancy", cards[0].merchant)
        assertEquals(3, cards[0].count)
        assertEquals(350.0, cards[0].expenseTotal, 0.001)
        assertEquals(1000.0, cards[0].incomeTotal, 0.001)
        assertEquals(base - 9 * day, cards[0].firstSeen)
        assertEquals(base - 1 * day, cards[0].lastSeen)
        assertEquals("Kevin", cards[1].merchant)
    }

    @Test
    fun `suggested category is the sender majority`() {
        val rows = listOf(
            tx("Nancy", 200.0, TransactionType.EXPENSE, 1, "Food"),
            tx("Nancy", 150.0, TransactionType.EXPENSE, 2, "Food"),
            tx("Nancy", 80.0, TransactionType.EXPENSE, 3, "Airtime")
        )
        assertEquals("Food", groupSenderCards(rows, minTransactions = 0)[0].suggestedCategory)
    }

    @Test
    fun `named senders never surface`() {
        val rows = listOf(tx("Nancy", 200.0, TransactionType.EXPENSE, 1))
        assertTrue(groupSenderCards(rows, { it.equals("nancy", true) }, minTransactions = 0).isEmpty())
        assertEquals(1, groupSenderCards(rows, minTransactions = 0).size)
    }

    @Test
    fun `system entities and unknown party never surface`() {
        assertTrue(isKnownEntity("Safaricom PLC"))
        assertTrue(isKnownEntity("KPLC Tokens"))
        assertTrue(isKnownEntity("HELB Disbursement"))
        assertTrue(isKnownEntity("Unknown Party"))
        assertTrue(isKnownEntity("123456"))
        assertTrue(isKnownEntity("Till 789012"))
        assertFalse(isKnownEntity("Nancy Wanjiru"))
        assertFalse(isKnownEntity("KIOSK 42"))
        // Carriers never ask "who is this": OD, Data, Ziidi, Fuliza, Okoa.
        assertTrue(isKnownEntity("M-Pesa Ziidi"))
        assertTrue(isKnownEntity("ZIIDI MWANZA"))
        assertTrue(isKnownEntity("OverDraft of Credit Party"))
        assertTrue(isKnownEntity("OD Loan Repayment"))
        assertTrue(isKnownEntity("Safaricom Data"))
        assertTrue(isKnownEntity("Airtime Purchase"))
        assertTrue(isKnownEntity("Withdrawal Charge"))
        assertTrue(isKnownEntity("Fuliza"))
        assertTrue(isKnownEntity("Okoa Jahazi"))
        // ...but real places with charge-like substrings still surface.
        assertFalse(isKnownEntity("Coffee House"))
        assertFalse(isKnownEntity("Java House"))
    }

    @Test
    fun `merge drops newly named senders and repeat merchants`() {
        fun card(merchant: String) = SenderCard(merchant, 20, 100.0, 0.0, 1L, 2L, "Other")
        val existing = listOf(card("Nancy Kamuyu"), card("Brian"))
        val fresh = listOf(card("Nancy Kamuyu"), card("Safaricom"), card("Peter"))
        // Nancy got named after the scan; Safaricom was never a person.
        val merged = mergeSenderCards(existing, fresh) { it == "Nancy Kamuyu" || it == "Safaricom" }
        assertEquals(listOf("Brian", "Peter"), merged.map { it.merchant })
    }

    @Test
    fun `empty scan yields no cards`() {
        assertTrue(groupSenderCards(emptyList()).isEmpty())
    }

    @Test
    fun `only senders above sixteen transactions surface`() {
        val quiet = (1..16).map { tx("Quiet", 100.0, TransactionType.EXPENSE, it.toLong()) }
        assertTrue(groupSenderCards(quiet, minTransactions = 16, minTotal = Double.MAX_VALUE).isEmpty())
        val busy = (1..17).map { tx("Busy", 100.0, TransactionType.EXPENSE, it.toLong()) }
        val cards = groupSenderCards(quiet + busy, minTransactions = 16, minTotal = Double.MAX_VALUE)
        assertEquals(1, cards.size)
        assertEquals("Busy", cards[0].merchant)
        assertEquals(17, cards[0].count)
    }

    @Test
    fun `heavy money below count still earns a card`() {
        // 3 texts moving KSh 2,000: under the count bar, over the money bar.
        val rows = listOf(
            tx("Landlord", 1000.0, TransactionType.EXPENSE, 1),
            tx("Landlord", 800.0, TransactionType.EXPENSE, 5),
            tx("Landlord", 200.0, TransactionType.EXPENSE, 9)
        )
        val cards = groupSenderCards(rows)
        assertEquals(1, cards.size)
        assertEquals("Landlord", cards[0].merchant)
    }

    @Test
    fun `light money below count stays silent`() {
        val rows = (1..5).map { tx("Kiosk", 100.0, TransactionType.EXPENSE, it.toLong()) }
        assertTrue(groupSenderCards(rows).isEmpty())
    }
}
