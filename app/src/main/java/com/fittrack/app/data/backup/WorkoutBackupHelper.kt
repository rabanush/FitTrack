package com.fittrack.app.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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
import com.fittrack.app.data.preferences.Gender
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.preferences.UserProfile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

private const val BACKUP_SUBFOLDER = "FitTrackerBackup"
private const val BACKUP_FILENAME = "fittrack_workouts.json"
private const val TAG = "WorkoutBackup"

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
    @SerializedName("activityLevel") val activityLevel: String
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
     * Serialises all app data to JSON and writes it to the user-chosen backup folder.
     * The folder is represented as a SAF tree URI stored in [BackupPreferences].
     * A "FitTrackerBackup" subdirectory is created automatically inside the chosen folder.
     * If no folder has been selected yet the call is silently skipped.
     */
    fun exportData(
        context: Context,
        treeUri: Uri?,
        workoutsWithExercises: List<Pair<Workout, List<WorkoutExerciseWithExercise>>>,
        userProfile: UserProfile,
        customFoods: List<CustomFood>,
        recipes: List<RecipeWithItems>
    ) {
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
                activityLevel = userProfile.activityLevel.name
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
        writeJson(context, treeUri, gson.toJson(data))
    }

    /**
     * Reads the backup file from the user-chosen backup folder and inserts all data
     * into the DB and DataStore.
     * Workout plans, custom foods, and recipes are only restored when the respective
     * tables are empty (i.e., on a fresh install or after clearing app data).
     * The user profile is always restored from backup when a valid backup file is found
     * and workouts are empty, so that body data is not silently overwritten.
     */
    suspend fun importData(
        context: Context,
        treeUri: Uri?,
        exerciseDao: ExerciseDao,
        workoutDao: WorkoutDao,
        userPreferences: UserPreferences,
        customFoodDao: CustomFoodDao,
        recipeDao: RecipeDao
    ) {
        val json = readJson(context, treeUri) ?: return
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
                        activityLevel = activityLevel
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

    private fun writeJson(context: Context, treeUri: Uri?, json: String) {
        if (treeUri == null) {
            Log.w(TAG, "No backup folder selected — skipping backup write")
            return
        }
        try {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: run {
                Log.w(TAG, "Cannot open backup tree URI — skipping write")
                return
            }
            val folder = tree.findFile(BACKUP_SUBFOLDER)
                ?: tree.createDirectory(BACKUP_SUBFOLDER)
                ?: run {
                    Log.w(TAG, "Cannot create $BACKUP_SUBFOLDER subdirectory — skipping write")
                    return
                }
            // Overwrite any existing backup file.
            folder.findFile(BACKUP_FILENAME)?.delete()
            val file = folder.createFile("application/json", BACKUP_FILENAME) ?: run {
                Log.w(TAG, "Cannot create backup file — skipping write")
                return
            }
            context.contentResolver.openOutputStream(file.uri)?.use { it.write(json.toByteArray()) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write workout backup", e)
        }
    }

    private fun readJson(context: Context, treeUri: Uri?): String? {
        if (treeUri == null) return null
        return try {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            val folder = tree.findFile(BACKUP_SUBFOLDER) ?: return null
            val file = folder.findFile(BACKUP_FILENAME) ?: return null
            context.contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() }
        } catch (_: Exception) { null }
    }
}
