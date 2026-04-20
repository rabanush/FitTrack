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

    @Query("SELECT COALESCE(SUM(calories_burned), 0) FROM workout_calories WHERE date_millis = :dateMillis")
    fun getTotalBurnedForDay(dateMillis: Long): Flow<Float>

    /** Deletes all workout-calorie records from days before [beforeDateMillis]. */
    @Query("DELETE FROM workout_calories WHERE date_millis < :beforeDateMillis")
    suspend fun deleteOlderThan(beforeDateMillis: Long)
}
