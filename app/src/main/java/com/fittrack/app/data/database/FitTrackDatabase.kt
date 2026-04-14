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
                Exercise(name = "Bench Press", muscleGroup = "Chest", germanName = "Bankdrücken"),
                Exercise(name = "Incline Bench Press", muscleGroup = "Chest", germanName = "Schrägbankdrücken"),
                Exercise(name = "Decline Bench Press", muscleGroup = "Chest", germanName = "Negativbankdrücken"),
                Exercise(name = "Dumbbell Flyes", muscleGroup = "Chest", germanName = "Kurzhantel Fliegende"),
                Exercise(name = "Cable Flyes", muscleGroup = "Chest", germanName = "Kabel Fliegende"),
                Exercise(name = "Push-ups", muscleGroup = "Chest", germanName = "Liegestütze"),
                Exercise(name = "Dips", muscleGroup = "Chest", germanName = "Dips"),
                // Back
                Exercise(name = "Pull-ups", muscleGroup = "Back", germanName = "Klimmzüge"),
                Exercise(name = "Barbell Row", muscleGroup = "Back", germanName = "Langhantelrudern"),
                Exercise(name = "Seated Cable Row", muscleGroup = "Back", germanName = "Rudern am Kabel sitzend"),
                Exercise(name = "Lat Pulldown", muscleGroup = "Back", germanName = "Latziehen"),
                Exercise(name = "T-Bar Row", muscleGroup = "Back", germanName = "T-Stangen Rudern"),
                Exercise(name = "Deadlift", muscleGroup = "Back", germanName = "Kreuzheben"),
                Exercise(name = "Face Pulls", muscleGroup = "Back", germanName = "Gesichtszüge"),
                // Shoulders
                Exercise(name = "Overhead Press", muscleGroup = "Shoulders", germanName = "Schulterdrücken"),
                Exercise(name = "Dumbbell Shoulder Press", muscleGroup = "Shoulders", germanName = "Kurzhantel Schulterdrücken"),
                Exercise(name = "Lateral Raises", muscleGroup = "Shoulders", germanName = "Seitheben"),
                Exercise(name = "Front Raises", muscleGroup = "Shoulders", germanName = "Frontheben"),
                Exercise(name = "Rear Delt Flyes", muscleGroup = "Shoulders", germanName = "Hintere Schulter Fliegende"),
                Exercise(name = "Arnold Press", muscleGroup = "Shoulders", germanName = "Arnold Press"),
                // Arms
                Exercise(name = "Barbell Curl", muscleGroup = "Biceps", germanName = "Langhantel Bizepscurl"),
                Exercise(name = "Dumbbell Curl", muscleGroup = "Biceps", germanName = "Kurzhantel Bizepscurl"),
                Exercise(name = "Hammer Curl", muscleGroup = "Biceps", germanName = "Hammer Curl"),
                Exercise(name = "Preacher Curl", muscleGroup = "Biceps", germanName = "Scott Curl"),
                Exercise(name = "Tricep Pushdown", muscleGroup = "Triceps", germanName = "Trizepsdrücken"),
                Exercise(name = "Skull Crushers", muscleGroup = "Triceps", germanName = "Stirndrücken"),
                Exercise(name = "Overhead Tricep Extension", muscleGroup = "Triceps", germanName = "Trizeps Überkopfdrücken"),
                Exercise(name = "Close Grip Bench Press", muscleGroup = "Triceps", germanName = "Enges Bankdrücken"),
                // Legs
                Exercise(name = "Squat", muscleGroup = "Quads", germanName = "Kniebeuge"),
                Exercise(name = "Leg Press", muscleGroup = "Quads", germanName = "Beinpresse"),
                Exercise(name = "Leg Extension", muscleGroup = "Quads", germanName = "Beinstrecker"),
                Exercise(name = "Romanian Deadlift", muscleGroup = "Hamstrings", germanName = "Rumänisches Kreuzheben"),
                Exercise(name = "Leg Curl", muscleGroup = "Hamstrings", germanName = "Beincurl"),
                Exercise(name = "Hip Thrust", muscleGroup = "Glutes", germanName = "Hüftstrecker"),
                Exercise(name = "Bulgarian Split Squat", muscleGroup = "Quads", germanName = "Bulgarische Split Kniebeuge"),
                Exercise(name = "Calf Raises", muscleGroup = "Calves", germanName = "Wadenheben"),
                Exercise(name = "Standing Calf Raises", muscleGroup = "Calves", germanName = "Stehendes Wadenheben"),
                // Core
                Exercise(name = "Plank", muscleGroup = "Core", germanName = "Unterarmstütz"),
                Exercise(name = "Crunches", muscleGroup = "Core", germanName = "Bauchpressen"),
                Exercise(name = "Leg Raises", muscleGroup = "Core", germanName = "Beinheben"),
                Exercise(name = "Cable Crunches", muscleGroup = "Core", germanName = "Kabel Bauchpressen"),
                Exercise(name = "Ab Wheel Rollout", muscleGroup = "Core", germanName = "Bauchradübung")
            )
            exercises.forEach { exerciseDao.insertExercise(it) }
        }
    }
}
