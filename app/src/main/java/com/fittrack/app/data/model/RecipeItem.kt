package com.fittrack.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipe_items",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recipe_id")]
)
data class RecipeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recipe_id") val recipeId: Long,
    val name: String,
    @ColumnInfo(name = "calories_per100") val caloriesPer100: Float,
    @ColumnInfo(name = "protein_per100") val proteinPer100: Float,
    @ColumnInfo(name = "carbs_per100") val carbsPer100: Float,
    @ColumnInfo(name = "fat_per100") val fatPer100: Float,
    /** Amount in grams to use for this ingredient when the recipe is added to a meal. */
    val amount: Float
) {
    val calories: Float get() = caloriesPer100 * amount / 100f
    val protein: Float get() = proteinPer100 * amount / 100f
    val carbs: Float get() = carbsPer100 * amount / 100f
    val fat: Float get() = fatPer100 * amount / 100f
}
