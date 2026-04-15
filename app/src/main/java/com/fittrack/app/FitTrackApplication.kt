package com.fittrack.app

import android.app.Application
import com.fittrack.app.data.backup.WorkoutBackupHelper
import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.network.RetrofitInstance
import com.fittrack.app.data.preferences.BackupPreferences
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.repository.FitTrackRepository
import com.fittrack.app.data.repository.FoodRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class FitTrackApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val userPreferences by lazy { UserPreferences(this) }
    val backupPreferences by lazy { BackupPreferences(this) }
    val database by lazy { FitTrackDatabase.getDatabase(this, userPreferences, backupPreferences) }
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
        // Auto-export workout plans, user profile, custom foods, and recipes to the
        // user-chosen backup folder whenever any of them change.
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
                    backupPreferences.getTreeUri(),
                    workouts,
                    profile,
                    customFoods,
                    recipes
                )
            }.collect {}
        }
    }

    /**
     * Explicitly triggers a backup import. Call this when the backup folder URI becomes
     * available after the database has already been opened (e.g. after the user picks the
     * folder via the system picker on first launch or after a reinstall).
     * An [AtomicBoolean] guard prevents concurrent import runs.
     */
    private val importInProgress = AtomicBoolean(false)

    fun importBackupNow() {
        if (!importInProgress.compareAndSet(false, true)) return
        applicationScope.launch {
            try {
                WorkoutBackupHelper.importData(
                    this@FitTrackApplication,
                    backupPreferences.getTreeUri(),
                    database.exerciseDao(),
                    database.workoutDao(),
                    userPreferences,
                    database.customFoodDao(),
                    database.recipeDao()
                )
            } finally {
                importInProgress.set(false)
            }
        }
    }
}
