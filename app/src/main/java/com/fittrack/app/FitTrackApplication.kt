package com.fittrack.app

import android.app.Application
import com.fittrack.app.data.backup.WorkoutBackupHelper
import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.model.CustomFood
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.data.model.Meal
import com.fittrack.app.data.model.RecipeWithItems
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.model.WorkoutCalories
import com.fittrack.app.data.model.WorkoutExerciseWithExercise
import com.fittrack.app.data.network.RetrofitInstance
import com.fittrack.app.data.preferences.ActiveWorkoutSession
import com.fittrack.app.data.preferences.ActiveWorkoutSessionPreferences
import com.fittrack.app.data.preferences.UserProfile
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.repository.FitTrackRepository
import com.fittrack.app.data.repository.FoodRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FitTrackApplication : Application() {

    private companion object {
        const val BACKUP_DEBOUNCE_MILLIS = 300L
    }

    // Internal so MainActivity can launch import after the user picks a backup folder.
    internal val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val userPreferences by lazy { UserPreferences(this) }
    val activeWorkoutSessionPreferences by lazy { ActiveWorkoutSessionPreferences(this) }
    val database by lazy { FitTrackDatabase.getDatabase(this) }
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
            // Restore from backup using whatever folder URI is already stored (may be null on
            // a truly fresh install before the user has picked a folder).
            runImport()
            observeAndPersistBackups()
        }
    }

    /**
     * Called by MainActivity after the user selects a backup folder for the first time.
     * Waits for DB initialisation to complete and then attempts to restore from the backup.
     */
    suspend fun performImportAfterFolderSelected() {
        database.initializationComplete.await()
        runImport()
    }

    private suspend fun runImport() {
        val treeUriString = userPreferences.backupFolderUri.first()
        WorkoutBackupHelper.importData(
            context = this@FitTrackApplication,
            treeUriString = treeUriString,
            exerciseDao = database.exerciseDao(),
            workoutDao = database.workoutDao(),
            userPreferences = userPreferences,
            customFoodDao = database.customFoodDao(),
            recipeDao = database.recipeDao(),
            foodDao = database.foodDao(),
            workoutCaloriesDao = database.workoutCaloriesDao(),
            activeWorkoutSessionPreferences = activeWorkoutSessionPreferences
        )
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
            .debounce(BACKUP_DEBOUNCE_MILLIS)
            .collect { snapshot ->
                // Read the current URI at export time so that folder changes are always picked
                // up without requiring a restart.  Intentionally not combined into the flow to
                // avoid exporting an empty snapshot immediately after the user selects a folder
                // (before import has run).
                val treeUriString = userPreferences.backupFolderUri.first()
                WorkoutBackupHelper.exportData(
                    context = this@FitTrackApplication,
                    treeUriString = treeUriString,
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
        val workouts: List<Pair<Workout, List<WorkoutExerciseWithExercise>>> = emptyList(),
        val userProfile: UserProfile = UserProfile(),
        val customFoods: List<CustomFood> = emptyList(),
        val recipes: List<RecipeWithItems> = emptyList(),
        val customExercises: List<Exercise> = emptyList(),
        val meals: List<Meal> = emptyList(),
        val foodEntries: List<FoodEntry> = emptyList(),
        val workoutCalories: List<WorkoutCalories> = emptyList(),
        val activeWorkoutSession: ActiveWorkoutSession? = null,
        val activeWorkoutExerciseSessionsState: String? = null
    )
}
