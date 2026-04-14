package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.repository.FitTrackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExerciseViewModel(private val repository: FitTrackRepository) : ViewModel() {
    val allExercises = repository.allExercises.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList<Exercise>()
    )

    fun insertExercise(name: String, muscleGroup: String, isCustom: Boolean = true) {
        viewModelScope.launch {
            repository.insertExercise(Exercise(name = name, muscleGroup = muscleGroup, isCustom = isCustom))
        }
    }

    fun updateExercise(exercise: Exercise) {
        viewModelScope.launch {
            repository.updateExercise(exercise)
        }
    }

    fun deleteExercise(exercise: Exercise) {
        viewModelScope.launch {
            repository.deleteExercise(exercise)
        }
    }
}

class ExerciseViewModelFactory(private val repository: FitTrackRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ExerciseViewModel(repository) as T
}
