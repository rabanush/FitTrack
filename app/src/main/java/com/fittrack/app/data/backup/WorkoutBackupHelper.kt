package com.fittrack.app.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.fittrack.app.data.dao.ExerciseDao
import com.fittrack.app.data.dao.LogEntryDao
import com.fittrack.app.data.dao.WorkoutDao
import com.fittrack.app.data.model.LogEntry
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.model.WorkoutExercise
import com.fittrack.app.data.model.WorkoutExerciseWithExercise
import com.fittrack.app.data.preferences.ActivityLevel
import com.fittrack.app.data.preferences.Gender
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.preferences.UserProfile
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

private data class BackupLogEntry(
    @SerializedName("exerciseName") val exerciseName: String,
    @SerializedName("workoutName") val workoutName: String,
    @SerializedName("date") val date: Long,
    @SerializedName("setNumber") val setNumber: Int,
    @SerializedName("weight") val weight: Float,
    @SerializedName("reps") val reps: Int,
    @SerializedName("rir") val rir: Int
)

private data class BackupUserProfile(
    @SerializedName("weightKg") val weightKg: Float,
    @SerializedName("heightCm") val heightCm: Float,
    @SerializedName("ageYears") val ageYears: Int,
    @SerializedName("gender") val gender: String,
    @SerializedName("activityLevel") val activityLevel: String
)

private data class BackupData(
    @SerializedName("workouts") val workouts: List<BackupWorkout>,
    @SerializedName("logEntries") val logEntries: List<BackupLogEntry> = emptyList(),
    @SerializedName("userProfile") val userProfile: BackupUserProfile? = null
)

object WorkoutBackupHelper {

    private val gson = Gson()

    /**
     * Serialises all app data to JSON and writes it to the Downloads folder.
     * Called automatically whenever workouts, log entries, or the user profile change.
     */
    fun exportData(
        context: Context,
        workoutsWithExercises: List<Pair<Workout, List<WorkoutExerciseWithExercise>>>,
        logEntries: List<LogEntry>,
        exerciseNameById: Map<Long, String>,
        workoutNameById: Map<Long, String>,
        userProfile: UserProfile
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
            },
            logEntries = logEntries.mapNotNull { entry ->
                val exerciseName = exerciseNameById[entry.exerciseId] ?: return@mapNotNull null
                val workoutName = workoutNameById[entry.workoutId] ?: return@mapNotNull null
                BackupLogEntry(
                    exerciseName = exerciseName,
                    workoutName = workoutName,
                    date = entry.date,
                    setNumber = entry.setNumber,
                    weight = entry.weight,
                    reps = entry.reps,
                    rir = entry.rir
                )
            },
            userProfile = BackupUserProfile(
                weightKg = userProfile.weightKg,
                heightCm = userProfile.heightCm,
                ageYears = userProfile.ageYears,
                gender = userProfile.gender.name,
                activityLevel = userProfile.activityLevel.name
            )
        )
        writeJson(context, gson.toJson(data))
    }

    /**
     * Reads the backup file from Downloads and inserts all data into the DB and DataStore.
     * Workout plans and log entries are only restored when their respective tables are empty
     * (i.e., on a fresh install or after clearing app data).
     * The user profile is always restored from backup when a valid backup file is found and
     * log entries are empty, so that body data is not silently overwritten on subsequent opens.
     */
    suspend fun importData(
        context: Context,
        exerciseDao: ExerciseDao,
        workoutDao: WorkoutDao,
        logEntryDao: LogEntryDao,
        userPreferences: UserPreferences
    ) {
        val json = readJson(context) ?: return
        val data = try { gson.fromJson(json, BackupData::class.java) } catch (e: Exception) {
            Log.w(TAG, "Failed to parse backup JSON — restore skipped", e)
            return
        }

        val workoutsEmpty = workoutDao.getWorkoutCount() == 0
        val logEntriesEmpty = logEntryDao.getCount() == 0

        // Restore workout plans
        if (workoutsEmpty) {
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

        // Restore log entries (only on a fresh install to avoid duplicates)
        if (logEntriesEmpty && data.logEntries.isNotEmpty()) {
            val exerciseIdByName = exerciseDao.getAllExercisesSync().associate { it.name to it.id }
            val workoutIdByName = workoutDao.getAllWorkoutsSync().associate { it.name to it.id }
            val entries = data.logEntries.mapNotNull { entry ->
                val exerciseId = exerciseIdByName[entry.exerciseName] ?: return@mapNotNull null
                val workoutId = workoutIdByName[entry.workoutName] ?: return@mapNotNull null
                LogEntry(
                    exerciseId = exerciseId,
                    workoutId = workoutId,
                    date = entry.date,
                    setNumber = entry.setNumber,
                    weight = entry.weight,
                    reps = entry.reps,
                    rir = entry.rir
                )
            }
            logEntryDao.insertLogEntries(entries)
        }

        // Restore user profile when this looks like a fresh install
        if (logEntriesEmpty) {
            data.userProfile?.let { profile ->
                val gender = runCatching { Gender.valueOf(profile.gender) }.getOrElse {
                    Log.w(TAG, "Unknown gender value '${profile.gender}' in backup — falling back to MALE")
                    Gender.MALE
                }
                val activityLevel = runCatching { ActivityLevel.valueOf(profile.activityLevel) }.getOrElse {
                    Log.w(TAG, "Unknown activityLevel value '${profile.activityLevel}' in backup — falling back to MODERATE")
                    ActivityLevel.MODERATE
                }
                userPreferences.save(
                    UserProfile(
                        weightKg = profile.weightKg,
                        heightCm = profile.heightCm,
                        ageYears = profile.ageYears,
                        gender = gender,
                        activityLevel = activityLevel
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
