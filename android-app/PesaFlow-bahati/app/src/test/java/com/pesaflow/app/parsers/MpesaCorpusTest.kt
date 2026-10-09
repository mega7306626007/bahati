package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.MpesaParser
import com.pesaflow.app.data.parsers.parseFee
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


/**
 * pesaPi historical corpus (php/documentation/mpesa_private_examples.txt):
 * shape recognition, party cleanup, balance-tail rejection. Dates from the
 * 2013-14 era fail the parser's 370-day sanity gate by design (restored
 * backups ride the carrier stamp), so exact-date assertions use modern twins.
 */
class MpesaCorpusTest {

    private fun fields(ts: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ts }

    @Test
    fun `bank to mpesa receive parses as transfer income`() {
        val sms = "DT85TH896 Confirmed.\nYou have received Ksh3,500.00 from\n501901 - KCB Money Transfer Services \non 31/7/13 at 6:43 PM\nNew M-PESA balance is Ksh11,312.00.Save & get a loan on Mshwari"
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(3500.0, tx.amount, 0.001)
        assertEquals("Transfers", tx.category)
        assertTrue(tx.merchant.contains("KCB"))
    }

    @Test
    fun `person receive strips trailing phone`() {
        val sms = "BS49OR201 Confirmed.\nYou have received Ksh50.00 from\nMICHAEL FEDERSEN 254729901555\non 15/10/11 at 11:52 AM\nNew M-PESA balance is Ksh100.00"
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals("MICHAEL FEDERSEN", tx.merchant)
    }

    @Test
    fun `person receive keeps apostrophe names`() {
        val sms = "DT82ZD611 Confirmed.\nYou have received Ksh5,500.00 from\nALEX NDUNG'U 254723784491\non 31/7/13 at 3:08 PM\nNew M-PESA balance is Ksh5,844.00.Save & get a loan on Mshwari"
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals("ALEX NDUNG'U", tx!!.merchant)
    }

    @Test
    fun `failed messages never parse`() {
        assertNull(MpesaParser.parseMessage("Failed. The entered phone number is incorrect 07221889563.", "MPESA"))
        assertNull(MpesaParser.parseMessage("Failed. M-PESA is temporarily unable to authorise airtime purchase of Ksh100.00.\nPlease try again later.", "MPESA"))
    }

    @Test
    fun `date first deposit parses as income not expense`() {
        val sms = "DQ94ZE762 Confirmed.\non 3/7/13 at 9:07 AM\nGive Ksh1,000.00 cash to Digital Africa Services Jolet Supermarket\nNew M-PESA balance is Ksh1,338.00"
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(1000.0, tx.amount, 0.001)
        assertEquals("Digital Africa Services Jolet Supermarket", tx.merchant)
    }

    @Test
    fun `date first withdraw stops party at balance tail`() {
        val sms = "ET04TG335 Confirmed.\non 20/2/14 at 2:44 PM\nWithdraw Ksh16,000.00 from\n129324 - Brothers Link Agency Vetngong Road\nNew M-PESA balance is Ksh570.00.Save & get a loan on MShwari"
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER, tx!!.type)
        assertEquals(16000.0, tx.amount, 0.001)
        assertEquals("129324 - Brothers Link Agency Vetngong Road", tx.merchant)
    }

    @Test
    fun `date first person send drops the phone`() {
        val sms = "DZ12GX874 Confirmed. Ksh2,100.00 sent to BRIAN MBUGUA 0723447655 on 17/9/13 at 3:16 PM New M-PESA balance is Ksh106.00.PIN YAKO SIRI YAKO"
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(2100.0, tx.amount, 0.001)
        assertEquals("BRIAN MBUGUA", tx.merchant)
    }

    @Test
    fun `self airtime purchase parses`() {
        val sms = "DZ55IX312 confirmed. You bought Ksh100.00 of airtime on 21/9/13 at 5:51 PM\nNew M-PESA balance is Ksh6.00.Safaricom only calls you from 0722000000"
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals("Airtime", tx.category)
        assertEquals(100.0, tx.amount, 0.001)
    }

    @Test
    fun `balance inquiry never parses`() {
        val sms = "DQ91IB986 Confirmed.\nYour M-PESA balance was Ksh339.00\non 2/7/13 at 6:46 PM.Safaricom only calls you from 0722000000"
        assertNull(MpesaParser.parseMessage(sms, "MPESA"))
    }

    @Test
    fun `paybill with account keeps business and account`() {
        val sms = "DY28XV679 Confirmed. Ksh4,000.00 sent to KCB Paybill AC for account 1137238445 on 9/9/13 at 11:31 PM\nNew M-PESA balance is Ksh22.00."
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(4000.0, tx.amount, 0.001)
        assertTrue(tx.merchant.contains("KCB Paybill AC"))
        assertTrue(tx.merchant.contains("1137238445"))
    }

    @Test
    fun `buygoods receive strips leading phone`() {
        val sms = "EA54HY643 Confirmed.\non 28/9/13 at 1:14 PM\nKsh50.00 received from\n254729639024 MORRIS M.\nNew Account balance is Ksh54.00"
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals("MORRIS M", tx.merchant)
    }

    @Test
    fun `mshwari transfer in parses as savings`() {
        val sms = "EB97SA431 Confirmed. Ksh50.00 transferred to M-Shwari account on 13/10/13 at 2:13 AM. M-PESA balance is Ksh4,265.00, new M-Shwari account balance is Ksh20,087.69."
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(TransactionType.SAVING, tx!!.type)
        assertEquals(50.0, tx.amount, 0.001)
        assertEquals("Savings", tx.category)
    }

    @Test
    fun `mshwari transfer out tolerates your account phrasing`() {
        val sms = "EB87ST824 Confirmed. You have transferred Ksh50.00 from your M-Shwari account on 13/10/13 at 2:14 AM. M-Shwari balance is Ksh20,037.69. M-PESA balance is Ksh4,315.00."
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER, tx!!.type)
        assertEquals(50.0, tx.amount, 0.001)
    }

    @Test
    fun `refund with amount before currency parses as transfer`() {
        val sms = "EE56TY519 confirmed. Your Pay Shop transaction EE56TT315 of 10Ksh has been refunded by 971577 - JUKKA ENTERPRISES. Please contact 971577 - JUKKA ENTERPRISES for more information.  Your account balance is now 47Ksh."
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER, tx!!.type)
        assertEquals(10.0, tx.amount, 0.001)
    }

    @Test
    fun `reversal with only a balance tail does not book the balance`() {
        val sms = "ER30SR746 Confirmed. Transaction EQ47FM754 has been reversed.  Your account balance is now Ksh5,987.00."
        assertNull(MpesaParser.parseMessage(sms, "MPESA"))
    }

    @Test
    fun `article main example parses with fee`() {
        val sms = "ABCDE12345 Confirmed. Ksh150.00 sent to JOHN DOE 0722000000 on 23/6/23 at 3:41 PM. New M-PESA balance is Ksh1,205.10. Transaction cost, Ksh6.00. Amount you can transact within the day is 289,950.00."
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(150.0, tx.amount, 0.001)
        assertEquals("JOHN DOE", tx.merchant)
        assertEquals(6.0, parseFee(sms) ?: -1.0, 0.001)
    }

    @Test
    fun `modern dated twins parse to the right day`() {
        val send = MpesaParser.parseMessage(
            "DZ12GX874 Confirmed. Ksh2,100.00 sent to BRIAN MBUGUA 0723447655 on 12/9/26 at 3:16 PM New M-PESA balance is Ksh106.00.",
            "MPESA"
        )
        assertNotNull(send)
        val c = fields(send!!.dateTimestamp)
        assertEquals(2026, c.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, c.get(Calendar.MONTH))
        assertEquals(12, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(15, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(16, c.get(Calendar.MINUTE))

        val deposit = MpesaParser.parseMessage(
            "DQ94ZE762 Confirmed. on 12/9/26 at 9:07 AM Give Ksh1,000.00 cash to Digital Africa Services Jolet Supermarket New M-PESA balance is Ksh1,338.00",
            "MPESA"
        )
        assertNotNull(deposit)
        val cd = fields(deposit!!.dateTimestamp)
        assertEquals(12, cd.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, cd.get(Calendar.HOUR_OF_DAY))
        assertEquals(7, cd.get(Calendar.MINUTE))

        val mshwari = MpesaParser.parseMessage(
            "EB97SA431 Confirmed. Ksh50.00 transferred to M-Shwari account on 12/9/26 at 2:13 AM. M-PESA balance is Ksh4,265.00.",
            "MPESA"
        )
        assertNotNull(mshwari)
        val cm = fields(mshwari!!.dateTimestamp)
        assertEquals(12, cm.get(Calendar.DAY_OF_MONTH))
        assertEquals(2, cm.get(Calendar.HOUR_OF_DAY))
        assertEquals(13, cm.get(Calendar.MINUTE))
    }
}
