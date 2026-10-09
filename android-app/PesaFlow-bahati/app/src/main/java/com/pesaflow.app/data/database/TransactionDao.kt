package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


@Dao
interface TransactionDao {


    @Query("SELECT * FROM transactions ORDER BY dateTimestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE confirmed = 1 ORDER BY dateTimestamp DESC")
    fun getConfirmedTransactions(): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE dateTimestamp >= :start AND dateTimestamp <= :end ORDER BY dateTimestamp DESC")
    fun getTransactionsInTimeframe(start: Long, end: Long): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY dateTimestamp DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY dateTimestamp DESC")
    fun getTransactionsByCategory(category: String): Flow<List<Transaction>>


    // Same receipt + same amount + same direction = same money event. Same
    // receipt otherwise is a fee/principal pair or a reversal (statements
    // reuse one receipt for both: -70 withdrawn AND +70 paid) and must
    // survive. Never match on code alone.
    @Query("SELECT * FROM transactions WHERE sourceTransactionId = :code AND amount = :amount AND type = :type LIMIT 1")
    suspend fun findBySourceCodeAmountType(code: String, amount: Double, type: TransactionType): Transaction?


    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): Transaction?


    // One-shot read for use inside write transactions — collecting the Flow
    // query above from within a transact{} block pins a read snapshot on a
    // table being written and stalls the pool. Same rows, no Flow.
    @Query("SELECT * FROM transactions WHERE source = :source ORDER BY dateTimestamp DESC")
    suspend fun getAllBySourceOnce(source: TransactionSource): List<Transaction>


    @Query("SELECT COUNT(*) FROM transactions WHERE dateTimestamp >= :start AND dateTimestamp <= :end")
    fun countInTimeframe(start: Long, end: Long): Long


    @Query("SELECT category, SUM(amount) as amount, COUNT(*) as count FROM transactions WHERE confirmed = 1 AND dateTimestamp >= :start AND dateTimestamp <= :end GROUP BY category")
    fun getTransactionsByCategoryInTimeframe(start: Long, end: Long): Flow<List<TransactionSummary>>


    // IGNORE (not REPLACE): with the unique (code, amount) index, a duplicate
    // insert skips instead of deleting the twin. All callers pre-check by
    // code + amount; this is the structural backstop.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: Transaction)


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<Transaction>)


    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)


    // Batch removal: one statement, one Flow emission. Deleting N rows
    // one-by-one re-emits the whole ledger N times — after a big scan that
    // recomposition storm ANRs the app ("keeps stopping" on Remove).
    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int


    @Query("SELECT * FROM transactions WHERE amount = :amount AND dateTimestamp >= :start AND dateTimestamp <= :end")
    suspend fun findInWindow(amount: Double, start: Long, end: Long): List<Transaction>


    @Query("SELECT COUNT(*) FROM transactions WHERE isSample = 1")
    suspend fun countSamples(): Int


    @Query("DELETE FROM transactions WHERE isSample = 1")
    suspend fun deleteSamples(): Int


    @Query("DELETE FROM transactions WHERE batchId = :batch")
    suspend fun deleteBatch(batch: String): Int


    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()


    @Query("UPDATE transactions SET confirmed = :confirmed WHERE id = :id")
    suspend fun updateConfirmation(id: String, confirmed: Boolean)

    @Query("UPDATE transactions SET category = :category, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCategory(id: String, category: String, updatedAt: Long)


    // Ziidi-matcher repair: same id, same code — dedup keys untouched.
    @Query("UPDATE transactions SET type = :type, category = :category, subcategory = :subcategory, updatedAt = :updatedAt WHERE id = :id")
    suspend fun retypeTransaction(id: String, type: TransactionType, category: String, subcategory: String, updatedAt: Long)


    // SIM backfill twin of the pending table: rescan stamps, first import kept.
    @Query("UPDATE transactions SET simSlot = :slot WHERE sourceTransactionId = :code AND simSlot = -1")
    suspend fun fillSimSlotByCode(code: String, slot: Int): Int


    @Query("UPDATE transactions SET transferGroupId = :groupId WHERE id = :id")
    suspend fun updateTransferGroup(id: String, groupId: String?)


    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
    suspend fun getByTransferGroup(groupId: String): List<Transaction>


    @Query("SELECT * FROM transactions WHERE (amount >= :minAmount AND amount <= :maxAmount) AND (merchant LIKE '%' || :searchTerm || '%' OR category LIKE '%' || :searchTerm || '%')")
    fun searchTransactions(minAmount: Double, maxAmount: Double, searchTerm: String): Flow<List<Transaction>>
}