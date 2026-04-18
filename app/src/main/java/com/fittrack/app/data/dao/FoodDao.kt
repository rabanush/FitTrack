package com.fittrack.app.data.dao

import androidx.room.*
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.data.model.Meal
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    // ---- Meal ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: Meal): Long

    @Update
    suspend fun updateMeal(meal: Meal)

    @Delete
    suspend fun deleteMeal(meal: Meal)

    @Query("SELECT * FROM meals WHERE date_millis = :dateMillis ORDER BY id ASC")
    fun getMealsForDay(dateMillis: Long): Flow<List<Meal>>

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun getMealById(id: Long): Meal?

    // ---- FoodEntry ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodEntry(entry: FoodEntry): Long

    @Update
    suspend fun updateFoodEntry(entry: FoodEntry)

    @Delete
    suspend fun deleteFoodEntry(entry: FoodEntry)

    @Query("SELECT * FROM food_entries WHERE meal_id = :mealId ORDER BY id ASC")
    fun getFoodEntriesForMeal(mealId: Long): Flow<List<FoodEntry>>

    /** Returns all food entries logged on a given day (joined via meals). */
    @Query("""
        SELECT fe.* FROM food_entries fe
        INNER JOIN meals m ON fe.meal_id = m.id
        WHERE m.date_millis = :dateMillis
    """)
    fun getFoodEntriesForDay(dateMillis: Long): Flow<List<FoodEntry>>

    @Query(
        """
        SELECT barcode
        FROM food_entries
        WHERE barcode IS NOT NULL AND TRIM(barcode) != ''
        GROUP BY barcode
        ORDER BY MAX(id) DESC
    """
    )
    suspend fun getRecentlyUsedBarcodes(): List<String>
}
