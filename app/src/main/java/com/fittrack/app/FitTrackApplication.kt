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
import kotlinx.coroutines.flow.debounce
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
        applicationScope.launch {
            database.initializationComplete.await()
            WorkoutBackupHelper.importData(
                context = this@FitTrackApplication,
                exerciseDao = database.exerciseDao(),
                workoutDao = database.workoutDao(),
                userPreferences = userPreferences,
                customFoodDao = database.customFoodDao(),
                recipeDao = database.recipeDao(),
                foodDao = database.foodDao(),
                workoutCaloriesDao = database.workoutCaloriesDao(),
                activeWorkoutSessionPreferences = activeWorkoutSessionPreferences
            )
            observeAndPersistBackups()
        }
    }

    private suspend fun observeAndPersistBackups() {
        combine(
            repository.observeAllWorkoutsWithExercises(),
            userPreferences.userProfile
        ) { workouts, profile ->
            BackupSnapshot(
                workouts = workouts,
                userProfile = profile
            )
        }
            .combine(foodRepository.observeAllCustomFoods()) { snapshot, customFoods ->
                snapshot.copy(customFoods = customFoods)
            }
            .combine(foodRepository.observeAllRecipesWithItems()) { snapshot, recipes ->
                snapshot.copy(recipes = recipes)
            }
            .combine(repository.allExercises) { snapshot, exercises ->
                snapshot.copy(customExercises = exercises.filter { it.isCustom })
            }
            .combine(foodRepository.observeAllMeals()) { snapshot, meals ->
                snapshot.copy(meals = meals)
            }
            .combine(foodRepository.observeAllFoodEntries()) { snapshot, foodEntries ->
                snapshot.copy(foodEntries = foodEntries)
            }
            .combine(foodRepository.observeAllWorkoutCalories()) { snapshot, workoutCalories ->
                snapshot.copy(workoutCalories = workoutCalories)
            }
            .combine(activeWorkoutSessionPreferences.changeEvents) { snapshot, _ ->
                snapshot.copy(
                    activeWorkoutSession = activeWorkoutSessionPreferences.getSession(),
                    activeWorkoutExerciseSessionsState = activeWorkoutSessionPreferences.getExerciseSessionsState()
                )
            }
            .debounce(300)
            .collect { snapshot ->
                WorkoutBackupHelper.exportData(
                    context = this,
                    workoutsWithExercises = snapshot.workouts,
                    userProfile = snapshot.userProfile,
                    customFoods = snapshot.customFoods,
                    recipes = snapshot.recipes,
                    customExercises = snapshot.customExercises,
                    meals = snapshot.meals,
                    foodEntries = snapshot.foodEntries,
                    workoutCalories = snapshot.workoutCalories,
                    activeWorkoutSession = snapshot.activeWorkoutSession,
                    activeWorkoutExerciseSessionsState = snapshot.activeWorkoutExerciseSessionsState
                )
            }
    }

    private data class BackupSnapshot(
        val workouts: List<Pair<com.fittrack.app.data.model.Workout, List<com.fittrack.app.data.model.WorkoutExerciseWithExercise>>> = emptyList(),
        val userProfile: com.fittrack.app.data.preferences.UserProfile = com.fittrack.app.data.preferences.UserProfile(),
        val customFoods: List<com.fittrack.app.data.model.CustomFood> = emptyList(),
        val recipes: List<com.fittrack.app.data.model.RecipeWithItems> = emptyList(),
        val customExercises: List<com.fittrack.app.data.model.Exercise> = emptyList(),
        val meals: List<com.fittrack.app.data.model.Meal> = emptyList(),
        val foodEntries: List<com.fittrack.app.data.model.FoodEntry> = emptyList(),
        val workoutCalories: List<com.fittrack.app.data.model.WorkoutCalories> = emptyList(),
        val activeWorkoutSession: com.fittrack.app.data.preferences.ActiveWorkoutSession? = null,
        val activeWorkoutExerciseSessionsState: String? = null
    )
}
