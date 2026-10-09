package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


@Dao
interface ChamaDao {


    @Query("SELECT * FROM chama_groups ORDER BY startTimestamp DESC")
    fun getAllChamaGroups(): Flow<List<ChamaGroup>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChamaGroup(group: ChamaGroup)


    @Query("DELETE FROM chama_groups WHERE id = :id")
    suspend fun deleteChamaGroup(id: String)


    @Query("DELETE FROM chama_groups")
    suspend fun deleteAllChamaGroups()


    @Query("UPDATE chama_groups SET paidCycles = :cycles WHERE id = :id")
    suspend fun updatePaidCycles(id: String, cycles: Int)
}
