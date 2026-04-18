package com.fittrack.app.data.dao

import androidx.room.*
import com.fittrack.app.data.model.WorkoutCalories
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutCaloriesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WorkoutCalories): Long

    @Delete
    suspend fun delete(entry: WorkoutCalories)

    @Query("SELECT * FROM workout_calories WHERE date_millis = :dateMillis")
    fun getForDay(dateMillis: Long): Flow<List<WorkoutCalories>>

    @Query("SELECT * FROM workout_calories ORDER BY date_millis ASC, id ASC")
    fun getAll(): Flow<List<WorkoutCalories>>

    @Query("SELECT * FROM workout_calories ORDER BY date_millis ASC, id ASC")
    suspend fun getAllSync(): List<WorkoutCalories>

    @Query("SELECT COUNT(*) FROM workout_calories")
    suspend fun getCount(): Int

    @Query("SELECT COALESCE(SUM(calories_burned), 0) FROM workout_calories WHERE date_millis = :dateMillis")
    fun getTotalBurnedForDay(dateMillis: Long): Flow<Float>
}
