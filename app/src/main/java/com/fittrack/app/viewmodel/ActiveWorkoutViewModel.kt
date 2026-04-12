package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import com.fittrack.app.data.model.*
import com.fittrack.app.data.repository.FitTrackRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SetData(
    val setNumber: Int,
    var weight: String = "",
    var reps: String = "",
    var rir: String = "0",
    var isCompleted: Boolean = false
)

data class ExerciseSessionData(
    val workoutExercise: WorkoutExerciseWithExercise,
    val sets: MutableList<SetData> = mutableListOf(SetData(1)),
    val previousSets: List<LogEntry> = emptyList()
)

data class TimerState(
    val isRunning: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val exerciseIndex: Int = -1,
    val setNumber: Int = -1
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

    val workoutExercises = repository.getWorkoutExercisesWithExercise(workoutId).asLiveData()

    init {
        viewModelScope.launch {
            _workout.value = repository.getWorkoutById(workoutId)
            repository.getWorkoutExercisesWithExercise(workoutId).collect { exercises ->
                val sessions = exercises.map { weWithEx ->
                    val previous = repository.getPreviousLogEntriesForExercise(
                        weWithEx.exercise.id,
                        _workoutStartTime
                    )
                    ExerciseSessionData(
                        workoutExercise = weWithEx,
                        sets = mutableListOf(SetData(1)),
                        previousSets = previous
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
        val updatedSets = session.sets.toMutableList().apply { add(SetData(newSetNumber)) }
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

    fun updateSetData(exerciseIndex: Int, setIndex: Int, weight: String? = null, reps: String? = null, rir: String? = null) {
        val sessions = _exerciseSessions.value.toMutableList()
        if (exerciseIndex >= sessions.size) return
        val session = sessions[exerciseIndex]
        val sets = session.sets.toMutableList()
        if (setIndex >= sets.size) return
        val set = sets[setIndex]
        sets[setIndex] = set.copy(
            weight = weight ?: set.weight,
            reps = reps ?: set.reps,
            rir = rir ?: set.rir
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
        sets[setIndex] = sets[setIndex].copy(isCompleted = true)
        sessions[exerciseIndex] = session.copy(sets = sets)
        _exerciseSessions.value = sessions

        // Start rest timer
        val restSeconds = session.workoutExercise.workoutExercise.restTimerSeconds
        startTimer(restSeconds, exerciseIndex, setIndex + 1)
    }

    fun startTimer(seconds: Int, exerciseIndex: Int, setNumber: Int) {
        timerJob?.cancel()
        _timerState.value = TimerState(
            isRunning = true,
            remainingSeconds = seconds,
            totalSeconds = seconds,
            exerciseIndex = exerciseIndex,
            setNumber = setNumber
        )
        timerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _timerState.value = _timerState.value.copy(remainingSeconds = remaining)
            }
            _timerState.value = _timerState.value.copy(isRunning = false, remainingSeconds = 0)
        }
    }

    fun skipTimer() {
        timerJob?.cancel()
        _timerState.value = _timerState.value.copy(isRunning = false, remainingSeconds = 0)
    }

    fun adjustTimer(deltaSecs: Int) {
        val current = _timerState.value
        if (!current.isRunning) return
        val newRemaining = maxOf(0, current.remainingSeconds + deltaSecs)
        _timerState.value = current.copy(remainingSeconds = newRemaining)
        if (newRemaining == 0) {
            skipTimer()
        }
    }

    fun finishWorkout(onFinished: () -> Unit) {
        viewModelScope.launch {
            val entries = mutableListOf<LogEntry>()
            val workoutIdVal = workoutId
            val date = _workoutStartTime

            _exerciseSessions.value.forEach { session ->
                session.sets.forEach { set ->
                    val w = set.weight.toFloatOrNull() ?: 0f
                    val r = set.reps.toIntOrNull() ?: 0
                    val rir = set.rir.toIntOrNull() ?: 0
                    entries.add(
                        LogEntry(
                            exerciseId = session.workoutExercise.exercise.id,
                            workoutId = workoutIdVal,
                            date = date,
                            setNumber = set.setNumber,
                            weight = w,
                            reps = r,
                            rir = rir
                        )
                    )
                }
            }
            repository.insertLogEntries(entries)
            timerJob?.cancel()
            onFinished()
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
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
