package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.*
import com.pesaflow.app.ui.analytics.dailySpendSeries
import org.junit.Assert.*
import org.junit.Test

class VelocitySeriesTest {

    private val dayMs = 24L * 60 * 60 * 1000
    private val now = 1_750_000_000_000L

    private fun expense(amount: Double, daysAgo: Int): Transaction {
        return Transaction(
            amount = amount, type = TransactionType.EXPENSE, category = "Food",
            merchant = "Kibanda", dateTimestamp = now - daysAgo * dayMs
        )
    }

    @Test
    fun `buckets are time uniform oldest to newest`() {
        // Two lunches 6 days ago, rent yesterday: the rent day — not the
        // last point by position — carries the spike.
        val txs = listOf(expense(150.0, 6), expense(150.0, 6), expense(12000.0, 1))
        val series = dailySpendSeries(txs, 7, now)
        assertEquals(7, series.size)
        assertEquals(300.0, series[0], 0.001)
        assertEquals(0.0, series[3], 0.001)
        assertEquals(12000.0, series[5], 0.001)
        assertEquals(0.0, series[6], 0.001)
    }

    @Test
    fun `window excludes out of range rows`() {
        val txs = listOf(expense(500.0, 40), expense(100.0, 2))
        val series = dailySpendSeries(txs, 7, now)
        assertEquals(100.0, series.sum(), 0.001)
    }

    @Test
    fun `samples and income never count`() {
        val sample = expense(9999.0, 1).copy(isSample = true)
        val income = Transaction(
            amount = 50000.0, type = TransactionType.INCOME, category = "Salary",
            merchant = "HELB", dateTimestamp = now - dayMs
        )
        val series = dailySpendSeries(listOf(sample, income), 7, now)
        assertEquals(0.0, series.sum(), 0.001)
    }

    @Test
    fun `empty and zero windows`() {
        assertTrue(dailySpendSeries(emptyList(), 7, now).all { it == 0.0 })
        assertTrue(dailySpendSeries(emptyList(), 0, now).isEmpty())
    }
}
