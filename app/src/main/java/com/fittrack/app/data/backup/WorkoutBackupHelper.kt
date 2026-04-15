package com.fittrack.app.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.fittrack.app.data.dao.ExerciseDao
import com.fittrack.app.data.dao.WorkoutDao
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.model.WorkoutExercise
import com.fittrack.app.data.model.WorkoutExerciseWithExercise
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

private const val BACKUP_FILENAME = "fittrack_workouts.json"
private const val TAG = "WorkoutBackup"

private data class BackupExercise(
    @SerializedName("exerciseName") val exerciseName: String,
    @SerializedName("setCount") val setCount: Int,
    @SerializedName("orderIndex") val orderIndex: Int,
    @SerializedName("restTimerSeconds") val restTimerSeconds: Int
)

private data class BackupWorkout(
    @SerializedName("name") val name: String,
    @SerializedName("exercises") val exercises: List<BackupExercise>
)

private data class BackupData(
    @SerializedName("workouts") val workouts: List<BackupWorkout>
)

object WorkoutBackupHelper {

    private val gson = Gson()

    /** Serialises [workoutsWithExercises] to JSON and writes it to the Downloads folder. */
    fun exportWorkouts(
        context: Context,
        workoutsWithExercises: List<Pair<Workout, List<WorkoutExerciseWithExercise>>>
    ) {
        val data = BackupData(
            workouts = workoutsWithExercises.map { (workout, exercises) ->
                BackupWorkout(
                    name = workout.name,
                    exercises = exercises.map { ex ->
                        BackupExercise(
                            exerciseName = ex.exercise.name,
                            setCount = ex.workoutExercise.setCount,
                            orderIndex = ex.workoutExercise.orderIndex,
                            restTimerSeconds = ex.workoutExercise.restTimerSeconds
                        )
                    }
                )
            }
        )
        writeJson(context, gson.toJson(data))
    }

    /**
     * Reads the backup file from Downloads and inserts the workout plans into the DB.
     * Exercises are matched by name against the already-populated exercises table.
     * Called only when the workouts table is empty (e.g., after a reinstall).
     */
    suspend fun importWorkoutsToDao(context: Context, exerciseDao: ExerciseDao, workoutDao: WorkoutDao) {
        val json = readJson(context) ?: return
        val data = try { gson.fromJson(json, BackupData::class.java) } catch (e: Exception) {
            Log.w(TAG, "Failed to parse backup JSON — restore skipped", e)
            return
        }

        for (backupWorkout in data.workouts) {
            val workoutId = workoutDao.insertWorkout(Workout(name = backupWorkout.name))
            for (ex in backupWorkout.exercises) {
                val exercise = exerciseDao.getExerciseByName(ex.exerciseName) ?: continue
                workoutDao.insertWorkoutExercise(
                    WorkoutExercise(
                        workoutId = workoutId,
                        exerciseId = exercise.id,
                        setCount = ex.setCount,
                        orderIndex = ex.orderIndex,
                        restTimerSeconds = ex.restTimerSeconds
                    )
                )
            }
        }
    }

    private fun writeJson(context: Context, json: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                // Remove any existing backup entry so we don't accumulate duplicates.
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID),
                    selection,
                    arrayOf(BACKUP_FILENAME),
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        resolver.delete(
                            ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                            null, null
                        )
                    }
                }
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, BACKUP_FILENAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return
                resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(dir, BACKUP_FILENAME).writeText(json)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write workout backup to Downloads", e)
        }
    }

    private fun readJson(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID),
                    selection,
                    arrayOf(BACKUP_FILENAME),
                    null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return null
                    val id = cursor.getLong(0)
                    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    resolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                }
            } else {
                @Suppress("DEPRECATION")
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    BACKUP_FILENAME
                )
                if (file.exists()) file.readText() else null
            }
        } catch (_: Exception) { null }
    }
}
