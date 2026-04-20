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

    data class RecentlyUsedFoodWithNutrition(
        @ColumnInfo(name = "barcode") val barcode: String?,
        @ColumnInfo(name = "name") val name: String,
        @ColumnInfo(name = "calories_per100") val caloriesPer100: Float,
        @ColumnInfo(name = "protein_per100") val proteinPer100: Float,
        @ColumnInfo(name = "carbs_per100") val carbsPer100: Float,
        @ColumnInfo(name = "fat_per100") val fatPer100: Float,
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

    @Query("SELECT COUNT(*) FROM meals")
    suspend fun getMealCount(): Int

    /**
     * Deletes ALL meals from days before [beforeDateMillis], regardless of whether they have
     * food entries. The FK is SET_NULL so food entries survive (with meal_id = null) and remain
     * available for "recently used" lookups via their [logged_date_millis] column.
     */
    @Query("DELETE FROM meals WHERE date_millis < :beforeDateMillis")
    suspend fun deleteMealsOlderThan(beforeDateMillis: Long)

    /**
     * Deletes food entries whose [logged_date_millis] is before [beforeDateMillis].
     * Used to enforce the 90-day retention window for the "recently used" history.
     */
    @Query("DELETE FROM food_entries WHERE logged_date_millis > 0 AND logged_date_millis < :beforeDateMillis")
    suspend fun deleteFoodEntriesOlderThan(beforeDateMillis: Long)

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

    @Query("SELECT COUNT(*) FROM food_entries")
    suspend fun getFoodEntryCount(): Int

    /** Returns all food entries logged on a given day via the stored date column. */
    @Query("SELECT * FROM food_entries WHERE logged_date_millis = :dateMillis ORDER BY id ASC")
    fun getFoodEntriesForDay(dateMillis: Long): Flow<List<FoodEntry>>

    @Query(
        """
        SELECT
            NULLIF(TRIM(fe.barcode), '') AS barcode,
            TRIM(fe.name) AS name,
            MAX(fe.logged_date_millis) AS last_used_date_millis,
            MAX(fe.id) AS last_entry_id
        FROM food_entries fe
        WHERE fe.logged_date_millis >= :sinceMillis
          AND TRIM(fe.name) != ''
        GROUP BY NULLIF(TRIM(fe.barcode), ''), LOWER(TRIM(fe.name))
        ORDER BY last_used_date_millis DESC, last_entry_id DESC
        """
    )
    suspend fun getRecentlyUsedFoodsSince(sinceMillis: Long): List<RecentlyUsedFood>

    @Query(
        """
        SELECT
            r.barcode AS barcode,
            r.name AS name,
            fe.calories_per100 AS calories_per100,
            fe.protein_per100 AS protein_per100,
            fe.carbs_per100 AS carbs_per100,
            fe.fat_per100 AS fat_per100,
            r.last_used_date_millis AS last_used_date_millis,
            r.last_entry_id AS last_entry_id
        FROM (
            SELECT
                NULLIF(TRIM(fe.barcode), '') AS barcode,
                TRIM(fe.name) AS name,
                MAX(fe.logged_date_millis) AS last_used_date_millis,
                MAX(fe.id) AS last_entry_id
            FROM food_entries fe
            WHERE fe.logged_date_millis >= :sinceMillis
              AND LOWER(TRIM(fe.name)) LIKE '%' || LOWER(TRIM(:query)) || '%'
              AND TRIM(fe.name) != ''
            GROUP BY NULLIF(TRIM(fe.barcode), ''), LOWER(TRIM(fe.name))
        ) r
        INNER JOIN food_entries fe ON fe.id = r.last_entry_id
        ORDER BY r.last_used_date_millis DESC, r.last_entry_id DESC
        """
    )
    suspend fun searchRecentFoodEntriesWithNutrition(
        query: String,
        sinceMillis: Long
    ): List<RecentlyUsedFoodWithNutrition>
}
