package com.pesaflow.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.pesaflow.app.data.models.ContextFact

@Dao
interface ContextFactDao {
    @Query("SELECT * FROM context_facts ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ContextFact>>

    @Query("SELECT * FROM context_facts WHERE `key` = :key ORDER BY updatedAt DESC")
    fun getByKey(key: String): Flow<List<ContextFact>>

    @Query("SELECT * FROM context_facts WHERE `key` = :key ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestByKey(key: String): ContextFact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fact: ContextFact)

    @Query("DELETE FROM context_facts WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM context_facts WHERE id = :id")
    suspend fun delete(id: String)
}
