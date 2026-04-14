package com.fittrack.app.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fittrack.app.data.database.FitTrackDatabase
import com.fittrack.app.data.repository.FitTrackRepository

object TestDatabase {
    fun create(): FitTrackDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, FitTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    fun createRepository(db: FitTrackDatabase): FitTrackRepository =
        FitTrackRepository(db.exerciseDao(), db.workoutDao(), db.logEntryDao())
}
