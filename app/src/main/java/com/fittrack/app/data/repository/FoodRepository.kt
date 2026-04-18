package com.fittrack.app.data.repository

import com.fittrack.app.data.dao.CustomFoodDao
import com.fittrack.app.data.dao.FoodDao
import com.fittrack.app.data.dao.RecipeDao
import com.fittrack.app.data.dao.WorkoutCaloriesDao
import com.fittrack.app.data.model.CustomFood
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.data.model.Meal
import com.fittrack.app.data.model.Recipe
import com.fittrack.app.data.model.RecipeItem
import com.fittrack.app.data.model.RecipeWithItems
import com.fittrack.app.data.model.WorkoutCalories
import com.fittrack.app.data.network.OFFProduct
import com.fittrack.app.data.network.OpenFoodFactsApi
import com.fittrack.app.data.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class FoodRepository(
    private val foodDao: FoodDao,
    private val workoutCaloriesDao: WorkoutCaloriesDao,
    private val api: OpenFoodFactsApi,
    val userPreferences: UserPreferences,
    private val customFoodDao: CustomFoodDao,
    private val recipeDao: RecipeDao
) {
    private companion object {
        const val EXACT_MATCH_SCORE = 1000
        const val PREFIX_MATCH_SCORE = 700
        const val WORD_MATCH_SCORE = 500
        const val CONTAINS_MATCH_SCORE = 300
        const val BRAND_MATCH_SCORE = 80
        const val TOKEN_PREFIX_SCORE = 40
        const val TOKEN_WORD_SCORE = 35
        const val TOKEN_CONTAINS_SCORE = 25
        const val TOKEN_BRAND_SCORE = 10
        const val MAX_NAME_LENGTH_PENALTY = 80
    }

    // ---- Meals ----

    fun getMealsForDay(dateMillis: Long): Flow<List<Meal>> =
        foodDao.getMealsForDay(dateMillis)

    suspend fun insertMeal(meal: Meal): Long = foodDao.insertMeal(meal)

    suspend fun deleteMeal(meal: Meal) = foodDao.deleteMeal(meal)

    suspend fun getMealById(id: Long): Meal? = foodDao.getMealById(id)

    // ---- Food Entries ----

    fun getFoodEntriesForMeal(mealId: Long): Flow<List<FoodEntry>> =
        foodDao.getFoodEntriesForMeal(mealId)

    fun getFoodEntriesForDay(dateMillis: Long): Flow<List<FoodEntry>> =
        foodDao.getFoodEntriesForDay(dateMillis)

    suspend fun insertFoodEntry(entry: FoodEntry): Long = foodDao.insertFoodEntry(entry)

    suspend fun updateFoodEntry(entry: FoodEntry) = foodDao.updateFoodEntry(entry)

    suspend fun deleteFoodEntry(entry: FoodEntry) = foodDao.deleteFoodEntry(entry)

    // ---- Workout Calories ----

    fun getWorkoutCaloriesForDay(dateMillis: Long): Flow<List<WorkoutCalories>> =
        workoutCaloriesDao.getForDay(dateMillis)

    fun getTotalBurnedForDay(dateMillis: Long): Flow<Float> =
        workoutCaloriesDao.getTotalBurnedForDay(dateMillis)

    suspend fun insertWorkoutCalories(entry: WorkoutCalories): Long =
        workoutCaloriesDao.insert(entry)

    // ---- OpenFoodFacts API ----

    suspend fun searchProducts(query: String): List<OFFProduct> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return emptyList()

        return runCatching { api.searchProducts(trimmedQuery, pageSize = 100).products }
            .getOrElse { emptyList() }
            .asSequence()
            .filter { it.displayName != "Unbekanntes Produkt" }
            .distinctBy { product ->
                product.code?.trim().takeUnless { it.isNullOrBlank() }?.normalizedForSearch()
                    ?: "${product.displayName}|${product.brands.orEmpty()}|${product.quantity.orEmpty()}"
                        .normalizedForSearch()
            }
            .sortedByDescending { relevanceScore(it, trimmedQuery) }
            .take(30)
            .toList()
    }

    suspend fun getProductByBarcode(barcode: String): OFFProduct? =
        runCatching { api.getProductByBarcode(barcode).product }.getOrNull()

    // ---- Custom Foods ----

    fun observeAllCustomFoods(): Flow<List<CustomFood>> = customFoodDao.getAll()

    suspend fun insertCustomFood(food: CustomFood): Long = customFoodDao.insert(food)

    suspend fun deleteCustomFood(food: CustomFood) = customFoodDao.delete(food)

    suspend fun searchCustomFoods(query: String): List<CustomFood> = customFoodDao.search(query)

    suspend fun getCustomFoodByBarcode(barcode: String): CustomFood? =
        customFoodDao.findByBarcode(barcode)

    suspend fun getCustomFoodCount(): Int = customFoodDao.getCount()

    // ---- Recipes ----

    fun observeAllRecipesWithItems(): Flow<List<RecipeWithItems>> =
        recipeDao.getAllRecipesWithItems()

    suspend fun getAllRecipesWithItemsOnce(): List<RecipeWithItems> =
        recipeDao.getAllRecipesWithItemsOnce()

    suspend fun insertRecipe(recipe: Recipe): Long = recipeDao.insertRecipe(recipe)

    suspend fun deleteRecipe(recipe: Recipe) = recipeDao.deleteRecipe(recipe)

    suspend fun insertRecipeItem(item: RecipeItem): Long = recipeDao.insertRecipeItem(item)

    suspend fun deleteRecipeItem(item: RecipeItem) = recipeDao.deleteRecipeItem(item)

    suspend fun getRecipeCount(): Int = recipeDao.getCount()

    private fun relevanceScore(product: OFFProduct, query: String): Int {
        val normalizedQuery = query.normalizedForSearch()
        if (normalizedQuery.isBlank()) return 0

        val name = product.displayName.normalizedForSearch()
        val brand = product.brands?.normalizedForSearch().orEmpty()

        var score = 0
        when {
            name == normalizedQuery -> score += EXACT_MATCH_SCORE
            name.startsWith(normalizedQuery) -> score += PREFIX_MATCH_SCORE
            name.contains(" $normalizedQuery") -> score += WORD_MATCH_SCORE
            name.contains(normalizedQuery) -> score += CONTAINS_MATCH_SCORE
        }
        if (brand.contains(normalizedQuery)) score += BRAND_MATCH_SCORE

        val tokens = normalizedQuery.split(" ")
        tokens.forEach { token ->
            when {
                name.startsWith(token) -> score += TOKEN_PREFIX_SCORE
                name.contains(" $token") -> score += TOKEN_WORD_SCORE
                name.contains(token) -> score += TOKEN_CONTAINS_SCORE
            }
            if (brand.contains(token)) score += TOKEN_BRAND_SCORE
        }

        score -= name.length.coerceAtMost(MAX_NAME_LENGTH_PENALTY)
        return score
    }

    private fun String.normalizedForSearch(): String =
        lowercase(Locale.ROOT)
            .replace('ä', 'a')
            .replace('ö', 'o')
            .replace('ü', 'u')
            .replace('ß', 's')
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
