package com.fittrack.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "log_entries",
    foreignKeys = [
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exercise_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workout_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("exercise_id"), Index("workout_id")]
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    val date: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "set_number") val setNumber: Int,
    val weight: Float,
    val reps: Int
)
