package com.pesaflow.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.pesaflow.app.data.models.UserRhythm

@Dao
interface UserRhythmDao {
    @Query("SELECT * FROM user_rhythms ORDER BY createdAt DESC")
    fun getAll(): Flow<List<UserRhythm>>

    @Query("SELECT * FROM user_rhythms WHERE kind = :kind ORDER BY createdAt DESC")
    fun getByKind(kind: String): Flow<List<UserRhythm>>

    @Query("SELECT * FROM user_rhythms WHERE confirmed = 1")
    fun getConfirmed(): Flow<List<UserRhythm>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rhythm: UserRhythm)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rhythms: List<UserRhythm>)

    @Query("UPDATE user_rhythms SET confirmed = 1 WHERE id = :id")
    suspend fun confirm(id: String)

    @Query("UPDATE user_rhythms SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: String)

    @Query("DELETE FROM user_rhythms WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM user_rhythms")
    suspend fun deleteAll()
}
