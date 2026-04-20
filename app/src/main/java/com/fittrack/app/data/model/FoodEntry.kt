package com.fittrack.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_entries",
    foreignKeys = [
        ForeignKey(
            entity = Meal::class,
            parentColumns = ["id"],
            childColumns = ["meal_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("meal_id"), Index("logged_date_millis")]
)
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null when the parent meal has been cleaned up (past-day meal deleted by daily cleanup). */
    @ColumnInfo(name = "meal_id") val mealId: Long?,
    val name: String,
    val barcode: String? = null,
    /** Midnight-normalised epoch millis for the day this entry was logged.
     *  Populated automatically from the parent meal on insert; survives meal deletion. */
    @ColumnInfo(name = "logged_date_millis") val loggedDateMillis: Long = 0L,
    /** Calories per 100 g (from API or manual entry). */
    @ColumnInfo(name = "calories_per100") val caloriesPer100: Float,
    @ColumnInfo(name = "protein_per100") val proteinPer100: Float,
    @ColumnInfo(name = "carbs_per100") val carbsPer100: Float,
    @ColumnInfo(name = "fat_per100") val fatPer100: Float,
    /** Amount consumed in grams. */
    val amount: Float
) {
    val calories: Float get() = caloriesPer100 * amount / 100f
    val protein: Float get() = proteinPer100 * amount / 100f
    val carbs: Float get() = carbsPer100 * amount / 100f
    val fat: Float get() = fatPer100 * amount / 100f
}
