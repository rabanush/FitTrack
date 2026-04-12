package com.fittrack.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workout_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exercise_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("workout_id"), Index("exercise_id")]
)
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    @ColumnInfo(name = "order_index") val orderIndex: Int = 0,
    @ColumnInfo(name = "rest_timer_seconds") val restTimerSeconds: Int = 90
)
