package com.fittrack.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Records calories burned during a completed workout session. */
@Entity(tableName = "workout_calories")
data class WorkoutCalories(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Midnight-normalised epoch millis for the day of the workout. */
    @ColumnInfo(name = "date_millis") val dateMillis: Long,
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    @ColumnInfo(name = "calories_burned") val caloriesBurned: Float,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int
)
