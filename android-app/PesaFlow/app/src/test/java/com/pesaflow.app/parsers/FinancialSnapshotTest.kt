package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.money.buildFinancialSnapshot
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialSnapshotTest {

    private fun dayOffset(offset: Int, hour: Int = 12): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, offset)
        return c.timeInMillis
    }

    @Test
    fun oneSnapshotProducesSharedTotals() {
        val now = dayOffset(0)
        val txs = listOf(
            Transaction(
                amount = 1000.0,
                type = TransactionType.INCOME,
                category = "HELB",
                dateTimestamp = now,
                merchant = "HELB"
            ),
            Transaction(
                amount = 200.0,
                type = TransactionType.EXPENSE,
                category = "Food",
                dateTimestamp = now,
                merchant = "Mess"
            ),
            Transaction(
                amount = 80.0,
                type = TransactionType.EXPENSE,
                category = "Transport",
                dateTimestamp = dayOffset(-1),
                merchant = "Matatu"
            )
        )

        val snapshot = buildFinancialSnapshot(txs, now)

        assertEquals(720.0, snapshot.balance, 0.001)
        assertEquals(1000.0, snapshot.monthIncome, 0.001)
        assertEquals(280.0, snapshot.monthExpense, 0.001)
        assertEquals(200.0, snapshot.todayExpense, 0.001)
        assertEquals(80.0, snapshot.yesterdayExpense, 0.001)
        assertEquals(200.0, snapshot.monthCategory("Food"), 0.001)
        assertEquals(80.0, snapshot.yesterdayCategory("Transport"), 0.001)
    }
}
