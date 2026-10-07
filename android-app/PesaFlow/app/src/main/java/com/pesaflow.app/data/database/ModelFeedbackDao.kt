package com.pesaflow.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.pesaflow.app.data.models.ModelFeedback

@Dao
interface ModelFeedbackDao {
    @Query("SELECT * FROM model_feedback ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ModelFeedback>>

    @Query("SELECT COUNT(*) FROM model_feedback")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: ModelFeedback)

    @Query("DELETE FROM model_feedback WHERE id = :id")
    suspend fun delete(id: String)
}
