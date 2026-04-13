package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.*
import com.fittrack.app.data.repository.FitTrackRepository
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SetData(
    val setNumber: Int,
    var weight: String = "",
    var reps: String = "",
    var isCompleted: Boolean = false,
    val prevWeight: String = "",
    val prevReps: String = ""
)

data class ExerciseSessionData(
    val workoutExercise: WorkoutExerciseWithExercise,
    val sets: List<SetData> = emptyList()
)

data class TimerState(
    val isRunning: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val exerciseIndex: Int = -1,
    val setNumber: Int = -1,
    val endTimeMillis: Long = 0
)

class ActiveWorkoutViewModel(
    private val repository: FitTrackRepository,
    private val workoutId: Long
) : ViewModel() {

    private val _workout = MutableStateFlow<Workout?>(null)
    val workout: StateFlow<Workout?> = _workout

    private val _exerciseSessions = MutableStateFlow<List<ExerciseSessionData>>(emptyList())
    val exerciseSessions: StateFlow<List<ExerciseSessionData>> = _exerciseSessions

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState

    private val _workoutStartTime = System.currentTimeMillis()
    private var timerJob: Job? = null
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (_: Exception) {}

        viewModelScope.launch {
            _workout.value = repository.getWorkoutById(workoutId)
            repository.getWorkoutExercisesWithExercise(workoutId).collect { exercises ->
                val sessions = exercises.map { weWithEx ->
                    val previousLogs = repository.getPreviousLogEntriesForExercise(
                        weWithEx.exercise.id,
                        _workoutStartTime
                    ).sortedBy { it.setNumber }

                    val targetSetCount = weWithEx.workoutExercise.setCount
                    val initialSets = (1..targetSetCount).map { setNum ->
                        val prev = previousLogs.find { it.setNumber == setNum } ?: previousLogs.lastOrNull()
                        SetData(
                            setNumber = setNum,
                            prevWeight = prev?.weight?.toString() ?: "",
                            prevReps = prev?.reps?.toString() ?: ""
                        )
                    }
                    
                    ExerciseSessionData(
                        workoutExercise = weWithEx,
                        sets = initialSets
                    )
                }
                _exerciseSessions.value = sessions
            }
        }
    }

    fun addSet(exerciseIndex: Int) {
        val sessions = _exerciseSessions.value.toMutableList()
        if (exerciseIndex >= sessions.size) return
        val session = sessions[exerciseIndex]
        val newSetNumber = session.sets.size + 1
        
        // Use last set or previous log as template for the new set
        val template = session.sets.lastOrNull()
        val updatedSets = session.sets.toMutableList().apply { 
            add(SetData(
                setNumber = newSetNumber,
                prevWeight = template?.prevWeight ?: "",
                prevReps = template?.prevReps ?: ""
            )) 
        }
        sessions[exerciseIndex] = session.copy(sets = updatedSets)
        _exerciseSessions.value = sessions
    }

    fun removeSet(exerciseIndex: Int) {
        val sessions = _exerciseSessions.value.toMutableList()
        if (exerciseIndex >= sessions.size) return
        val session = sessions[exerciseIndex]
        if (session.sets.size <= 1) return
        val updatedSets = session.sets.toMutableList().apply { removeLastOrNull() }
        sessions[exerciseIndex] = session.copy(sets = updatedSets)
        _exerciseSessions.value = sessions
    }

    fun updateSetData(exerciseIndex: Int, setIndex: Int, weight: String? = null, reps: String? = null) {
        val sessions = _exerciseSessions.value.toMutableList()
        if (exerciseIndex >= sessions.size) return
        val session = sessions[exerciseIndex]
        val sets = session.sets.toMutableList()
        if (setIndex >= sets.size) return
        val set = sets[setIndex]
        sets[setIndex] = set.copy(
            weight = weight ?: set.weight,
            reps = reps ?: set.reps
        )
        sessions[exerciseIndex] = session.copy(sets = sets)
        _exerciseSessions.value = sessions
    }

    fun completeSet(exerciseIndex: Int, setIndex: Int) {
        val sessions = _exerciseSessions.value.toMutableList()
        if (exerciseIndex >= sessions.size) return
        val session = sessions[exerciseIndex]
        val sets = session.sets.toMutableList()
        if (setIndex >= sets.size) return

<<<<<<< copilot/fix-timer-sound-issues
        val currentSet = sets[setIndex]
        if (currentSet.isCompleted) {
            // Toggle: un-complete the set so it can be re-edited
            sets[setIndex] = currentSet.copy(isCompleted = false)
            sessions[exerciseIndex] = session.copy(sets = sets)
            _exerciseSessions.value = sessions
        } else {
            // Complete the set, filling in previous values if fields are empty
            sets[setIndex] = currentSet.copy(
                isCompleted = true,
                weight = if (currentSet.weight.isEmpty()) currentSet.prevWeight else currentSet.weight,
                reps = if (currentSet.reps.isEmpty()) currentSet.prevReps else currentSet.reps
            )
            sessions[exerciseIndex] = session.copy(sets = sets)
            _exerciseSessions.value = sessions

            // Start rest timer
=======
        // If values are empty, use previous values as defaults
        val currentSet = sets[setIndex]
        val wasCompleted = currentSet.isCompleted
        sets[setIndex] = currentSet.copy(
            isCompleted = true,
            weight = if (currentSet.weight.isEmpty()) currentSet.prevWeight else currentSet.weight,
            reps = if (currentSet.reps.isEmpty()) currentSet.prevReps else currentSet.reps
        )

        sessions[exerciseIndex] = session.copy(sets = sets)
        _exerciseSessions.value = sessions

        // Only start rest timer on first completion, not when re-editing a completed set
        if (!wasCompleted) {
>>>>>>> main
            val restSeconds = session.workoutExercise.workoutExercise.restTimerSeconds
            if (restSeconds > 0) {
                startTimer(restSeconds, exerciseIndex, setIndex + 1)
            }
        }
    }

    fun startTimer(seconds: Int, exerciseIndex: Int, setNumber: Int) {
        timerJob?.cancel()
        val endTime = System.currentTimeMillis() + (seconds * 1000L)
        _timerState.value = TimerState(
            isRunning = true,
            remainingSeconds = seconds,
            totalSeconds = seconds,
            exerciseIndex = exerciseIndex,
            setNumber = setNumber,
            endTimeMillis = endTime
        )

        timerJob = viewModelScope.launch {
            var lastBeep = -1
            while (true) {
                val now = System.currentTimeMillis()
                val remaining = ((_timerState.value.endTimeMillis - now) / 1000L).toInt().coerceAtLeast(0)

                _timerState.value = _timerState.value.copy(remainingSeconds = remaining)

                if (remaining in 1..3 && remaining != lastBeep) {
                    lastBeep = remaining
                    playTone(ToneGenerator.TONE_PROP_BEEP, 150)
                }

                if (remaining <= 0) {
                    _timerState.value = _timerState.value.copy(isRunning = false, remainingSeconds = 0)
                    playTone(ToneGenerator.TONE_PROP_BEEP, 400)
                    break
                }
                delay(200L) // Oft genug prüfen
            }
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                toneGenerator?.startTone(toneType, durationMs)
            } catch (_: Exception) {}
        }
    }

    fun skipTimer() {
        timerJob?.cancel()
        _timerState.value = _timerState.value.copy(isRunning = false, remainingSeconds = 0)
    }

    fun adjustTimer(deltaSecs: Int) {
        val current = _timerState.value
        if (!current.isRunning) return

        val newEndTime = current.endTimeMillis + (deltaSecs * 1000L)
        val now = System.currentTimeMillis()
        val newRemaining = ((newEndTime - now) / 1000L).toInt().coerceAtLeast(0)

        if (newRemaining <= 0) {
            skipTimer()
        } else {
            _timerState.value = current.copy(endTimeMillis = newEndTime, remainingSeconds = newRemaining)
        }
    }

    fun finishWorkout(onFinished: () -> Unit) {
        viewModelScope.launch {
            val entries = mutableListOf<LogEntry>()
            val workoutIdVal = workoutId
            val date = _workoutStartTime

            _exerciseSessions.value.forEach { session ->
                session.sets.forEach { set ->
                    // Only save sets that were completed or have data
                    if (set.isCompleted || set.weight.isNotEmpty() || set.reps.isNotEmpty()) {
                        val w = set.weight.toFloatOrNull() ?: set.prevWeight.toFloatOrNull() ?: 0f
                        val r = set.reps.toIntOrNull() ?: set.prevReps.toIntOrNull() ?: 0
                        entries.add(
                            LogEntry(
                                exerciseId = session.workoutExercise.exercise.id,
                                workoutId = workoutIdVal,
                                date = date,
                                setNumber = set.setNumber,
                                weight = w,
                                reps = r
                            )
                        )
                    }
                }
            }
            if (entries.isNotEmpty()) {
                repository.insertLogEntries(entries)
            }
            timerJob?.cancel()
            onFinished()
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
    }
}

class ActiveWorkoutViewModelFactory(
    private val repository: FitTrackRepository,
    private val workoutId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ActiveWorkoutViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ActiveWorkoutViewModel(repository, workoutId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
