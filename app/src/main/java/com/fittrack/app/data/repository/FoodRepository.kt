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

class FoodRepository(
    private val foodDao: FoodDao,
    private val workoutCaloriesDao: WorkoutCaloriesDao,
    private val api: OpenFoodFactsApi,
    val userPreferences: UserPreferences,
    private val customFoodDao: CustomFoodDao,
    private val recipeDao: RecipeDao
) {

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

    suspend fun searchProducts(query: String): List<OFFProduct> =
        runCatching { api.searchProducts(query).products }.getOrElse { emptyList() }

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
}
