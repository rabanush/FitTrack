package com.fittrack.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fittrack.app.data.backup.WorkoutBackupHelper
import com.fittrack.app.data.dao.CustomFoodDao
import com.fittrack.app.data.dao.ExerciseDao
import com.fittrack.app.data.dao.FoodDao
import com.fittrack.app.data.dao.LogEntryDao
import com.fittrack.app.data.dao.RecipeDao
import com.fittrack.app.data.dao.WorkoutCaloriesDao
import com.fittrack.app.data.dao.WorkoutDao
import com.fittrack.app.data.model.CustomFood
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.data.model.LogEntry
import com.fittrack.app.data.model.Meal
import com.fittrack.app.data.model.Recipe
import com.fittrack.app.data.model.RecipeItem
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.model.WorkoutCalories
import com.fittrack.app.data.model.WorkoutExercise
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.seed.ExerciseSeedLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Exercise::class, Workout::class, WorkoutExercise::class, LogEntry::class,
                Meal::class, FoodEntry::class, WorkoutCalories::class,
                CustomFood::class, Recipe::class, RecipeItem::class],
    version = 6,
    exportSchema = false
)
abstract class FitTrackDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun foodDao(): FoodDao
    abstract fun workoutCaloriesDao(): WorkoutCaloriesDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun recipeDao(): RecipeDao

    /**
     * Completes once the one-time on-open initialization (exercise seeding + backup import)
     * has finished. Any observer that writes derived state (e.g. the auto-export) must
     * await this before starting to avoid capturing intermediate, partially-restored state.
     */
    val initializationComplete: CompletableDeferred<Unit> = CompletableDeferred()

    companion object {
        @Volatile
        private var INSTANCE: FitTrackDatabase? = null

        fun getDatabase(context: Context, userPreferences: UserPreferences): FitTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FitTrackDatabase::class.java,
                    "fittrack_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    val exerciseDao = database.exerciseDao()
                                    if (exerciseDao.getCount() == 0) {
                                        exerciseDao.insertAll(ExerciseSeedLoader.loadFromAssets(appContext))
                                    }
                                    // Restore all data from the automatic app-internal backup
                                    // if this looks like a fresh install/reinstall.
                                    WorkoutBackupHelper.importData(
                                        appContext,
                                        exerciseDao,
                                        database.workoutDao(),
                                        userPreferences,
                                        database.customFoodDao(),
                                        database.recipeDao(),
                                        database.foodDao(),
                                        database.workoutCaloriesDao()
                                    )
                                    // Signal that seeding + import are complete so that the
                                    // auto-export observer can start safely (avoids writing a
                                    // partial snapshot while exercises are still being inserted).
                                    database.initializationComplete.complete(Unit)
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

    }
}
