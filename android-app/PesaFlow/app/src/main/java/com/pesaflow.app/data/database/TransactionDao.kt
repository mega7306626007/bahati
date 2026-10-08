package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


@Dao
interface TransactionDao {


    @Query("SELECT * FROM transactions ORDER BY dateTimestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>


    // SQL aggregate: the running balance without loading every row. Workers
    // that need both balance and a filtered window use this + a timeframe
    // query instead of parsing the whole ledger.
    @Query("SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0) FROM transactions")
    suspend fun ledgerBalance(): Double


    @Query("SELECT * FROM transactions WHERE confirmed = 1 ORDER BY dateTimestamp DESC")
    fun getConfirmedTransactions(): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE dateTimestamp >= :start AND dateTimestamp <= :end ORDER BY dateTimestamp DESC")
    fun getTransactionsInTimeframe(start: Long, end: Long): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY dateTimestamp DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY dateTimestamp DESC")
    fun getTransactionsByCategory(category: String): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE sourceTransactionId = :code")
    suspend fun findBySourceCode(code: String): Transaction?

    @Query("SELECT sourceTransactionId FROM transactions WHERE sourceTransactionId IN (:codes)")
    suspend fun findExistingSourceCodes(codes: List<String>): List<String?>


    @Query("SELECT COUNT(*) FROM transactions WHERE dateTimestamp >= :start AND dateTimestamp <= :end")
    fun countInTimeframe(start: Long, end: Long): Long


    @Query("SELECT category, SUM(amount) as amount, COUNT(*) as count FROM transactions WHERE confirmed = 1 AND dateTimestamp >= :start AND dateTimestamp <= :end GROUP BY category")
    fun getTransactionsByCategoryInTimeframe(start: Long, end: Long): Flow<List<TransactionSummary>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)


    // True in-place update: the row keeps its id, so undo, source links and
    // any reference to it survive an edit. The old path was delete+insert with
    // a fresh UUID — the row silently changed identity.
    @Update
    suspend fun updateTransaction(transaction: Transaction)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)


    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)


    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()


    // Batch delete by source for sample-data wipe and audit cleanup.
    @Query("DELETE FROM transactions WHERE source = :source")
    suspend fun deleteBySource(source: String)


    @Query("UPDATE transactions SET confirmed = :confirmed WHERE id = :id")
    suspend fun updateConfirmation(id: String, confirmed: Boolean)


    @Query("SELECT * FROM transactions WHERE (amount >= :minAmount AND amount <= :maxAmount) OR (merchant LIKE '%' || :searchTerm || '%' OR category LIKE '%' || :searchTerm || '%')")
    fun searchTransactions(minAmount: Double, maxAmount: Double, searchTerm: String): Flow<List<Transaction>>
}