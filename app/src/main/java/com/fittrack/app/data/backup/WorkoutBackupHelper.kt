package com.fittrack.app.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.fittrack.app.data.dao.CustomFoodDao
import com.fittrack.app.data.dao.ExerciseDao
import com.fittrack.app.data.dao.FoodDao
import com.fittrack.app.data.dao.RecipeDao
import com.fittrack.app.data.dao.WorkoutCaloriesDao
import com.fittrack.app.data.dao.WorkoutDao
import com.fittrack.app.data.model.CustomFood
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.FoodEntry
import com.fittrack.app.data.model.Meal
import com.fittrack.app.data.model.Recipe
import com.fittrack.app.data.model.RecipeItem
import com.fittrack.app.data.model.RecipeWithItems
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.model.WorkoutCalories
import com.fittrack.app.data.model.WorkoutExercise
import com.fittrack.app.data.model.WorkoutExerciseWithExercise
import com.fittrack.app.data.preferences.ActiveWorkoutSession
import com.fittrack.app.data.preferences.ActiveWorkoutSessionPreferences
import com.fittrack.app.data.preferences.ActivityLevel
import com.fittrack.app.data.preferences.Gender
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.preferences.UserProfile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.util.Locale

// Backup file stored in the public Downloads folder – survives app reinstall on all Android versions.
private const val BACKUP_FILENAME = "FitTrackBackup.json"
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

private data class BackupMeal(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String,
    @SerializedName("dateMillis") val dateMillis: Long
)

private data class BackupFoodEntry(
    @SerializedName("mealId") val mealId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("barcode") val barcode: String?,
    @SerializedName("caloriesPer100") val caloriesPer100: Float,
    @SerializedName("proteinPer100") val proteinPer100: Float,
    @SerializedName("carbsPer100") val carbsPer100: Float,
    @SerializedName("fatPer100") val fatPer100: Float,
    @SerializedName("amount") val amount: Float
)

private data class BackupWorkoutCalories(
    @SerializedName("workoutId") val workoutId: Long,
    @SerializedName("dateMillis") val dateMillis: Long,
    @SerializedName("caloriesBurned") val caloriesBurned: Float,
    @SerializedName("durationMinutes") val durationMinutes: Int
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
    @SerializedName("workoutCalories") val workoutCalories: List<BackupWorkoutCalories> = emptyList(),
    @SerializedName("activeWorkoutSession") val activeWorkoutSession: BackupActiveWorkoutSession? = null
)

object WorkoutBackupHelper {

    private val gson = Gson()

    /**
     * Serialises all relevant app data to JSON and writes it to the public Downloads folder
     * as FitTrackBackup.json. This file survives app reinstall on all Android versions,
     * including Xiaomi/MIUI devices that use scoped storage.
     */
    fun exportData(
        context: Context,
        workoutsWithExercises: List<Pair<Workout, List<WorkoutExerciseWithExercise>>>,
        userProfile: UserProfile,
        customFoods: List<CustomFood>,
        recipes: List<RecipeWithItems>,
        customExercises: List<Exercise>,
        meals: List<Meal>,
        foodEntries: List<FoodEntry>,
        workoutCalories: List<WorkoutCalories>,
        activeWorkoutSession: ActiveWorkoutSession?,
        activeWorkoutExerciseSessionsState: String?
    ) {
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
                    amount = entry.amount
                )
            },
            workoutCalories = workoutCalories.map { entry ->
                BackupWorkoutCalories(
                    workoutId = entry.workoutId,
                    dateMillis = entry.dateMillis,
                    caloriesBurned = entry.caloriesBurned,
                    durationMinutes = entry.durationMinutes
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

        writeJson(context, gson.toJson(data))
    }

    /**
     * Reads the backup file from app storage and restores all data on a fresh install.
     */
    suspend fun importData(
        context: Context,
        exerciseDao: ExerciseDao,
        workoutDao: WorkoutDao,
        userPreferences: UserPreferences,
        customFoodDao: CustomFoodDao,
        recipeDao: RecipeDao,
        foodDao: FoodDao,
        workoutCaloriesDao: WorkoutCaloriesDao,
        activeWorkoutSessionPreferences: ActiveWorkoutSessionPreferences
    ) {
        val json = readJson(context) ?: return
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
        val caloriesEmpty = workoutCaloriesDao.getCount() == 0

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
            if (mealIdMap.isEmpty() && data.foodEntries.isNotEmpty()) {
                Log.w(TAG, "Skipping food-entry restore because no meal ID mapping is available")
            } else {
                data.foodEntries.forEach { backupEntry ->
                    val mappedMealId = mealIdMap[backupEntry.mealId]
                    if (mappedMealId == null) {
                        Log.w(TAG, "Skipping food-entry restore for unknown mealId=${backupEntry.mealId}")
                        return@forEach
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
                            amount = backupEntry.amount
                        )
                    )
                }
            }
        }

        if (caloriesEmpty) {
            data.workoutCalories.forEach { backupEntry ->
                val mappedWorkoutId = workoutIdMapping[backupEntry.workoutId]
                if (mappedWorkoutId == null) {
                    Log.w(TAG, "Skipping workout-calorie restore for unknown workoutId=${backupEntry.workoutId}")
                    return@forEach
                }
                workoutCaloriesDao.insert(
                    WorkoutCalories(
                        dateMillis = backupEntry.dateMillis,
                        workoutId = mappedWorkoutId,
                        caloriesBurned = backupEntry.caloriesBurned,
                        durationMinutes = backupEntry.durationMinutes
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
                        timerVolumePercent = profile.timerVolumePercent ?: DEFAULT_TIMER_VOLUME_PERCENT
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

        // After a successful import the backup file in Downloads already contains
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

    private fun writeJson(context: Context, json: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToMediaStoreDownloads(context, json)
        } else {
            writeToDownloadsFile(json)
        }
    }

    private fun readJson(context: Context): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            readFromMediaStoreDownloads(context)
        } else {
            readFromDownloadsFile()
        }
    }

    /**
     * Writes the backup JSON to the public Downloads folder via MediaStore (Android 10+).
     * The file is visible in the file manager as "FitTrackBackup.json" in Downloads,
     * survives app reinstall, and requires no special permissions on Android 10+.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToMediaStoreDownloads(context: Context, json: String) {
        val resolver = context.contentResolver
        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val existingUri = findInMediaStoreDownloads(context)
        if (existingUri != null) {
            try {
                // Overwrite the existing file in place (wt = write-truncate).
                resolver.openOutputStream(existingUri, "wt")?.use { it.write(jsonBytes) }
                Log.i(TAG, "Updated backup in MediaStore Downloads")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update backup in MediaStore Downloads", e)
            }
        } else {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_FILENAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val insertUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (insertUri == null) {
                Log.w(TAG, "Failed to create backup entry in MediaStore Downloads")
                return
            }
            try {
                resolver.openOutputStream(insertUri)?.use { it.write(jsonBytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(insertUri, values, null, null)
                Log.i(TAG, "Created new backup in MediaStore Downloads")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write backup to MediaStore Downloads", e)
                runCatching { resolver.delete(insertUri, null, null) }
            }
        }
    }

    /**
     * Reads the backup JSON from the public Downloads folder via MediaStore (Android 10+).
     * Queries by filename only so the file can be found after app reinstall even when
     * ownership metadata has been reset by the system.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readFromMediaStoreDownloads(context: Context): String? {
        val uri = findInMediaStoreDownloads(context) ?: run {
            Log.i(TAG, "No backup found in MediaStore Downloads")
            return null
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read backup from MediaStore Downloads", e)
            null
        }
    }

    /**
     * Returns the MediaStore URI for the most recently modified FitTrackBackup.json in Downloads,
     * or null if no such file exists.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findInMediaStoreDownloads(context: Context): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        val args = arrayOf(BACKUP_FILENAME)
        val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"
        return resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection, selection, args, sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            } else null
        }
    }

    /**
     * Writes the backup JSON directly to the public Downloads folder (Android 8–9).
     * Requires WRITE_EXTERNAL_STORAGE permission granted at runtime.
     */
    private fun writeToDownloadsFile(json: String) {
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "Failed to create Downloads directory: ${dir.absolutePath}")
                return
            }
            File(dir, BACKUP_FILENAME).writeText(json, Charsets.UTF_8)
            Log.i(TAG, "Wrote backup to Downloads folder")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write backup to Downloads folder", e)
        }
    }

    /**
     * Reads the backup JSON directly from the public Downloads folder (Android 8–9).
     * Requires READ_EXTERNAL_STORAGE permission granted at runtime.
     */
    private fun readFromDownloadsFile(): String? {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            BACKUP_FILENAME
        )
        if (!file.exists()) return null
        return runCatching { file.readText(Charsets.UTF_8) }
            .onFailure { Log.w(TAG, "Failed to read backup from Downloads folder", it) }
            .getOrNull()
    }
}
