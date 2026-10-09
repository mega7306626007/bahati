package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.PendingPolicy
import org.junit.Assert.*
import org.junit.Test


/** First-sync policy: coded + sure auto-confirms, everything else waits. */
class PendingPolicyTest {

    @Test
    fun `coded sure row auto approves`() {
        assertTrue(PendingPolicy.isAutoApprovable("QWERTY1234", 0.95f))
        assertTrue(PendingPolicy.isAutoApprovable("TID99281", 0.85f))
    }

    @Test
    fun `codeless row waits even when sure`() {
        assertFalse(PendingPolicy.isAutoApprovable(null, 0.99f))
        assertFalse(PendingPolicy.isAutoApprovable("", 0.99f))
        assertFalse(PendingPolicy.isAutoApprovable("   ", 0.99f))
    }

    @Test
    fun `coded unsure row waits`() {
        assertFalse(PendingPolicy.isAutoApprovable("QWERTY1234", 0.84f))
        assertFalse(PendingPolicy.isAutoApprovable("QWERTY1234", 0.5f))
    }

    @Test
    fun `threshold is shared and sane`() {
        assertEquals(0.85f, PendingPolicy.SURE_CONFIDENCE, 0.0f)
    }

    @Test
    fun `categorized rows pass through untouched`() {
        assertEquals("Food", PendingPolicy.upgradeOtherCategory("Food", "Transport", "Shopping"))
        assertEquals("food", PendingPolicy.upgradeOtherCategory("food", null, "Transport"))
    }

    @Test
    fun `blank and other categories need human eyes`() {
        fun row(category: String) = PendingTransaction(
            amount = 10.0, type = TransactionType.EXPENSE, category = category,
            merchant = "M", dateTimestamp = 0L, paymentMethod = PaymentMethod.MPESA,
            source = TransactionSource.MPESA_SMS, sourceTransactionId = "Q1",
            rawText = ""
        )
        assertTrue(PendingPolicy.needsCategory(row("")))
        assertTrue(PendingPolicy.needsCategory(row("Other")))
        assertTrue(PendingPolicy.needsCategory(row("other")))
        assertFalse(PendingPolicy.needsCategory(row("Food")))
        assertFalse(PendingPolicy.needsCategory(row("Transport")))
    }

    @Test
    fun `other consults memory then inference`() {
        assertEquals("Transport", PendingPolicy.upgradeOtherCategory("Other", "Transport", "Shopping"))
        assertEquals("Shopping", PendingPolicy.upgradeOtherCategory("Other", null, "Shopping"))
        assertEquals("OTHER", PendingPolicy.upgradeOtherCategory("OTHER", null, "Other"))
        assertEquals("Other", PendingPolicy.upgradeOtherCategory("Other", null, ""))
        assertEquals("", PendingPolicy.upgradeOtherCategory("", null, "Other"))
    }
}
