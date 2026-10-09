package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.MpesaParser
import com.pesaflow.app.data.parsers.parseBalance
import org.junit.Assert.*
import org.junit.Test


// Airtel Money (TID fingerprint): separate wallet (OTHER), never MPESA.
class AirtelParserTest {

    @Test
    fun `airtel send parses with date`() {
        val sms = "You have sent Ksh1,000.00 to PETER OTIENO 0736000000. TID: AT123ABC456. Balance: Ksh4,000.00. Date: 10/09/2026 09:45."
        val tx = MpesaParser.parseMessage(sms, "AIRTEL")
        assertNotNull(tx)
        assertEquals(1000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertTrue(tx.merchant.contains("PETER"))
        assertEquals("AT123ABC456", tx.sourceTransactionId)
        assertEquals(PaymentMethod.OTHER, tx.paymentMethod)
    }

    @Test
    fun `airtel receive parses`() {
        val sms = "You have received Ksh3,000.00 from MARY ACHIENG 0755000000. TID: AT789XYZ123. New balance: Ksh9,000.00."
        val tx = MpesaParser.parseMessage(sms, "AIRTEL")
        assertNotNull(tx)
        assertEquals(3000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals(PaymentMethod.OTHER, tx.paymentMethod)
    }

    @Test
    fun `airtel paybill parses`() {
        val sms = "You have paid Ksh500.00 to KPLC PREPAID Paybill 888899 Acc 123456. TID: AT111. Balance: Ksh2,000.00."
        val tx = MpesaParser.parseMessage(sms, "AIRTEL")
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Electricity", tx.category)
    }

    @Test
    fun `airtel airtime and bundle categorize`() {
        val airtime = MpesaParser.parseMessage(
            "You have bought Ksh20.00 airtime for 0736000000. TID: AT222. Balance: Ksh100.00.", "AIRTEL"
        )
        assertNotNull(airtime)
        assertEquals("Airtime", airtime!!.category)
        val bundle = MpesaParser.parseMessage(
            "You have bought 1GB daily data bundle for Ksh99.00. TID: AT333. Balance: Ksh50.00.", "AIRTEL"
        )
        assertNotNull(bundle)
        assertEquals(99.0, bundle!!.amount, 0.001)
        assertEquals("Data", bundle.category)
    }

    @Test
    fun `airtel balance tail harvests`() {
        assertEquals(4000.0, parseBalance("Balance: Ksh4,000.00. Date: 10/09/2026 09:45.")!!, 0.001)
    }
}
