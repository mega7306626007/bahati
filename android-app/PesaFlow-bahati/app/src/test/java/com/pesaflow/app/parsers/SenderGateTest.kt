package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.MpesaParser
import com.pesaflow.app.data.parsers.assignSimSlot
import org.junit.Assert.*
import org.junit.Test


/** Sender authority: people are never money, no matter the body shape. */
class SenderGateTest {

    @Test
    fun `official senders are recognized case insensitively`() {
        assertTrue(MpesaParser.isOfficialSender("MPESA"))
        assertTrue(MpesaParser.isOfficialSender("mpesa"))
        assertTrue(MpesaParser.isOfficialSender("Safaricom"))
        assertTrue(MpesaParser.isOfficialSender("AIRTELMONEY"))
        assertTrue(MpesaParser.isOfficialSender("TELKOM"))
        assertTrue(MpesaParser.isOfficialSender("KCB"))
        assertTrue(MpesaParser.isOfficialSender("EQUITY"))
        assertTrue(MpesaParser.isOfficialSender("CO-OP"))
        assertTrue(MpesaParser.isOfficialSender("HELB"))
        assertTrue(MpesaParser.isOfficialSender("STIMA SACCO"))
        assertTrue(MpesaParser.isOfficialSender("UNAITAS"))
    }

    @Test
    fun `personal senders are rejected`() {
        assertFalse(MpesaParser.isOfficialSender("+254712345678"))
        assertFalse(MpesaParser.isOfficialSender("0712345678"))
        assertFalse(MpesaParser.isOfficialSender("John Doe"))
        assertFalse(MpesaParser.isOfficialSender("Mama Mboga"))
        assertFalse(MpesaParser.isOfficialSender("ZUKU"))
    }

    @Test
    fun `confirmed shape from a phone number never parses`() {
        val sms = "AB12CD34 Confirmed. You have sent KSh1,000.00 to John Doe on 12/9/26 at 10:30 AM"
        assertNull(MpesaParser.parseMessage(sms, "+254712345678"))
        assertNull(MpesaParser.parseMessage(sms, "0712345678"))
    }

    @Test
    fun `swahili transaction text from a person never parses`() {
        // "I was sent an SMS transaction": a friend's money talk is not money.
        val sms = "SB12AB34CG Imethibitishwa. Umetuma Ksh500.00 kwa JUMA JUMA tarehe 12/9/26 saa 10:30 AM"
        assertNull(MpesaParser.parseMessage(sms, "0712345678"))
        assertNull(MpesaParser.parseMessage(sms, "Juma"))
    }

    @Test
    fun `confirmed promise from a contact never parses`() {
        assertNull(MpesaParser.parseMessage("Confirmed nitakutumia KSh500 kesho", "John Doe"))
        assertNull(MpesaParser.parseMessage("Nimetuma KES 500 kwa mpesa yako", "+254722000111"))
    }

    @Test
    fun `official swahili confirmation still parses`() {
        val sms = "SB12AB34CG Imethibitishwa. Umetuma Ksh500.00 kwa JUMA JUMA tarehe 12/9/26 saa 10:30 AM"
        val tx = MpesaParser.parseMessage(sms, "MPESA")
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun `helb and sacco senders parse`() {
        val helb = MpesaParser.parseMessage("HELB upkeep of KSh 4,500.00 disbursed to your account.", "HELB")
        assertNotNull(helb)
        assertEquals(TransactionType.INCOME, helb!!.type)
        val sacco = MpesaParser.parseMessage("STIMA SACCO: Deposit of KES 3,000.00 confirmed. New balance KES 45,000.00.", "STIMA SACCO")
        assertNotNull(sacco)
        assertEquals(TransactionType.SAVING, sacco!!.type)
    }

    @Test
    fun `blank sender keeps manual flows working`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh1,000.00 to John Doe on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1000.0, tx!!.amount, 0.001)
    }

    @Test
    fun `unknown sub id never claims a sim`() {
        val (slot, same) = assignSimSlot(emptyMap(), -1L)
        assertEquals(-1, slot)
        assertTrue(same.isEmpty())
    }

    @Test
    fun `first seen subscription becomes sim one second becomes sim two`() {
        val (s1, m1) = assignSimSlot(emptyMap(), 100L)
        assertEquals(0, s1)
        val (s1again, m1b) = assignSimSlot(m1, 100L)
        assertEquals(0, s1again)
        assertEquals(m1, m1b)
        val (s2, m2) = assignSimSlot(m1b, 200L)
        assertEquals(1, s2)
        assertEquals(0, m2[100L])
        assertEquals(1, m2[200L])
    }

    @Test
    fun `bank sender parses debit`() {
        val sms = "KCB Alert: Your account 123456 has been debited with KSh2,000.00 on 12-Sep-26 at 10:30 AM. Available balance KSh5,000.00."
        val tx = MpesaParser.parseMessage(sms, "KCB")
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
    }
}
