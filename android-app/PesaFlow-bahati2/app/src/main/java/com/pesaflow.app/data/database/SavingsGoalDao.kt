package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


@Dao
interface SavingsGoalDao {


    @Query("SELECT * FROM savings_goals ORDER BY targetTimestamp ASC")
    fun getAllSavingsGoals(): Flow<List<SavingsGoal>>


    @Query("SELECT * FROM savings_goals WHERE targetAmount > :minCurrent")
    fun getGoalsWithRemaining(minCurrent: Double): Flow<List<SavingsGoal>>


    @Query("SELECT * FROM savings_goals WHERE title LIKE :filter")
    fun searchGoals(filter: String): Flow<List<SavingsGoal>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsGoal(goal: SavingsGoal)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsGoals(goals: List<SavingsGoal>)


    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteSavingsGoal(id: String)


    @Query("DELETE FROM savings_goals")
    suspend fun deleteAllSavingsGoals()


    @Query("UPDATE savings_goals SET currentAmount = :current WHERE id = :id")
    suspend fun updateSavingsGoalCurrent(id: String, current: Double)


    @Query("SELECT * FROM savings_goals WHERE currentAmount >= targetAmount")
    fun getCompletedGoals(): Flow<List<SavingsGoal>>
}