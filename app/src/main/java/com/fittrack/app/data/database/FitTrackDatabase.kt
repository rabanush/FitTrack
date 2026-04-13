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
    version = 3,
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
                Exercise(name = "Bench Press", germanName = "Bankdrücken", muscleGroup = "Chest"),
                Exercise(name = "Incline Bench Press", germanName = "Schrägbankdrücken", muscleGroup = "Chest"),
                Exercise(name = "Decline Bench Press", germanName = "Negativbankdrücken", muscleGroup = "Chest"),
                Exercise(name = "Dumbbell Flyes", germanName = "Fliegende mit Kurzhanteln", muscleGroup = "Chest"),
                Exercise(name = "Cable Flyes", germanName = "Fliegende am Kabelzug", muscleGroup = "Chest"),
                Exercise(name = "Push-ups", germanName = "Liegestütze", muscleGroup = "Chest"),
                Exercise(name = "Dips", germanName = "Dips", muscleGroup = "Chest"),
                // Back
                Exercise(name = "Pull-ups", germanName = "Klimmzüge", muscleGroup = "Back"),
                Exercise(name = "Barbell Row", germanName = "Langhantelrudern", muscleGroup = "Back"),
                Exercise(name = "Seated Cable Row", germanName = "Rudern am Kabelzug", muscleGroup = "Back"),
                Exercise(name = "Lat Pulldown", germanName = "Latzug", muscleGroup = "Back"),
                Exercise(name = "T-Bar Row", germanName = "T-Bar Rudern", muscleGroup = "Back"),
                Exercise(name = "Deadlift", germanName = "Kreuzheben", muscleGroup = "Back"),
                Exercise(name = "Face Pulls", germanName = "Face Pulls", muscleGroup = "Back"),
                // Shoulders
                Exercise(name = "Overhead Press", germanName = "Schulterdrücken", muscleGroup = "Shoulders"),
                Exercise(name = "Dumbbell Shoulder Press", germanName = "Kurzhantel-Schulterdrücken", muscleGroup = "Shoulders"),
                Exercise(name = "Lateral Raises", germanName = "Seitheben", muscleGroup = "Shoulders"),
                Exercise(name = "Front Raises", germanName = "Frontheben", muscleGroup = "Shoulders"),
                Exercise(name = "Rear Delt Flyes", germanName = "Reverse Flyes", muscleGroup = "Shoulders"),
                Exercise(name = "Arnold Press", germanName = "Arnold Press", muscleGroup = "Shoulders"),
                // Arms
                Exercise(name = "Barbell Curl", germanName = "Langhantel-Curls", muscleGroup = "Biceps"),
                Exercise(name = "Dumbbell Curl", germanName = "Kurzhantel-Curls", muscleGroup = "Biceps"),
                Exercise(name = "Hammer Curl", germanName = "Hammer-Curls", muscleGroup = "Biceps"),
                Exercise(name = "Preacher Curl", germanName = "Preacher-Curls", muscleGroup = "Biceps"),
                Exercise(name = "Tricep Pushdown", germanName = "Trizepsdrücken am Kabel", muscleGroup = "Triceps"),
                Exercise(name = "Skull Crushers", germanName = "Skull Crushers", muscleGroup = "Triceps"),
                Exercise(name = "Overhead Tricep Extension", germanName = "Trizepsstrecken über Kopf", muscleGroup = "Triceps"),
                Exercise(name = "Close Grip Bench Press", germanName = "Enges Bankdrücken", muscleGroup = "Triceps"),
                // Legs
                Exercise(name = "Squat", germanName = "Kniebeugen", muscleGroup = "Quads"),
                Exercise(name = "Leg Press", germanName = "Beinpresse", muscleGroup = "Quads"),
                Exercise(name = "Leg Extension", germanName = "Beinstrecker", muscleGroup = "Quads"),
                Exercise(name = "Romanian Deadlift", germanName = "Rumänisches Kreuzheben", muscleGroup = "Hamstrings"),
                Exercise(name = "Leg Curl", germanName = "Beinbeuger", muscleGroup = "Hamstrings"),
                Exercise(name = "Hip Thrust", germanName = "Hüftstoßen", muscleGroup = "Glutes"),
                Exercise(name = "Bulgarian Split Squat", germanName = "Bulgarische Kniebeugen", muscleGroup = "Quads"),
                Exercise(name = "Calf Raises", germanName = "Wadenheben", muscleGroup = "Calves"),
                Exercise(name = "Standing Calf Raises", germanName = "Wadenheben im Stehen", muscleGroup = "Calves"),
                // Core
                Exercise(name = "Plank", germanName = "Unterarmstütz", muscleGroup = "Core"),
                Exercise(name = "Crunches", germanName = "Crunches", muscleGroup = "Core"),
                Exercise(name = "Leg Raises", germanName = "Beinheben", muscleGroup = "Core"),
                Exercise(name = "Cable Crunches", germanName = "Kabel-Crunches", muscleGroup = "Core"),
                Exercise(name = "Ab Wheel Rollout", germanName = "Ab Wheel Rollout", muscleGroup = "Core")
            )
            exercises.forEach { exerciseDao.insertExercise(it) }
        }
    }
}
