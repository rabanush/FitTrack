package com.fittrack.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_foods")
data class CustomFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    @ColumnInfo(name = "calories_per100") val caloriesPer100: Float,
    @ColumnInfo(name = "protein_per100") val proteinPer100: Float,
    @ColumnInfo(name = "carbs_per100") val carbsPer100: Float,
    @ColumnInfo(name = "fat_per100") val fatPer100: Float
)
