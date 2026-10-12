package com.pesaflow.app.parsers

import com.pesaflow.app.data.ledger.ContactMemory
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isFeeRow
import com.pesaflow.app.data.parsers.StatementImporter
import org.junit.Assert.*
import org.junit.Test


// Statement import behaves like an SMS scan: Receipt No. is the dedup code,
// merchants map through the same category inference, remembered faces stamp.
class StatementImporterTest {

    private val mpesaCsv = """
        Receipt No.,Completion Time,Details,Transaction Status,Paid In,Withdrawn,Balance
        QHX1A2B3C4D,2024-06-15 10:30:45,"Customer Transfer to 0712345678 - JANE WAMBUI",COMPLETED,0.00,500.00,12000.00
        QHX9Z8Y7X6W,2024-06-16 14:00:00,"Merchant Payment to 123456 - NAIVAS SUPERMARKET",COMPLETED,0.00,2500.00,9500.00
        QHXMNBVCXZ1,2024-06-17 09:00:00,"M-Shwari Deposit",COMPLETED,0.00,1000.00,8500.00
        QHXFAIL00001,2024-06-18 09:00:00,"Customer Transfer to 0700000000 - GHOST",FAILED,0.00,300.00,8500.00
        QHXINCOME01,2024-06-19 12:00:00,"Funds received from 0711111111 - HELB",COMPLETED,20000.00,0.00,28500.00
    """.trimIndent()

    @Test
    fun `mpesa csv maps direction type and receipt code`() {
        val r = StatementImporter.parseStatementCsv(mpesaCsv)
        assertEquals(4, r.rows.size)
        assertEquals(1, r.skipped)
        val out = r.rows.first { it.sourceTransactionId == "QHX1A2B3C4D" }
        assertEquals(TransactionType.EXPENSE, out.type)
        assertEquals(500.0, out.amount, 0.001)
        assertEquals("JANE WAMBUI", out.merchant)
        val saving = r.rows.first { it.sourceTransactionId == "QHXMNBVCXZ1" }
        assertEquals(TransactionType.SAVING, saving.type)
        val income = r.rows.first { it.sourceTransactionId == "QHXINCOME01" }
        assertEquals(TransactionType.INCOME, income.type)
        // Every row carries the receipt — the pending dedup sees statement
        // rows and SMS rows as one event, never double-books.
        assertTrue(r.rows.all { !it.sourceTransactionId.isNullOrBlank() })
        // Newest balance tail is harvested like SMS balance tails.
        assertEquals(28500.0, r.latestBalance ?: 0.0, 0.001)
    }

    @Test
    fun `mpesa csv infers categories like sms`() {
        val r = StatementImporter.parseStatementCsv(mpesaCsv)
        val naivas = r.rows.first { it.sourceTransactionId == "QHX9Z8Y7X6W" }
        assertEquals("Shopping", naivas.category)
        val saving = r.rows.first { it.sourceTransactionId == "QHXMNBVCXZ1" }
        assertEquals("Savings", saving.category)
    }

    @Test
    fun `contact memory stamps statement rows`() {
        val memories = mapOf(
            "Jane Wambui" to ContactMemory(label = "Sister", category = "Upkeep", scope = "BOTH")
        )
        val r = StatementImporter.parseStatementCsv(mpesaCsv, memories)
        val jane = r.rows.first { it.sourceTransactionId == "QHX1A2B3C4D" }
        assertEquals("Upkeep", jane.category)
        assertTrue(jane.displayMerchant.contains("Sister"))
    }

    @Test
    fun `generic csv falls back to pending rows`() {
        val csv = """
            Date,Description,Amount,Category
            2024-06-15,Naivas Supermarket,-2500.00,Shopping
            2024-06-16,HELB,20000.00,
        """.trimIndent()
        val r = StatementImporter.parseStatementCsv(csv)
        assertEquals("GENERIC_CSV", r.fileKind)
        assertEquals(2, r.rows.size)
        assertEquals(TransactionType.EXPENSE, r.rows[0].type)
        assertEquals(TransactionType.INCOME, r.rows[1].type)
        // Blank category infers; explicit category passes through.
        // HELB upkeep gets its own bucket, never blended into Salary.
        assertEquals("Shopping", r.rows[0].category)
        assertEquals("HELB", r.rows[1].category)
    }

    @Test
    fun `pdf text rows parse like csv rows`() {
        val text = """
            M-PESA Statement
            QHX1A2B3C4D 2024-06-15 10:30:45 Customer Transfer to 0712345678 - JANE WAMBUI COMPLETED 0.00 500.00 12000.00
            QHX9Z8Y7X6W 2024-06-16 14:00:00 Merchant Payment to 123456 - NAIVAS SUPERMARKET COMPLETED 0.00 2500.00 9500.00
        """.trimIndent()
        val r = StatementImporter.parseStatementPdfText(text)
        assertEquals("MPESA_PDF", r.fileKind)
        assertEquals(2, r.rows.size)
        assertEquals("JANE WAMBUI", r.rows[0].merchant)
        assertEquals("QHX1A2B3C4D", r.rows[0].sourceTransactionId)
    }

    @Test
    fun `pdf tolerates whole-shilling amounts and dd-Mon dates`() {
        val text = """
            QHX1A2B3C4D 15-Jun-2024 10:30 Customer Transfer to 0712 - JANE COMPLETED 0 500 12000
            QHX9Z8Y7X6W 2024-06-16 14:00 Merchant Payment NAIVAS FAILED 0 100 11900
        """.trimIndent()
        val r = StatementImporter.parseStatementPdfText(text)
        assertEquals(1, r.rows.size)
        assertEquals(1, r.skipped)
        assertEquals(2, r.candidates)
        assertEquals(500.0, r.rows[0].amount, 0.001)
    }

    @Test
    fun `real statement layout with negatives and wrapped details`() {
        val text = """
            SUMMARY
            UJ3CC8Z3YZ 2026-10-03 19:08:56 Unit Trust Invest To 4145555 -
            ZIIDI MMF by M-PESA\UnitTrust Completed  -50.00 240.04
            UJ3CC8YZS4 2026-10-03 19:08:40 Customer Transfer to -
            0707***786 DANIEL MAYOLI Completed  -500.00 290.04
        """.trimIndent()
        val r = StatementImporter.parseStatementPdfText(text)
        assertEquals(2, r.candidates)
        assertEquals(2, r.rows.size)
        assertEquals(50.0, r.rows[0].amount, 0.001)
        assertEquals(TransactionType.SAVING, r.rows[0].type)
        assertEquals(500.0, r.rows[1].amount, 0.001)
        assertEquals("DANIEL MAYOLI", r.rows[1].merchant)
    }

    @Test
    fun `first-page summary totals parse without disturbing rows`() {
        val text = """
            SUMMARY
            SEND MONEY: 0.00 63,637.00
            RECEIVED MONEY: 117,023.67 0.00
            OTHERS: 104,952.85 70,830.68
            TOTAL: 225,803.01 225,562.97
            UJ3CC8Z3YZ 2026-10-03 19:08:56 Unit Trust Invest Completed -50.00 240.04
        """.trimIndent()
        val r = StatementImporter.parseStatementPdfText(text)
        assertEquals(225803.01, r.summary.totalIn, 0.01)
        assertEquals(225562.97, r.summary.totalOut, 0.01)
        assertEquals(63637.0, r.summary.byType["SEND MONEY"]?.second ?: 0.0, 0.01)
        assertEquals(1, r.rows.size)
        assertEquals(50.0, r.rows[0].amount, 0.001)
    }

    @Test
    fun `amount tails assign by sign with footer immunity`() {
        assertEquals(
            Triple(0.0, 500.0, 12000.0),
            StatementImporter.splitStatementAmounts("0.00 500.00 12000.00")
        )
        assertEquals(
            Triple(0.0, 50.0, 240.04),
            StatementImporter.splitStatementAmounts("-50.00 240.04 Page 2 of 67")
        )
        assertEquals(
            Triple(800.0, 0.0, 800.0),
            StatementImporter.splitStatementAmounts("800.00 800.00")
        )
        assertEquals(
            Triple(0.0, 0.0, null),
            StatementImporter.splitStatementAmounts("no numbers here")
        )
    }

    @Test
    fun `same receipt pair survives with distinct amounts`() {
        // Real statements reuse one receipt for a payment and its reversal
        // (UI3CC5RVM: -70 withdrawn AND 70.00 paid). Code-only dedup would
        // eat one of them; the pair must both parse with the same code.
        val text = """
            UI3CC5RVM 2026-09-03 17:31:56 Merchant Payment Fuliza M-Pesa to 6533524 - Benard Justus Ongele Completed -70.00 0.00
            UI3CC5RVM 2026-09-03 17:31:56 OverDraft of Credit Party Completed 70.00 70.00
        """.trimIndent()
        val r = StatementImporter.parseStatementPdfText(text)
        assertEquals(2, r.rows.size)
        assertTrue(r.rows.all { it.sourceTransactionId == "UI3CC5RVM" })
        assertEquals(70.0, r.rows[0].amount, 0.001)
        assertEquals(70.0, r.rows[1].amount, 0.001)
        assertEquals("Debt", r.rows[1].category)
    }

    @Test
    fun `overdraft reads as debt both directions`() {
        val text = """
            UJ3CC8Z3AA 2026-10-03 18:52:14 OverDraft of Credit Party Completed 40.00 40.00
            UJ3CC8Z3AB 2026-10-03 18:52:14 OD Loan Repayment to 3201003 - Fuliza II Overdraw Completed -90.00 0.00
        """.trimIndent()
        val r = StatementImporter.parseStatementPdfText(text)
        assertEquals(2, r.rows.size)
        val borrowed = r.rows.first { it.sourceTransactionId == "UJ3CC8Z3AA" }
        assertEquals(TransactionType.INCOME, borrowed.type)
        assertEquals("Debt", borrowed.category)
        val repaid = r.rows.first { it.sourceTransactionId == "UJ3CC8Z3AB" }
        assertEquals(TransactionType.EXPENSE, repaid.type)
        assertEquals("Debt", repaid.category)
    }

    @Test
    fun `bill and airtime rows categorize by merchant words`() {
        val csv = """
            Receipt No.,Completion Time,Details,Transaction Status,Paid In,Withdrawn,Balance
            QBILL00001,2026-09-03 17:16:04,Pay Bill Charge,Completed,0.00,-10.00,0.00
            QBILL00002,2026-09-03 17:16:04,Pay Bill 222111 - Family Bank,Completed,0.00,-100.00,664.02
            QRECH00002,2026-09-04 11:32:33,Recharge for Customer to 4093441SAFARICOM DATA BUNDLES by - 254717***112 EMMANUEL MAYOLI,Completed,0.00,-20.00,0.00
        """.trimIndent()
        val r = StatementImporter.parseStatementCsv(csv)
        assertEquals(3, r.rows.size)
        assertEquals("Bills", r.rows.first { it.sourceTransactionId == "QBILL00001" }.category)
        // Bank-routed bill payments stay Transfers, exactly like SMS bank moves.
        assertEquals("Transfers", r.rows.first { it.sourceTransactionId == "QBILL00002" }.category)
        val recharge = r.rows.first { it.sourceTransactionId == "QRECH00002" }
        assertEquals("Safaricom Data", recharge.merchant)
        assertEquals("Data", recharge.category)
    }

    @Test
    fun `charge rows flag fees and withdrawals move as transfers`() {
        val csv = """
            Receipt No.,Completion Time,Details,Transaction Status,Paid In,Withdrawn,Balance
            QWD0000001,2026-09-03 10:00:00,Cash Withdrawal - 12345 - JOHN DOE,Completed,0.00,-500.00,9500.00
            QFEE000001,2026-09-03 10:05:00,Withdrawal Charge,Completed,0.00,-28.65,9471.35
        """.trimIndent()
        val r = StatementImporter.parseStatementCsv(csv)
        assertEquals(2, r.rows.size)
        // Same figures as the SMS path: cash out is a move, never spending.
        val cash = r.rows.first { it.sourceTransactionId == "QWD0000001" }
        assertEquals(TransactionType.TRANSFER, cash.type)
        assertEquals("Transfers", cash.category)
        // Fee lines stay outflows but ride the fee pot, never spending.
        val fee = r.rows.first { it.sourceTransactionId == "QFEE000001" }
        assertEquals(TransactionType.EXPENSE, fee.type)
        assertEquals("Transaction Cost", fee.subcategory)
        assertTrue(fee.isFeeRow())
        // Wallet flow reconciles straight from the money columns.
        assertEquals(0.0, r.walletIn, 0.001)
        assertEquals(528.65, r.walletOut, 0.001)
    }

    @Test
    fun `send to a person named ziidi is spending not savings`() {
        // Audit verdict: 936 Ziidi SAVING rows. Only product phrasing
        // (M-Pesa Ziidi, MMF, invest/…) counts as the money-market fund.
        val csv = """
            Receipt No.,Completion Time,Details,Transaction Status,Paid In,Withdrawn,Balance
            QZII000001,2026-09-13 10:00:00,Customer Transfer to 0712345678 - ZIIDI MWANZA,Completed,0.00,-20829.00,12000.00
            QZII000002,2026-09-13 10:05:00,M-Pesa Ziidi Deposit MMF,Completed,0.00,-5000.00,7000.00
        """.trimIndent()
        val r = StatementImporter.parseStatementCsv(csv)
        assertEquals(2, r.rows.size)
        val person = r.rows.first { it.sourceTransactionId == "QZII000001" }
        assertEquals(TransactionType.EXPENSE, person.type)
        assertEquals("ZIIDI MWANZA", person.merchant)
        val product = r.rows.first { it.sourceTransactionId == "QZII000002" }
        assertEquals(TransactionType.SAVING, product.type)
        assertEquals("Ziidi transfer", product.subcategory)
    }

    @Test
    fun `od loan repayment flags deni but fuliza funded sends do not`() {
        // Research-backed statement phrasings: "OD Loan Repayment to" and
        // "OverDraft of Credit Party" (open-source M-Pesa parsers confirm).
        val csv = """
            Receipt No.,Completion Time,Details,Transaction Status,Paid In,Withdrawn,Balance
            QOD0000001,2026-09-03 10:00:00,OD Loan Repayment to 3201003,Completed,0.00,-90.00,1000.00
            QOD0000002,2026-09-03 10:05:00,Customer Transfer Fuliza MPesa to 0712345678 - JOHN DOE,Completed,0.00,-500.00,500.00
        """.trimIndent()
        val r = StatementImporter.parseStatementCsv(csv)
        assertEquals(2, r.rows.size)
        val repay = r.rows.first { it.sourceTransactionId == "QOD0000001" }
        assertEquals("Fuliza repayment", repay.subcategory)
        // A Fuliza-funded send is new borrowing out — it must never reduce
        // the outstanding deni like a repayment does.
        val spend = r.rows.first { it.sourceTransactionId == "QOD0000002" }
        assertEquals(TransactionType.EXPENSE, spend.type)
        assertEquals("Borrowed spend", spend.subcategory)
        assertFalse(spend.isFeeRow())
    }

    @Test
    fun `wallet flow sums completed columns only`() {
        val r = StatementImporter.parseStatementCsv(mpesaCsv)
        // HELB 20000 in; 500 + 2500 + 1000 out (FAILED row moves nothing).
        assertEquals(20000.0, r.walletIn, 0.001)
        assertEquals(4000.0, r.walletOut, 0.001)
    }

    @Test
    fun `csv with bom preamble and semicolons still parses`() {
        val csv = "\uFEFFM-PESA Statement for 0712345678\n" +
            "Generated: 2026-10-05\n" +
            "\n" +
            "Receipt No.;Completion Time;Details;Transaction Status;Paid In;Withdrawn;Balance\n" +
            "QHX1A2B3C4D;2024-06-15 10:30:45;Customer Transfer to 0712345678 - JANE WAMBUI;COMPLETED;0.00;500.00;12000.00\n"
        val r = StatementImporter.parseStatementCsv(csv)
        assertEquals(1, r.rows.size)
        assertEquals("QHX1A2B3C4D", r.rows[0].sourceTransactionId)
        assertEquals(500.0, r.rows[0].amount, 0.001)
        assertEquals(500.0, r.walletOut, 0.001)
    }

    @Test
    fun `csv failure names the header found`() {
        val r = StatementImporter.parseStatementCsv("Just some notes\nno tabular data here\n")
        assertTrue(r.rows.isEmpty())
        assertTrue(r.headerEcho.isNotBlank())
    }

    @Test
    fun `masked X numbers strip to the person`() {
        assertEquals(
            "JOHN WAMBUA",
            StatementImporter.cleanStatementMerchant("Customer Payment to Small Business to - 25472X***804 JOHN WAMBUA")
        )
        assertEquals(
            "NANCY KAMUYU",
            StatementImporter.cleanStatementMerchant("Small Business Payment to Customer via API from 254725***355 NANCY KAMUYU")
        )
    }

    @Test
    fun `statement fee rows are recognized by charge wording`() {
        fun row(merchant: String, type: TransactionType, category: String) = PendingTransaction(
            amount = 10.0, type = type, category = category, merchant = merchant,
            dateTimestamp = System.currentTimeMillis(), paymentMethod = PaymentMethod.MPESA,
            source = TransactionSource.CSV_IMPORT, sourceTransactionId = "QTEST",
            rawText = "Statement: QTEST · $merchant"
        )
        assertTrue(StatementImporter.isStatementFeeRow(row("Withdrawal Charge", TransactionType.EXPENSE, "Other")))
        assertTrue(StatementImporter.isStatementFeeRow(row("Pay Bill Charge", TransactionType.EXPENSE, "Bills")))
        // Ordinary spend, school fees, and money-in never feed the fee pot.
        assertFalse(StatementImporter.isStatementFeeRow(row("NAIVAS SUPERMARKET", TransactionType.EXPENSE, "Shopping")))
        assertFalse(StatementImporter.isStatementFeeRow(row("School Fees Payment", TransactionType.EXPENSE, "School")))
        assertFalse(StatementImporter.isStatementFeeRow(row("Safaricom", TransactionType.EXPENSE, "Airtime")))
        assertFalse(StatementImporter.isStatementFeeRow(row("Withdrawal Charge", TransactionType.INCOME, "Other")))
    }

    @Test
    fun `statement scan result mirrors sms scan figures`() {
        val dayMs = 24L * 60 * 60 * 1000
        // Fixed mid-month anchor: month-to-date assertions stay deterministic
        // in any machine timezone (midday rows never straddle month edges).
        val now = java.util.Calendar.getInstance().apply {
            clear()
            set(2026, java.util.Calendar.OCTOBER, 11, 12, 0)
        }.timeInMillis
        fun row(merchant: String, amount: Double, type: TransactionType, category: String, ts: Long) = PendingTransaction(
            amount = amount, type = type, category = category, merchant = merchant,
            dateTimestamp = ts, paymentMethod = PaymentMethod.MPESA,
            source = TransactionSource.CSV_IMPORT, sourceTransactionId = "Q$merchant",
            rawText = "Statement: $merchant"
        )
        val rows = listOf(
            row("MATATU FARE", 100.0, TransactionType.EXPENSE, "Transport", now - 40 * dayMs),
            row("HELB", 20000.0, TransactionType.INCOME, "Salary", now - 40 * dayMs),
            // Charge lines ride the fee pot, never spending — same as SMS tails.
            row("Withdrawal Charge", 28.65, TransactionType.EXPENSE, "Other", now - dayMs)
                .copy(subcategory = "Transaction Cost"),
            row("KIBANDA", 60.0, TransactionType.EXPENSE, "Food", now - dayMs)
        )
        val r = StatementImporter.statementScanResult(rows, skipped = 1, nowMs = now)
        // Same rules as the inbox scan: earned income in, EXPENSE out + byCategory.
        assertEquals(20000.0, r.incomeTotal, 0.001)
        assertEquals(160.0, r.expenseTotal, 0.001)
        assertEquals(100.0, r.byCategory["Transport"] ?: 0.0, 0.001)
        assertEquals("rows", r.label)
        assertEquals(5, r.found)
        assertEquals(1, r.unreadable)
        assertEquals(0.8, r.readRate, 0.001)
        assertEquals(4, r.byMonth.values.sum())
        assertTrue(r.topSenders.any { it.first == "MATATU FARE" && it.second == 1 })
        // Pace divides by months covered, never a hardcoded window.
        assertEquals(160.0 / (41 / 30.0), r.monthlyExpense, 0.01)
        // Fee bleed totals the whole scan; month-to-date fills mid-month opens.
        assertEquals(28.65, r.feeTotal, 0.001)
        assertEquals(0.0, r.mtdIncome, 0.001)
        assertEquals(60.0, r.mtdExpense, 0.001)
        assertEquals(11, r.mtdDays)
    }

    @Test
    fun `masked phones of every safaricom shape strip to the person`() {
        assertEquals("JOAN RJUCHU", StatementImporter.cleanStatementMerchant("Customer Transfer to - 0113***142 JOAN RJUCHU"))
        assertEquals("JOAN RJUCHU", StatementImporter.cleanStatementMerchant("Customer Transfer to - 0711***142 JOAN RJUCHU"))
        assertEquals("NANCY KAMUYU", StatementImporter.cleanStatementMerchant("Funds received from - 254725***355 NANCY KAMUYU"))
        assertEquals("DANIEL MAYOLI", StatementImporter.cleanStatementMerchant("Customer Transfer to - 0707***786 DANIEL MAYOLI"))
    }

    @Test
    fun `merchant cleaning strips phones and tills`() {
        assertEquals("JANE WAMBUI", StatementImporter.cleanStatementMerchant("Customer Transfer to 0712345678 - JANE WAMBUI"))
        assertEquals("NAIVAS", StatementImporter.cleanStatementMerchant("Merchant Payment to 123456 - NAIVAS"))
        assertEquals("Safaricom", StatementImporter.cleanStatementMerchant("Airtime Purchase"))
        assertEquals("Unknown Party", StatementImporter.cleanStatementMerchant("   "))
    }
}
