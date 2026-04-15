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
        // Auto-export workout plans and user profile to Downloads whenever they change.
        // Workout history (log entries) is intentionally excluded from the backup.
        applicationScope.launch {
            combine(
                repository.observeAllWorkoutsWithExercises(),
                userPreferences.userProfile
            ) { workouts, profile ->
                WorkoutBackupHelper.exportData(
                    this@FitTrackApplication,
                    workouts,
                    profile
                )
            }.collect {}
        }
    }
}
