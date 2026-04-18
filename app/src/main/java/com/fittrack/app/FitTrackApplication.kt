package com.fittrack.app

import android.app.Application
import com.fittrack.app.data.backup.WorkoutBackupHelper
import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.model.CustomFood
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.RecipeWithItems
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.model.WorkoutExerciseWithExercise
import com.fittrack.app.data.network.RetrofitInstance
import com.fittrack.app.data.preferences.ActiveWorkoutSessionPreferences
import com.fittrack.app.data.preferences.UserProfile
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.repository.FitTrackRepository
import com.fittrack.app.data.repository.FoodRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FitTrackApplication : Application() {

    private data class BackupCoreSnapshot(
        val workouts: List<Pair<Workout, List<WorkoutExerciseWithExercise>>>,
        val profile: UserProfile,
        val customFoods: List<CustomFood>,
        val recipes: List<RecipeWithItems>,
        val customExercises: List<Exercise>
    )

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
            val backupCoreFlow = combine(
                repository.observeAllWorkoutsWithExercises(),
                userPreferences.userProfile,
                foodRepository.observeAllCustomFoods(),
                foodRepository.observeAllRecipesWithItems(),
                repository.allExercises
            ) { workouts, profile, customFoods, recipes, exercises ->
                BackupCoreSnapshot(
                    workouts = workouts,
                    profile = profile,
                    customFoods = customFoods,
                    recipes = recipes,
                    customExercises = exercises.filter { it.isCustom }
                )
            }

            combine(
                backupCoreFlow,
                foodRepository.observeAllMeals(),
                foodRepository.observeAllFoodEntries(),
                foodRepository.observeAllWorkoutCalories()
            ) { core, meals, foodEntries, workoutCalories ->
                WorkoutBackupHelper.exportData(
                    this@FitTrackApplication,
                    core.workouts,
                    core.profile,
                    core.customFoods,
                    core.recipes,
                    core.customExercises,
                    meals,
                    foodEntries,
                    workoutCalories
                )
            }.collect {}
        }
    }
}
