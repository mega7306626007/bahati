package com.pesaflow.app.data.database

import androidx.room.*
import com.pesaflow.app.data.income.IncomeSource
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeSourceDao {
    @Query("SELECT * FROM income_sources ORDER BY expectedAmount DESC")
    fun getAll(): Flow<List<IncomeSource>>

    @Query("SELECT * FROM income_sources WHERE id = :id")
    suspend fun getById(id: String): IncomeSource?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: IncomeSource)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<IncomeSource>)

    @Query("DELETE FROM income_sources WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM income_sources")
    suspend fun deleteAll()
}
