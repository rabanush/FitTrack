package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.data.network.OFFProduct
import com.fittrack.app.data.repository.FoodRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val products: List<OFFProduct>) : SearchState()
    data class Error(val message: String) : SearchState()
}

class FoodSearchViewModel(
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState

    private val _barcodeProduct = MutableStateFlow<OFFProduct?>(null)
    val barcodeProduct: StateFlow<OFFProduct?> = _barcodeProduct

    fun search(query: String) {
        if (query.isBlank()) {
            _searchState.value = SearchState.Idle
            return
        }
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            val products = foodRepository.searchProducts(query)
            _searchState.value = if (products.isEmpty()) {
                SearchState.Error("Keine Produkte gefunden")
            } else {
                SearchState.Success(products)
            }
        }
    }

    fun lookupBarcode(barcode: String) {
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            val product = foodRepository.getProductByBarcode(barcode)
            if (product != null) {
                _barcodeProduct.value = product
                _searchState.value = SearchState.Success(listOf(product))
            } else {
                _searchState.value = SearchState.Error("Produkt nicht gefunden: $barcode")
            }
        }
    }

    fun clearBarcodeProduct() {
        _barcodeProduct.value = null
    }

    /**
     * Adds a product to a meal with the specified amount in grams.
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
