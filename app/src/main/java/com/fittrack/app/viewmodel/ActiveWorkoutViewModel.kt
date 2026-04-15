package com.fittrack.app.viewmodel

import androidx.lifecycle.*
import androidx.compose.runtime.Immutable
import com.fittrack.app.data.model.*
import com.fittrack.app.data.repository.FitTrackRepository
import android.media.AudioManager
import android.media.ToneGenerator
import com.fittrack.app.util.todayMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class SetData(
    val setNumber: Int,
    val weight: String = "",
    val reps: String = "",
    val rir: String = "0",
    val isCompleted: Boolean = false,
    val prevWeight: String = "",
    val prevReps: String = "",
    val prevRir: String = ""
)

@Immutable
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
    private val workoutId: Long,
    private val foodRepository: com.fittrack.app.data.repository.FoodRepository? = null
) : ViewModel() {

    private val _workout = MutableStateFlow<Workout?>(null)
    val workout: StateFlow<Workout?> = _workout

    private val _exerciseSessions = MutableStateFlow<List<ExerciseSessionData>>(emptyList())
    val exerciseSessions: StateFlow<List<ExerciseSessionData>> = _exerciseSessions

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState

    /** Emits only when the timer banner should appear or disappear, preventing full-screen recomposition on every tick. */
    val isTimerVisible: StateFlow<Boolean> = _timerState
        .map { it.isRunning || it.remainingSeconds > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
                _exerciseSessions.value = withContext(Dispatchers.IO) {
                    coroutineScope {
                        exercises.map { async { buildSessionForExercise(it) } }.awaitAll()
                    }
                }
            }
        }
    }

    // Applies a transformation to the session at the given index.
    private fun updateSessionAt(exerciseIndex: Int, transform: (ExerciseSessionData) -> ExerciseSessionData) {
        val sessions = _exerciseSessions.value.toMutableList()
        if (exerciseIndex >= sessions.size) return
        sessions[exerciseIndex] = transform(sessions[exerciseIndex])
        _exerciseSessions.value = sessions
    }

    // Returns a copy of this SetData that is marked as completed, back-filling any empty fields
    // from the previous-session values.
    private fun SetData.toCompleted() = copy(
        isCompleted = true,
        weight = weight.ifEmpty { prevWeight },
        reps = reps.ifEmpty { prevReps },
        rir = rir.ifEmpty { prevRir }
    )

    private fun buildInitialSet(setNum: Int, previousLogs: List<LogEntry>): SetData {
        val prev = previousLogs.find { it.setNumber == setNum } ?: previousLogs.lastOrNull()
        return SetData(
            setNumber = setNum,
            prevWeight = prev?.weight?.toString() ?: "",
            prevReps = prev?.reps?.toString() ?: "",
            prevRir = prev?.rir?.toString() ?: ""
        )
    }

    private suspend fun buildSessionForExercise(weWithEx: WorkoutExerciseWithExercise): ExerciseSessionData {
        val previousLogs = repository.getPreviousLogEntriesForExercise(
            weWithEx.exercise.id, _workoutStartTime
        ).sortedBy { it.setNumber }
        val initialSets = (1..weWithEx.workoutExercise.setCount).map { buildInitialSet(it, previousLogs) }
        return ExerciseSessionData(workoutExercise = weWithEx, sets = initialSets)
    }

    private fun buildLogEntry(session: ExerciseSessionData, set: SetData): LogEntry? {
        if (!set.isCompleted && set.weight.isEmpty() && set.reps.isEmpty()) return null
        return LogEntry(
            exerciseId = session.workoutExercise.exercise.id,
            workoutId = workoutId,
            date = _workoutStartTime,
            setNumber = set.setNumber,
            weight = set.weight.toFloatOrNull() ?: set.prevWeight.toFloatOrNull() ?: 0f,
            reps = set.reps.toIntOrNull() ?: set.prevReps.toIntOrNull() ?: 0,
            rir = set.rir.toIntOrNull() ?: set.prevRir.toIntOrNull() ?: 0
        )
    }

    fun addSet(exerciseIndex: Int) {
        updateSessionAt(exerciseIndex) { session ->
            val template = session.sets.lastOrNull()
            session.copy(
                sets = session.sets + SetData(
                    setNumber = session.sets.size + 1,
                    prevWeight = template?.prevWeight ?: "",
                    prevReps = template?.prevReps ?: "",
                    prevRir = template?.prevRir ?: ""
                )
            )
        }
    }

    fun removeSet(exerciseIndex: Int) {
        updateSessionAt(exerciseIndex) { session ->
            if (session.sets.size <= 1) return@updateSessionAt session
            session.copy(sets = session.sets.dropLast(1))
        }
    }

    fun updateSetData(exerciseIndex: Int, setIndex: Int, weight: String? = null, reps: String? = null) {
        updateSessionAt(exerciseIndex) { session ->
            if (setIndex >= session.sets.size) return@updateSessionAt session
            session.copy(sets = session.sets.mapIndexed { i, set ->
                if (i == setIndex) set.copy(weight = weight ?: set.weight, reps = reps ?: set.reps) else set
            })
        }
    }

    fun completeSet(exerciseIndex: Int, setIndex: Int) {
        updateSessionAt(exerciseIndex) { session ->
            if (setIndex >= session.sets.size) return@updateSessionAt session
            val currentSet = session.sets[setIndex]
            val updatedSet = if (currentSet.isCompleted) currentSet.copy(isCompleted = false) else currentSet.toCompleted()
            val restSeconds = session.workoutExercise.workoutExercise.restTimerSeconds
            if (!currentSet.isCompleted && restSeconds > 0) startTimer(restSeconds, exerciseIndex, setIndex + 1)
            session.copy(sets = session.sets.mapIndexed { i, s -> if (i == setIndex) updatedSet else s })
        }
    }

    fun startTimer(seconds: Int, exerciseIndex: Int, setNumber: Int) {
        timerJob?.cancel()
        _timerState.value = TimerState(
            isRunning = true,
            remainingSeconds = seconds,
            totalSeconds = seconds,
            exerciseIndex = exerciseIndex,
            setNumber = setNumber,
            endTimeMillis = System.currentTimeMillis() + (seconds * 1000L)
        )
        timerJob = viewModelScope.launch { tickTimer() }
    }

    private suspend fun tickTimer() {
        var lastBeep = -1
        while (true) {
            val remaining = ((_timerState.value.endTimeMillis - System.currentTimeMillis()) / 1000L)
                .toInt().coerceAtLeast(0)
            _timerState.value = _timerState.value.copy(remainingSeconds = remaining)
            if (remaining in 1..3 && remaining != lastBeep) {
                lastBeep = remaining
                playTone(ToneGenerator.TONE_PROP_BEEP, 150)
            }
            if (remaining <= 0) {
                _timerState.value = _timerState.value.copy(isRunning = false, remainingSeconds = 0)
                playEndTone()
                break
            }
            delay(200L) // Poll frequently enough for a smooth countdown display
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                toneGenerator?.startTone(toneType, durationMs)
            } catch (_: Exception) {}
        }
    }

    /** Two-beep sequence used when the rest timer expires – distinct from the per-second countdown beeps. */
    private fun playEndTone() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val shortBeepMs = 250
                // Wait slightly longer than the first beep so the two tones are clearly separate
                val delayBetweenBeepsMs = 380L
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, shortBeepMs)
                delay(delayBetweenBeepsMs)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 500)
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
            val entries = _exerciseSessions.value.flatMap { session ->
                session.sets.mapNotNull { set -> buildLogEntry(session, set) }
            }
            if (entries.isNotEmpty()) repository.insertLogEntries(entries)
            timerJob?.cancel()

            val durationMinutes = ((System.currentTimeMillis() - _workoutStartTime) / 60_000L)
                .toInt().coerceAtLeast(1)
            recordWorkoutCalories(durationMinutes)

            onFinished()
        }
    }

    private suspend fun recordWorkoutCalories(durationMinutes: Int) {
        val repo = foodRepository ?: return
        val userProfile = repo.userPreferences.userProfile.first()
        val sessions = _exerciseSessions.value
        val avgMet = if (sessions.isEmpty()) DEFAULT_MET
        else sessions.map { muscleGroupToMet(it.workoutExercise.exercise.muscleGroup) }
            .average().toFloat()
        // calories = MET × weight_kg × duration_hours
        val caloriesBurned = avgMet * userProfile.weightKg * (durationMinutes / 60f)
        repo.insertWorkoutCalories(
            WorkoutCalories(
                dateMillis = todayMillis(),
                workoutId = workoutId,
                caloriesBurned = caloriesBurned,
                durationMinutes = durationMinutes
            )
        )
    }

    /** Returns a MET (Metabolic Equivalent of Task) value for the given muscle group.
     *  Leg exercises involve the body's largest muscle groups and therefore have a
     *  higher energy cost than upper-body isolation work. */
    private fun muscleGroupToMet(muscleGroup: String): Float = when (muscleGroup.lowercase()) {
        "quads", "hamstrings", "glutes", "calves" -> 6.0f
        "back" -> 5.5f
        "chest", "shoulders" -> 4.5f
        else -> DEFAULT_MET // biceps, triceps, core, etc.
    }

    companion object {
        private const val DEFAULT_MET = 3.5f
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
    }
}

// Each factory below uses an unchecked cast instead of an isAssignableFrom guard:
// these factories are single-purpose and are always paired with the correct ViewModel
// class by their call sites, so a ClassCastException would be equally informative.

class ActiveWorkoutViewModelFactory(
    private val repository: FitTrackRepository,
    private val workoutId: Long,
    private val foodRepository: com.fittrack.app.data.repository.FoodRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ActiveWorkoutViewModel(repository, workoutId, foodRepository) as T
}
