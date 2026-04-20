package com.fittrack.app.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.fittrack.app.data.dao.CustomFoodDao
import com.fittrack.app.data.dao.ExerciseDao
import com.fittrack.app.data.dao.FoodDao
import com.fittrack.app.data.dao.RecipeDao
import com.fittrack.app.data.dao.WorkoutDao
import com.fittrack.app.data.model.CustomFood
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.data.model.Meal
import com.fittrack.app.data.model.Recipe
import com.fittrack.app.data.model.RecipeItem
import com.fittrack.app.data.model.RecipeWithItems
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.model.WorkoutExercise
import com.fittrack.app.data.model.WorkoutExerciseWithExercise
import com.fittrack.app.data.preferences.ActiveWorkoutSession
import com.fittrack.app.data.preferences.ActiveWorkoutSessionPreferences
import com.fittrack.app.data.preferences.ActivityLevel
import com.fittrack.app.data.preferences.DEFAULT_THEME_HUE_DEGREES
import com.fittrack.app.data.preferences.Gender
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.preferences.UserProfile
import com.fittrack.app.util.todayMillis
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.Locale

// FitTrackBackup.json is stored in the user-chosen folder (default: Documents).
// The folder is granted via ACTION_OPEN_DOCUMENT_TREE on first app start.
private const val BACKUP_FILENAME = "FitTrackBackup.json"
private const val BACKUP_FILENAME_TMP = "FitTrackBackup_tmp.json"
private const val BACKUP_FILENAME_BAK = "FitTrackBackup.bak.json"
private const val BACKUP_MIME_TYPE = "application/json"
private const val TAG = "WorkoutBackup"
private const val DEFAULT_TIMER_VOLUME_PERCENT = 50
private const val FALLBACK_MUSCLE_GROUP = "Sonstiges"
private const val FALLBACK_DESCRIPTION = "Aus Backup wiederhergestellt"
// v3 adds active workout session and timer state to the automatic backup payload.
private const val BACKUP_SCHEMA_VERSION = 3

private data class BackupCustomExercise(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String,
    @SerializedName("muscleGroup") val muscleGroup: String,
    @SerializedName("germanName") val germanName: String,
    @SerializedName("description") val description: String
)

private data class BackupExercise(
    @SerializedName("exerciseId") val exerciseId: Long? = null,
    @SerializedName("exerciseName") val exerciseName: String,
    @SerializedName("setCount") val setCount: Int,
    @SerializedName("orderIndex") val orderIndex: Int,
    @SerializedName("restTimerSeconds") val restTimerSeconds: Int
)

private data class BackupWorkout(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String,
    @SerializedName("exercises") val exercises: List<BackupExercise>
)

private data class BackupUserProfile(
    @SerializedName("weightKg") val weightKg: Float,
    @SerializedName("heightCm") val heightCm: Float,
    @SerializedName("ageYears") val ageYears: Int,
    @SerializedName("gender") val gender: String,
    @SerializedName("activityLevel") val activityLevel: String,
    @SerializedName("timerVolumePercent") val timerVolumePercent: Int? = null,
    @SerializedName("themeHueDegrees") val themeHueDegrees: Float? = null
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

private data class BackupMeal(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String,
    @SerializedName("dateMillis") val dateMillis: Long
)

private data class BackupFoodEntry(
    @SerializedName("mealId") val mealId: Long?,
    @SerializedName("name") val name: String,
    @SerializedName("barcode") val barcode: String?,
    @SerializedName("caloriesPer100") val caloriesPer100: Float,
    @SerializedName("proteinPer100") val proteinPer100: Float,
    @SerializedName("carbsPer100") val carbsPer100: Float,
    @SerializedName("fatPer100") val fatPer100: Float,
    @SerializedName("amount") val amount: Float,
    /** Midnight-normalised epoch millis recorded when the entry was inserted (added in schema v7).
     *  0 for entries restored from older backups that pre-date this field. */
    @SerializedName("loggedDateMillis") val loggedDateMillis: Long = 0L
)

private data class BackupActiveWorkoutSession(
    @SerializedName("workoutId") val workoutId: Long,
    @SerializedName("workoutStartTimeMillis") val workoutStartTimeMillis: Long,
    @SerializedName("timerEndTimeMillis") val timerEndTimeMillis: Long,
    @SerializedName("timerTotalSeconds") val timerTotalSeconds: Int,
    @SerializedName("timerExerciseIndex") val timerExerciseIndex: Int,
    @SerializedName("timerSetNumber") val timerSetNumber: Int,
    @SerializedName("exerciseSessionsState") val exerciseSessionsState: String? = null
)

private data class BackupData(
    @SerializedName("version") val version: Int = BACKUP_SCHEMA_VERSION,
    @SerializedName("workouts") val workouts: List<BackupWorkout> = emptyList(),
    @SerializedName("userProfile") val userProfile: BackupUserProfile? = null,
    @SerializedName("customFoods") val customFoods: List<BackupCustomFood> = emptyList(),
    @SerializedName("recipes") val recipes: List<BackupRecipe> = emptyList(),
    @SerializedName("customExercises") val customExercises: List<BackupCustomExercise> = emptyList(),
    @SerializedName("meals") val meals: List<BackupMeal> = emptyList(),
    @SerializedName("foodEntries") val foodEntries: List<BackupFoodEntry> = emptyList(),
    @SerializedName("activeWorkoutSession") val activeWorkoutSession: BackupActiveWorkoutSession? = null
)

object WorkoutBackupHelper {

    private val gson = Gson()

    /**
     * Serialises all relevant app data to JSON and writes FitTrackBackup.json into the
     * user-chosen SAF folder. No-op when [treeUriString] is null (folder not yet configured).
     */
    fun exportData(
        context: Context,
        treeUriString: String?,
        workoutsWithExercises: List<Pair<Workout, List<WorkoutExerciseWithExercise>>>,
        userProfile: UserProfile,
        customFoods: List<CustomFood>,
        recipes: List<RecipeWithItems>,
        customExercises: List<Exercise>,
        meals: List<Meal>,
        foodEntries: List<FoodEntry>,
        activeWorkoutSession: ActiveWorkoutSession?,
        activeWorkoutExerciseSessionsState: String?
    ) {
        if (treeUriString == null) return
        val data = BackupData(
            workouts = workoutsWithExercises.map { (workout, exercises) ->
                BackupWorkout(
                    id = workout.id,
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
                timerVolumePercent = userProfile.timerVolumePercent,
                themeHueDegrees = userProfile.themeHueDegrees
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
            },
            customExercises = customExercises.map { ex ->
                BackupCustomExercise(
                    id = ex.id,
                    name = ex.name,
                    muscleGroup = ex.muscleGroup,
                    germanName = ex.germanName,
                    description = ex.description
                )
            },
            meals = meals.map { meal ->
                BackupMeal(
                    id = meal.id,
                    name = meal.name,
                    dateMillis = meal.dateMillis
                )
            },
            foodEntries = foodEntries.map { entry ->
                BackupFoodEntry(
                    mealId = entry.mealId,
                    name = entry.name,
                    barcode = entry.barcode,
                    caloriesPer100 = entry.caloriesPer100,
                    proteinPer100 = entry.proteinPer100,
                    carbsPer100 = entry.carbsPer100,
                    fatPer100 = entry.fatPer100,
                    amount = entry.amount,
                    loggedDateMillis = entry.loggedDateMillis
                )
            },
            activeWorkoutSession = activeWorkoutSession?.let { session ->
                BackupActiveWorkoutSession(
                    workoutId = session.workoutId,
                    workoutStartTimeMillis = session.workoutStartTimeMillis,
                    timerEndTimeMillis = session.timerEndTimeMillis,
                    timerTotalSeconds = session.timerTotalSeconds,
                    timerExerciseIndex = session.timerExerciseIndex,
                    timerSetNumber = session.timerSetNumber,
                    exerciseSessionsState = activeWorkoutExerciseSessionsState
                )
            }
        )

        writeJson(context, treeUriString, gson.toJson(data))
    }

    /**
     * Reads FitTrackBackup.json from the SAF folder and restores all data.
     * Only restores each category if the corresponding DB table is empty (fresh install / data-clear).
     * Restore order: custom exercises → workouts + exercises in workouts → custom foods →
     * recipes → meals → food entries → user profile → active session.
     */
    suspend fun importData(
        context: Context,
        treeUriString: String?,
        exerciseDao: ExerciseDao,
        workoutDao: WorkoutDao,
        userPreferences: UserPreferences,
        customFoodDao: CustomFoodDao,
        recipeDao: RecipeDao,
        foodDao: FoodDao,
        activeWorkoutSessionPreferences: ActiveWorkoutSessionPreferences
    ) {
        val json = readJson(context, treeUriString) ?: return
        val data = try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse backup JSON — restore skipped", e)
            return
        }

        val workoutsEmpty = workoutDao.getWorkoutCount() == 0
        val customFoodsEmpty = customFoodDao.getCount() == 0
        val recipesEmpty = recipeDao.getCount() == 0
        val mealsEmpty = foodDao.getMealCount() == 0
        val foodEntriesEmpty = foodDao.getFoodEntryCount() == 0

        val workoutIdMapping = mutableMapOf<Long, Long>()

        if (workoutsEmpty) {
            data.customExercises.forEach { backupEx ->
                if (exerciseDao.getExerciseByNormalizedName(backupEx.name) == null) {
                    exerciseDao.insertExercise(
                        Exercise(
                            name = backupEx.name,
                            muscleGroup = backupEx.muscleGroup,
                            isCustom = true,
                            germanName = backupEx.germanName,
                            description = backupEx.description
                        )
                    )
                }
            }

            data.workouts.forEach { backupWorkout ->
                val newWorkoutId = workoutDao.insertWorkout(Workout(name = backupWorkout.name))
                backupWorkout.id?.let { oldId -> workoutIdMapping[oldId] = newWorkoutId }

                backupWorkout.exercises.forEach { ex ->
                    val resolvedExercise = resolveExerciseForImport(exerciseDao, ex)
                    val exerciseToUse = resolvedExercise ?: createFallbackExerciseForImport(exerciseDao, ex)

                    if (exerciseToUse == null) {
                        Log.w(TAG, "Skipping workout exercise restore: '${ex.exerciseName}' could not be resolved or recreated")
                        return@forEach
                    }
                    workoutDao.insertWorkoutExercise(
                        WorkoutExercise(
                            workoutId = newWorkoutId,
                            exerciseId = exerciseToUse.id,
                            setCount = ex.setCount,
                            orderIndex = ex.orderIndex,
                            restTimerSeconds = ex.restTimerSeconds
                        )
                    )
                }
            }
        }

        if (customFoodsEmpty) {
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

        if (recipesEmpty) {
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

        val mealIdMap = mutableMapOf<Long, Long>()
        if (mealsEmpty) {
            data.meals.forEach { backupMeal ->
                val newMealId = foodDao.insertMeal(
                    Meal(
                        name = backupMeal.name,
                        dateMillis = backupMeal.dateMillis
                    )
                )
                backupMeal.id?.let { oldId -> mealIdMap[oldId] = newMealId }
            }
        }

        if (foodEntriesEmpty) {
            data.foodEntries.forEach { backupEntry ->
                // Entries from old meals (cleaned up, mealId null in backup) are restored
                // as orphaned rows; their loggedDateMillis preserves the date for search.
                val mappedMealId = backupEntry.mealId?.let { mealIdMap[it] }
                if (backupEntry.mealId != null && mappedMealId == null) {
                    // Backup entry references a meal that was not restored (old backup without
                    // that meal or the meal was already cleaned up).  Keep the entry as orphaned.
                    Log.d(TAG, "Restoring food-entry as orphan (mealId=${backupEntry.mealId} not in map)")
                }
                // Resolve loggedDateMillis: prefer value from backup; fall back to the restored
                // meal's dateMillis for entries from older backups that lack this field.
                // If neither is available (truly orphaned entry from an old backup), use today
                // so the entry stays within the 90-day retention window instead of being
                // immediately purged by the next daily cleanup.
                val loggedDateMillis = if (backupEntry.loggedDateMillis != 0L) {
                    backupEntry.loggedDateMillis
                } else {
                    mappedMealId?.let { id ->
                        foodDao.getMealById(id)?.dateMillis
                    } ?: todayMillis()
                }
                foodDao.insertFoodEntry(
                    FoodEntry(
                        mealId = mappedMealId,
                        name = backupEntry.name,
                        barcode = backupEntry.barcode,
                        caloriesPer100 = backupEntry.caloriesPer100,
                        proteinPer100 = backupEntry.proteinPer100,
                        carbsPer100 = backupEntry.carbsPer100,
                        fatPer100 = backupEntry.fatPer100,
                        amount = backupEntry.amount,
                        loggedDateMillis = loggedDateMillis
                    )
                )
            }
        }

        if (workoutsEmpty) {
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
                        timerVolumePercent = profile.timerVolumePercent ?: DEFAULT_TIMER_VOLUME_PERCENT,
                        themeHueDegrees = profile.themeHueDegrees ?: DEFAULT_THEME_HUE_DEGREES
                    )
                )
            }
        }

        data.activeWorkoutSession?.let { session ->
            val restoredWorkoutId = workoutIdMapping[session.workoutId] ?: session.workoutId
            val workoutExists = workoutDao.getWorkoutById(restoredWorkoutId) != null
            if (!workoutExists) {
                Log.w(
                    TAG,
                    "Clearing active-workout session for non-existent workoutId=${session.workoutId} (remapped to $restoredWorkoutId)"
                )
                activeWorkoutSessionPreferences.clearSession()
            } else {
                activeWorkoutSessionPreferences.restoreSession(
                    session = ActiveWorkoutSession(
                        workoutId = restoredWorkoutId,
                        workoutStartTimeMillis = session.workoutStartTimeMillis,
                        timerEndTimeMillis = session.timerEndTimeMillis,
                        timerTotalSeconds = session.timerTotalSeconds,
                        timerExerciseIndex = session.timerExerciseIndex,
                        timerSetNumber = session.timerSetNumber
                    ),
                    exerciseSessionsState = session.exerciseSessionsState
                )
            }
        }

        // After a successful import the backup file in the SAF folder already contains
        // the current snapshot, so no further cleanup step is needed.
    }

    private suspend fun resolveExerciseForImport(
        exerciseDao: ExerciseDao,
        backupExercise: BackupExercise
    ): Exercise? {
        val byName = exerciseDao.getExerciseByName(backupExercise.exerciseName)
            ?: exerciseDao.getExerciseByNormalizedName(backupExercise.exerciseName)
            ?: exerciseDao.getExerciseByGermanName(backupExercise.exerciseName)
        val byId = backupExercise.exerciseId?.let { exerciseDao.getExerciseById(it) }

        if (byId != null && byName == null) return byId
        if (byId != null && byName != null) {
            val searchName = backupExercise.exerciseName.trim()
            val idNameMatches = byId.name.trim().equals(searchName, ignoreCase = true) ||
                byId.germanName.trim().equals(searchName, ignoreCase = true)
            return if (idNameMatches) byId else byName
        }
        return byName
    }

    private suspend fun createFallbackExerciseForImport(
        exerciseDao: ExerciseDao,
        backupExercise: BackupExercise
    ): Exercise? {
        val fallbackName = backupExercise.exerciseName.trim()
        if (fallbackName.isEmpty()) return null

        // Final duplicate guard before insert, in case multiple unresolved entries share a name.
        exerciseDao.getExerciseByNormalizedName(fallbackName)?.let { return it }

        val insertedId = exerciseDao.insertExercise(
            Exercise(
                name = fallbackName,
                muscleGroup = FALLBACK_MUSCLE_GROUP,
                isCustom = true,
                germanName = fallbackName.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                },
                description = FALLBACK_DESCRIPTION
            )
        )
        return exerciseDao.getExerciseById(insertedId)
    }

    /**
     * Writes the backup JSON using a rotate-then-swap strategy to protect against data loss:
     *
     * 1. Write new content to a temp file ([BACKUP_FILENAME_TMP]).
     * 2. On success, rename the current main file to [BACKUP_FILENAME_BAK] (one-generation
     *    rollback copy), then rename the temp file to the main [BACKUP_FILENAME].
     * 3. If any rename step fails, the temp file still holds the fresh data and the
     *    old main file is untouched, so no data is lost.
     *
     * This replaces the previous truncate-in-place write, which could leave the backup
     * file empty/corrupt if the app was killed or storage filled up mid-write.
     */
    private fun writeJson(context: Context, treeUriString: String, json: String) {
        try {
            val treeUri = Uri.parse(treeUriString)
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (treeDoc == null || !treeDoc.isDirectory) {
                Log.w(TAG, "Backup folder is not accessible: $treeUriString")
                return
            }

            // Step 1: write into the temp file (create or truncate).
            val tmpFile = treeDoc.findFile(BACKUP_FILENAME_TMP)
                ?: treeDoc.createFile(BACKUP_MIME_TYPE, BACKUP_FILENAME_TMP)
            if (tmpFile == null) {
                Log.w(TAG, "Could not create $BACKUP_FILENAME_TMP in backup folder")
                return
            }
            context.contentResolver.openOutputStream(tmpFile.uri, "wt")?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }

            // Step 2: rotate main → .bak, then tmp → main.
            val mainFile = treeDoc.findFile(BACKUP_FILENAME)
            if (mainFile != null) {
                // Remove stale .bak so rename doesn't collide on providers that disallow duplicates.
                val bakFile = treeDoc.findFile(BACKUP_FILENAME_BAK)
                if (bakFile != null && !bakFile.delete()) {
                    Log.w(TAG, "Could not delete stale $BACKUP_FILENAME_BAK; rename may fail on strict providers")
                }
                val renamed = mainFile.renameTo(BACKUP_FILENAME_BAK)
                if (!renamed) {
                    // Rename not supported by this provider (e.g. some cloud drives).
                    // Fall back: overwrite main in-place; the temp file already holds the
                    // fresh data, so partial-write risk is the same as the old approach,
                    // but only for this provider edge-case.
                    Log.w(TAG, "renameTo($BACKUP_FILENAME_BAK) failed — falling back to in-place overwrite")
                    context.contentResolver.openOutputStream(mainFile.uri, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                    tmpFile.delete()
                    Log.i(TAG, "Backup written (in-place fallback) to ${mainFile.uri}")
                    return
                }
            }
            val promoted = tmpFile.renameTo(BACKUP_FILENAME)
            if (!promoted) {
                // The main file was already renamed to .bak; try to restore it so the
                // directory is never left without a valid main backup file.
                treeDoc.findFile(BACKUP_FILENAME_BAK)?.renameTo(BACKUP_FILENAME)
                // Temp file still holds the fresh data under BACKUP_FILENAME_TMP so the
                // user can recover it manually.
                Log.w(TAG, "Could not rename $BACKUP_FILENAME_TMP → $BACKUP_FILENAME; fresh data is in $BACKUP_FILENAME_TMP")
                return
            }
            Log.i(TAG, "Backup written (rotate) to $BACKUP_FILENAME")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write backup to SAF folder", e)
        }
    }

    private fun readJson(context: Context, treeUriString: String?): String? {
        if (treeUriString == null) return null
        return try {
            val treeUri = Uri.parse(treeUriString)
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (treeDoc == null || !treeDoc.isDirectory) {
                Log.w(TAG, "Backup folder is not accessible: $treeUriString")
                return null
            }
            val backupFile = treeDoc.findFile(BACKUP_FILENAME)
            if (backupFile == null) {
                Log.i(TAG, "No $BACKUP_FILENAME found in backup folder")
                return null
            }
            context.contentResolver.openInputStream(backupFile.uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read backup from SAF folder", e)
            null
        }
    }
}
