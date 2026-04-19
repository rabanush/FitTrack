package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.Exercise
import com.fittrack.app.data.model.LogEntry
import com.fittrack.app.data.repository.FitTrackRepository
import com.fittrack.app.util.displayName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(private val repository: FitTrackRepository) : ViewModel() {
    val allWorkoutDates: StateFlow<List<Long>> = repository.allWorkoutDates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Map of exercise ID → display name for use in the history UI. */
    val exerciseNames: StateFlow<Map<Long, String>> = repository.allExercises
        .map { exercises -> exercises.associate { it.id to it.displayName() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun getLogEntriesForDate(date: Long): Flow<List<LogEntry>> =
        repository.getLogEntriesForDate(date)
}

class HistoryViewModelFactory(private val repository: FitTrackRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HistoryViewModel(repository) as T
}
