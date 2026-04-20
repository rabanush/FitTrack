package com.fittrack.app.data.dao

import androidx.room.*
import com.fittrack.app.data.model.Recipe
import com.fittrack.app.data.model.RecipeItem
import com.fittrack.app.data.model.RecipeWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    // ---- Recipes ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe): Long

    @Delete
    suspend fun deleteRecipe(recipe: Recipe)

    /** Returns all recipes with their items, kept up-to-date reactively. */
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name ASC")
    fun getAllRecipesWithItems(): Flow<List<RecipeWithItems>>

    /** One-shot snapshot used during backup export. */
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name ASC")
    suspend fun getAllRecipesWithItemsOnce(): List<RecipeWithItems>

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun getCount(): Int

    // ---- Recipe items ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipeItem(item: RecipeItem): Long

    @Delete
    suspend fun deleteRecipeItem(item: RecipeItem)
}
