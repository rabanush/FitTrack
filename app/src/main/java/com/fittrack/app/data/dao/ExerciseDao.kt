package com.fittrack.app.data.dao

import androidx.room.*
import com.fittrack.app.data.model.Exercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseById(id: Long): Exercise?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: Exercise): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<Exercise>)

    @Update
    suspend fun updateExercise(exercise: Exercise)

    @Delete
    suspend fun deleteExercise(exercise: Exercise)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getCount(): Int

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun getExerciseByName(name: String): Exercise?

    @Query("SELECT * FROM exercises WHERE lower(trim(name)) = lower(trim(:name)) LIMIT 1")
    suspend fun getExerciseByNormalizedName(name: String): Exercise?

    @Query("SELECT * FROM exercises WHERE lower(trim(german_name)) = lower(trim(:name)) LIMIT 1")
    suspend fun getExerciseByGermanName(name: String): Exercise?

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun getAllExercisesSync(): List<Exercise>
}
