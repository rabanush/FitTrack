package com.fittrack.app.data.backup

import android.content.Context
import android.os.Environment
import android.util.Log
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
import com.fittrack.app.data.preferences.ActivityLevel
import com.fittrack.app.data.preferences.Gender
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.preferences.UserProfile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.nio.file.Files
import java.util.Locale

private const val BACKUP_FILENAME = "auto_backup_snapshot.json"
private const val LEGACY_BACKUP_FILENAME = "fittrack_workouts.json"
private const val LEGACY_BACKUP_JSON_FILENAME = "backup.json"
private const val LEGACY_DIRECTORY = "FitTrackerBackup"
private const val LEGACY_DIRECTORY_ALT = "FitTrackBackup"
private const val TAG = "WorkoutBackup"
private const val DEFAULT_TIMER_VOLUME_PERCENT = 50
private const val FALLBACK_MUSCLE_GROUP = "Sonstiges"
private const val FALLBACK_DESCRIPTION = "Aus Backup wiederhergestellt"
// Safety guard for storage traversal to avoid long-running I/O on large devices.
// Once the limit is reached, scanning stops and only already-discovered artifacts are deleted.
private const val MAX_SCAN_DIRECTORIES = 15_000
// v2 adds meals, food entries, and workout calories to the automatic backup payload.
private const val BACKUP_SCHEMA_VERSION = 2

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

private data class BackupData(
    @SerializedName("version") val version: Int = BACKUP_SCHEMA_VERSION,
    @SerializedName("workouts") val workouts: List<BackupWorkout> = emptyList(),
    @SerializedName("userProfile") val userProfile: BackupUserProfile? = null,
    @SerializedName("customFoods") val customFoods: List<BackupCustomFood> = emptyList(),
    @SerializedName("recipes") val recipes: List<BackupRecipe> = emptyList(),
    @SerializedName("customExercises") val customExercises: List<BackupCustomExercise> = emptyList(),
    @SerializedName("meals") val meals: List<BackupMeal> = emptyList(),
    @SerializedName("foodEntries") val foodEntries: List<BackupFoodEntry> = emptyList(),
    @SerializedName("workoutCalories") val workoutCalories: List<BackupWorkoutCalories> = emptyList()
)

object WorkoutBackupHelper {

    private val gson = Gson()
    private val knownBackupFiles = setOf(BACKUP_FILENAME, LEGACY_BACKUP_FILENAME, LEGACY_BACKUP_JSON_FILENAME)
    private val knownBackupDirectories = setOf(LEGACY_DIRECTORY.lowercase(Locale.ROOT), LEGACY_DIRECTORY_ALT.lowercase(Locale.ROOT))

    fun purgeAllBackupData(context: Context) {
        val deleteCandidates = linkedSetOf<File>()
        deleteCandidates += backupCandidates(context).map { it.file }
        deleteCandidates += legacyBackupFiles(context)
        deleteCandidates += discoverBackupArtifacts(context)
        deleteCandidates.forEach { candidate ->
            runCatching {
                if (!candidate.exists()) return@runCatching
                if (candidate.isDirectory) {
                    candidate.deleteRecursively()
                } else {
                    candidate.delete()
                }
            }.onFailure {
                Log.w(TAG, "Could not delete backup artifact: ${candidate.absolutePath}", it)
            }
        }
    }

    /**
     * Serialises all relevant app data to JSON and writes it to app-private storage.
     * This file is intended to be restored automatically on reinstall via Android backup.
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
        workoutCalories: List<WorkoutCalories>
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
        workoutCaloriesDao: WorkoutCaloriesDao
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

        // After a successful import, remove any legacy backup files so they can never be read
        // again on a future reinstall.  The new backup system writes auto_backup_snapshot.json
        // after every data change, so the legacy files are no longer needed.
        cleanupLegacyFiles(context)
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
        // Write to internal private storage first (covered by Android Auto Backup).
        writeToFile(getInternalBackupFile(context), json)
        // Also write to external app-specific storage, which is preserved across
        // uninstalls on most Android versions, providing a local reinstall fallback.
        getExternalBackupFile(context)?.let { writeToFile(it, json) }
    }

    private fun writeToFile(file: File, json: String) {
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Log.w(TAG, "Failed to create backup directory: ${parent.absolutePath}")
                return
            }
            file.writeText(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write backup to ${file.absolutePath}", e)
        }
    }

    private fun readJson(context: Context): String? {
        val candidates = backupCandidates(context)
        if (candidates.isEmpty()) return null

        for (candidate in candidates) {
            val json = readJsonFromFile(candidate.file)
            if (!json.isNullOrBlank()) {
                Log.i(
                    TAG,
                    "Using backup from ${candidate.source} (${candidate.file.absolutePath}, ts=${candidate.file.lastModified()})"
                )
                return json
            }
        }
        return null
    }

    private fun readJsonFromFile(file: File?): String? {
        if (file == null || !file.exists()) return null
        return runCatching { file.readText() }
            .onFailure { Log.w(TAG, "Failed to read backup from ${file.absolutePath}", it) }
            .getOrNull()
    }

    /** Internal private storage – deleted on uninstall but covered by Android Auto Backup. */
    private fun getInternalBackupFile(context: Context): File = File(context.filesDir, BACKUP_FILENAME)

    /** External app-specific storage – survives uninstall on most Android devices.
     *  Returns null when external storage is unavailable. */
    private fun getExternalBackupFile(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, BACKUP_FILENAME)
    }

    private data class BackupCandidate(
        val file: File,
        val source: String,
        val priority: Int
    )

    private fun backupCandidates(context: Context): List<BackupCandidate> {
        val currentCandidates = listOfNotNull(
            BackupCandidate(getInternalBackupFile(context), "internal", priority = 2),
            getExternalBackupFile(context)?.let { BackupCandidate(it, "external", priority = 2) }
        )

        val legacyCandidates = legacyBackupFiles(context).map { file ->
            BackupCandidate(file, "legacy", priority = 1)
        }

        return (currentCandidates + legacyCandidates)
            .filter { it.file.exists() }
            .sortedWith(
                compareByDescending<BackupCandidate> { it.priority }
                    .thenByDescending { it.file.lastModified() }
            )
    }

    private fun legacyBackupFiles(context: Context): List<File> {
        return buildList {
            val externalDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (externalDocs != null) {
                val legacyDir = File(externalDocs, LEGACY_DIRECTORY)
                add(File(legacyDir, LEGACY_BACKUP_JSON_FILENAME))
                add(File(legacyDir, LEGACY_BACKUP_FILENAME))
                add(legacyDir)
                add(File(externalDocs, LEGACY_DIRECTORY_ALT))
            }
            add(File(context.filesDir, LEGACY_BACKUP_FILENAME))
            add(File(context.filesDir, BACKUP_FILENAME))
            context.getExternalFilesDir(null)?.let { add(File(it, BACKUP_FILENAME)) }
        }
    }

    private fun cleanupLegacyFiles(context: Context) {
        legacyBackupFiles(context).forEach { file ->
            if (file.exists()) {
                runCatching {
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                }
                    .onSuccess { Log.i(TAG, "Deleted legacy backup file: ${file.absolutePath}") }
                    .onFailure { Log.w(TAG, "Could not delete legacy backup file: ${file.absolutePath}", it) }
            }
        }
    }

    private fun discoverBackupArtifacts(context: Context): Set<File> {
        val results = linkedSetOf<File>()
        val queue = ArrayDeque<File>()
        val visited = hashSetOf<String>()
        var scannedDirectories = 0

        scanRoots(context)
            .filter { it.exists() }
            .forEach { queue.add(it) }

        while (queue.isNotEmpty() && scannedDirectories < MAX_SCAN_DIRECTORIES) {
            val directory = queue.removeFirst()
            if (!visited.add(directory.absolutePath)) continue
            if (!directory.isDirectory) continue
            if (runCatching { Files.isSymbolicLink(directory.toPath()) }.getOrDefault(false)) continue

            scannedDirectories++
            val children = runCatching { directory.listFiles()?.toList().orEmpty() }
                .onFailure { Log.w(TAG, "Cannot scan directory for backup artifacts: ${directory.absolutePath}", it) }
                .getOrDefault(emptyList())

            for (child in children) {
                val loweredName = child.name.lowercase(Locale.ROOT)
                if (child.isDirectory) {
                    val isNamedBackupDir = loweredName in knownBackupDirectories
                    if (isNamedBackupDir) {
                        results.add(child)
                        continue
                    }
                    if (runCatching { Files.isSymbolicLink(child.toPath()) }.getOrDefault(false)) continue
                    queue.add(child)
                    continue
                }

                val isKnownBackupFile = loweredName in knownBackupFiles
                if (isKnownBackupFile) {
                    results.add(child)
                }
            }
        }
        return results
    }

    private fun scanRoots(context: Context): List<File> {
        val roots = linkedSetOf<File>()
        roots += context.filesDir
        roots += context.noBackupFilesDir
        roots += context.cacheDir
        context.externalCacheDir?.let { roots += it }
        context.getExternalFilesDir(null)?.let { roots += it }
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { roots += it }
        context.externalMediaDirs.forEach { if (it != null) roots += it }
        context.getExternalFilesDirs(null).forEach { if (it != null) roots += it }
        context.getExternalFilesDirs(Environment.DIRECTORY_DOCUMENTS).forEach { if (it != null) roots += it }
        return roots.toList()
    }
}
