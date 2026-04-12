package com.fittrack.app.data.repository

import com.fittrack.app.data.dao.ExerciseDao
import com.fittrack.app.data.dao.LogEntryDao
import com.fittrack.app.data.dao.WorkoutDao
import com.fittrack.app.data.model.*
import kotlinx.coroutines.flow.Flow

class FitTrackRepository(
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val logEntryDao: LogEntryDao
) {
    // Exercises
    val allExercises: Flow<List<Exercise>> = exerciseDao.getAllExercises()

    suspend fun getExerciseById(id: Long) = exerciseDao.getExerciseById(id)
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

    suspend fun getPreviousLogEntriesForExercise(exerciseId: Long, beforeDate: Long) =
        logEntryDao.getPreviousLogEntriesForExercise(exerciseId, beforeDate)

    suspend fun insertLogEntry(logEntry: LogEntry) = logEntryDao.insertLogEntry(logEntry)
    suspend fun insertLogEntries(logEntries: List<LogEntry>) = logEntryDao.insertLogEntries(logEntries)
    suspend fun deleteLogEntry(logEntry: LogEntry) = logEntryDao.deleteLogEntry(logEntry)
}
