package com.pesaflow.app.data.database

import androidx.room.*
import com.pesaflow.app.data.models.Belonging
import kotlinx.coroutines.flow.Flow


@Dao
interface BelongingDao {
    @Query("SELECT * FROM belongings ORDER BY status ASC, priority ASC, name ASC")
    fun getAllBelongings(): Flow<List<Belonging>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBelonging(item: Belonging)

    @Update
    suspend fun updateBelonging(item: Belonging)

    @Query("DELETE FROM belongings WHERE id = :id")
    suspend fun deleteBelonging(id: String)

    @Query("DELETE FROM belongings")
    suspend fun deleteAllBelongings()
}
