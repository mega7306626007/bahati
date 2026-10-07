package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.duplicateIdsToRemove
import com.pesaflow.app.data.parsers.findDuplicateGroups
import com.pesaflow.app.data.parsers.findDuplicatePendingGroups
import com.pesaflow.app.data.parsers.pendingIdsToRemove
import org.junit.Assert.*
import org.junit.Test

private val base = 1700000000000L

private fun row(
    merchant: String,
    amount: Double,
    at: Long,
    code: String? = null,
    id: String = java.util.UUID.randomUUID().toString()
): Transaction = Transaction(
    id = id,
    amount = amount,
    type = TransactionType.EXPENSE,
    category = "Food",
    dateTimestamp = at,
    merchant = merchant,
    description = "",
    paymentMethod = PaymentMethod.MPESA,
    source = TransactionSource.MPESA_SMS,
    sourceTransactionId = code
)

class DuplicatesTest {
    @Test
    fun `same code twice is a dupe`() {
        val txs = listOf(
            row("Naivas", 500.0, base, code = "ABC123", id = "old"),
            row("Naivas", 500.0, base + 1000, code = "ABC123", id = "new")
        )
        val groups = findDuplicateGroups(txs)
        assertEquals(1, groups.size)
        assertEquals(listOf("old"), duplicateIdsToRemove(groups))
    }

    @Test
    fun `same minute same till is a dupe`() {
        val txs = listOf(
            row("Klabu", 70.0, base, id = "a"),
            row("klabu ", 70.0, base + 30 * 1000, id = "b")
        )
        val groups = findDuplicateGroups(txs)
        assertEquals(1, groups.size)
        assertEquals(1, duplicateIdsToRemove(groups).size)
    }

    @Test
    fun `real repeats on different days survive`() {
        val day = 24L * 60 * 60 * 1000
        val txs = (0..6).map { row("Klabu", 70.0, base + it * day) }
        assertTrue(findDuplicateGroups(txs).isEmpty())
    }

    @Test
    fun `same day different minutes survive`() {
        val txs = listOf(
            row("Klabu", 70.0, base),
            row("Klabu", 70.0, base + 5 * 60 * 1000)
        )
        assertTrue(findDuplicateGroups(txs).isEmpty())
    }

    @Test
    fun `different amounts never group`() {
        val txs = listOf(
            row("Naivas", 500.0, base, code = null, id = "a"),
            row("Naivas", 600.0, base, code = null, id = "b")
        )
        assertTrue(findDuplicateGroups(txs).isEmpty())
    }

    @Test
    fun `triple dupe keeps newest drops two`() {
        val txs = listOf(
            row("Naivas", 500.0, base, code = "X1", id = "a"),
            row("Naivas", 500.0, base + 1000, code = "X1", id = "b"),
            row("Naivas", 500.0, base + 2000, code = "X1", id = "c")
        )
        val removed = duplicateIdsToRemove(findDuplicateGroups(txs))
        assertEquals(2, removed.size)
        assertFalse(removed.contains("c"))
    }

    private fun pending(
        merchant: String,
        amount: Double,
        at: Long,
        code: String? = null,
        id: String = java.util.UUID.randomUUID().toString()
    ): PendingTransaction = PendingTransaction(
        id = id,
        amount = amount,
        type = TransactionType.EXPENSE,
        category = "Food",
        merchant = merchant,
        dateTimestamp = at,
        paymentMethod = PaymentMethod.MPESA,
        source = TransactionSource.MPESA_SMS,
        sourceTransactionId = code,
        rawText = "$merchant $amount"
    )

    @Test
    fun `pending inbox dupes group before confirm all`() {
        val rows = listOf(
            pending("Naivas", 500.0, base, code = "ABC123", id = "old"),
            pending("Naivas", 500.0, base + 1000, code = "ABC123", id = "new"),
            pending("Klabu", 70.0, base + 5 * 60 * 1000, id = "solo")
        )
        val groups = findDuplicatePendingGroups(rows)
        assertEquals(1, groups.size)
        assertEquals(listOf("old"), pendingIdsToRemove(groups))
    }

    @Test
    fun `pending same minute same person is a dupe`() {
        val rows = listOf(
            pending("Klabu", 70.0, base, id = "a"),
            pending("klabu ", 70.0, base + 30 * 1000, id = "b")
        )
        assertEquals(1, findDuplicatePendingGroups(rows).size)
    }

    @Test
    fun `pending real repeats survive`() {
        val day = 24L * 60 * 60 * 1000
        val rows = (0..3).map { pending("Klabu", 70.0, base + it * day) } +
            pending("Klabu", 70.0, base + 5 * 60 * 1000)
        assertTrue(findDuplicatePendingGroups(rows).isEmpty())
    }

    @Test
    fun `empty and single pending never group`() {
        assertTrue(findDuplicatePendingGroups(emptyList()).isEmpty())
        assertTrue(findDuplicatePendingGroups(listOf(pending("A", 1.0, base))).isEmpty())
    }
}
