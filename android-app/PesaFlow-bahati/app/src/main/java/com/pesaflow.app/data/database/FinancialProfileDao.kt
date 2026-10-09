package com.pesaflow.app.data.database

import androidx.room.*
import com.pesaflow.app.data.models.FinancialProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialProfileDao {
    @Query("SELECT * FROM financial_profile WHERE id = 'SINGLETON_PROFILE'")
    fun getProfile(): Flow<FinancialProfile?>

    @Query("SELECT * FROM financial_profile WHERE id = 'SINGLETON_PROFILE'")
    suspend fun getOnce(): FinancialProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: FinancialProfile)

    @Query("DELETE FROM financial_profile")
    suspend fun clear()
}
