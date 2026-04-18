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

    private data class RankedProduct(
        val product: OFFProduct,
        val recentUsageIndex: Int?,
        val relevance: Int
    )

    private data class RecentUsageIndex(
        val barcodes: Map<String, Int>,
        val names: Map<String, Int>,
        val orderedNames: List<SimilarityNameEntry>
    ) {
        data class SimilarityNameEntry(
            val normalizedName: String,
            val index: Int,
            val tokens: Set<String>
        )

        fun get(barcode: String?, normalizedName: String): Int? {
            val barcodeIndex = barcode?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { barcodes[it] }
            val nameIndex = names[normalizedName]
            val exactIndex = when {
                barcodeIndex != null && nameIndex != null -> minOf(barcodeIndex, nameIndex)
                barcodeIndex != null -> barcodeIndex
                else -> nameIndex
            }
            if (exactIndex != null) return exactIndex
            if (normalizedName.isBlank()) return null

            val normalizedTokens = normalizedName.tokensForSimilarity()
            return orderedNames.firstOrNull { recent ->
                recent.normalizedName.isLikelySameFoodName(
                    candidate = normalizedName,
                    thisTokens = recent.tokens,
                    candidateTokens = normalizedTokens
                )
            }?.index
        }
    }

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
        /** Ignore very short fragments when comparing food-name similarity. */
        const val MIN_SIMILARITY_TOKEN_LENGTH = 3
        /** Allow prefix similarity only for names with enough signal. */
        const val MIN_PREFIX_NAME_LENGTH = 4
        /** Require at least this many shared tokens for fuzzy name equality. */
        const val MIN_COMMON_TOKENS_FOR_SIMILARITY = 2
        /**
         * A single shared token of at least this length is considered distinctive enough
         * to count as a recency match on its own (e.g. "skyr" in "Naturl Skyr" vs "Skyr Natur").
         */
        const val MIN_DISTINCTIVE_TOKEN_LENGTH = 4
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
        val insertedId = foodDao.insertFoodEntry(entry)
        runCatching {
            userPreferences.addRecentFoodUsage(
                name = entry.name,
                barcode = entry.barcode,
                addedAtMillis = System.currentTimeMillis(),
                retentionDays = RECENT_USAGE_RETENTION_DAYS
            )
        }
        return insertedId
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
        val recentUsageIndex = getRecentUsageIndex()

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
            .map { product ->
                RankedProduct(
                    product = product,
                    recentUsageIndex = recentUsageIndex.get(
                        product.code,
                        product.displayName.normalizedForSearch()
                    ),
                    relevance = relevanceScore(product, normalizedQuery, queryTokens)
                )
            }
            .sortedWith(
                compareBy<RankedProduct> { it.recentUsageIndex ?: Int.MAX_VALUE }
                    .thenByDescending { it.relevance }
            )
            .map { it.product }
            .take(30)
            .toList()
    }

    suspend fun getProductByBarcode(barcode: String): OFFProduct? =
        runCatching { api.getProductByBarcode(barcode).product }.getOrNull()

    // ---- Custom Foods ----

    fun observeAllCustomFoods(): Flow<List<CustomFood>> = customFoodDao.getAll()

    suspend fun insertCustomFood(food: CustomFood): Long = customFoodDao.insert(food)

    suspend fun deleteCustomFood(food: CustomFood) = customFoodDao.delete(food)

    suspend fun searchCustomFoods(query: String): List<CustomFood> {
        val recentUsageIndex = getRecentUsageIndex()
        return customFoodDao.search(query).sortedWith(
            compareBy<CustomFood> {
                recentUsageIndex.get(it.barcode, it.name.normalizedForSearch()) ?: Int.MAX_VALUE
            }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

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

    private suspend fun getRecentUsageIndex(): RecentUsageIndex {
        val sinceMillis = System.currentTimeMillis() - (RECENT_USAGE_RETENTION_DAYS * MILLIS_PER_DAY)
        val rows = userPreferences.getRecentFoodUsagesSince(sinceMillis)

        val barcodeIndex = linkedMapOf<String, Int>()
        val nameIndex = linkedMapOf<String, Int>()
        val orderedNames = mutableListOf<RecentUsageIndex.SimilarityNameEntry>()
        val seenOrderedNames = hashSetOf<String>()

        rows.forEachIndexed { index, row ->
            row.barcode?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { barcodeIndex.putIfAbsent(it, index) }
            val normalizedName = row.name.normalizedForSearch()
            if (normalizedName.isNotEmpty()) {
                nameIndex.putIfAbsent(normalizedName, index)
                if (seenOrderedNames.add(normalizedName)) {
                    orderedNames += RecentUsageIndex.SimilarityNameEntry(
                        normalizedName = normalizedName,
                        index = index,
                        tokens = normalizedName.tokensForSimilarity()
                    )
                }
            }
        }

        return RecentUsageIndex(
            barcodes = barcodeIndex,
            names = nameIndex,
            orderedNames = orderedNames
        )
    }

    private fun String.tokensForSimilarity(): Set<String> =
        split(" ")
            .filter { it.length >= MIN_SIMILARITY_TOKEN_LENGTH }
            .toSet()

    private fun String.isLikelySameFoodName(
        candidate: String,
        thisTokens: Set<String>,
        candidateTokens: Set<String>
    ): Boolean {
        if (this == candidate) return true
        if (this.length >= MIN_PREFIX_NAME_LENGTH &&
            candidate.length >= MIN_PREFIX_NAME_LENGTH &&
            (startsWith(candidate) || candidate.startsWith(this))
        ) {
            return true
        }
        if (thisTokens.isEmpty() || candidateTokens.isEmpty()) return false
        val sharedTokens = thisTokens.intersect(candidateTokens)
        if (sharedTokens.size >= MIN_COMMON_TOKENS_FOR_SIMILARITY) return true
        // A single long-enough token (e.g. "skyr") is distinctive enough on its own.
        return sharedTokens.any { it.length >= MIN_DISTINCTIVE_TOKEN_LENGTH }
    }
}
