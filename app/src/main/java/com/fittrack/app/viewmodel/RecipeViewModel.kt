package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.data.model.Recipe
import com.fittrack.app.data.model.RecipeItem
import com.fittrack.app.data.model.RecipeWithItems
import com.fittrack.app.data.repository.FoodRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RecipeViewModel(
    private val foodRepository: FoodRepository
) : ViewModel() {

    val recipes: StateFlow<List<RecipeWithItems>> = foodRepository.observeAllRecipesWithItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
