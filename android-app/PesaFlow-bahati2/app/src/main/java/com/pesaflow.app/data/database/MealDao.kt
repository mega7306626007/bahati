package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


@Dao
interface MealDao {


    @Query("SELECT * FROM meal_items ORDER BY mealType ASC, price ASC")
    fun getAllMealItems(): Flow<List<MealItem>>


    @Query("SELECT * FROM meal_items WHERE mealType = :type ORDER BY price ASC")
    fun getMealsByType(type: String): Flow<List<MealItem>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealItem(item: MealItem)


    @Query("DELETE FROM meal_items WHERE id = :id")
    suspend fun deleteMealItem(id: String)


    @Query("DELETE FROM meal_items")
    suspend fun clearMealItems()
}
