package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.repository.FitTrackRepository

class HistoryViewModel(private val repository: FitTrackRepository) : ViewModel() {
    val allWorkoutDates = repository.allWorkoutDates.asLiveData()

    fun getLogEntriesForDate(date: Long) = repository.getLogEntriesForDate(date).asLiveData()
}

class HistoryViewModelFactory(private val repository: FitTrackRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HistoryViewModel(repository) as T
}
