package com.fittrack.app

import android.app.Application
import com.fittrack.app.data.backup.WorkoutBackupHelper
import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.network.RetrofitInstance
import com.fittrack.app.data.preferences.ActiveWorkoutSessionPreferences
import com.fittrack.app.data.preferences.BackupPreferences
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
    val backupPreferences by lazy { BackupPreferences(this) }
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
        // Auto-export workout plans, user profile, custom foods, and recipes to
        // app-internal private storage whenever any of them change.
        // Workout history (log entries) and daily food logs are intentionally excluded.
        applicationScope.launch {
            combine(
                repository.observeAllWorkoutsWithExercises(),
                userPreferences.userProfile,
                foodRepository.observeAllCustomFoods(),
                foodRepository.observeAllRecipesWithItems()
            ) { workouts, profile, customFoods, recipes ->
                WorkoutBackupHelper.exportData(
                    this@FitTrackApplication,
                    workouts,
                    profile,
                    customFoods,
                    recipes
                )
            }.collect {}
        }
    }
}
