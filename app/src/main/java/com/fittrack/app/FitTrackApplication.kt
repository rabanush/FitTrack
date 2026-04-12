package com.fittrack.app

import android.app.Application
import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.repository.FitTrackRepository

class FitTrackApplication : Application() {
    val database by lazy { FitTrackDatabase.getDatabase(this) }
    val repository by lazy {
        FitTrackRepository(
            database.exerciseDao(),
            database.workoutDao(),
            database.logEntryDao()
        )
    }
}
