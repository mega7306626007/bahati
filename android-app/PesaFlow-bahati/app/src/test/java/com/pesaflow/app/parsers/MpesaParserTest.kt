package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isEarnedIncome
import com.pesaflow.app.data.parsers.MpesaParser
import com.pesaflow.app.data.parsers.StatementImporter
import org.junit.Assert.*
import org.junit.Test


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
        assertEquals("Data", tx.category)
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
        assertEquals("Transfers", tx.category)
        assertFalse(tx.isEarnedIncome())
    }

    @Test
    fun `fuliza overdraft parses as income for pending review`() {
        val sms = "QWERTY1234 Confirmed. You have borrowed KSh500.00 via Fuliza M-PESA on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Borrowed funds", tx.subcategory)
    }

    @Test
    fun `ziidi saving and withdrawal are distinct non-earned movements`() {
        val deposit = MpesaParser.parseMessage("You have deposited KSh1,000.00 to Ziidi on 12/9/26 at 10:30 AM")
        val withdrawal = MpesaParser.parseMessage("You have withdrawn KSh300.00 from Ziidi to M-PESA on 12/9/26 at 10:30 AM")
        assertNotNull(deposit)
        assertNotNull(withdrawal)
        assertEquals(TransactionType.SAVING, deposit!!.type)
        assertEquals(TransactionType.INCOME, withdrawal!!.type)
        assertEquals("Ziidi transfer", withdrawal.subcategory)
        assertFalse(withdrawal.isEarnedIncome())
    }

    @Test
    fun `safaricom bundle alert is categorized as data`() {
        val tx = MpesaParser.parseMessage(
            "Confirmed. You have bought 1GB bundles for KSh99.00. Dial *544# for balance.",
            "SAFARICOM"
        )
        assertNotNull(tx)
        assertEquals("Data", tx!!.category)
        assertEquals("Safaricom Data", tx.merchant)
    }

    @Test
    fun `bare reversal without confirmed header parses as transfer`() {
        val sms = "QWERTY1234 You have successfully reversed KSh250.00 to John Doe"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(250.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.TRANSFER, tx.type)
    }

    @Test
    fun `expanded merchant keywords map to new categories`() {        assertEquals("Transport", MpesaParser.inferCategory("Matatu Fare", TransactionType.EXPENSE))
        assertEquals("Clothes", MpesaParser.inferCategory("Shirt Store", TransactionType.EXPENSE))
        assertEquals("Kujibamba", MpesaParser.inferCategory("Salon Beauty", TransactionType.EXPENSE))
        assertEquals("Health", MpesaParser.inferCategory("Pharmacy Plus", TransactionType.EXPENSE))
        assertEquals("Data", MpesaParser.inferCategory("Wifi Kenya", TransactionType.EXPENSE))
        assertEquals("Savings", MpesaParser.inferCategory("M-Shwari", TransactionType.SAVING))
        assertEquals("Transfers", MpesaParser.inferCategory("KCB transfer", TransactionType.INCOME))
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
    fun `send to a person named ziidi is spending not savings`() {
        // Audit verdict: bare "sent KSh X to ZIIDI" person-sends were eaten
        // as SAVING. Only product phrasing counts as the money-market fund.
        val sms = "UJ3CC8Z3YZ Confirmed. You have sent KSh20,829.00 to ZIIDI MWANZA on 3/10/26 at 7:08 PM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals("ZIIDI MWANZA", tx.merchant)
        assertFalse(tx.category.equals("Savings", ignoreCase = true))
    }

    @Test
    fun `fuliza fee marker needs both fuliza and fee words`() {
        assertTrue(MpesaParser.isFulizaFeeText("fuliza daily maintenance fee ksh 25"))
        assertTrue(MpesaParser.isFulizaFeeText("ksh 10 fuliza access fee charged"))
        assertFalse(MpesaParser.isFulizaFeeText("transaction cost ksh 7"))
        assertFalse(MpesaParser.isFulizaFeeText("fuliza disbursed ksh 1000"))
        assertFalse(MpesaParser.isFulizaFeeText("repaid fuliza ksh 500"))
    }

    @Test
    fun `fuliza maintenance fee types as charges not repayment or spend`() {
        // KSh125: too big for the generic small-amount cost lane, so the
        // dedicated Fuliza-charges typing applies (small fee-shaped debits
        // keep "Transaction Cost" and still count via the fee-row clause).
        val sms = "UJ3CC8Z3YZ Confirmed. You have sent KSh125.00 to FULIZA daily maintenance fee on 3/10/26 at 7:08 PM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals("Fuliza charges", tx.subcategory)
    }

    @Test
    fun `genuine ziidi product movement stays savings`() {
        val sms = "UJ3CC8Z3AA Confirmed. You have invested KSh5,000.00 in M-Pesa Ziidi MMF on 3/10/26 at 7:08 PM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.SAVING, tx!!.type)
        assertEquals("Ziidi transfer", tx.subcategory)
    }

    @Test
    fun `mpesa sender covers all five movement types to perfection`() {
        // The automated-tracking contract: sent, received, till, paybill,
        // withdrawal and airtime from MPESA all parse with code + amount.
        val sent = MpesaParser.parseMessage(
            "QWERTY1234 Confirmed. You have sent KSh1,250.00 to John Doe on 12/9/26 at 10:30 AM", "MPESA"
        )
        assertNotNull(sent)
        assertEquals(TransactionType.EXPENSE, sent!!.type)
        assertEquals("QWERTY1234", sent.sourceTransactionId)
        assertEquals(1250.0, sent.amount, 0.001)

        val received = MpesaParser.parseMessage(
            "QWERTY1235 Confirmed. You have received KSh2,000.00 from JANE WAMBUI on 12/9/26 at 11:00 AM", "MPESA"
        )
        assertNotNull(received)
        assertEquals(TransactionType.INCOME, received!!.type)
        assertEquals("QWERTY1235", received.sourceTransactionId)

        val till = MpesaParser.parseMessage(
            "QWERTY1236 Confirmed. KSh350.00 paid to Till 123456 - NAIVAS on 12/9/26 at 12:00 PM", "MPESA"
        )
        assertNotNull(till)
        assertEquals(TransactionType.EXPENSE, till!!.type)
        assertEquals("QWERTY1236", till.sourceTransactionId)

        val paybill = MpesaParser.parseMessage(
            "QWERTY1237 Confirmed. KSh500.00 sent to KPLC for account 12345 on 12/9/26 at 1:00 PM", "MPESA"
        )
        assertNotNull(paybill)
        assertEquals(TransactionType.EXPENSE, paybill!!.type)
        assertEquals("QWERTY1237", paybill.sourceTransactionId)

        val withdrawal = MpesaParser.parseMessage(
            "QW12ER34TY Confirmed. Withdraw KSh500.00 from JOHN AGENT New M-PESA balance is KSh1,200.00", "MPESA"
        )
        assertNotNull(withdrawal)
        // Cash in hand is a move, never spending — same rule both pipelines.
        assertEquals(TransactionType.TRANSFER, withdrawal!!.type)
        assertEquals("QW12ER34TY", withdrawal.sourceTransactionId)

        val airtime = MpesaParser.parseMessage(
            "QWERTY1238 Confirmed. You bought KSh50.00 of Airtime on 12/9/26 at 9:15 AM", "MPESA"
        )
        assertNotNull(airtime)
        assertEquals(TransactionType.EXPENSE, airtime!!.type)
        assertEquals("QWERTY1238", airtime.sourceTransactionId)
    }

    @Test
    fun `wifi providers file as bills without stealing phone data`() {
        assertEquals("Bills", MpesaParser.inferCategory("Faiba", TransactionType.EXPENSE))
        assertEquals("Bills", MpesaParser.inferCategory("Zuku Fibre", TransactionType.EXPENSE))
        assertEquals("Bills", MpesaParser.inferCategory("Mawingu", TransactionType.EXPENSE))
        assertEquals("Bills", MpesaParser.inferCategory("Poa Internet", TransactionType.EXPENSE))
        // Safaricom data/minutes stay phone-side no matter what.
        assertEquals("Data", MpesaParser.inferCategory("Safaricom Data", TransactionType.EXPENSE))
        assertEquals("Airtime", MpesaParser.inferCategory("Safaricom", TransactionType.EXPENSE))
        assertEquals("Data", MpesaParser.inferCategory("Zuku", TransactionType.EXPENSE))
    }

    @Test
    fun `real statement vocabulary lands in honest buckets`() {
        // Phrases lifted from genuine M-Pesa statements (not invented).
        assertEquals("Debt", MpesaParser.inferCategory("Customer Transfer Fuliza MPesa", TransactionType.EXPENSE, "Customer Transfer Fuliza MPesa to 0712 - JOHN"))
        assertEquals("HELB", MpesaParser.inferCategory("HELB STUDENTS DISBURSEMENT", TransactionType.INCOME))
        // Giving is deliberately unfiled: no Fundraising auto-category, so
        // church/charity flows land in reviewable Other instead of a bucket
        // most users never asked for.
        assertEquals("Other", MpesaParser.inferCategory("CHARITY MAINA", TransactionType.EXPENSE))
        assertEquals("Other", MpesaParser.inferCategory("WINNERS CHAPEL", TransactionType.EXPENSE))
        assertEquals("Other", MpesaParser.inferCategory("NAIROBI WATER", TransactionType.EXPENSE))
        assertEquals("Health", MpesaParser.inferCategory("SHA", TransactionType.EXPENSE))
        assertEquals("Health", MpesaParser.inferCategory("CYRUS ALMASI MEDICAL FUND", TransactionType.EXPENSE))
        assertEquals("Transport", MpesaParser.inferCategory("SUPER METRO", TransactionType.EXPENSE))
        assertEquals("Transfers", MpesaParser.inferCategory("AIRTEL MONEY, for Mobile No. 254756061190", TransactionType.EXPENSE))
        assertEquals("Transfers", MpesaParser.inferCategory("I&M Bank", TransactionType.EXPENSE))
        // Churchill the comedian is not a church offering — word boundary holds.
        assertEquals("Other", MpesaParser.inferCategory("CHURCHILL SHOW", TransactionType.EXPENSE))
    }

    @Test
    fun `reversal rows restore instead of earning`() {
        val csv = """
            Receipt No.,Completion Time,Details,Transaction Status,Paid In,Withdrawn,Balance
            QRV0000001,2026-09-03 10:00:00,Send Money Reversal via API to - Daniel,Completed,50.00,0.00,100.00
            QRV0000002,2026-09-03 10:05:00,Customer Withdrawal At Agent Till 441610 - Marone,Completed,0.00,-10000.00,5000.00
        """.trimIndent()
        val r = StatementImporter.parseStatementCsv(csv)
        assertEquals(2, r.rows.size)
        assertEquals(TransactionType.TRANSFER, r.rows.first { it.sourceTransactionId == "QRV0000001" }.type)
        assertEquals(TransactionType.TRANSFER, r.rows.first { it.sourceTransactionId == "QRV0000002" }.type)
        assertEquals("Transfers", r.rows.first { it.sourceTransactionId == "QRV0000002" }.category)
    }

    @Test
    fun `stripped paybill merchants still read as bills`() {
        val csv = """
            Receipt No.,Completion Time,Details,Transaction Status,Paid In,Withdrawn,Balance
            QPB0000001,2026-09-03 10:00:00,Pay Bill Online to 4131317 - OKAVA KENYA LTD,Completed,0.00,-258.00,1000.00
        """.trimIndent()
        val r = StatementImporter.parseStatementCsv(csv)
        assertEquals("Bills", r.rows.single().category)
    }

    @Test
    fun `person ziidi categorizes without fund context`() {
        assertEquals("Other", MpesaParser.inferCategory("ZIIDI MWANZA", TransactionType.EXPENSE))
        assertEquals("Savings", MpesaParser.inferCategory("M-Pesa Ziidi", TransactionType.EXPENSE, "M-Pesa Ziidi Deposit MMF"))
    }

    @Test
    fun `fuliza confirm prompt never parses as borrowing`() {
        // Prompts have no receipt code and move nothing yet.
        assertNull(MpesaParser.parseMessage("Confirm Fuliza of KES 500.00 to complete this transaction. Enter M-Pesa PIN."))
    }

    @Test
    fun `fuliza advanced wording parses as borrowing`() {
        val sms = "UJ3CC8Z3AB Confirmed. Advanced KES1,200.00 to your M-PESA via Fuliza on 3/10/26 at 7:08 PM. Access fee deducted."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals("Borrowed funds", tx.subcategory)
    }

    @Test
    fun `lowercase receipt code normalizes to uppercase for statement dedup`() {
        val sms = "uj3cc8z3yz Confirmed. You have sent KSh500.00 to Jane Wambui on 3/10/26 at 7:08 PM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        // Statement imports store receipts uppercase — SMS rows must match
        // exactly or the daily scan re-queues what the CSV already imported.
        assertEquals("UJ3CC8Z3YZ", tx!!.sourceTransactionId)
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
        assertEquals("Transfers", tx.category)
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
        assertEquals("Transfers", tx.category)
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
    fun `mshwari withdrawal parses as internal transfer`() {
        val sms = "QWERTY1234 Confirmed. You have transferred KSh1,000.00 from M-Shwari to M-PESA on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.TRANSFER, tx.type)
    }

    @Test
    fun `sacco deposit parses as saving`() {
        val sms = "QWERTY1234 Confirmed. You have deposited KSh500.00 to Stima Sacco on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.SAVING, tx.type)
        assertEquals("Savings", tx.category)
    }

    @Test
    fun `digital loan disbursement parses as income debt`() {
        val sms = "Your Tala loan of KSh3,000.00 has been approved and disbursed to M-PESA."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(3000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Debt", tx.category)
    }

    @Test
    fun `loan repayment parses as debt expense`() {
        val sms = "Loan repayment of KSh750.00 received from 0712345678. Thank you."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(750.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Debt", tx.category)
    }

    @Test
    fun `fuliza repaid parses as debt expense`() {
        val sms = "You have repaid KSh200.00 Fuliza amount. Outstanding balance KSh0.00."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(200.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Debt", tx.category)
        assertEquals("Fuliza repayment", tx.subcategory)
    }

    @Test
    fun `fuliza limit notice is skipped not income`() {
        assertNull(MpesaParser.parseMessage("Your Fuliza limit is KSh2,000.00. Available KSh2,000.00."))
        assertNull(MpesaParser.parseMessage("Fuliza balance KSh500.00. Repay to restore your limit."))
    }

    @Test
    fun `sender gate accepts carriers and banks but rejects spoofs and people`() {
        // Real IDs, exact and suffixed.
        assertTrue(MpesaParser.isOfficialSender("MPESA"))
        assertTrue(MpesaParser.isOfficialSender("M-PESA"))
        assertTrue(MpesaParser.isOfficialSender("Safaricom PLC"))
        assertTrue(MpesaParser.isOfficialSender("Equity Bank"))
        assertTrue(MpesaParser.isOfficialSender("Co-op Bank"))
        assertTrue(MpesaParser.isOfficialSender("KCB M-PESA"))
        assertTrue(MpesaParser.isOfficialSender("HELB"))
        assertTrue(MpesaParser.isOfficialSender("Unaitas Sacco"))
        assertTrue(MpesaParser.isOfficialSender("XYZ SACCO"))
        // Spoofs, people, shortcodes, blanks.
        assertFalse(MpesaParser.isOfficialSender("FAMILY-BLAST"))
        assertFalse(MpesaParser.isOfficialSender("John Doe"))
        assertFalse(MpesaParser.isOfficialSender("0722123456"))
        assertFalse(MpesaParser.isOfficialSender(""))
        assertFalse(MpesaParser.isOfficialSender("M-PESA Casino"))
    }

    @Test
    fun `safaricom promo ads never parse`() {
        assertNull(MpesaParser.parseMessage("Get unbeatable offers! Saksaka 20GB for KSh499 only. Dial *544# today.", "SAFARICOM"))
        assertNull(MpesaParser.parseMessage("Special offer just for you: FREE 1GB when you buy 5GB. Offer valid till Sunday.", "SAFARICOM"))
        assertNull(MpesaParser.parseMessage("Enjoy amazing discounts on Tunukiwa devices this weekend. Visit a Safaricom shop.", "SAFARICOM"))
        assertNull(MpesaParser.parseMessage("Pata 50% extra data on all weekly bundles. Promotion ends soon. STOP to opt out.", "SAFARICOM"))
        assertNull(MpesaParser.parseMessage("You have received 100MB free data valid 24hrs. Thank you for staying with us.", "SAFARICOM"))
        assertTrue(MpesaParser.isPromoAd("Get unbeatable offers! Dial *544# today."))
        assertTrue(MpesaParser.isPromoAd("Pata 50% extra data. Promotion ends soon."))
        assertFalse(MpesaParser.isPromoAd("Congratulations! You have earned cashback of KSh45.00 on your transaction."))
        assertFalse(MpesaParser.isPromoAd("QHX123 Confirmed. KSh1,000.00 sent to Jane on 20/9/26. New Mpesa balance is ksh0.00."))
    }

    @Test
    fun `cashback parses as income`() {
        val sms = "Congratulations! You have earned cashback of KSh45.00 on your transaction."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(45.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `savings interest parses as income`() {
        val sms = "M-Shwari interest of KSh12.50 has been credited to your savings."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(12.5, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Savings", tx.category)
    }

    @Test
    fun `failed transactions never parse`() {
        assertNull(MpesaParser.parseMessage("QWERTY1234 Transaction failed. You have insufficient funds to send KSh500.00."))
        assertNull(MpesaParser.parseMessage("Sorry, your request for KSh100.00 was unsuccessful. Please try again."))
        assertNull(MpesaParser.parseMessage("Transaction cancelled. KSh250.00 not sent."))
    }

    @Test
    fun `generic confirmed received types as income`() {
        val sms = "QWERTY1234 Confirmed. KSh1,200.00 received from HELB upkeep on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1200.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertTrue(tx.confidenceScore < 0.75f)
    }

    @Test
    fun `generic confirmed transfer types as transfer`() {
        val sms = "QWERTY1234 Confirmed. KSh2,000.00 transferred to KCB account on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(2000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.TRANSFER, tx.type)
    }

    @Test
    fun `generic confirmed paid types as expense`() {
        val sms = "QWERTY1234 Confirmed. KSh350.00 paid to Quickmart Lavington on 12/9/26 at 10:30 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(350.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun `new keyword inference covers loans insurance and fuel`() {
        assertEquals("Debt", MpesaParser.inferCategory("Tala Kenya", TransactionType.EXPENSE))
        assertEquals("Savings", MpesaParser.inferCategory("Stima Sacco", TransactionType.EXPENSE))
        assertEquals("Health", MpesaParser.inferCategory("SHIF contribution", TransactionType.EXPENSE))
        assertEquals("Kujibamba", MpesaParser.inferCategory("Netflix subscription", TransactionType.EXPENSE))
        assertEquals("Transport", MpesaParser.inferCategory("Shell fuel", TransactionType.EXPENSE))
        assertEquals("School", MpesaParser.inferCategory("exam fee", TransactionType.EXPENSE))
        assertEquals("Other", MpesaParser.inferCategory("Coffee house", TransactionType.EXPENSE))
    }

    @Test
    fun `salary credit without bank name parses as income`() {
        val sms = "Your salary of KES 45,000.00 has been processed."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(45000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `standing order parses as expense with payee`() {
        val sms = "Your standing order of KSh 2,500.00 to Zuku has been effected."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(2500.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertTrue(tx.merchant.contains("Zuku"))
    }

    @Test
    fun `cleared cheque parses as income`() {
        val sms = "Cheque no. 123456 of KES 15,000.00 cleared."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(15000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `bare atm cash out parses as transfer`() {
        val sms = "ATM withdrawal of KSh 5,000.00 at Kenyatta Ave."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(5000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.TRANSFER, tx.type)
    }

    @Test
    fun `card pos purchase parses merchant as expense`() {
        val sms = "POS purchase of KES 1,200.00 at Naivas Westlands."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(1200.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertTrue(tx.merchant.contains("Naivas"))
        assertEquals("Shopping", tx.category)
    }

    @Test
    fun `okoa without ksh marker parses`() {
        val sms = "Enjoy! Okoa 50 bob advanced to your line."
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals(50.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun `loan offers without movement never parse`() {
        assertNull(MpesaParser.parseMessage("You are eligible for a Tala loan of up to KES 30,000. Apply now!"))
        assertNull(MpesaParser.parseMessage("Your loan repayment of KSh 750 is due tomorrow. Pay via M-PESA."))
    }
}
