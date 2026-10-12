package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.MpesaParser
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


/** SMS date recognition: text stamps in every provider shape, carrier-stamp
 * fallbacks, and rejection of due-date ghosts. All dates local-time. */
class SmsDateTest {

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(y, m, d, h, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun fields(ts: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ts }

    private fun assertStamp(ts: Long, y: Int, m: Int, d: Int, h: Int, min: Int = 0) {
        val c = fields(ts)
        assertEquals(y, c.get(Calendar.YEAR))
        assertEquals(m, c.get(Calendar.MONTH))
        assertEquals(d, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(h, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(min, c.get(Calendar.MINUTE))
    }

    @Test
    fun `bank dd-Mon-yy with time harvests the stamp`() {
        val sms = "KCB Alert: Your account 123456 has been debited with KSh2,000.00 on 12-Sep-26 at 10:30 AM. Available balance KSh5,000.00."
        // Scan conditions: the inbox stamp corroborates the body within minutes.
        val tx = MpesaParser.parseMessage(sms, "KCB", at(2026, Calendar.SEPTEMBER, 12, 10, 35))
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertStamp(tx.dateTimestamp, 2026, Calendar.SEPTEMBER, 12, 10, 30)
    }

    @Test
    fun `harvested stamp disagreeing with carrier loses to carrier`() {
        // A harvested date 11 days off the inbox stamp is a due-date ghost or
        // wrong-year read, never the transaction — the carrier rules.
        val sms = "KCB Alert: Your account 123456 has been debited with KSh2,000.00 on 12-Sep-26 at 10:30 AM. Available balance KSh5,000.00."
        val carrier = at(2026, Calendar.SEPTEMBER, 1, 8, 0)
        val tx = MpesaParser.parseMessage(sms, "KCB", carrier)
        assertNotNull(tx)
        assertEquals(carrier, tx!!.dateTimestamp)
    }

    @Test
    fun `bank date without clock lands at noon never midnight`() {
        val sms = "Dear member, your account 123456 has been credited with KES 8,500.00 on 12/9/26. Available balance KES 9,000."
        val tx = MpesaParser.parseMessage(sms, "EQUITY", at(2026, Calendar.SEPTEMBER, 12, 15, 0))
        assertNotNull(tx)
        assertStamp(tx!!.dateTimestamp, 2026, Calendar.SEPTEMBER, 12, 12, 0)
    }

    @Test
    fun `swahili tarehe and saa harvest`() {
        val sms = "Umetuma KSh200.00 kwa MAMA MBOGA tarehe 12/09/26 saa 09:15."
        val tx = MpesaParser.parseMessage(sms, "MPESA", at(2026, Calendar.SEPTEMBER, 12, 9, 20))
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertStamp(tx.dateTimestamp, 2026, Calendar.SEPTEMBER, 12, 9, 15)
    }

    @Test
    fun `day-first dash date parses instead of defaulting to now`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh1,000.00 to John Doe on 12-09-2026 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertStamp(tx!!.dateTimestamp, 2026, Calendar.SEPTEMBER, 12, 10, 30)
    }

    @Test
    fun `iso date harvests from bank bodies`() {
        val sms = "Your account 123456 debited KSh300.00 on 2026-09-12 at 10:30:00. Ref 98765."
        val tx = MpesaParser.parseMessage(sms, "KCB", at(2026, Calendar.SEPTEMBER, 12, 10, 31))
        assertNotNull(tx)
        assertStamp(tx!!.dateTimestamp, 2026, Calendar.SEPTEMBER, 12, 10, 30)
    }

    @Test
    fun `seconds in time do not break the day`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh1,000.00 to John Doe on 12/09/26 at 10:30:45"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertStamp(tx!!.dateTimestamp, 2026, Calendar.SEPTEMBER, 12, 10, 30)
    }

    @Test
    fun `real text date beats a stale carrier stamp`() {
        // Branch-captured `on…at…` stamps keep full trust even against a
        // disagreeing carrier: restored-SMS backups rewrite inbox dates to
        // restore-time while body dates stay true.
        val sms = "QWERTY1234 Confirmed. You have sent KSh1,000.00 to John Doe on 12/09/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms, "MPESA", at(2026, Calendar.SEPTEMBER, 1))
        assertNotNull(tx)
        assertStamp(tx!!.dateTimestamp, 2026, Calendar.SEPTEMBER, 12, 10, 30)
    }

    @Test
    fun `future text date falls back to the carrier stamp`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh1,000.00 to John Doe on 12/09/2030 at 10:30 AM"
        val carrier = at(2026, Calendar.SEPTEMBER, 1, 8, 0)
        val tx = MpesaParser.parseMessage(sms, "MPESA", carrier)
        assertNotNull(tx)
        assertEquals(carrier, tx!!.dateTimestamp)
    }

    @Test
    fun `dateless bundle uses stale carrier stamp`() {
        val sms = "Confirmed. You have bought 1GB bundles for KSh99.00. Dial *544# for balance."
        val carrier = at(2026, Calendar.SEPTEMBER, 1, 8, 0)
        val tx = MpesaParser.parseMessage(sms, "SAFARICOM", carrier)
        assertNotNull(tx)
        assertEquals(carrier, tx!!.dateTimestamp)
    }

    @Test
    fun `fresh carrier stamp keeps now default`() {
        val sms = "Confirmed. You have bought 1GB bundles for KSh99.00. Dial *544# for balance."
        val before = System.currentTimeMillis()
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertTrue(tx!!.dateTimestamp >= before - 1_000)
        assertTrue(tx.dateTimestamp <= System.currentTimeMillis() + 1_000)
    }

    @Test
    fun `telco on-date harvests without clock`() {
        val sms = "Dear customer, you have successfully sent Ksh 500.00 to 0712345678 on 12/9/26. Transaction ID: ABC123XYZ."
        val tx = MpesaParser.parseMessage(sms, "", at(2026, Calendar.SEPTEMBER, 12, 18, 0))
        assertNotNull(tx)
        assertStamp(tx!!.dateTimestamp, 2026, Calendar.SEPTEMBER, 12, 12, 0)
    }
}
