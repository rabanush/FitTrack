package com.fittrack.app

import android.app.Application
import com.fittrack.app.data.backup.WorkoutBackupHelper
import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.network.RetrofitInstance
import com.fittrack.app.data.preferences.ActiveWorkoutSessionPreferences
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.repository.FitTrackRepository
import com.fittrack.app.data.repository.FoodRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FitTrackApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val userPreferences by lazy { UserPreferences(this) }
    val activeWorkoutSessionPreferences by lazy { ActiveWorkoutSessionPreferences(this) }
    val database by lazy { FitTrackDatabase.getDatabase(this, userPreferences) }
    val repository by lazy {
        FitTrackRepository(
            database.exerciseDao(),
            database.workoutDao(),
            database.logEntryDao()
        )
    }
    val foodRepository by lazy {
        FoodRepository(
            database.foodDao(),
            database.workoutCaloriesDao(),
            RetrofitInstance.api,
            userPreferences,
            database.customFoodDao(),
            database.recipeDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Auto-export all relevant data to app-private storage whenever it changes.
        applicationScope.launch {
            combine(
                repository.observeAllWorkoutsWithExercises(),
                userPreferences.userProfile,
                foodRepository.observeAllCustomFoods(),
                foodRepository.observeAllRecipesWithItems(),
                repository.allExercises,
                foodRepository.observeAllMeals(),
                foodRepository.observeAllFoodEntries(),
                foodRepository.observeAllWorkoutCalories()
            ) { workouts, profile, customFoods, recipes, exercises, meals, foodEntries, workoutCalories ->
                WorkoutBackupHelper.exportData(
                    this@FitTrackApplication,
                    workouts,
                    profile,
                    customFoods,
                    recipes,
                    exercises.filter { it.isCustom },
                    meals,
                    foodEntries,
                    workoutCalories
                )
            }.collect {}
        }
    }
}
