package com.fittrack.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.fittrack.app.data.seed.ExerciseSeedLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Exercise::class, Workout::class, WorkoutExercise::class, LogEntry::class,
                Meal::class, FoodEntry::class, WorkoutCalories::class,
                CustomFood::class, Recipe::class, RecipeItem::class],
    version = 8,
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
     * Completes once one-time on-open initialization has finished.
     */
    val initializationComplete: CompletableDeferred<Unit> = CompletableDeferred()

    companion object {
        @Volatile
        private var INSTANCE: FitTrackDatabase? = null

        /** Adds an index on food_entries.logged_date_millis to speed up per-day queries. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_food_entries_logged_date_millis` " +
                        "ON `food_entries` (`logged_date_millis`)"
                )
            }
        }

        fun getDatabase(context: Context): FitTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FitTrackDatabase::class.java,
                    "fittrack_database"
                )
                    .addMigrations(MIGRATION_7_8)
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
