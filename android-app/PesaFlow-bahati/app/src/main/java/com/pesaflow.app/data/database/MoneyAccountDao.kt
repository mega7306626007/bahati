package com.pesaflow.app.data.database

import androidx.room.*
import com.pesaflow.app.data.models.MoneyAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyAccountDao {
    @Query("SELECT * FROM money_accounts ORDER BY createdAt ASC")
    fun getAll(): Flow<List<MoneyAccount>>

    @Query("SELECT * FROM money_accounts WHERE kind = :kind LIMIT 1")
    suspend fun getByKind(kind: String): MoneyAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: MoneyAccount)

    @Query("UPDATE money_accounts SET openingMinorUnits = :opening WHERE id = :id")
    suspend fun updateOpening(id: String, opening: Long)

    @Query("DELETE FROM money_accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM money_accounts")
    suspend fun deleteAll()
}
