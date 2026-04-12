package com.fittrack.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fittrack.app.data.dao.ExerciseDao
import com.fittrack.app.data.dao.LogEntryDao
import com.fittrack.app.data.dao.WorkoutDao
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.LogEntry
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.model.WorkoutExercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Exercise::class, Workout::class, WorkoutExercise::class, LogEntry::class],
    version = 2,
    exportSchema = false
)
abstract class FitTrackDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun logEntryDao(): LogEntryDao

    companion object {
        @Volatile
        private var INSTANCE: FitTrackDatabase? = null

        fun getDatabase(context: Context): FitTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FitTrackDatabase::class.java,
                    "fittrack_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    val dao = database.exerciseDao()
                                    if (dao.getCount() == 0) {
                                        populateInitialData(dao)
                                    }
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateInitialData(exerciseDao: ExerciseDao) {
            val exercises = listOf(
                // Chest
                Exercise(name = "Bench Press", muscleGroup = "Chest"),
                Exercise(name = "Incline Bench Press", muscleGroup = "Chest"),
                Exercise(name = "Decline Bench Press", muscleGroup = "Chest"),
                Exercise(name = "Dumbbell Flyes", muscleGroup = "Chest"),
                Exercise(name = "Cable Flyes", muscleGroup = "Chest"),
                Exercise(name = "Push-ups", muscleGroup = "Chest"),
                Exercise(name = "Dips", muscleGroup = "Chest"),
                // Back
                Exercise(name = "Pull-ups", muscleGroup = "Back"),
                Exercise(name = "Barbell Row", muscleGroup = "Back"),
                Exercise(name = "Seated Cable Row", muscleGroup = "Back"),
                Exercise(name = "Lat Pulldown", muscleGroup = "Back"),
                Exercise(name = "T-Bar Row", muscleGroup = "Back"),
                Exercise(name = "Deadlift", muscleGroup = "Back"),
                Exercise(name = "Face Pulls", muscleGroup = "Back"),
                // Shoulders
                Exercise(name = "Overhead Press", muscleGroup = "Shoulders"),
                Exercise(name = "Dumbbell Shoulder Press", muscleGroup = "Shoulders"),
                Exercise(name = "Lateral Raises", muscleGroup = "Shoulders"),
                Exercise(name = "Front Raises", muscleGroup = "Shoulders"),
                Exercise(name = "Rear Delt Flyes", muscleGroup = "Shoulders"),
                Exercise(name = "Arnold Press", muscleGroup = "Shoulders"),
                // Arms
                Exercise(name = "Barbell Curl", muscleGroup = "Biceps"),
                Exercise(name = "Dumbbell Curl", muscleGroup = "Biceps"),
                Exercise(name = "Hammer Curl", muscleGroup = "Biceps"),
                Exercise(name = "Preacher Curl", muscleGroup = "Biceps"),
                Exercise(name = "Tricep Pushdown", muscleGroup = "Triceps"),
                Exercise(name = "Skull Crushers", muscleGroup = "Triceps"),
                Exercise(name = "Overhead Tricep Extension", muscleGroup = "Triceps"),
                Exercise(name = "Close Grip Bench Press", muscleGroup = "Triceps"),
                // Legs
                Exercise(name = "Squat", muscleGroup = "Quads"),
                Exercise(name = "Leg Press", muscleGroup = "Quads"),
                Exercise(name = "Leg Extension", muscleGroup = "Quads"),
                Exercise(name = "Romanian Deadlift", muscleGroup = "Hamstrings"),
                Exercise(name = "Leg Curl", muscleGroup = "Hamstrings"),
                Exercise(name = "Hip Thrust", muscleGroup = "Glutes"),
                Exercise(name = "Bulgarian Split Squat", muscleGroup = "Quads"),
                Exercise(name = "Calf Raises", muscleGroup = "Calves"),
                Exercise(name = "Standing Calf Raises", muscleGroup = "Calves"),
                // Core
                Exercise(name = "Plank", muscleGroup = "Core"),
                Exercise(name = "Crunches", muscleGroup = "Core"),
                Exercise(name = "Leg Raises", muscleGroup = "Core"),
                Exercise(name = "Cable Crunches", muscleGroup = "Core"),
                Exercise(name = "Ab Wheel Rollout", muscleGroup = "Core")
            )
            exercises.forEach { exerciseDao.insertExercise(it) }
        }
    }
}
