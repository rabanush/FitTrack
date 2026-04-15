package com.fittrack.app.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class RecipeWithItems(
    @Embedded val recipe: Recipe,
    @Relation(parentColumn = "id", entityColumn = "recipe_id")
    val items: List<RecipeItem>
) {
    val totalCalories: Float get() = items.sumOf { it.calories.toDouble() }.toFloat()
    val totalProtein: Float get() = items.sumOf { it.protein.toDouble() }.toFloat()
    val totalCarbs: Float get() = items.sumOf { it.carbs.toDouble() }.toFloat()
    val totalFat: Float get() = items.sumOf { it.fat.toDouble() }.toFloat()
}
