package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.*
import com.fittrack.app.data.repository.FitTrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WorkoutDetailViewModel(
    private val repository: FitTrackRepository,
    private val workoutId: Long
) : ViewModel() {

    private val _workout = MutableStateFlow<Workout?>(null)
    val workout: StateFlow<Workout?> = _workout

    val workoutExercises = repository.getWorkoutExercisesWithExercise(workoutId).asLiveData()
    val allExercises = repository.allExercises.asLiveData()

    init {
        viewModelScope.launch {
            _workout.value = repository.getWorkoutById(workoutId)
        }
    }

    fun updateWorkoutName(name: String) {
        viewModelScope.launch {
            val current = _workout.value ?: return@launch
            val updated = current.copy(name = name.uppercase())
            repository.updateWorkout(updated)
            _workout.value = updated
        }
    }

    fun addExerciseToWorkout(exerciseId: Long, restTimerSeconds: Int = 90) {
        viewModelScope.launch {
            val currentCount = workoutExercises.value?.size ?: 0
            repository.insertWorkoutExercise(
                WorkoutExercise(
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    orderIndex = currentCount,
                    restTimerSeconds = restTimerSeconds
                )
            )
        }
    }

    fun removeExerciseFromWorkout(workoutExercise: WorkoutExercise) {
        viewModelScope.launch {
            repository.deleteWorkoutExercise(workoutExercise)
        }
    }

    fun updateRestTimer(workoutExercise: WorkoutExercise, newSeconds: Int) {
        viewModelScope.launch {
            repository.updateWorkoutExercise(workoutExercise.copy(restTimerSeconds = newSeconds))
        }
    }

    fun reorderExercises(from: Int, to: Int) {
        viewModelScope.launch {
            val exercises = workoutExercises.value?.toMutableList() ?: return@launch
            if (from < 0 || to < 0 || from >= exercises.size || to >= exercises.size) return@launch
            val moved = exercises.removeAt(from)
            exercises.add(to, moved)
            exercises.forEachIndexed { index, item ->
                repository.updateWorkoutExercise(item.workoutExercise.copy(orderIndex = index))
            }
        }
    }
}

class WorkoutDetailViewModelFactory(
    private val repository: FitTrackRepository,
    private val workoutId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WorkoutDetailViewModel(repository, workoutId) as T
}
