package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


@Dao
interface DebtDao {


    @Query("SELECT * FROM debts ORDER BY dueDate ASC")
    fun getAllDebts(): Flow<List<Debt>>


    @Query("SELECT * FROM debts WHERE person LIKE :filter")
    fun searchDebts(filter: String): Flow<List<Debt>>


    @Query("SELECT * FROM debts WHERE status = :status")
    fun getDebtsByStatus(status: String): Flow<List<Debt>>


    @Query("SELECT * FROM debts WHERE dueDate < :now AND status != 'PAID'")
    fun getOverdueDebts(now: Long): Flow<List<Debt>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: Debt)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebts(debts: List<Debt>)


    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebt(id: String)


    @Query("DELETE FROM debts")
    suspend fun deleteAllDebts()


    @Query("UPDATE debts SET amount = :amount, dueDate = :dueDate, description = :description, status = :status, reminderEnabled = :reminder, reminderLeadDays = :leadDays WHERE id = :id")
    suspend fun updateDebt(
        id: String,
        amount: Double,
        dueDate: Long,
        description: String,
        status: String,
        reminder: Boolean,
        leadDays: Int
    )


    @Query("UPDATE debts SET status = 'PAID' WHERE id = :id")
    suspend fun markDebtPaid(id: String)


    @Query("SELECT SUM(amount) FROM debts WHERE status = 'OWING'")
    fun totalOwing(): Double


    @Query("SELECT SUM(amount) FROM debts WHERE status = 'OVERDUE'")
    fun totalOverdue(): Double
}