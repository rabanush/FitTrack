package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.CustomFood
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.data.network.OFFProduct
import com.fittrack.app.data.repository.FoodRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(
        val products: List<OFFProduct>,
        val customFoods: List<CustomFood> = emptyList()
    ) : SearchState()
    data class Error(val message: String) : SearchState()
    /** Barcode was not found in API or local DB; barcode string is provided to pre-fill creation. */
    data class BarcodeNotFound(val barcode: String) : SearchState()
}

class FoodSearchViewModel(
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    private val _barcodeProduct = MutableStateFlow<OFFProduct?>(null)
    val barcodeProduct: StateFlow<OFFProduct?> = _barcodeProduct
    private var searchJob: Job? = null

    fun search(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            searchJob?.cancel()
            _searchState.value = SearchState.Idle
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchState.value = SearchState.Loading
            val apiProducts = foodRepository.searchProducts(trimmedQuery)
            val customFoods = foodRepository.searchCustomFoods(trimmedQuery)
            _searchState.value = if (apiProducts.isEmpty() && customFoods.isEmpty()) {
                SearchState.Error("Keine Produkte gefunden")
            } else {
                SearchState.Success(apiProducts, customFoods)
            }
        }
    }

    fun lookupBarcode(barcode: String) {
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            // Check local custom foods first (no network needed)
            val localFood = foodRepository.getCustomFoodByBarcode(barcode)
            if (localFood != null) {
                _searchState.value = SearchState.Success(emptyList(), listOf(localFood))
                return@launch
            }
            // Fall back to OpenFoodFacts
            val product = foodRepository.getProductByBarcode(barcode)
            if (product != null) {
                _barcodeProduct.value = product
                _searchState.value = SearchState.Success(listOf(product))
            } else {
                _searchState.value = SearchState.BarcodeNotFound(barcode)
            }
        }
    }

    fun clearBarcodeProduct() {
        _barcodeProduct.value = null
    }

    /**
     * Creates a custom food entry in the database and immediately adds it
     * to the given meal with the specified amount.
     */
    fun createCustomFoodAndAddToMeal(
        name: String,
        barcode: String?,
        caloriesPer100: Float,
        proteinPer100: Float,
        carbsPer100: Float,
        fatPer100: Float,
        mealId: Long,
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
            foodRepository.insertFoodEntry(
                FoodEntry(
                    mealId = mealId,
                    name = name,
                    barcode = trimmedBarcode,
                    caloriesPer100 = caloriesPer100,
                    proteinPer100 = proteinPer100,
                    carbsPer100 = carbsPer100,
                    fatPer100 = fatPer100,
                    amount = amount
                )
            )
        }
    }

    /**
     * Adds an existing custom food product to a meal with the specified amount in grams.
     */
    fun addCustomFoodToMeal(food: CustomFood, mealId: Long, amountGrams: Float) {
        viewModelScope.launch {
            foodRepository.insertFoodEntry(
                FoodEntry(
                    mealId = mealId,
                    name = food.name,
                    barcode = food.barcode,
                    caloriesPer100 = food.caloriesPer100,
                    proteinPer100 = food.proteinPer100,
                    carbsPer100 = food.carbsPer100,
                    fatPer100 = food.fatPer100,
                    amount = amountGrams
                )
            )
        }
    }

    /**
     * Adds a product from OpenFoodFacts to a meal with the specified amount in grams.
     */
    fun addProductToMeal(product: OFFProduct, mealId: Long, amountGrams: Float) {
        viewModelScope.launch {
            val n = product.nutriments
            foodRepository.insertFoodEntry(
                FoodEntry(
                    mealId = mealId,
                    name = product.displayName,
                    barcode = product.code,
                    caloriesPer100 = n?.kcalPer100g ?: 0f,
                    proteinPer100 = n?.proteins100g ?: 0f,
                    carbsPer100 = n?.carbohydrates100g ?: 0f,
                    fatPer100 = n?.fat100g ?: 0f,
                    amount = amountGrams
                )
            )
        }
    }

    fun resetState() {
        _searchState.value = SearchState.Idle
    }
}

class FoodSearchViewModelFactory(
    private val foodRepository: FoodRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FoodSearchViewModel(foodRepository) as T
}
