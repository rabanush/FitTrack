package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.*
import com.fittrack.app.data.repository.FitTrackRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WorkoutDetailViewModel(
    private val repository: FitTrackRepository,
    private val workoutId: Long
) : ViewModel() {

    private val _workout = MutableStateFlow<Workout?>(null)
    val workout: StateFlow<Workout?> = _workout.asStateFlow()

    // Umstellung auf StateFlow für bessere Typ-Stabilität in Compose
    val workoutExercises: StateFlow<List<WorkoutExerciseWithExercise>> = repository
        .getWorkoutExercisesWithExercise(workoutId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allExercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            _workout.value = repository.getWorkoutById(workoutId)
        }
    }

    fun updateWorkoutName(name: String) {
        viewModelScope.launch {
            val current = _workout.value ?: return@launch
            val updated = current.copy(name = name)
            repository.updateWorkout(updated)
            _workout.value = updated
        }
    }

    fun addExerciseToWorkout(exerciseId: Long, restTimerSeconds: Int = 90) {
        viewModelScope.launch {
            val currentCount = workoutExercises.value.size
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
            val exercises = workoutExercises.value.toMutableList()
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
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkoutDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkoutDetailViewModel(repository, workoutId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
