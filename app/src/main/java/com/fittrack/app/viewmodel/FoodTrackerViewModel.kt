package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.data.model.Meal
import com.fittrack.app.data.preferences.UserProfile
import com.fittrack.app.data.repository.FoodRepository
import com.fittrack.app.util.todayMillis
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DailyMacros(
    val calories: Float = 0f,
    val protein: Float = 0f,
    val carbs: Float = 0f,
    val fat: Float = 0f
)

data class MealWithEntries(
    val meal: Meal,
    val entries: List<FoodEntry>
)

class FoodTrackerViewModel(
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _selectedDateMillis = MutableStateFlow(todayMillis())
    val selectedDateMillis: StateFlow<Long> = _selectedDateMillis

    private val defaultMealsMutex = Mutex()

    init {
        viewModelScope.launch {
            _selectedDateMillis.collect { date -> ensureDefaultMeals(date) }
        }
    }

    private suspend fun ensureDefaultMeals(dateMillis: Long) {
        defaultMealsMutex.withLock {
            val existing = foodRepository.getMealsForDay(dateMillis).first()
            if (existing.isEmpty()) {
                listOf("Breakfast", "Lunch", "Snack", "Dinner").forEach { name ->
                    foodRepository.insertMeal(Meal(name = name, dateMillis = dateMillis))
                }
            }
        }
    }

    val userProfile: StateFlow<UserProfile> = foodRepository.userPreferences.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    val mealsWithEntries: StateFlow<List<MealWithEntries>> =
        _selectedDateMillis.flatMapLatest { date ->
            foodRepository.getMealsForDay(date).flatMapLatest { meals ->
                if (meals.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        meals.map { meal ->
                            foodRepository.getFoodEntriesForMeal(meal.id).map { entries ->
                                MealWithEntries(meal, entries)
                            }
                        }
                    ) { it.toList() }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dailyConsumed: StateFlow<DailyMacros> = mealsWithEntries.map { meals ->
        meals.flatMap { it.entries }.fold(DailyMacros()) { acc, entry ->
            acc.copy(
                calories = acc.calories + entry.calories,
                protein = acc.protein + entry.protein,
                carbs = acc.carbs + entry.carbs,
                fat = acc.fat + entry.fat
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyMacros())

    val totalBurnedToday: StateFlow<Float> = _selectedDateMillis.flatMapLatest { date ->
        foodRepository.getTotalBurnedForDay(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    /** Net calories = target – consumed + burned (burned reduces the deficit). */
    val netCalories: StateFlow<Float> = combine(
        userProfile, dailyConsumed, totalBurnedToday
    ) { profile, consumed, burned ->
        profile.tdee - consumed.calories + burned
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    fun selectDate(dateMillis: Long) {
        _selectedDateMillis.value = dateMillis
    }

    fun addMeal(name: String) {
        viewModelScope.launch {
            foodRepository.insertMeal(Meal(name = name, dateMillis = _selectedDateMillis.value))
        }
    }

    fun deleteMeal(meal: Meal) {
        viewModelScope.launch { foodRepository.deleteMeal(meal) }
    }

    fun deleteFoodEntry(entry: FoodEntry) {
        viewModelScope.launch { foodRepository.deleteFoodEntry(entry) }
    }
}

class FoodTrackerViewModelFactory(
    private val foodRepository: FoodRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FoodTrackerViewModel(foodRepository) as T
}
