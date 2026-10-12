package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.MpesaParser
import org.junit.Assert.*
import org.junit.Test


// Credit-product coverage from the sms-engine template lab: Pochi, Hustler
// Fund, Lipa Mdogo, Okoa data — plus the ported merchant catalog keywords.
class CreditProductsTest {

    @Test
    fun `hustler borrow is debt income not fuliza`() {
        val sms = "PERHH1J63S Confirmed. You have borrowed Ksh4,774.47 from Hustler Fund on 27/10/25 at 7:11 PM. Repay within 14 days. M-PESA balance is Ksh93,852.35."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(4774.47, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Debt", tx.category)
        assertTrue(tx.merchant.contains("Hustler"))
    }

    @Test
    fun `hustler repay is debt expense`() {
        val sms = "MK9UY3BJPM Confirmed. Hustler Fund repayment of Ksh185.56 received on 26/3/26 at 11:39 AM. M-PESA balance is Ksh191,804.66."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(185.56, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Debt", tx.category)
    }

    @Test
    fun `hustler save is savings`() {
        val sms = "WI9MPUEI0E Confirmed. You have saved Ksh731.48 to Hustler Fund savings on 14/6/26 at 2:10 PM. M-PESA balance is Ksh172,261.87."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(731.48, tx!!.amount, 0.001)
        assertEquals(TransactionType.SAVING, tx.type)
        assertEquals("Savings", tx.category)
    }

    @Test
    fun `pochi send is a transfer not paybill spending`() {
        val sms = "OYM9X39T44 Confirmed. Ksh410.51 sent to Pochi La Biashara 960523 on 17/9/26 at 11:17 AM. M-PESA balance is Ksh306,521.20. Transaction cost, Ksh22.00."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(410.51, tx!!.amount, 0.001)
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals("Transfers", tx.category)
    }

    @Test
    fun `pochi receive names the customer`() {
        val sms = "6RW98WPPIG Confirmed. You have received Ksh278.29 to Pochi La Biashara from Peter Odhiambo 0111389507 on 21/1/26 at 11:06 AM. Pochi balance is Ksh4,243.61."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(278.29, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertTrue(tx.merchant.contains("Peter"))
    }

    @Test
    fun `pochi withdraw is a transfer`() {
        val sms = "79C8UDMEDU Confirmed. You have withdrawn Ksh1,020.19 from Pochi La Biashara to M-PESA on 1/9/26 at 9:41 AM. M-PESA balance is Ksh10,754.22."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1020.19, tx!!.amount, 0.001)
        assertEquals(TransactionType.TRANSFER, tx.type)
        assertEquals("Transfers", tx.category)
    }

    @Test
    fun `lipa mdogo installment is shopping`() {
        val sms = "K6UNGF4Q33 Confirmed. Ksh3,000.00 paid to Lipa Mdogo Mdogo for TECNO SPARK on 22/9/26 at 8:03 AM. New M-PESA balance is Ksh226,092.81. Transaction cost, Ksh22.00."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(3000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Shopping", tx.category)
        assertTrue(tx.merchant.contains("TECNO"))
    }

    @Test
    fun `okoa data reads the worth amount`() {
        val sms = "Request successful. You have received 1GB 1hr Okoa data worth Ksh499.00 on 20/6/26 at 8:11 PM. Your Okoa credit is Ksh499.00 to be paid before 25/6."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(499.0, tx!!.amount, 0.001)
        assertEquals("Airtime", tx.category)
    }

    @Test
    fun `ported catalog keywords classify`() {
        val e = TransactionType.EXPENSE
        assertEquals("Shopping", MpesaParser.inferCategory("JUMIA", e))
        assertEquals("Shopping", MpesaParser.inferCategory("Kilimall", e))
        assertEquals("Food", MpesaParser.inferCategory("Artcaffe", e))
        assertEquals("Food", MpesaParser.inferCategory("Galitos", e))
        assertEquals("Transport", MpesaParser.inferCategory("Shell", e))
        assertEquals("Transport", MpesaParser.inferCategory("Rubis", e))
        assertEquals("Health", MpesaParser.inferCategory("Goodlife Pharmacy", e))
        assertEquals("Health", MpesaParser.inferCategory("MyDawa", e))
        assertEquals("Kujibamba", MpesaParser.inferCategory("SportPesa", e))
        assertEquals("Kujibamba", MpesaParser.inferCategory("Betika", e))
        assertEquals("Data", MpesaParser.inferCategory("Zuku", e))
        assertEquals("School", MpesaParser.inferCategory("Strathmore", e))
        assertEquals("School", MpesaParser.inferCategory("TEXT BOOK CENTRE", e))
        assertEquals("Transport", MpesaParser.inferCategory("Nairobi Expressway", e))
        assertEquals("Debt", MpesaParser.inferCategory("Hustler Fund", e))
        assertEquals("Transfers", MpesaParser.inferCategory("Pochi La Biashara", e))
    }
}
