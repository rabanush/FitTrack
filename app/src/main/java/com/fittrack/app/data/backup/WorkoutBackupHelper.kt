package com.fittrack.app.data.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import com.fittrack.app.data.dao.CustomFoodDao
import com.fittrack.app.data.dao.ExerciseDao
import com.fittrack.app.data.dao.RecipeDao
import com.fittrack.app.data.dao.WorkoutDao
import com.fittrack.app.data.model.CustomFood
import com.fittrack.app.data.model.Recipe
import com.fittrack.app.data.model.RecipeItem
import com.fittrack.app.data.model.RecipeWithItems
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.model.WorkoutExercise
import com.fittrack.app.data.model.WorkoutExerciseWithExercise
import com.fittrack.app.data.preferences.ActivityLevel
import com.fittrack.app.data.preferences.BackupPreferences
import com.fittrack.app.data.preferences.Gender
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.preferences.UserProfile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

private const val BACKUP_FILENAME = "backup.json"
private const val LEGACY_BACKUP_FILENAME = "fittrack_workouts.json"
private const val BACKUP_DIRECTORY = "FitTrackerBackup"
private const val TAG = "WorkoutBackup"
private const val DEFAULT_TIMER_VOLUME_PERCENT = 50

private data class BackupExercise(
    @SerializedName("exerciseId") val exerciseId: Long? = null,
    @SerializedName("exerciseName") val exerciseName: String,
    @SerializedName("setCount") val setCount: Int,
    @SerializedName("orderIndex") val orderIndex: Int,
    @SerializedName("restTimerSeconds") val restTimerSeconds: Int
)

private data class BackupWorkout(
    @SerializedName("name") val name: String,
    @SerializedName("exercises") val exercises: List<BackupExercise>
)

private data class BackupUserProfile(
    @SerializedName("weightKg") val weightKg: Float,
    @SerializedName("heightCm") val heightCm: Float,
    @SerializedName("ageYears") val ageYears: Int,
    @SerializedName("gender") val gender: String,
    @SerializedName("activityLevel") val activityLevel: String,
    @SerializedName("timerVolumePercent") val timerVolumePercent: Int? = null
)

private data class BackupCustomFood(
    @SerializedName("name") val name: String,
    @SerializedName("barcode") val barcode: String?,
    @SerializedName("caloriesPer100") val caloriesPer100: Float,
    @SerializedName("proteinPer100") val proteinPer100: Float,
    @SerializedName("carbsPer100") val carbsPer100: Float,
    @SerializedName("fatPer100") val fatPer100: Float
)

private data class BackupRecipeItem(
    @SerializedName("name") val name: String,
    @SerializedName("caloriesPer100") val caloriesPer100: Float,
    @SerializedName("proteinPer100") val proteinPer100: Float,
    @SerializedName("carbsPer100") val carbsPer100: Float,
    @SerializedName("fatPer100") val fatPer100: Float,
    @SerializedName("amount") val amount: Float
)

private data class BackupRecipe(
    @SerializedName("name") val name: String,
    @SerializedName("items") val items: List<BackupRecipeItem>
)

private data class BackupData(
    @SerializedName("workouts") val workouts: List<BackupWorkout>,
    @SerializedName("userProfile") val userProfile: BackupUserProfile? = null,
    @SerializedName("customFoods") val customFoods: List<BackupCustomFood> = emptyList(),
    @SerializedName("recipes") val recipes: List<BackupRecipe> = emptyList()
)

object WorkoutBackupHelper {

    private val gson = Gson()

    /**
     * Serialises all app data to JSON and writes it to
     * [Context.getExternalFilesDir]`(Environment.DIRECTORY_DOCUMENTS)/FitTrackerBackup`.
     * Falls back to legacy app-internal storage when needed.
     */
    fun exportData(
        context: Context,
        workoutsWithExercises: List<Pair<Workout, List<WorkoutExerciseWithExercise>>>,
        userProfile: UserProfile,
        customFoods: List<CustomFood>,
        recipes: List<RecipeWithItems>
    ) {
        // On fresh installs, observers can emit an initial empty state before import has
        // restored existing backups; avoid overwriting those backups with empty data.
        if (shouldSkipEmptyExportDueToExistingBackup(context, workoutsWithExercises, customFoods, recipes)) {
            return
        }
        val data = BackupData(
            workouts = workoutsWithExercises.map { (workout, exercises) ->
                BackupWorkout(
                    name = workout.name,
                    exercises = exercises.map { ex ->
                        BackupExercise(
                            exerciseId = ex.exercise.id,
                            exerciseName = ex.exercise.name,
                            setCount = ex.workoutExercise.setCount,
                            orderIndex = ex.workoutExercise.orderIndex,
                            restTimerSeconds = ex.workoutExercise.restTimerSeconds
                        )
                    }
                )
            },
            userProfile = BackupUserProfile(
                weightKg = userProfile.weightKg,
                heightCm = userProfile.heightCm,
                ageYears = userProfile.ageYears,
                gender = userProfile.gender.name,
                activityLevel = userProfile.activityLevel.name,
                timerVolumePercent = userProfile.timerVolumePercent
            ),
            customFoods = customFoods.map { food ->
                BackupCustomFood(
                    name = food.name,
                    barcode = food.barcode,
                    caloriesPer100 = food.caloriesPer100,
                    proteinPer100 = food.proteinPer100,
                    carbsPer100 = food.carbsPer100,
                    fatPer100 = food.fatPer100
                )
            },
            recipes = recipes.map { rw ->
                BackupRecipe(
                    name = rw.recipe.name,
                    items = rw.items.map { item ->
                        BackupRecipeItem(
                            name = item.name,
                            caloriesPer100 = item.caloriesPer100,
                            proteinPer100 = item.proteinPer100,
                            carbsPer100 = item.carbsPer100,
                            fatPer100 = item.fatPer100,
                            amount = item.amount
                        )
                    }
                )
            }
        )
        writeJson(context, gson.toJson(data))
    }

    /**
     * Reads the backup file from `Documents/FitTrackerBackup` (or legacy app-internal storage)
     * and inserts all data into the DB and DataStore.
     * Workout plans, custom foods, and recipes are only restored when the respective
     * tables are empty (i.e., on a fresh install or after clearing app data).
     * The user profile is always restored from backup when a valid backup file is found
     * and workouts are empty, so that body data is not silently overwritten.
     */
    suspend fun importData(
        context: Context,
        exerciseDao: ExerciseDao,
        workoutDao: WorkoutDao,
        userPreferences: UserPreferences,
        customFoodDao: CustomFoodDao,
        recipeDao: RecipeDao
    ) {
        val json = readJson(context) ?: return
        val data = try { gson.fromJson(json, BackupData::class.java) } catch (e: Exception) {
            Log.w(TAG, "Failed to parse backup JSON — restore skipped", e)
            return
        }

        val workoutsEmpty = workoutDao.getWorkoutCount() == 0

        // Restore workout plans
        if (workoutsEmpty) {
            data.workouts.forEach { backupWorkout ->
                val workoutId = workoutDao.insertWorkout(Workout(name = backupWorkout.name))
                backupWorkout.exercises.forEach { ex ->
                    val exercise = when {
                        ex.exerciseId != null -> exerciseDao.getExerciseById(ex.exerciseId)
                            ?: exerciseDao.getExerciseByName(ex.exerciseName)
                        else -> exerciseDao.getExerciseByName(ex.exerciseName)
                    } ?: return@forEach
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

            // Restore user profile when this looks like a fresh install
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
                        activityLevel = activityLevel,
                        timerVolumePercent = profile.timerVolumePercent ?: DEFAULT_TIMER_VOLUME_PERCENT
                    )
                )
            }
        }

        // Restore custom foods (only when table is empty)
        if (customFoodDao.getCount() == 0) {
            data.customFoods.forEach { backupFood ->
                customFoodDao.insert(
                    CustomFood(
                        name = backupFood.name,
                        barcode = backupFood.barcode,
                        caloriesPer100 = backupFood.caloriesPer100,
                        proteinPer100 = backupFood.proteinPer100,
                        carbsPer100 = backupFood.carbsPer100,
                        fatPer100 = backupFood.fatPer100
                    )
                )
            }
        }

        // Restore recipes (only when table is empty)
        if (recipeDao.getCount() == 0) {
            data.recipes.forEach { backupRecipe ->
                val recipeId = recipeDao.insertRecipe(Recipe(name = backupRecipe.name))
                backupRecipe.items.forEach { backupItem ->
                    recipeDao.insertRecipeItem(
                        RecipeItem(
                            recipeId = recipeId,
                            name = backupItem.name,
                            caloriesPer100 = backupItem.caloriesPer100,
                            proteinPer100 = backupItem.proteinPer100,
                            carbsPer100 = backupItem.carbsPer100,
                            fatPer100 = backupItem.fatPer100,
                            amount = backupItem.amount
                        )
                    )
                }
            }
        }
    }

    private fun writeJson(context: Context, json: String) {
        try {
            // Priority 1: user-selected SAF folder; falls back to app Documents path, then filesDir.
            if (writeJsonToSelectedTree(context, json)) return
            val backupFile = getPrimaryBackupFile(context) ?: run {
                Log.w(TAG, "Documents backup folder unavailable, writing backup to legacy internal storage")
                getInternalBackupFile(context)
            }
            val parent = backupFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Log.w(TAG, "Failed to create backup directory: ${parent.absolutePath}")
                return
            }
            backupFile.writeText(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write workout backup", e)
        }
    }

    private fun readJson(context: Context): String? {
        return try {
            readJsonFromSelectedTree(context) ?: readJsonFromFallbackFiles(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read workout backup", e)
            null
        }
    }

    private fun getPrimaryBackupFile(context: Context): File? {
        val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
        val backupDir = File(documentsDir, BACKUP_DIRECTORY)
        return File(backupDir, BACKUP_FILENAME)
    }

    private fun getLegacyPrimaryBackupFile(context: Context): File? {
        val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
        val backupDir = File(documentsDir, BACKUP_DIRECTORY)
        return File(backupDir, LEGACY_BACKUP_FILENAME)
    }

    private fun writeJsonToSelectedTree(context: Context, json: String): Boolean {
        val treeUri = getSelectedBackupTreeUri(context) ?: return false
        return try {
            val backupDirUri = findOrCreateDocument(
                context = context,
                parentUri = rootDocumentUri(treeUri),
                name = BACKUP_DIRECTORY,
                mimeType = DocumentsContract.Document.MIME_TYPE_DIR
            ) ?: return false
            val backupFileUri = findOrCreateDocument(
                context = context,
                parentUri = backupDirUri,
                name = BACKUP_FILENAME,
                mimeType = "application/json"
            ) ?: return false
            context.contentResolver.openOutputStream(backupFileUri, "rwt")?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write selected-tree backup", e)
            false
        }
    }

    private fun readJsonFromSelectedTree(context: Context): String? {
        val treeUri = getSelectedBackupTreeUri(context) ?: return null
        return try {
            val backupDirUri = findDocumentUri(context, rootDocumentUri(treeUri), BACKUP_DIRECTORY)
                ?: return null
            val backupFileUri = findDocumentUri(context, backupDirUri, BACKUP_FILENAME)
                ?: findDocumentUri(context, backupDirUri, LEGACY_BACKUP_FILENAME)
                ?: return null
            context.contentResolver.openInputStream(backupFileUri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read selected-tree backup", e)
            null
        }
    }

    private fun rootDocumentUri(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    private fun findOrCreateDocument(
        context: Context,
        parentUri: Uri,
        name: String,
        mimeType: String
    ): Uri? {
        val existing = findDocumentUri(context, parentUri, name)
        if (existing != null) return existing
        return DocumentsContract.createDocument(context.contentResolver, parentUri, mimeType, name)
    }

    private fun findDocumentUri(context: Context, parentUri: Uri, name: String): Uri? {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri,
            DocumentsContract.getDocumentId(parentUri)
        )
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (nameColumn == -1 || idColumn == -1) {
                Log.w(TAG, "Backup document query missing required columns")
                return null
            }
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(parentUri, cursor.getString(idColumn))
                }
            }
        }
        return null
    }

    private fun getSelectedBackupTreeUri(context: Context): Uri? =
        BackupPreferences(context).getBackupTreeUri()

    private fun getInternalBackupFile(context: Context): File = File(context.filesDir, BACKUP_FILENAME)

    private fun getOldInternalBackupFile(context: Context): File = File(context.filesDir, LEGACY_BACKUP_FILENAME)

    private fun getFallbackBackupFiles(context: Context): List<File> {
        // Priority (when available): new external name -> old external name -> new internal name -> old internal name.
        val files = mutableListOf<File>()
        getPrimaryBackupFile(context)?.let(files::add)
        getLegacyPrimaryBackupFile(context)?.let(files::add)
        files += getInternalBackupFile(context)
        files += getOldInternalBackupFile(context)
        return files
    }

    private fun readJsonFromFallbackFiles(context: Context): String? {
        return runCatching {
            val existingFile = getFallbackBackupFiles(context).firstOrNull { it.exists() } ?: return null
            existingFile.readText()
        }.onFailure { Log.w(TAG, "Failed to read backup from fallback files", it) }
            .getOrNull()
    }

    private fun shouldSkipEmptyExportDueToExistingBackup(
        context: Context,
        workoutsWithExercises: List<Pair<Workout, List<WorkoutExerciseWithExercise>>>,
        customFoods: List<CustomFood>,
        recipes: List<RecipeWithItems>
    ): Boolean {
        val isEmptyExport = workoutsWithExercises.isEmpty() && customFoods.isEmpty() && recipes.isEmpty()
        if (!isEmptyExport) return false
        return hasExistingBackup(context)
    }

    private fun hasExistingBackup(context: Context): Boolean {
        if (selectedTreeBackupExists(context)) return true
        return getFallbackBackupFiles(context).any { it.exists() }
    }

    private fun selectedTreeBackupExists(context: Context): Boolean {
        val treeUri = getSelectedBackupTreeUri(context) ?: return false
        return try {
            val backupDirUri = findDocumentUri(context, rootDocumentUri(treeUri), BACKUP_DIRECTORY)
                ?: return false
            findDocumentUri(context, backupDirUri, BACKUP_FILENAME) != null ||
                findDocumentUri(context, backupDirUri, LEGACY_BACKUP_FILENAME) != null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check selected-tree backup existence", e)
            false
        }
    }
}
