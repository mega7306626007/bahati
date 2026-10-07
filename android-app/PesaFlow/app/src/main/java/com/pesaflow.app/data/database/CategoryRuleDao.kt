package com.pesaflow.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.pesaflow.app.data.models.CategoryRule

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CategoryRule>>

    @Query("SELECT * FROM category_rules WHERE enabled = 1")
    fun getEnabled(): Flow<List<CategoryRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CategoryRule)

    @Query("UPDATE category_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun delete(id: String)
}
