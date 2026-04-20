package com.fittrack.app.data.repository

import com.fittrack.app.data.dao.ExerciseDao
import com.fittrack.app.data.dao.LogEntryDao
import com.fittrack.app.data.dao.WorkoutDao
import com.fittrack.app.data.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FitTrackRepository(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val logEntryDao: LogEntryDao
) {
    // Exercises
    val allExercises: Flow<List<Exercise>> = exerciseDao.getAllExercises()

    suspend fun getExerciseById(id: Long) = exerciseDao.getExerciseById(id)
    suspend fun getExerciseByName(name: String) = exerciseDao.getExerciseByName(name)
    suspend fun insertExercise(exercise: Exercise) = exerciseDao.insertExercise(exercise)
    suspend fun updateExercise(exercise: Exercise) = exerciseDao.updateExercise(exercise)
    suspend fun deleteExercise(exercise: Exercise) = exerciseDao.deleteExercise(exercise)

    // Workouts
    val allWorkouts: Flow<List<Workout>> = workoutDao.getAllWorkouts()

    suspend fun getWorkoutById(id: Long) = workoutDao.getWorkoutById(id)
    suspend fun insertWorkout(workout: Workout) = workoutDao.insertWorkout(workout)
    suspend fun updateWorkout(workout: Workout) = workoutDao.updateWorkout(workout)
    suspend fun deleteWorkout(workout: Workout) = workoutDao.deleteWorkout(workout)

    fun getWorkoutExercisesWithExercise(workoutId: Long): Flow<List<WorkoutExerciseWithExercise>> =
        workoutDao.getWorkoutExercisesWithExercise(workoutId)

    suspend fun insertWorkoutExercise(workoutExercise: WorkoutExercise) =
        workoutDao.insertWorkoutExercise(workoutExercise)

    suspend fun updateWorkoutExercise(workoutExercise: WorkoutExercise) =
        workoutDao.updateWorkoutExercise(workoutExercise)

    suspend fun deleteWorkoutExercise(workoutExercise: WorkoutExercise) =
        workoutDao.deleteWorkoutExercise(workoutExercise)

    suspend fun deleteAllWorkoutExercises(workoutId: Long) =
        workoutDao.deleteAllWorkoutExercises(workoutId)

    // Log Entries
    val allLogEntries: Flow<List<LogEntry>> = logEntryDao.getAllLogEntries()
    val allWorkoutDates: Flow<List<Long>> = logEntryDao.getAllWorkoutDates()

    fun getLogEntriesForExercise(exerciseId: Long) = logEntryDao.getLogEntriesForExercise(exerciseId)
    fun getLogEntriesForDate(date: Long) = logEntryDao.getLogEntriesForDate(date)

    suspend fun getPreviousLogEntries(exerciseId: Long, workoutId: Long, beforeDate: Long) =
        logEntryDao.getPreviousLogEntries(exerciseId, workoutId, beforeDate)

    suspend fun getPreviousLogEntriesForExercises(exerciseIds: List<Long>, beforeDate: Long) =
        logEntryDao.getPreviousLogEntriesForExercises(exerciseIds, beforeDate)

    suspend fun insertLogEntry(logEntry: LogEntry) = logEntryDao.insertLogEntry(logEntry)
    suspend fun insertLogEntries(logEntries: List<LogEntry>) = logEntryDao.insertLogEntries(logEntries)
    suspend fun deleteLogEntry(logEntry: LogEntry) = logEntryDao.deleteLogEntry(logEntry)

    // Workout backup support
    suspend fun getWorkoutCount(): Int = workoutDao.getWorkoutCount()

    /** Emits the full snapshot of all workouts with their exercises whenever anything changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllWorkoutsWithExercises(): Flow<List<Pair<Workout, List<WorkoutExerciseWithExercise>>>> =
        allWorkouts.flatMapLatest { workouts ->
            if (workouts.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    workouts.map { workout ->
                        getWorkoutExercisesWithExercise(workout.id).map { exercises ->
                            workout to exercises
                        }
                    }
                ) { it.toList() }
            }
        }
}
