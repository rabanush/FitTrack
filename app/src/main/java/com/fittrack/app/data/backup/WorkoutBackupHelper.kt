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

private const val BACKUP_FILENAME = "auto_backup_snapshot.json"
private const val LEGACY_BACKUP_FILENAME = "fittrack_workouts.json"
private const val LEGACY_DIRECTORY = "FitTrackerBackup"
private const val TAG = "WorkoutBackup"
private const val DEFAULT_TIMER_VOLUME_PERCENT = 50
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
                    val resolvedExercise = resolveExerciseForImport(exerciseDao, ex) ?: return@forEach
                    workoutDao.insertWorkoutExercise(
                        WorkoutExercise(
                            workoutId = newWorkoutId,
                            exerciseId = resolvedExercise.id,
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

        if (mealsEmpty && foodEntriesEmpty) {
            val mealIdMap = mutableMapOf<Long, Long>()
            data.meals.forEach { backupMeal ->
                val newMealId = foodDao.insertMeal(
                    Meal(
                        name = backupMeal.name,
                        dateMillis = backupMeal.dateMillis
                    )
                )
                backupMeal.id?.let { oldId -> mealIdMap[oldId] = newMealId }
            }

            data.foodEntries.forEach { backupEntry ->
                val mappedMealId = mealIdMap[backupEntry.mealId] ?: return@forEach
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

    private fun writeJson(context: Context, json: String) {
        try {
            val backupFile = getBackupFile(context)
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
            readJsonFromPrimaryFile(context) ?: readJsonFromLegacyFiles(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read workout backup", e)
            null
        }
    }

    private fun readJsonFromPrimaryFile(context: Context): String? {
        val file = getBackupFile(context)
        if (!file.exists()) return null
        return file.readText()
    }

    private fun getBackupFile(context: Context): File = File(context.filesDir, BACKUP_FILENAME)

    private fun readJsonFromLegacyFiles(context: Context): String? {
        val files = buildList {
            val externalDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (externalDocs != null) {
                val legacyDir = File(externalDocs, LEGACY_DIRECTORY)
                add(File(legacyDir, "backup.json"))
                add(File(legacyDir, LEGACY_BACKUP_FILENAME))
            }
            add(File(context.filesDir, LEGACY_BACKUP_FILENAME))
        }
        val existing = files.firstOrNull { it.exists() } ?: return null
        return runCatching { existing.readText() }
            .onFailure { Log.w(TAG, "Failed to read legacy backup file", it) }
            .getOrNull()
    }
}
