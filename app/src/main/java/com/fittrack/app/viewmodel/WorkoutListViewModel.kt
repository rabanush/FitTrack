package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.repository.FitTrackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutListViewModel(private val repository: FitTrackRepository) : ViewModel() {
    val workouts = repository.allWorkouts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun createWorkout(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.insertWorkout(Workout(name = name))
            onCreated(id)
        }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch {
            repository.deleteWorkout(workout)
        }
    }
}

class WorkoutListViewModelFactory(private val repository: FitTrackRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkoutListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkoutListViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
