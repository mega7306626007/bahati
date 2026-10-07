package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.MpesaParser
import com.pesaflow.app.data.parsers.SmsScanResult
import org.junit.Assert.*
import org.junit.Test


// 100%-reading contract: any SMS carrying a KSh movement becomes a dated
// cashflow. Nothing with money in it is ever silently dropped; whatever we
// cannot understand is tagged Unknown for the user to classify.
class ParserRecallTest {

    @Test
    fun `KES prefix parses like KSh`() {
        val tx = MpesaParser.parseMessage(
            "QWERTY1234 Confirmed. You have sent KES1,250.00 to John Doe on 12/9/26 at 10:30 AM"
        )
        assertNotNull(tx)
        assertEquals(1250.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun `spent verb parses as expense`() {
        val tx = MpesaParser.parseMessage(
            "QWERTY1234 Confirmed. You have spent KSh50.00 at Kibanda on 12/9/26 at 8:00 AM"
        )
        assertNotNull(tx)
        assertEquals(50.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun `verbless confirmed text becomes Unknown instead of dropped`() {
        val tx = MpesaParser.parseMessage("Confirmed. KSh50.00 Ref AA11BB22CC done.")
        assertNotNull("verb-less M-Pesa-shaped text must be captured", tx)
        assertEquals(50.0, tx!!.amount, 0.001)
        assertEquals("Unknown", tx.category)
        assertEquals(0.35f, tx.confidenceScore, 0.001f)
        assertEquals("AA11BB22CC", tx.sourceTransactionId)
    }

    @Test
    fun `confirmed word alone is not a code`() {
        assertNull(MpesaParser.parseMessage("Confirmed. KSh50.00 done."))
    }

    @Test
    fun `outstanding balance text is not a movement`() {
        assertNull(
            MpesaParser.parseMessage("Your Fuliza outstanding balance is KSh200.00. Repay to restore limit.")
        )
    }

    @Test
    fun `CONFIRMED never becomes a dedupe code`() {
        val a = MpesaParser.parseMessage("Confirmed. KSh50.00 Ref AA11BB22CC done.")
        val b = MpesaParser.parseMessage("Confirmed. KSh60.00 Ref ZZ99YY88XX done.")
        assertNotNull(a)
        assertNotNull(b)
        assertEquals("AA11BB22CC", a!!.sourceTransactionId)
        assertEquals("ZZ99YY88XX", b!!.sourceTransactionId)
        assertNotEquals(a.sourceTransactionId, b.sourceTransactionId)
    }

    @Test
    fun `promo and balance-only still dropped`() {
        assertNull(MpesaParser.parseMessage("Your M-PESA balance is KSh390.00."))
        assertNull(MpesaParser.parseMessage("Get 100MB at KSh20 dial *544# today. T&C apply."))
    }

    @Test
    fun `scan monthly math respects the window length`() {
        val rows = List(90) { i ->
            com.pesaflow.app.data.models.PendingTransaction(
                amount = 100.0,
                type = TransactionType.EXPENSE,
                category = if (i < 3) "Unknown" else "Food",
                merchant = "M-Pesa",
                dateTimestamp = 0L,
                paymentMethod = com.pesaflow.app.data.models.PaymentMethod.MPESA,
                source = com.pesaflow.app.data.models.TransactionSource.MPESA_SMS,
                sourceTransactionId = "FP|$i",
                rawText = "row $i"
            )
        }
        val r = SmsScanResult(found = 100, parsed = rows, unreadable = 10, incomeTotal = 30000.0, expenseTotal = 15000.0, daysBack = 150)
        assertEquals(6000.0, r.monthlyIncome, 0.001)
        assertEquals(3000.0, r.monthlyExpense, 0.001)
        assertEquals(0.9, r.readRate, 0.001)
        assertEquals(3, r.unknownCount)
        val r60 = SmsScanResult(found = 40, parsed = emptyList(), unreadable = 4, incomeTotal = 12000.0, expenseTotal = 6000.0, daysBack = 60)
        assertEquals(6000.0, r60.monthlyIncome, 0.001)
        assertEquals(3000.0, r60.monthlyExpense, 0.001)
    }
}
