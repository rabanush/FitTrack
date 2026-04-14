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
    version = 4,
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
                // Chest (additional)
                Exercise(name = "Incline Dumbbell Press", muscleGroup = "Chest", germanName = "Schrägbank Kurzhanteldrücken"),
                Exercise(name = "Chest Press Machine", muscleGroup = "Chest", germanName = "Brustpresse Maschine"),
                Exercise(name = "Pec Deck", muscleGroup = "Chest", germanName = "Pec Deck"),
                Exercise(name = "Chest Dips", muscleGroup = "Chest", germanName = "Brust Dips"),
                // Back (additional)
                Exercise(name = "Single Arm Dumbbell Row", muscleGroup = "Back", germanName = "Einarmiges Kurzhantelrudern"),
                Exercise(name = "Chest Supported Row", muscleGroup = "Back", germanName = "Stützrudern"),
                Exercise(name = "Straight Arm Pulldown", muscleGroup = "Back", germanName = "Gerader Arm Latziehen"),
                Exercise(name = "Hyperextensions", muscleGroup = "Back", germanName = "Rückenstrecker"),
                Exercise(name = "Chin-ups", muscleGroup = "Back", germanName = "Untergriff Klimmzüge"),
                // Shoulders (additional)
                Exercise(name = "Cable Lateral Raises", muscleGroup = "Shoulders", germanName = "Kabel Seitheben"),
                Exercise(name = "Upright Row", muscleGroup = "Shoulders", germanName = "Aufrechtes Rudern"),
                Exercise(name = "Machine Shoulder Press", muscleGroup = "Shoulders", germanName = "Schulterdrücken Maschine"),
                Exercise(name = "Shrugs", muscleGroup = "Shoulders", germanName = "Schulterheben"),
                // Biceps (additional)
                Exercise(name = "Concentration Curl", muscleGroup = "Biceps", germanName = "Konzentrationscurl"),
                Exercise(name = "Cable Curl", muscleGroup = "Biceps", germanName = "Kabel Bizepscurl"),
                Exercise(name = "Incline Dumbbell Curl", muscleGroup = "Biceps", germanName = "Schrägbank Kurzhantelcurl"),
                Exercise(name = "EZ Bar Curl", muscleGroup = "Biceps", germanName = "EZ Stangen Curl"),
                // Triceps (additional)
                Exercise(name = "Tricep Dips", muscleGroup = "Triceps", germanName = "Trizeps Dips"),
                Exercise(name = "Diamond Push-ups", muscleGroup = "Triceps", germanName = "Diamant Liegestütze"),
                Exercise(name = "Cable Overhead Tricep Extension", muscleGroup = "Triceps", germanName = "Kabel Trizeps Überkopfdrücken"),
                Exercise(name = "Tricep Kickback", muscleGroup = "Triceps", germanName = "Trizeps Kickback"),
                // Quads (additional)
                Exercise(name = "Hack Squat", muscleGroup = "Quads", germanName = "Hackenschmidt Kniebeuge"),
                Exercise(name = "Front Squat", muscleGroup = "Quads", germanName = "Frontkniebeuge"),
                Exercise(name = "Lunges", muscleGroup = "Quads", germanName = "Ausfallschritte"),
                Exercise(name = "Step-ups", muscleGroup = "Quads", germanName = "Aufsteigen"),
                // Hamstrings (additional)
                Exercise(name = "Seated Leg Curl", muscleGroup = "Hamstrings", germanName = "Sitzender Beincurl"),
                Exercise(name = "Good Mornings", muscleGroup = "Hamstrings", germanName = "Good Mornings"),
                Exercise(name = "Stiff Leg Deadlift", muscleGroup = "Hamstrings", germanName = "Stiffleg Kreuzheben"),
                // Glutes (additional)
                Exercise(name = "Cable Kickbacks", muscleGroup = "Glutes", germanName = "Kabel Kickback"),
                Exercise(name = "Sumo Squat", muscleGroup = "Glutes", germanName = "Sumo Kniebeuge"),
                Exercise(name = "Glute Bridge", muscleGroup = "Glutes", germanName = "Gesäßbrücke"),
                // Calves (additional)
                Exercise(name = "Seated Calf Raises", muscleGroup = "Calves", germanName = "Sitzendes Wadenheben"),
                Exercise(name = "Donkey Calf Raises", muscleGroup = "Calves", germanName = "Esels Wadenheben"),
                // Core
                Exercise(name = "Plank", muscleGroup = "Core", germanName = "Unterarmstütz"),
                Exercise(name = "Crunches", muscleGroup = "Core", germanName = "Bauchpressen"),
                Exercise(name = "Leg Raises", muscleGroup = "Core", germanName = "Beinheben"),
                Exercise(name = "Cable Crunches", muscleGroup = "Core", germanName = "Kabel Bauchpressen"),
                Exercise(name = "Ab Wheel Rollout", muscleGroup = "Core", germanName = "Bauchradübung"),
                Exercise(name = "Russian Twists", muscleGroup = "Core", germanName = "Russische Drehungen"),
                Exercise(name = "Bicycle Crunches", muscleGroup = "Core", germanName = "Fahrradcrunches"),
                Exercise(name = "Hanging Leg Raises", muscleGroup = "Core", germanName = "Hängendes Beinheben"),
                Exercise(name = "Side Plank", muscleGroup = "Core", germanName = "Seitstütz"),
                Exercise(name = "Mountain Climbers", muscleGroup = "Core", germanName = "Bergsteiger"),
                // Full Body / Cardio
                Exercise(name = "Burpees", muscleGroup = "Full Body", germanName = "Burpees"),
                Exercise(name = "Kettlebell Swing", muscleGroup = "Full Body", germanName = "Kettlebell Schwung"),
                Exercise(name = "Box Jumps", muscleGroup = "Full Body", germanName = "Kastensprünge"),
                Exercise(name = "Clean and Press", muscleGroup = "Full Body", germanName = "Reißen und Drücken"),
                Exercise(name = "Farmers Walk", muscleGroup = "Full Body", germanName = "Bauerngang"),
                Exercise(name = "Battle Ropes", muscleGroup = "Full Body", germanName = "Battle Ropes")
            )
            exercises.forEach { exerciseDao.insertExercise(it) }
        }
    }
}
