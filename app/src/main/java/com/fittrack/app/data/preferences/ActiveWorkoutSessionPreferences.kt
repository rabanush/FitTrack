package com.fittrack.app.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveWorkoutSession(
    val workoutId: Long,
    val workoutStartTimeMillis: Long,
    val timerEndTimeMillis: Long = 0L,
    val timerTotalSeconds: Int = 0,
    val timerExerciseIndex: Int = -1,
    val timerSetNumber: Int = -1
)

class ActiveWorkoutSessionPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _changeEvents = MutableStateFlow(0L)
    val changeEvents: StateFlow<Long> = _changeEvents.asStateFlow()

    fun getSession(): ActiveWorkoutSession? {
        val workoutId = prefs.getLong(KEY_WORKOUT_ID, NO_WORKOUT_ID)
        if (workoutId == NO_WORKOUT_ID) return null
        return ActiveWorkoutSession(
            workoutId = workoutId,
            workoutStartTimeMillis = prefs.getLong(KEY_WORKOUT_START_TIME, System.currentTimeMillis()),
            timerEndTimeMillis = prefs.getLong(KEY_TIMER_END_TIME, 0L),
            timerTotalSeconds = prefs.getInt(KEY_TIMER_TOTAL_SECONDS, 0),
            timerExerciseIndex = prefs.getInt(KEY_TIMER_EXERCISE_INDEX, -1),
            timerSetNumber = prefs.getInt(KEY_TIMER_SET_NUMBER, -1)
        )
    }

    fun startSession(workoutId: Long, workoutStartTimeMillis: Long) {
        val existingWorkoutId = prefs.getLong(KEY_WORKOUT_ID, NO_WORKOUT_ID)
        val existingStartTime = prefs.getLong(KEY_WORKOUT_START_TIME, NO_WORKOUT_START_TIME)
        val isSameSession = existingWorkoutId == workoutId && existingStartTime == workoutStartTimeMillis
        prefs.edit()
            .putLong(KEY_WORKOUT_ID, workoutId)
            .putLong(KEY_WORKOUT_START_TIME, workoutStartTimeMillis)
            .remove(KEY_TIMER_END_TIME)
            .remove(KEY_TIMER_TOTAL_SECONDS)
            .remove(KEY_TIMER_EXERCISE_INDEX)
            .remove(KEY_TIMER_SET_NUMBER)
            .apply {
                if (!isSameSession) {
                    remove(KEY_EXERCISE_SESSIONS_STATE)
                }
            }
            .apply()
        notifyChange()
    }

    fun saveTimerState(endTimeMillis: Long, totalSeconds: Int, exerciseIndex: Int, setNumber: Int) {
        prefs.edit()
            .putLong(KEY_TIMER_END_TIME, endTimeMillis)
            .putInt(KEY_TIMER_TOTAL_SECONDS, totalSeconds)
            .putInt(KEY_TIMER_EXERCISE_INDEX, exerciseIndex)
            .putInt(KEY_TIMER_SET_NUMBER, setNumber)
            .apply()
        notifyChange()
    }

    fun clearTimerState() {
        prefs.edit()
            .remove(KEY_TIMER_END_TIME)
            .remove(KEY_TIMER_TOTAL_SECONDS)
            .remove(KEY_TIMER_EXERCISE_INDEX)
            .remove(KEY_TIMER_SET_NUMBER)
            .apply()
        notifyChange()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        notifyChange()
    }

    fun saveExerciseSessionsState(stateJson: String) {
        prefs.edit()
            .putString(KEY_EXERCISE_SESSIONS_STATE, stateJson)
            .apply()
        notifyChange()
    }

    fun getExerciseSessionsState(): String? = prefs.getString(KEY_EXERCISE_SESSIONS_STATE, null)

    fun clearExerciseSessionsState() {
        prefs.edit()
            .remove(KEY_EXERCISE_SESSIONS_STATE)
            .apply()
        notifyChange()
    }

    fun restoreSession(session: ActiveWorkoutSession, exerciseSessionsState: String?) {
        val editor = prefs.edit()
            .putLong(KEY_WORKOUT_ID, session.workoutId)
            .putLong(KEY_WORKOUT_START_TIME, session.workoutStartTimeMillis)
            .putLong(KEY_TIMER_END_TIME, session.timerEndTimeMillis)
            .putInt(KEY_TIMER_TOTAL_SECONDS, session.timerTotalSeconds)
            .putInt(KEY_TIMER_EXERCISE_INDEX, session.timerExerciseIndex)
            .putInt(KEY_TIMER_SET_NUMBER, session.timerSetNumber)
        if (exerciseSessionsState.isNullOrBlank()) {
            editor.remove(KEY_EXERCISE_SESSIONS_STATE)
        } else {
            editor.putString(KEY_EXERCISE_SESSIONS_STATE, exerciseSessionsState)
        }
        editor.apply()
        notifyChange()
    }

    private fun notifyChange() {
        _changeEvents.value = _changeEvents.value + 1
    }

    companion object {
        private const val PREFS_NAME = "active_workout_session"
        private const val KEY_WORKOUT_ID = "workout_id"
        private const val KEY_WORKOUT_START_TIME = "workout_start_time"
        private const val KEY_TIMER_END_TIME = "timer_end_time"
        private const val KEY_TIMER_TOTAL_SECONDS = "timer_total_seconds"
        private const val KEY_TIMER_EXERCISE_INDEX = "timer_exercise_index"
        private const val KEY_TIMER_SET_NUMBER = "timer_set_number"
        private const val KEY_EXERCISE_SESSIONS_STATE = "exercise_sessions_state"
        private const val NO_WORKOUT_ID = -1L
        private const val NO_WORKOUT_START_TIME = -1L
    }
}
