package com.fittrack.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Midnight-normalised epoch millis for the day this meal belongs to. */
    @ColumnInfo(name = "date_millis") val dateMillis: Long
)
