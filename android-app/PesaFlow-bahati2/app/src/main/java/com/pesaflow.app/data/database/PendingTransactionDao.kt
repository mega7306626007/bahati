package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


@Dao
interface PendingTransactionDao {


    @Query("SELECT * FROM pending_transactions ORDER BY dateTimestamp DESC")
    fun getAllPendingTransactions(): Flow<List<PendingTransaction>>


    // Same receipt + same amount + same direction = same money event. Same
    // receipt otherwise is a fee/principal pair or a reversal (statements
    // reuse one receipt for both: -70 withdrawn AND +70 paid) and must
    // survive. Never match on code alone.
    @Query("SELECT * FROM pending_transactions WHERE sourceTransactionId = :code AND amount = :amount AND type = :type LIMIT 1")
    suspend fun findBySourceCodeAmountType(code: String, amount: Double, type: TransactionType): PendingTransaction?


    @Query("SELECT EXISTS(SELECT 1 FROM pending_transactions WHERE sourceTransactionId = :code LIMIT 1)")
    suspend fun isDuplicateMpesa(code: String): Boolean


    @Query("SELECT * FROM pending_transactions WHERE amount = :amount AND dateTimestamp >= :start AND dateTimestamp <= :end")
    suspend fun findInWindow(amount: Double, start: Long, end: Long): List<PendingTransaction>


    // One-shot read for use inside write transactions — collecting the Flow
    // query above from within a transact{} block pins a read snapshot on a
    // table being written and stalls the pool. Same rows, no Flow.
    @Query("SELECT * FROM pending_transactions WHERE source = :source ORDER BY dateTimestamp DESC")
    suspend fun getAllBySourceOnce(source: TransactionSource): List<PendingTransaction>


    // IGNORE (not REPLACE): same twin-safety as the confirmed ledger.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPendingTransaction(pending: PendingTransaction)


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPendingTransactions(pending: List<PendingTransaction>)


    @Query("DELETE FROM pending_transactions WHERE id = :id")
    suspend fun deletePendingTransaction(id: String)


    // Same batch rule as the ledger: one statement, one emission.
    @Query("DELETE FROM pending_transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int


    @Query("DELETE FROM pending_transactions")
    suspend fun deleteAllPendingTransactions()


    @Query("UPDATE pending_transactions SET category = :category, merchant = :merchant WHERE id = :id")
    suspend fun updatePendingTransaction(id: String, category: String, merchant: String)

    @Query("UPDATE pending_transactions SET category = :category, displayCategory = :displayCategory, displayMerchant = :displayMerchant WHERE id = :id")
    suspend fun updateClassification(
        id: String,
        category: String,
        displayCategory: String,
        displayMerchant: String
    )


    // Ziidi-matcher repair: retype misfiled savings rows in place (same id,
    // same code — dedup keys untouched, history preserved).
    @Query("UPDATE pending_transactions SET type = :type, category = :category, subcategory = :subcategory WHERE id = :id")
    suspend fun retypePending(id: String, type: TransactionType, category: String, subcategory: String)


    // SIM backfill: a rescan carrying sub_id stamps rows the first import
    // left unknown. Same code only — never amount/merchant guessing.
    @Query("UPDATE pending_transactions SET simSlot = :slot WHERE sourceTransactionId = :code AND simSlot = -1")
    suspend fun fillSimSlotByCode(code: String, slot: Int): Int


    // Pipeline status reads/writes. All enum values pass as bound parameters
    // (converted to Int) — never inline enum names in SQL.
    @Query("SELECT * FROM pending_transactions WHERE status = :status ORDER BY dateTimestamp DESC")
    suspend fun getAllByStatusOnce(status: PendingTransactionStatus): List<PendingTransaction>


    @Query("UPDATE pending_transactions SET status = :newStatus WHERE status = :oldStatus")
    suspend fun resetStatus(oldStatus: PendingTransactionStatus, newStatus: PendingTransactionStatus): Int


    @Query("UPDATE pending_transactions SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: PendingTransactionStatus)


    // Bulk state flip for batched approves: one statement per batch, not one
    // per row — marking must never cost more writes than the approve itself.
    @Query("UPDATE pending_transactions SET status = :status WHERE id IN (:ids)")
    suspend fun setStatusByIds(ids: List<String>, status: PendingTransactionStatus): Int
}