package com.pesaflow.app.parsers

import com.pesaflow.app.data.academic.tsMs
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.bills.matchBillPayments
import com.pesaflow.app.ui.dashboard.semesterRunway
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


private val bday = 24L * 60 * 60 * 1000
private val bnow = tsMs(2026, Calendar.OCTOBER, 15, 12)

private fun bexp(amount: Double, at: Long, merchant: String, source: TransactionSource = TransactionSource.MPESA_SMS): Transaction =
    Transaction(
        amount = amount,
        type = TransactionType.EXPENSE,
        category = "Rent",
        dateTimestamp = at,
        merchant = merchant,
        description = "",
        paymentMethod = PaymentMethod.MPESA,
        source = source
    )

private fun bill(name: String, amount: Double, dueInDays: Long, status: String = "UNPAID"): Bill =
    Bill(name = name, amount = amount, dueDate = bnow + dueInDays * bday, category = "Rent", status = status)


class BillMatchTest {

    @Test
    fun `paid bill never matches`() {
        val bills = listOf(bill("Hostel", 8000.0, 7, "PAID"))
        // Exact merchant + amount: would match if UNPAID — proves the filter.
        val txs = listOf(bexp(8000.0, bnow - 2 * bday, "Hostel"))
        assertTrue(matchBillPayments(bills, txs, bnow).isEmpty())
    }

    @Test
    fun `exact merchant and amount matches`() {
        val bills = listOf(bill("KPLC", 500.0, 7))
        val txs = listOf(bexp(500.0, bnow - 2 * bday, "KPLC"))
        val matches = matchBillPayments(bills, txs, bnow)
        assertEquals(1, matches.size)
        assertEquals(500.0, matches.values.first().amount, 0.001)
    }

    @Test
    fun `amount outside tolerance does not match`() {
        val bills = listOf(bill("KPLC", 500.0, 7))
        val txs = listOf(bexp(900.0, bnow - 2 * bday, "KPLC"))
        assertTrue(matchBillPayments(bills, txs, bnow).isEmpty())
    }

    @Test
    fun `sample rows never match bills`() {
        val bills = listOf(bill("Hostel", 8000.0, 7))
        val txs = listOf(bexp(8000.0, bnow - 2 * bday, "Hostel", TransactionSource.SAMPLE))
        assertTrue(matchBillPayments(bills, txs, bnow).isEmpty())
    }

    @Test
    fun `semester runway danger when bills exceed balance`() {
        val bills = listOf(bill("Hostel", 5000.0, 10))
        val r = semesterRunway(emptyList(), bills, 100.0, bnow)
        assertTrue(r.daysLeft >= 1)
        assertEquals(0.0, r.discretionary, 0.001)
        assertTrue(r.alert?.contains("Danger") == true)
    }

    @Test
    fun `semester runway warns when pace burns before term end`() {
        val txs = (0 until 14).map { bexp(500.0, bnow - it * bday, "Kibanda") }
        val r = semesterRunway(txs, emptyList(), 2000.0, bnow)
        assertNotNull(r.runwayDays)
        assertTrue("runway=${r.runwayDays} daysLeft=${r.daysLeft}", r.runwayDays!! < r.daysLeft)
        assertTrue(r.alert?.contains("Runway") == true)
    }

    @Test
    fun `semester runway stays quiet when flush`() {
        val r = semesterRunway(emptyList(), emptyList(), 50000.0, bnow)
        assertNull(r.runwayDays)
        assertNull(r.alert)
        assertTrue(r.dailyToSemesterEnd > 0)
    }
}
