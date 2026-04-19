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
    private data class ProductDedupKey(
        val code: String?,
        val name: String,
        val brand: String,
        val quantity: String
    )

    private companion object {
        const val RECENT_USAGE_RETENTION_DAYS = 90L
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
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
        val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9 ]")
        val MULTI_SPACE_REGEX = Regex("\\s+")
    }

    // ---- Meals ----

    fun getMealsForDay(dateMillis: Long): Flow<List<Meal>> =
        foodDao.getMealsForDay(dateMillis)

    fun observeAllMeals(): Flow<List<Meal>> = foodDao.getAllMeals()

    suspend fun insertMeal(meal: Meal): Long = foodDao.insertMeal(meal)

    suspend fun deleteMeal(meal: Meal) = foodDao.deleteMeal(meal)

    suspend fun getMealById(id: Long): Meal? = foodDao.getMealById(id)

    // ---- Food Entries ----

    fun getFoodEntriesForMeal(mealId: Long): Flow<List<FoodEntry>> =
        foodDao.getFoodEntriesForMeal(mealId)

    fun getFoodEntriesForDay(dateMillis: Long): Flow<List<FoodEntry>> =
        foodDao.getFoodEntriesForDay(dateMillis)

    fun observeAllFoodEntries(): Flow<List<FoodEntry>> = foodDao.getAllFoodEntries()

    suspend fun insertFoodEntry(entry: FoodEntry): Long {
        // Populate loggedDateMillis from the parent meal so the entry retains its date
        // even after old meals are cleaned up (meal_id becomes null via SET_NULL FK).
        val dated = if (entry.loggedDateMillis == 0L && entry.mealId != null) {
            val meal = foodDao.getMealById(entry.mealId)
            if (meal != null) entry.copy(loggedDateMillis = meal.dateMillis) else entry
        } else entry
        return foodDao.insertFoodEntry(dated)
    }

    suspend fun updateFoodEntry(entry: FoodEntry) = foodDao.updateFoodEntry(entry)

    suspend fun deleteFoodEntry(entry: FoodEntry) = foodDao.deleteFoodEntry(entry)

    // ---- Workout Calories ----

    fun getWorkoutCaloriesForDay(dateMillis: Long): Flow<List<WorkoutCalories>> =
        workoutCaloriesDao.getForDay(dateMillis)

    fun getTotalBurnedForDay(dateMillis: Long): Flow<Float> =
        workoutCaloriesDao.getTotalBurnedForDay(dateMillis)

    fun observeAllWorkoutCalories(): Flow<List<WorkoutCalories>> = workoutCaloriesDao.getAll()

    suspend fun insertWorkoutCalories(entry: WorkoutCalories): Long =
        workoutCaloriesDao.insert(entry)

    // ---- OpenFoodFacts API ----

    suspend fun searchProducts(query: String): List<OFFProduct> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return emptyList()
        val normalizedQuery = trimmedQuery.normalizedForSearch()
        val queryTokens = normalizedQuery.split(" ")
        return runCatching { api.searchProducts(trimmedQuery, pageSize = 100).products }
            .getOrElse { emptyList() }
            .asSequence()
            .filter { it.displayName != "Unbekanntes Produkt" }
            .distinctBy { product ->
                ProductDedupKey(
                    code = product.code?.trim().takeUnless { it.isNullOrBlank() }?.normalizedForSearch(),
                    name = product.displayName.normalizedForSearch(),
                    brand = product.brands.orEmpty().normalizedForSearch(),
                    quantity = product.quantity.orEmpty().normalizedForSearch()
                )
            }
            .sortedByDescending { relevanceScore(it, normalizedQuery, queryTokens) }
            .take(30)
            .toList()
    }

    suspend fun getProductByBarcode(barcode: String): OFFProduct? =
        runCatching { api.getProductByBarcode(barcode).product }.getOrNull()

    // ---- Custom Foods ----

    fun observeAllCustomFoods(): Flow<List<CustomFood>> = customFoodDao.getAll()

    suspend fun insertCustomFood(food: CustomFood): Long = customFoodDao.insert(food)

    suspend fun deleteCustomFood(food: CustomFood) = customFoodDao.delete(food)

    suspend fun searchCustomFoods(query: String): List<CustomFood> =
        customFoodDao.search(query).sortedBy { it.name.lowercase(Locale.ROOT) }

    suspend fun getCustomFoodByBarcode(barcode: String): CustomFood? =
        customFoodDao.findByBarcode(barcode)

    suspend fun getCustomFoodCount(): Int = customFoodDao.getCount()

    private fun FoodDao.RecentlyUsedFoodWithNutrition.toCustomFood() = CustomFood(
        id = 0,
        name = name,
        barcode = barcode,
        caloriesPer100 = caloriesPer100,
        proteinPer100 = proteinPer100,
        carbsPer100 = carbsPer100,
        fatPer100 = fatPer100
    )

    /**
     * Returns the most recently used foods (distinct by barcode/name) with full nutrition
     * data from the matching food_entry row. Used to populate the "recently used" list on
     * the food search screen before the user has entered any query.
     */
    suspend fun getRecentlyUsedFoodsAsCustom(): List<CustomFood> {
        val sinceMillis = System.currentTimeMillis() - (RECENT_USAGE_RETENTION_DAYS * MILLIS_PER_DAY)
        return foodDao.searchRecentFoodEntriesWithNutrition(query = "", sinceMillis = sinceMillis)
            .map { it.toCustomFood() }
    }

    /**
     * Searches the history of logged food entries (food_entries table) for items whose
     * name contains [query]. Returns distinct results (by barcode/name) sorted by most
     * recently used. This ensures products previously scanned or logged – including
     * OpenFoodFacts products like "Natural SKyr" – always appear when the user searches
     * a matching keyword, regardless of what the API returns.
     */
    suspend fun searchRecentFoodEntries(query: String): List<CustomFood> {
        val sinceMillis = System.currentTimeMillis() - (RECENT_USAGE_RETENTION_DAYS * MILLIS_PER_DAY)
        return foodDao.searchRecentFoodEntriesWithNutrition(query, sinceMillis).map { it.toCustomFood() }
    }

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

    private fun relevanceScore(
        product: OFFProduct,
        normalizedQuery: String,
        queryTokens: List<String>
    ): Int {
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

        queryTokens.forEach { token ->
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
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

}
