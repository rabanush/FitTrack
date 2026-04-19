package com.fittrack.app.viewmodel

import android.content.Context
import androidx.lifecycle.*
import androidx.compose.runtime.Immutable
import com.fittrack.app.data.model.*
import com.fittrack.app.data.preferences.ActiveWorkoutSession
import com.fittrack.app.data.preferences.ActiveWorkoutSessionPreferences
import com.fittrack.app.data.preferences.UserPreferences
import com.fittrack.app.data.repository.FitTrackRepository
import com.fittrack.app.util.RestTimerNotificationHelper
import com.fittrack.app.util.TimerAudioPlayer
import com.fittrack.app.util.displayName
import com.fittrack.app.util.todayMillis
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
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
    private val appContext: Context,
    private val activeWorkoutSessionPreferences: ActiveWorkoutSessionPreferences,
    private val userPreferences: UserPreferences,
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

    private var workoutStartTimeMillis = System.currentTimeMillis()
    private var timerJob: Job? = null
    private var workoutElapsedJob: Job? = null
    private val timerAudioPlayer = TimerAudioPlayer(appContext)
    private val timerNotificationHelper = RestTimerNotificationHelper(appContext)
    private val _timerVolumePercent = MutableStateFlow(100)
    private val _workoutElapsedSeconds = MutableStateFlow(0)
    val workoutElapsedSeconds: StateFlow<Int> = _workoutElapsedSeconds
    private var restoredProgressByWorkoutExerciseId = emptyMap<Long, PersistedExerciseSessionState>()

    init {
        val restoredSession = activeWorkoutSessionPreferences.getSession()
            ?.takeIf { it.workoutId == workoutId }
        workoutStartTimeMillis = restoredSession?.workoutStartTimeMillis ?: System.currentTimeMillis()
        activeWorkoutSessionPreferences.startSession(workoutId, workoutStartTimeMillis)
        restoredProgressByWorkoutExerciseId = if (restoredSession != null) {
            restoreProgressByWorkoutExerciseId()
        } else {
            activeWorkoutSessionPreferences.clearExerciseSessionsState()
            emptyMap()
        }

        viewModelScope.launch {
            userPreferences.userProfile.collect { profile ->
                _timerVolumePercent.value = profile.timerVolumePercent.coerceIn(0, 100)
            }
        }

        viewModelScope.launch {
            _workout.value = repository.getWorkoutById(workoutId)
            repository.getWorkoutExercisesWithExercise(workoutId).collect { exercises ->
                if (exercises.isEmpty()) {
                    _exerciseSessions.value = emptyList()
                    return@collect
                }
                // Render immediately without waiting for previous-log lookup to finish.
                _exerciseSessions.value = exercises
                    .map { buildSessionForExercise(it, emptyList()) }
                    .let(::applyPersistedProgress)

                val exerciseIds = exercises.map { it.exercise.id }

                val previousLogsMap = withContext(Dispatchers.IO) {
                    repository.getPreviousLogEntriesForExercises(exerciseIds, workoutStartTimeMillis)
                        .groupBy { it.exerciseId }
                }
                _exerciseSessions.value = exercises
                    .map { buildSessionForExercise(it, previousLogsMap[it.exercise.id] ?: emptyList()) }
                    .let(::applyPersistedProgress)
            }
        }

        restoredSession?.let { restoreTimerFromSession(it) }
        startWorkoutElapsedTicker()
    }

    // Applies a transformation to the session at the given index.
    private fun updateSessionAt(exerciseIndex: Int, transform: (ExerciseSessionData) -> ExerciseSessionData) {
        val sessions = _exerciseSessions.value.toMutableList()
        if (exerciseIndex >= sessions.size) return
        sessions[exerciseIndex] = transform(sessions[exerciseIndex])
        _exerciseSessions.value = sessions
        persistCurrentProgress()
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

    private fun buildSessionForExercise(weWithEx: WorkoutExerciseWithExercise, previousLogs: List<LogEntry>): ExerciseSessionData {
        val sortedLogs = previousLogs.sortedBy { it.setNumber }
        val initialSets = (1..weWithEx.workoutExercise.setCount).map { buildInitialSet(it, sortedLogs) }
        return ExerciseSessionData(workoutExercise = weWithEx, sets = initialSets)
    }

    private fun buildLogEntry(session: ExerciseSessionData, set: SetData): LogEntry? {
        if (!set.isCompleted && set.weight.isEmpty() && set.reps.isEmpty()) return null
        return LogEntry(
            exerciseId = session.workoutExercise.exercise.id,
            workoutId = workoutId,
            date = workoutStartTimeMillis,
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
        val endTimeMillis = System.currentTimeMillis() + (seconds * 1000L)
        _timerState.value = TimerState(
            isRunning = true,
            remainingSeconds = seconds,
            totalSeconds = seconds,
            exerciseIndex = exerciseIndex,
            setNumber = setNumber,
            endTimeMillis = endTimeMillis
        )
        saveTimerStateForRestore(_timerState.value)
        val exerciseName = _exerciseSessions.value
            .getOrNull(exerciseIndex)
            ?.workoutExercise
            ?.exercise
            ?.displayName()
        timerNotificationHelper.showRunningTimer(endTimeMillis, exerciseName, setNumber, workoutId)
        timerNotificationHelper.scheduleCompletionAlarm(endTimeMillis, _timerVolumePercent.value, workoutId)
        timerJob = viewModelScope.launch { tickTimer() }
    }

    private suspend fun tickTimer() {
        while (true) {
            val state = _timerState.value
            val now = System.currentTimeMillis()
            val remaining = ((state.endTimeMillis - now) / 1000L)
                .toInt().coerceAtLeast(0)
            _timerState.value = state.copy(remainingSeconds = remaining)
            if (remaining <= 0) {
                _timerState.value = state.copy(isRunning = false, remainingSeconds = 0)
                timerNotificationHelper.cancelRunningTimer()
                activeWorkoutSessionPreferences.clearTimerState()
                val timeSinceEndMs = now - state.endTimeMillis
                if (timeSinceEndMs < LOCAL_END_TONE_WINDOW_MS) {
                    timerNotificationHelper.cancelCompletionAlarm()
                    playEndTone()
                    timerNotificationHelper.showFinishedNotification(workoutId)
                }
                break
            }
            delay(200L) // Poll frequently enough for a smooth countdown display
        }
    }

    /** End-sequence alarm used when the rest timer expires. */
    private fun playEndTone() {
        val volume = _timerVolumePercent.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                timerAudioPlayer.playEndSequence(volume)
            } catch (_: Exception) {}
        }
    }

    fun skipTimer() {
        timerJob?.cancel()
        timerNotificationHelper.cancelRunningTimer()
        timerNotificationHelper.cancelCompletionAlarm()
        activeWorkoutSessionPreferences.clearTimerState()
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
            saveTimerStateForRestore(_timerState.value)
            val exerciseName = _exerciseSessions.value
                .getOrNull(current.exerciseIndex)
                ?.workoutExercise
                ?.exercise
                ?.displayName()
            timerNotificationHelper.showRunningTimer(newEndTime, exerciseName, current.setNumber, workoutId)
            timerNotificationHelper.scheduleCompletionAlarm(newEndTime, _timerVolumePercent.value, workoutId)
        }
    }

    fun finishWorkout(onFinished: () -> Unit) {
        viewModelScope.launch {
            val entries = _exerciseSessions.value.flatMap { session ->
                session.sets.mapNotNull { set -> buildLogEntry(session, set) }
            }
            if (entries.isNotEmpty()) repository.insertLogEntries(entries)
            timerJob?.cancel()
            timerNotificationHelper.cancelRunningTimer()
            timerNotificationHelper.cancelCompletionAlarm()
            activeWorkoutSessionPreferences.clearSession()

            val durationMinutes = ((System.currentTimeMillis() - workoutStartTimeMillis) / 60_000L)
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
        private const val LOCAL_END_TONE_WINDOW_MS = 1_500L
        private val GSON = Gson()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        workoutElapsedJob?.cancel()
    }

    private fun restoreTimerFromSession(session: ActiveWorkoutSession) {
        if (session.timerEndTimeMillis <= 0L || session.timerTotalSeconds <= 0) return
        val now = System.currentTimeMillis()
        val remainingSeconds = ((session.timerEndTimeMillis - now) / 1000L).toInt().coerceAtLeast(0)
        if (remainingSeconds <= 0) {
            activeWorkoutSessionPreferences.clearTimerState()
            return
        }
        _timerState.value = TimerState(
            isRunning = true,
            remainingSeconds = remainingSeconds,
            totalSeconds = session.timerTotalSeconds,
            exerciseIndex = session.timerExerciseIndex,
            setNumber = session.timerSetNumber,
            endTimeMillis = session.timerEndTimeMillis
        )
        timerJob?.cancel()
        timerJob = viewModelScope.launch { tickTimer() }
        val exerciseName = _exerciseSessions.value
            .getOrNull(session.timerExerciseIndex)
            ?.workoutExercise
            ?.exercise
            ?.displayName()
        timerNotificationHelper.showRunningTimer(
            endTimeMillis = session.timerEndTimeMillis,
            exerciseName = exerciseName,
            setNumber = session.timerSetNumber,
            workoutId = workoutId
        )
    }

    private fun saveTimerStateForRestore(state: TimerState) {
        activeWorkoutSessionPreferences.saveTimerState(
            endTimeMillis = state.endTimeMillis,
            totalSeconds = state.totalSeconds,
            exerciseIndex = state.exerciseIndex,
            setNumber = state.setNumber
        )
    }

    private fun startWorkoutElapsedTicker() {
        workoutElapsedJob?.cancel()
        workoutElapsedJob = viewModelScope.launch {
            while (isActive) {
                _workoutElapsedSeconds.value =
                    ((System.currentTimeMillis() - workoutStartTimeMillis) / 1000L).toInt().coerceAtLeast(0)
                delay(1_000L)
            }
        }
    }

    private fun applyPersistedProgress(freshSessions: List<ExerciseSessionData>): List<ExerciseSessionData> {
        if (restoredProgressByWorkoutExerciseId.isEmpty()) return freshSessions
        return freshSessions.map { session ->
            val persistedSets = restoredProgressByWorkoutExerciseId[session.workoutExercise.workoutExercise.id]
                ?: return@map session
            val maxSize = maxOf(session.sets.size, persistedSets.sets.size)
            val restoredSets = (0 until maxSize).mapNotNull { index ->
                val persisted = persistedSets.sets.getOrNull(index)
                val base = session.sets.getOrNull(index)
                when {
                    persisted != null && base != null -> persisted.toSetData(base)
                    persisted != null -> persisted.toSetData()
                    else -> base
                }
            }
            session.copy(sets = restoredSets)
        }
    }

    private fun persistCurrentProgress() {
        val persisted = _exerciseSessions.value.map { session ->
            PersistedExerciseSessionState(
                workoutExerciseId = session.workoutExercise.workoutExercise.id,
                sets = session.sets.map { it.toPersisted() }
            )
        }
        restoredProgressByWorkoutExerciseId = persisted.associateBy { it.workoutExerciseId }
        activeWorkoutSessionPreferences.saveExerciseSessionsState(GSON.toJson(persisted))
    }

    private fun restoreProgressByWorkoutExerciseId(): Map<Long, PersistedExerciseSessionState> {
        val stateJson = activeWorkoutSessionPreferences.getExerciseSessionsState() ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<List<PersistedExerciseSessionState>>() {}.type
            val list: List<PersistedExerciseSessionState> = GSON.fromJson(stateJson, type) ?: emptyList()
            list.associateBy { it.workoutExerciseId }
        }.getOrDefault(emptyMap())
    }

    private fun SetData.toPersisted() = PersistedSetState(
        setNumber = setNumber,
        weight = weight,
        reps = reps,
        rir = rir,
        isCompleted = isCompleted,
        prevWeight = prevWeight,
        prevReps = prevReps,
        prevRir = prevRir
    )

    private fun PersistedSetState.toSetData(base: SetData? = null): SetData =
        SetData(
            setNumber = setNumber,
            weight = weight,
            reps = reps,
            rir = rir,
            isCompleted = isCompleted,
            prevWeight = base?.prevWeight ?: prevWeight,
            prevReps = base?.prevReps ?: prevReps,
            prevRir = base?.prevRir ?: prevRir
        )
}

private data class PersistedExerciseSessionState(
    val workoutExerciseId: Long,
    val sets: List<PersistedSetState>
)

private data class PersistedSetState(
    val setNumber: Int,
    val weight: String,
    val reps: String,
    val rir: String,
    val isCompleted: Boolean,
    val prevWeight: String,
    val prevReps: String,
    val prevRir: String
)

// Each factory below uses an unchecked cast instead of an isAssignableFrom guard:
// these factories are single-purpose and are always paired with the correct ViewModel
// class by their call sites, so a ClassCastException would be equally informative.

class ActiveWorkoutViewModelFactory(
    private val repository: FitTrackRepository,
    private val workoutId: Long,
    private val appContext: Context,
    private val activeWorkoutSessionPreferences: ActiveWorkoutSessionPreferences,
    private val userPreferences: UserPreferences,
    private val foodRepository: com.fittrack.app.data.repository.FoodRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ActiveWorkoutViewModel(
            repository,
            workoutId,
            appContext,
            activeWorkoutSessionPreferences,
            userPreferences,
            foodRepository
        ) as T
}
