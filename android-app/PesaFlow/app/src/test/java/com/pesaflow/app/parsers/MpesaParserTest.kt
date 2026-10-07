package com.pesaflow.app.parsers

import com.pesaflow.app.data.academic.tsMs
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.MpesaParser
import com.pesaflow.app.data.parsers.parseBalance
import com.pesaflow.app.data.parsers.parseFee
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


class MpesaParserTest {

    @Test
    fun `sent money sms parses amount recipient and type`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh1,250.00 to John Doe on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1250.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(PaymentMethod.MPESA, tx.paymentMethod)
        assertEquals(TransactionSource.MPESA_SMS, tx.source)
        assertEquals("QWERTY1234", tx.sourceTransactionId)
        assertTrue(tx.merchant.contains("John"))
    }

    @Test
    fun `paybill sms parses as expense`() {
        val sms = "QWERTY1234 Confirmed. KSh500.00 paid to Naivas Supermarket. on 12/9/26 at 9:15 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Shopping", tx.category)
    }

    @Test
    fun `received money sms parses as income`() {
        val sms = "QWERTY1234 Confirmed. You have received KSh2,000.00 from Mary Jane on 12/9/26 at 11:00 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(2000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `non mpesa text returns null`() {
        assertNull(MpesaParser.parseMessage("Hello, how are you today?"))
        assertNull(MpesaParser.parseMessage(""))
        assertNull(MpesaParser.parseMessage("KSh without confirmation"))
    }

    @Test
    fun `malformed amount returns null`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh to John Doe on 12/9/26 at 10:30 AM"
        assertNull(MpesaParser.parseMessage(sms))
    }

    @Test
    fun `category inference covers student staples`() {
        assertEquals("Food", MpesaParser.inferCategory("Kibanda Lunch", TransactionType.EXPENSE))
        assertEquals("Transport", MpesaParser.inferCategory("Matatu Stage 46", TransactionType.EXPENSE))
        assertEquals("Airtime", MpesaParser.inferCategory("Safaricom Airtime", TransactionType.EXPENSE))
        assertEquals("Salary", MpesaParser.inferCategory("Anything", TransactionType.INCOME))
        assertEquals("Other", MpesaParser.inferCategory("Unknown Shop XYZ", TransactionType.EXPENSE))
    }

    @Test
    fun `paybill with account parses business and keeps account`() {
        val sms = "QWERTY1234 Confirmed. KSh1000.00 sent to KPLC PREPAID for account 123456 on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Electricity", tx.category)
        assertTrue(tx.merchant.contains("KPLC"))
        assertTrue(tx.merchant.contains("123456"))
    }

    @Test
    fun `till payment with shop name parses merchant`() {
        val sms = "QWERTY1234 Confirmed. KSh200.00 paid to Till 567890 - Mama Mboga on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(200.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertTrue(tx.merchant.contains("Mama Mboga"))
    }

    @Test
    fun `till payment without shop name falls back to shopping`() {
        val sms = "QWERTY1234 Confirmed. KSh200.00 paid to 567890 on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals("Shopping", tx!!.category)
        assertTrue(tx.merchant.contains("567890"))
    }

    @Test
    fun `gifted airtime parses as airtime expense`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh20.00 worth of airtime to 0712345678 on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(20.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Airtime", tx.category)
    }

    @Test
    fun `bundle purchase parses without code or date`() {
        val sms = "Confirmed. You have bought 1GB bundles for KSh99.00. Dial *544# for balance."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(99.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Airtime", tx.category)
    }

    @Test
    fun `mshwari deposit parses as saving excluded from spending`() {
        val sms = "QWERTY1234 Confirmed. You have transferred KSh500.00 from M-PESA to M-Shwari on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.SAVING, tx.type)
        assertEquals("Savings", tx.category)
    }

    @Test
    fun `bank to mpesa parses as transfer income`() {
        val sms = "QWERTY1234 Confirmed. You have transferred KSh1000.00 from KCB to M-PESA on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Transfer", tx.category)
    }

    @Test
    fun `fuliza overdraft parses as income for pending review`() {
        val sms = "QWERTY1234 Confirmed. You have borrowed KSh500.00 via Fuliza M-PESA on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `bare reversal without confirmed header parses as income`() {
        val sms = "QWERTY1234 You have successfully reversed KSh250.00 to John Doe"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(250.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `expanded merchant keywords map to new categories`() {        assertEquals("Transport", MpesaParser.inferCategory("Matatu Fare", TransactionType.EXPENSE))
        assertEquals("Clothes", MpesaParser.inferCategory("Shirt Store", TransactionType.EXPENSE))
        assertEquals("Kujibamba", MpesaParser.inferCategory("Salon Beauty", TransactionType.EXPENSE))
        assertEquals("Health", MpesaParser.inferCategory("Pharmacy Plus", TransactionType.EXPENSE))
        assertEquals("Data", MpesaParser.inferCategory("Wifi Kenya", TransactionType.EXPENSE))
        assertEquals("Savings", MpesaParser.inferCategory("M-Shwari", TransactionType.SAVING))
        assertEquals("Transfer", MpesaParser.inferCategory("KCB transfer", TransactionType.INCOME))
    }

    @Test
    fun `hardened patterns tolerate missing spaces and long dates`() {
        val sms = "ab12cd34 Confirmed.You have sent KSh 2,500.00 to John Doe on 12/09/2026 at 10:30AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(2500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertTrue(tx.merchant.contains("John"))
    }

    @Test
    fun `hardened patterns tolerate lowercase messages`() {
        val sms = "shtest12 confirmed. ksh300.00 paid to naivas. on 12/9/26 at 9:15 am"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(300.0, tx!!.amount, 0.001)
        assertEquals("Shopping", tx!!.category)
    }

    @Test
    fun `four digit year does not land in 2020`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh1,000.00 to John Doe on 12/09/2026 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        val year = java.util.Calendar.getInstance().apply { timeInMillis = tx!!.dateTimestamp }
            .get(java.util.Calendar.YEAR)
        assertEquals(2026, year)
    }

    @Test
    fun `24h time parses to the same day`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh1,000.00 to John Doe on 12/09/2026 at 22:30"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = tx!!.dateTimestamp }
        assertEquals(2026, cal.get(java.util.Calendar.YEAR))
        assertEquals(22, cal.get(java.util.Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `agent deposit parses as transfer income`() {
        val sms = "AB12CD34 Confirmed.You have deposited KSh1,000.00 to agent 234567 - JOHN DOE on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Transfer", tx.category)
    }

    @Test
    fun `airtel style send parses without code`() {
        val sms = "Dear customer, you have successfully sent Ksh 500.00 to 0712345678 on 12/9/26. Transaction ID: ABC123XYZ."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertTrue(tx.merchant.contains("0712345678"))
    }

    @Test
    fun `bank credit via sender parses when body omits bank name`() {
        val sms = "Dear member, your account 123456 has been credited with KES 8,500.00 on 12/9/26. Available balance KES 9,000."
        val tx = MpesaParser.parseMessage(sms, "EQUITY")
        assertNotNull(tx)
        assertEquals(8500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Transfer", tx.category)
        assertEquals(PaymentMethod.BANK_TRANSFER, tx.paymentMethod)
    }

    @Test
    fun `bank debit via sender parses as expense`() {
        val sms = "KCB Alert: Withdrawal of Ksh 2,000.00 at ATM Kenyatta Ave on 12/9/26. Available balance Ksh 5,000."
        val tx = MpesaParser.parseMessage(sms, "KCB")
        assertNotNull(tx)
        assertEquals(2000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun `purchased airtime variant parses`() {
        val sms = "Confirmed. You have purchased KSh20.00 airtime for 0712345678."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(20.0, tx!!.amount, 0.001)
        assertEquals("Airtime", tx!!.category)
    }

    @Test
    fun `helb upkeep text parses as income`() {
        val sms = "HELB upkeep of KSh 4,500.00 disbursed to your account."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(4500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `tala loan disbursement parses as debt income`() {
        val sms = "Your Tala loan of KSh 5,000.00 has been approved and disbursed to M-Pesa."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(5000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Debt", tx.category)
        assertEquals("Tala", tx.merchant)
    }

    @Test
    fun `hustler fund borrow parses as debt income`() {
        val sms = "You have borrowed KSh 1,000.00 from Hustler Fund on 12/9/26 at 10:30 AM."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Debt", tx.category)
    }

    @Test
    fun `transaction cost tail parses as fee`() {
        assertEquals(22.0, parseFee("QWERTY1234 Confirmed. KSh500 paid to Naivas. Transaction cost, KSh22.00.") ?: -1.0, 0.001)
        assertNull(parseFee("School fees KSh 20000 due next week."))
    }

    @Test
    fun `universal reader catches odd shapes`() {
        val p = com.pesaflow.app.data.parsers.MpesaParser.parseMessage(
            "XYZ12345NB Note: KSh250 sent to 0712345678 on 12/9/26. Thank you."
        )
        assertNotNull(p)
        assertEquals(250.0, p!!.amount, 0.001)
    }

    @Test
    fun `universal reader ignores balance-only and promos`() {
        assertNull(
            com.pesaflow.app.data.parsers.MpesaParser.parseMessage("Your M-PESA balance is KSh390.00.")
        )
        assertNull(
            com.pesaflow.app.data.parsers.MpesaParser.parseMessage("Get 100MB at KSh20 dial *544# today. T&C apply.")
        )
    }

    @Test
    fun `codeless texts carry dedup fingerprints`() {
        val p = com.pesaflow.app.data.parsers.MpesaParser.parseMessage("You bought 1GB for KSh99. Enjoy!")
        assertNotNull(p)
        assertTrue(p!!.sourceTransactionId?.startsWith("FP|") == true)
    }

    @Test
    fun `mpesa balance tail parses`() {
        assertEquals(1240.0, parseBalance("QWERTY1234 Confirmed. KSh500 paid to Naivas. New M-PESA balance is KSh1,240.00.") ?: -1.0, 0.001)
    }

    @Test
    fun `loose balance phrasing parses without mpesa prefix`() {
        assertEquals(350.0, parseBalance("Your balance is KSh350.00. Dial *544# for more.") ?: -1.0, 0.001)
    }

    @Test
    fun `fuliza outstanding never counts as wallet`() {
        assertNull(parseBalance("Your Fuliza outstanding balance is KSh200.00. Repay to restore limit."))
        assertNull(parseBalance("M-Shwari balance is KSh5000.00"))
    }

    @Test
    fun `income names its source in main format`() {
        assertEquals("HELB", MpesaParser.inferCategory("HELB upkeep", TransactionType.INCOME))
        assertEquals("Parent", MpesaParser.inferCategory("Mum", TransactionType.INCOME))
        assertEquals("Scholarship", MpesaParser.inferCategory("Wings scholarship", TransactionType.INCOME))
        assertEquals("Transfer", MpesaParser.inferCategory("Equity bank transfer", TransactionType.INCOME))
        assertEquals("Gift", MpesaParser.inferCategory("Birthday gift", TransactionType.INCOME))
        assertEquals("Salary", MpesaParser.inferCategory("Anything", TransactionType.INCOME))
    }

    @Test
    fun `pochi send parses with pochi tag`() {
        val sms = "QX12AB34CD Confirmed. Ksh250.00 sent to MAMA MBOGA POCHI LA BIASHARA 0722000000 on 12/9/26 at 9:41 AM. New M-PESA balance is Ksh1000.00. Transaction cost, Ksh5.00."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(250.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertTrue(tx.merchant.contains("MAMA MBOGA"))
        assertTrue(tx.merchant.contains("Pochi"))
        assertEquals("Food", tx.category)
    }

    @Test
    fun `pochi paid parses as shopping when nameless`() {
        val sms = "QW12CD34EF Confirmed. Ksh120.00 paid to JUJA POCHI STORE. on 12/9/26 at 6:05 PM. New M-PESA balance is Ksh800.00."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(120.0, tx!!.amount, 0.001)
        assertEquals("Shopping", tx.category)
    }

    @Test
    fun `pochi income is business`() {
        assertEquals("Business", MpesaParser.inferCategory("POCHI LA BIASHARA", TransactionType.INCOME))
    }

    @Test
    fun `plain sends without pochi stay untagged`() {
        val sms = "QWERTY1234 Confirmed. You have sent KSh1,250.00 to John Doe on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertFalse(tx!!.merchant.contains("Pochi"))
    }

    @Test
    fun `dateless text lands on its inbox day not scan day`() {
        val sms = "Confirmed. You have purchased weekly bundles at KSh100"
        val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val tx = MpesaParser.parseMessage(sms, "Safaricom", monthAgo)
        assertNotNull(tx)
        assertEquals(100.0, tx!!.amount, 0.001)
        // The event clock wins: a month-old buy must not pollute "today".
        assertEquals(monthAgo, tx.dateTimestamp)
    }

    @Test
    fun `dateless text without inbox clock stamps now`() {
        val sms = "Confirmed. You have purchased weekly bundles at KSh100"
        val before = System.currentTimeMillis()
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertTrue(tx!!.dateTimestamp in before..System.currentTimeMillis())
    }

    @Test
    fun `future inbox clock is ignored`() {
        val sms = "Confirmed. You have purchased weekly bundles at KSh100"
        val tx = MpesaParser.parseMessage(sms, "Safaricom", System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
        assertNotNull(tx)
        assertTrue(tx!!.dateTimestamp <= System.currentTimeMillis())
    }

    private fun calOf(ts: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ts }

    @Test
    fun `time only past today stays today`() {
        val now = tsMs(2026, Calendar.OCTOBER, 14, 12)
        val t = MpesaParser.resolveTimeOnly("10:30 AM", now)
        assertNotNull(t)
        val c = calOf(t!!)
        assertEquals(14, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, c.get(Calendar.MINUTE))
    }

    @Test
    fun `time only in the future steps a day behind`() {
        // 11:59 PM against a 9 AM now: tonight hasn't happened, so the
        // event was last night — one 24h step behind, never tomorrow.
        val now = tsMs(2026, Calendar.OCTOBER, 14, 9)
        val t = MpesaParser.resolveTimeOnly("11:59 PM", now)
        assertNotNull(t)
        val c = calOf(t!!)
        assertEquals(13, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, c.get(Calendar.MINUTE))
        assertTrue(t <= now)
    }

    @Test
    fun `unparseable time only returns null`() {
        val now = tsMs(2026, Calendar.OCTOBER, 14, 12)
        assertNull(MpesaParser.resolveTimeOnly("sometime-ish", now))
        assertNull(MpesaParser.resolveTimeOnly("25:99", now))
    }
}