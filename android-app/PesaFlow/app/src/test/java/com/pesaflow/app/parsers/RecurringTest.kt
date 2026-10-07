package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.analytics.detectRecurring
import com.pesaflow.app.ui.analytics.predictPaydays
import org.junit.Assert.*
import org.junit.Test


private fun tx(merchant: String, amount: Double, daysAgo: Long, type: TransactionType = TransactionType.EXPENSE): Transaction {
    return Transaction(
        amount = amount,
        type = type,
        category = "Other",
        dateTimestamp = System.currentTimeMillis() - daysAgo * 24L * 60 * 60 * 1000,
        merchant = merchant
    )
}


class RecurringTest {

    @Test
    fun `monthly rhythm detected`() {
        val txs = listOf(
            tx("Netflix", 500.0, 62),
            tx("Netflix", 500.0, 32),
            tx("Netflix", 500.0, 2)
        )
        val hits = detectRecurring(txs)
        assertEquals(1, hits.size)
        assertTrue(hits[0].isMonthly)
        assertEquals(500.0, hits[0].avgAmount, 0.001)
    }

    @Test
    fun `erratic merchant ignored`() {
        val txs = listOf(
            tx("Kibanda", 250.0, 60),
            tx("Kibanda", 80.0, 3),
            tx("Kibanda", 400.0, 1)
        )
        assertTrue(detectRecurring(txs).isEmpty())
    }

    @Test
    fun `payday predicted from same sender months`() {
        val txs = listOf(
            tx("HELB", 5000.0, 65, TransactionType.INCOME),
            tx("HELB", 5000.0, 35, TransactionType.INCOME),
            tx("HELB", 5000.0, 5, TransactionType.INCOME)
        )
        val pays = predictPaydays(txs)
        assertEquals(1, pays.size)
        assertTrue(pays[0].third > System.currentTimeMillis())
    }
}
