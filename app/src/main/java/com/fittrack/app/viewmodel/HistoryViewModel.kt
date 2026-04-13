package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.Workout
import com.fittrack.app.data.repository.FitTrackRepository

class HistoryViewModel(private val repository: FitTrackRepository) : ViewModel() {
    val allWorkoutDates = repository.allWorkoutDates.asLiveData()

    fun getLogEntriesForDate(date: Long) = repository.getLogEntriesForDate(date).asLiveData()

    suspend fun getWorkoutById(id: Long): Workout? = repository.getWorkoutById(id)
    
    suspend fun getExerciseById(id: Long): Exercise? = repository.getExerciseById(id)
}

class HistoryViewModelFactory(private val repository: FitTrackRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
