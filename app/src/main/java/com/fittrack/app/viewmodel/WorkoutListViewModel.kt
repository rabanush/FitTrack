package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.repository.FitTrackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutListViewModel(private val repository: FitTrackRepository) : ViewModel() {
    val workouts: StateFlow<List<Workout>> = repository.allWorkouts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createWorkout(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.insertWorkout(Workout(name = name.uppercase()))
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
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WorkoutListViewModel(repository) as T
}
