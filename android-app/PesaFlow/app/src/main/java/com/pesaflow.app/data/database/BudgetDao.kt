package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


@Dao
interface BudgetDao {


    @Query("SELECT * FROM budgets ORDER BY startTimestamp DESC")
    fun getAllBudgets(): Flow<List<Budget>>


    @Query("SELECT * FROM budgets WHERE category = :category")
    fun getBudgetByCategory(category: String): Flow<List<Budget>>


    @Query("SELECT * FROM budgets WHERE type = :type")
    fun getBudgetsByType(type: BudgetType): Flow<List<Budget>>


    @Query("SELECT SUM(limitAmount) FROM budgets WHERE type = :type")
    fun getTotalBudgetLimit(type: BudgetType): Double


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(budgets: List<Budget>)


    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudget(id: String)


    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()


    @Query("UPDATE budgets SET limitAmount = :amount WHERE id = :id")
    suspend fun updateBudgetAmount(id: String, amount: Double)


    @Query("DELETE FROM budgets WHERE type = :type")
    suspend fun deleteBudgetsByType(type: BudgetType)


    @Query("UPDATE budgets SET sharedWith = :names WHERE id = :id")
    suspend fun updateSharedWith(id: String, names: String)


    @Query("SELECT * FROM budgets WHERE startTimestamp <= :end AND endTimestamp >= :start")
    fun getActiveBudgets(start: Long, end: Long): Flow<List<Budget>>
}