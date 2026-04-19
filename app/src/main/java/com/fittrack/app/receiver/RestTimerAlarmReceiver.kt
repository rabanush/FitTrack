package com.fittrack.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.fittrack.app.data.preferences.ActiveWorkoutSessionPreferences
import com.fittrack.app.util.RestTimerNotificationHelper
import com.fittrack.app.util.TimerAudioPlayer
import java.util.concurrent.Executors
import kotlin.math.abs

class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val volume = intent.getIntExtra(RestTimerNotificationHelper.EXTRA_TIMER_VOLUME_PERCENT, 100)
            .coerceIn(0, 100)
        val workoutId = intent.getLongExtra(RestTimerNotificationHelper.EXTRA_WORKOUT_ID, -1L)
            .takeIf { it > 0L }
        val timerEndTimeMillis = intent.getLongExtra(
            RestTimerNotificationHelper.EXTRA_TIMER_END_TIME_MILLIS,
            0L
        )
        val wakeLock = acquireWakeLock(appContext)
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                if (isCurrentTimerAlarm(appContext, workoutId, timerEndTimeMillis)) {
                    val preferences = ActiveWorkoutSessionPreferences(appContext)
                    val notifications = RestTimerNotificationHelper(appContext)
                    notifications.cancelRunningTimer()
                    runCatching { TimerAudioPlayer(appContext).playEndSequenceBlocking(volume) }
                    notifications.showFinishedNotification(workoutId)
                    preferences.clearTimerState()
                }
            } finally {
                runCatching {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Failed to release rest-timer wake lock", error)
                }
                pendingResult.finish()
                executor.shutdown()
            }
        }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun isCurrentTimerAlarm(context: Context, workoutId: Long?, alarmEndTimeMillis: Long): Boolean {
        val session = ActiveWorkoutSessionPreferences(context).getSession() ?: return false
        if (workoutId != null && session.workoutId != workoutId) return false
        if (alarmEndTimeMillis <= 0L) return false
        return abs(session.timerEndTimeMillis - alarmEndTimeMillis) <= END_TIME_TOLERANCE_MS
    }

    companion object {
        private const val WAKE_LOCK_TAG = "fittrack:rest_timer_alarm"
        private const val END_TONE_TOTAL_DURATION_MS =
            (TimerAudioPlayer.END_SEQUENCE_REPEAT_COUNT - 1L) * TimerAudioPlayer.END_SEQUENCE_STEP_DURATION_MS +
                TimerAudioPlayer.END_SEQUENCE_TONE_DURATION_MS.toLong()
        private const val WAKE_LOCK_TIMEOUT_MS = END_TONE_TOTAL_DURATION_MS + 4_000L
        // 1.5 s covers AlarmManager dispatch drift, background scheduling jitter, and second-based countdown rounding.
        private const val END_TIME_TOLERANCE_MS = 1_500L
        private const val TAG = "RestTimerAlarmReceiver"
    }
}
