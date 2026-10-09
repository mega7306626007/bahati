package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.*
import com.pesaflow.app.data.models.*
import org.junit.Assert.*
import org.junit.Test

class FulizaTotalsTest {

    private fun tx(
        amount: Double,
        type: TransactionType,
        merchant: String = "Fuliza",
        subcategory: String = "",
        description: String = ""
    ): Transaction {
        return Transaction(
            amount = amount, type = type, category = "Debt", merchant = merchant,
            subcategory = subcategory, description = description,
            dateTimestamp = System.currentTimeMillis()
        )
    }

    private fun borrowed(amount: Double): Transaction =
        tx(amount, TransactionType.INCOME, subcategory = "Borrowed funds")

    private fun repaid(amount: Double): Transaction =
        tx(amount, TransactionType.EXPENSE, subcategory = "Fuliza repayment")

    private fun charged(amount: Double): Transaction =
        tx(amount, TransactionType.EXPENSE, subcategory = "Fuliza charges")

    @Test
    fun `borrowed minus repaid is outstanding`() {
        val totals = fulizaTotals(listOf(borrowed(1000.0), borrowed(500.0), repaid(400.0)))
        assertEquals(1500.0, totals.borrowed, 0.001)
        assertEquals(400.0, totals.repaid, 0.001)
        assertEquals(1100.0, totals.outstanding, 0.001)
        assertEquals(2, totals.borrowCount)
        assertEquals(1, totals.repayCount)
    }

    @Test
    fun `outstanding never goes negative`() {
        val totals = fulizaTotals(listOf(borrowed(500.0), repaid(800.0)))
        assertEquals(0.0, totals.outstanding, 0.001)
    }

    @Test
    fun `charges never inflate repaid`() {
        val totals = fulizaTotals(listOf(borrowed(1000.0), charged(25.0), charged(6.0)))
        assertEquals(0.0, totals.repaid, 0.001)
        assertEquals(31.0, totals.chargesObserved, 0.001)
        assertEquals(2, totals.chargeCount)
        assertEquals(1000.0, totals.outstanding, 0.001)
    }

    @Test
    fun `borrowed spend is neither repaid nor charges`() {
        val spend = tx(300.0, TransactionType.EXPENSE, subcategory = "Borrowed spend")
        val totals = fulizaTotals(listOf(borrowed(1000.0), repaid(200.0), spend, charged(5.0)))
        assertEquals(200.0, totals.repaid, 0.001)
        assertEquals(5.0, totals.chargesObserved, 0.001)
        assertEquals(800.0, totals.outstanding, 0.001)
    }

    @Test
    fun `access estimate is one percent of borrowed`() {
        val totals = fulizaTotals(listOf(borrowed(1000.0)))
        assertEquals(10.0, totals.accessEstimate, 0.001)
    }

    @Test
    fun `tariff bands match published rates`() {
        assertEquals(0.0, dailyRateFor(50.0), 0.001)
        assertEquals(2.5, dailyRateFor(300.0), 0.001)
        assertEquals(5.0, dailyRateFor(800.0), 0.001)
        assertEquals(18.0, dailyRateFor(1200.0), 0.001)
        assertEquals(20.0, dailyRateFor(2000.0), 0.001)
        assertEquals(25.0, dailyRateFor(5000.0), 0.001)
        assertEquals(10.0, accessFeeFor(1000.0), 0.001)
        assertTrue(waiverApplies(500.0))
        assertFalse(waiverApplies(1500.0))
    }

    @Test
    fun `fee rows mentioning fuliza count as charges`() {
        val fee = tx(
            7.0, TransactionType.EXPENSE, merchant = "M-Pesa",
            subcategory = "Transaction Cost", description = "Transaction cost KSh 7 Fuliza daily fee"
        )
        val totals = fulizaTotals(listOf(borrowed(1000.0), fee))
        assertEquals(7.0, totals.chargesObserved, 0.001)
    }

    @Test
    fun `lifetime fees sum fee rows only`() {
        val txs = listOf(
            tx(7.0, TransactionType.EXPENSE, subcategory = "Transaction Cost"),
            tx(500.0, TransactionType.EXPENSE, merchant = "Naivas")
        )
        assertEquals(7.0, lifetimeFeeTotal(txs), 0.001)
    }

    @Test
    fun `empty ledger totals zero`() {
        val totals = fulizaTotals(emptyList())
        assertEquals(0.0, totals.borrowed, 0.001)
        assertEquals(0.0, totals.outstanding, 0.001)
        assertEquals(0, totals.borrowCount)
    }
}
