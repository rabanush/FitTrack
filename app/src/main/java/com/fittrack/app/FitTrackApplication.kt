package com.fittrack.app

import android.app.Application
import com.fittrack.app.data.backup.WorkoutBackupHelper
import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.network.RetrofitInstance
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
            userPreferences
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Auto-export all app data to Downloads whenever workouts, log entries, or
        // the user profile change. This keeps the JSON backup in sync without any
        // user interaction, and covers fresh-install restore automatically.
        applicationScope.launch {
            combine(
                repository.observeAllWorkoutsWithExercises(),
                repository.allLogEntries,
                repository.allExercises,
                userPreferences.userProfile
            ) { workouts, logEntries, exercises, profile ->
                val exerciseNameById = exercises.associate { it.id to it.name }
                val workoutNameById = workouts.associate { (workout, _) -> workout.id to workout.name }
                WorkoutBackupHelper.exportData(
                    this@FitTrackApplication,
                    workouts,
                    logEntries,
                    exerciseNameById,
                    workoutNameById,
                    profile
                )
            }.collect {}
        }
    }
}
