package com.pesaflow.app.parsers

import com.pesaflow.app.data.database.TransactionDao
import com.pesaflow.app.data.database.readAllChunked
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionSummary
import com.pesaflow.app.data.models.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

// Fake ledger of N rows served in pages: proves readAllChunked returns
// every row in order without ever issuing an unbounded query.
private class PagedFake(private val rows: List<Transaction>) : TransactionDao {
    val pageSizes = mutableListOf<Int>()
    override suspend fun getTransactionsPage(limit: Int, offset: Int): List<Transaction> {
        pageSizes.add(limit)
        return rows.drop(offset).take(limit)
    }
    override fun getAllTransactions(): Flow<List<Transaction>> = flowOf(rows)
    override fun getConfirmedTransactions(): Flow<List<Transaction>> = flowOf(rows)
    override fun getTransactionsInTimeframe(start: Long, end: Long): Flow<List<Transaction>> = flowOf(rows)
    override fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> = flowOf(rows)
    override fun getTransactionsByCategory(category: String): Flow<List<Transaction>> = flowOf(rows)
    override suspend fun findBySourceCodeAmountType(code: String, amount: Double, type: TransactionType): Transaction? = null
    override suspend fun getById(id: String): Transaction? = null
    override suspend fun getAllBySourceOnce(source: TransactionSource): List<Transaction> = rows
    override fun countInTimeframe(start: Long, end: Long): Long = rows.size.toLong()
    override fun getTransactionsByCategoryInTimeframe(start: Long, end: Long): Flow<List<TransactionSummary>> = flowOf(emptyList())
    override suspend fun insertTransaction(transaction: Transaction) = Unit
    override suspend fun insertTransactions(transactions: List<Transaction>) = Unit
    override suspend fun deleteTransaction(id: String) = Unit
    override suspend fun deleteByIds(ids: List<String>): Int = 0
    override suspend fun findInWindow(amount: Double, start: Long, end: Long): List<Transaction> = emptyList()
    override suspend fun countSamples(): Int = 0
    override suspend fun deleteSamples(): Int = 0
    override suspend fun deleteBatch(batch: String): Int = 0
    override suspend fun deleteAllTransactions() = Unit
    override suspend fun updateConfirmation(id: String, confirmed: Boolean) = Unit
    override suspend fun updateCategory(id: String, category: String, updatedAt: Long) = Unit
    override suspend fun retypeTransaction(id: String, type: TransactionType, category: String, subcategory: String, updatedAt: Long) = Unit
    override suspend fun fillSimSlotByCode(code: String, slot: Int): Int = 0
    override suspend fun updateTransferGroup(id: String, groupId: String?) = Unit
    override suspend fun getByTransferGroup(groupId: String): List<Transaction> = emptyList()
    override fun searchTransactions(minAmount: Double, maxAmount: Double, searchTerm: String): Flow<List<Transaction>> = flowOf(emptyList())
}

private fun row(i: Int) = Transaction(
    id = "t$i", amount = i.toDouble(), type = TransactionType.EXPENSE,
    category = "Food", dateTimestamp = i.toLong(), merchant = "M$i",
    source = TransactionSource.MPESA_SMS
)

class ChunkedReadTest {

    @Test
    fun `twelve hundred rows come back whole in small pages`() = runBlocking {
        val fake = PagedFake((0 until 1200).map { row(it) })
        val out = fake.readAllChunked(pageSize = 500)
        assertEquals(1200, out.size)
        assertEquals((0 until 1200).map { "t$it" }, out.map { it.id })
        assertTrue(fake.pageSizes.all { it <= 500 })
    }

    @Test
    fun `empty ledger reads one page and stops`() = runBlocking {
        val fake = PagedFake(emptyList())
        assertTrue(fake.readAllChunked().isEmpty())
        assertEquals(1, fake.pageSizes.size)
    }
}
