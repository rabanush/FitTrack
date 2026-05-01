package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.CustomFood
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.data.model.Recipe
import com.fittrack.app.data.model.RecipeItem
import com.fittrack.app.data.model.RecipeWithItems
import com.fittrack.app.data.network.OFFProduct
import com.fittrack.app.data.repository.FoodRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class RecipeBarcodeLookupState {
    object Idle : RecipeBarcodeLookupState()
    data class Found(
        val recipeId: Long,
        val name: String,
        val kcalPer100: Float,
        val proteinPer100: Float,
        val carbsPer100: Float,
        val fatPer100: Float
    ) : RecipeBarcodeLookupState()
    data class NotFound(val recipeId: Long) : RecipeBarcodeLookupState()
}

class RecipeViewModel(
    private val foodRepository: FoodRepository
) : ViewModel() {

    val recipes: StateFlow<List<RecipeWithItems>> = foodRepository.observeAllRecipesWithItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _barcodeLookupState =
        MutableStateFlow<RecipeBarcodeLookupState>(RecipeBarcodeLookupState.Idle)
    val barcodeLookupState: StateFlow<RecipeBarcodeLookupState> = _barcodeLookupState

    private val _ingredientSearchResults = MutableStateFlow<List<CustomFood>>(emptyList())
    val ingredientSearchResults: StateFlow<List<CustomFood>> = _ingredientSearchResults
    private var ingredientSearchJob: Job? = null

    fun lookupBarcodeForRecipe(barcode: String, recipeId: Long) {
        viewModelScope.launch {
            val product = foodRepository.getProductByBarcode(barcode)
            _barcodeLookupState.value = if (product != null) {
                val n = product.nutriments
                RecipeBarcodeLookupState.Found(
                    recipeId = recipeId,
                    name = product.displayName,
                    kcalPer100 = n?.kcalPer100g ?: 0f,
                    proteinPer100 = n?.proteins100g ?: 0f,
                    carbsPer100 = n?.carbohydrates100g ?: 0f,
                    fatPer100 = n?.fat100g ?: 0f
                )
            } else {
                RecipeBarcodeLookupState.NotFound(recipeId)
            }
        }
    }

    fun clearBarcodeLookupState() {
        _barcodeLookupState.value = RecipeBarcodeLookupState.Idle
    }

    /**
     * Searches the local food database (custom foods + recently used) for ingredients
     * matching [query]. Results are debounced by 300 ms and capped at 10 entries.
     * Clears [ingredientSearchResults] when [query] is blank.
     */
    fun searchIngredients(query: String) {
        ingredientSearchJob?.cancel()
        ingredientSearchJob = viewModelScope.launch {
            if (query.isBlank()) {
                _ingredientSearchResults.value = emptyList()
                return@launch
            }
            delay(300)
            val customFoods = foodRepository.searchCustomFoods(query)
            val recentFoods = foodRepository.searchRecentFoodEntries(query)
                .filterNot { recent ->
                    customFoods.any { it.name.equals(recent.name, ignoreCase = true) }
                }
            _ingredientSearchResults.value = (customFoods + recentFoods).take(10)
        }
    }

    fun clearIngredientSearch() {
        ingredientSearchJob?.cancel()
        _ingredientSearchResults.value = emptyList()
    }

    fun createRecipe(name: String) {
        viewModelScope.launch { foodRepository.insertRecipe(Recipe(name = name)) }
    }

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch { foodRepository.deleteRecipe(recipe) }
    }

    fun addRecipeItem(
        recipeId: Long,
        name: String,
        caloriesPer100: Float,
        proteinPer100: Float,
        carbsPer100: Float,
        fatPer100: Float,
        amount: Float
    ) {
        viewModelScope.launch {
            foodRepository.insertRecipeItem(
                RecipeItem(
                    recipeId = recipeId,
                    name = name,
                    caloriesPer100 = caloriesPer100,
                    proteinPer100 = proteinPer100,
                    carbsPer100 = carbsPer100,
                    fatPer100 = fatPer100,
                    amount = amount
                )
            )
        }
    }

    fun addCustomFoodToRecipe(food: CustomFood, recipeId: Long, amount: Float) {
        viewModelScope.launch {
            foodRepository.insertRecipeItem(
                RecipeItem(
                    recipeId = recipeId,
                    name = food.name,
                    caloriesPer100 = food.caloriesPer100,
                    proteinPer100 = food.proteinPer100,
                    carbsPer100 = food.carbsPer100,
                    fatPer100 = food.fatPer100,
                    amount = amount
                )
            )
        }
    }

    fun addProductToRecipe(
        product: OFFProduct,
        recipeId: Long,
        amount: Float
    ) {
        viewModelScope.launch {
            val n = product.nutriments
            foodRepository.insertRecipeItem(
                RecipeItem(
                    recipeId = recipeId,
                    name = product.displayName,
                    caloriesPer100 = n?.kcalPer100g ?: 0f,
                    proteinPer100 = n?.proteins100g ?: 0f,
                    carbsPer100 = n?.carbohydrates100g ?: 0f,
                    fatPer100 = n?.fat100g ?: 0f,
                    amount = amount
                )
            )
        }
    }

    fun createCustomFoodAndAddToRecipe(
        name: String,
        barcode: String?,
        caloriesPer100: Float,
        proteinPer100: Float,
        carbsPer100: Float,
        fatPer100: Float,
        recipeId: Long,
        amount: Float
    ) {
        viewModelScope.launch {
            val trimmedBarcode = barcode?.trim()?.takeIf { it.isNotEmpty() }
            foodRepository.insertCustomFood(
                CustomFood(
                    name = name,
                    barcode = trimmedBarcode,
                    caloriesPer100 = caloriesPer100,
                    proteinPer100 = proteinPer100,
                    carbsPer100 = carbsPer100,
                    fatPer100 = fatPer100
                )
            )
            foodRepository.insertRecipeItem(
                RecipeItem(
                    recipeId = recipeId,
                    name = name,
                    caloriesPer100 = caloriesPer100,
                    proteinPer100 = proteinPer100,
                    carbsPer100 = carbsPer100,
                    fatPer100 = fatPer100,
                    amount = amount
                )
            )
        }
    }

    fun deleteRecipeItem(item: RecipeItem) {
        viewModelScope.launch { foodRepository.deleteRecipeItem(item) }
    }

    /** Adds all items of a recipe as individual [FoodEntry] rows to the given meal. */
    fun addRecipeToMeal(recipeWithItems: RecipeWithItems, mealId: Long) {
        viewModelScope.launch {
            recipeWithItems.items.forEach { item ->
                foodRepository.insertFoodEntry(
                    FoodEntry(
                        mealId = mealId,
                        name = item.name,
                        caloriesPer100 = item.caloriesPer100,
                        proteinPer100 = item.proteinPer100,
                        carbsPer100 = item.carbsPer100,
                        fatPer100 = item.fatPer100,
                        amount = item.amount
                    )
                )
            }
        }
    }
}

class RecipeViewModelFactory(
    private val foodRepository: FoodRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RecipeViewModel(foodRepository) as T
}
