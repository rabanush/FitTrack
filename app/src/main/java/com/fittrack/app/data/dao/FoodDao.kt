package com.fittrack.app.data.dao

import androidx.room.*
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.data.model.Meal
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    data class RecentlyUsedFood(
        @ColumnInfo(name = "barcode") val barcode: String?,
        @ColumnInfo(name = "name") val name: String,
        @ColumnInfo(name = "last_used_date_millis") val lastUsedDateMillis: Long,
        @ColumnInfo(name = "last_entry_id") val lastEntryId: Long
    )

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

    @Query("SELECT * FROM meals ORDER BY date_millis ASC, id ASC")
    fun getAllMeals(): Flow<List<Meal>>

    @Query("SELECT * FROM meals ORDER BY date_millis ASC, id ASC")
    suspend fun getAllMealsSync(): List<Meal>

    @Query("SELECT COUNT(*) FROM meals")
    suspend fun getMealCount(): Int

    // ---- FoodEntry ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodEntry(entry: FoodEntry): Long

    @Update
    suspend fun updateFoodEntry(entry: FoodEntry)

    @Delete
    suspend fun deleteFoodEntry(entry: FoodEntry)

    @Query("SELECT * FROM food_entries WHERE meal_id = :mealId ORDER BY id ASC")
    fun getFoodEntriesForMeal(mealId: Long): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entries ORDER BY id ASC")
    fun getAllFoodEntries(): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entries ORDER BY id ASC")
    suspend fun getAllFoodEntriesSync(): List<FoodEntry>

    @Query("SELECT COUNT(*) FROM food_entries")
    suspend fun getFoodEntryCount(): Int

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

    @Query(
        """
        SELECT
            NULLIF(TRIM(fe.barcode), '') AS barcode,
            TRIM(fe.name) AS name,
            MAX(m.date_millis) AS last_used_date_millis,
            MAX(fe.id) AS last_entry_id
        FROM food_entries fe
        INNER JOIN meals m ON m.id = fe.meal_id
        WHERE m.date_millis >= :sinceMillis
          AND TRIM(fe.name) != ''
        GROUP BY NULLIF(TRIM(fe.barcode), ''), LOWER(TRIM(fe.name))
        ORDER BY last_used_date_millis DESC, last_entry_id DESC
        """
    )
    suspend fun getRecentlyUsedFoodsSince(sinceMillis: Long): List<RecentlyUsedFood>
}
