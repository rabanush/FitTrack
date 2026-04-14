package com.fittrack.app.fake

import com.fittrack.app.data.dao.WorkoutDao
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.model.WorkoutExercise
import com.fittrack.app.data.model.WorkoutExerciseWithExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeWorkoutDao : WorkoutDao {
    private val workouts = mutableListOf<Workout>()
    private val workoutsFlow = MutableStateFlow<List<Workout>>(emptyList())
    private val workoutExercises = mutableListOf<WorkoutExercise>()
    private val exercisesWithExercise =
        mutableMapOf<Long, MutableStateFlow<List<WorkoutExerciseWithExercise>>>()
    private var nextWorkoutId = 1L
    private var nextWEId = 1L

    override fun getAllWorkouts(): Flow<List<Workout>> = workoutsFlow

    override suspend fun getWorkoutById(id: Long): Workout? = workouts.find { it.id == id }

    override suspend fun insertWorkout(workout: Workout): Long {
        val id = if (workout.id == 0L) nextWorkoutId++ else workout.id
        workouts.removeIf { it.id == id }
        workouts.add(workout.copy(id = id))
        workoutsFlow.value = workouts.toList()
        return id
    }

    override suspend fun updateWorkout(workout: Workout) {
        val idx = workouts.indexOfFirst { it.id == workout.id }
        if (idx >= 0) {
            workouts[idx] = workout
            workoutsFlow.value = workouts.toList()
        }
    }

    override suspend fun deleteWorkout(workout: Workout) {
        workouts.removeIf { it.id == workout.id }
        workoutsFlow.value = workouts.toList()
    }

    override fun getWorkoutExercises(workoutId: Long): Flow<List<WorkoutExercise>> =
        MutableStateFlow(workoutExercises.filter { it.workoutId == workoutId })

    override fun getWorkoutExercisesWithExercise(workoutId: Long): Flow<List<WorkoutExerciseWithExercise>> =
        exercisesWithExercise.getOrPut(workoutId) { MutableStateFlow(emptyList()) }

    fun setExercisesWithExercise(workoutId: Long, items: List<WorkoutExerciseWithExercise>) {
        exercisesWithExercise.getOrPut(workoutId) { MutableStateFlow(emptyList()) }.value = items
    }

    override suspend fun insertWorkoutExercise(workoutExercise: WorkoutExercise): Long {
        val id = if (workoutExercise.id == 0L) nextWEId++ else workoutExercise.id
        workoutExercises.removeIf { it.id == id }
        workoutExercises.add(workoutExercise.copy(id = id))
        return id
    }

    override suspend fun updateWorkoutExercise(workoutExercise: WorkoutExercise) {
        val idx = workoutExercises.indexOfFirst { it.id == workoutExercise.id }
        if (idx >= 0) workoutExercises[idx] = workoutExercise
    }

    override suspend fun deleteWorkoutExercise(workoutExercise: WorkoutExercise) {
        workoutExercises.removeIf { it.id == workoutExercise.id }
    }

    override suspend fun deleteAllWorkoutExercises(workoutId: Long) {
        workoutExercises.removeIf { it.workoutId == workoutId }
    }
}
