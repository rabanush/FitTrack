package com.fittrack.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fittrack.app.data.preferences.ActiveWorkoutSessionPreferences
import com.fittrack.app.util.RestTimerNotificationHelper

/**
 * Restores the rest-timer alarm after a device reboot.
 *
 * Android clears all [android.app.AlarmManager] alarms when the device powers off. Without this
 * receiver the alarm that triggers the end-of-timer sound and "start next set" notification
 * would never fire after reboot, even though the timer session is persisted in SharedPreferences.
 *
 * Behaviour on reboot:
 * - Timer still running  → re-show the countdown notification and re-schedule the exact alarm.
 * - Timer already expired → cancel any lingering countdown notification, show the "finished"
 *                           notification immediately and clear the timer state.
 * - No active timer       → nothing to do.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val preferences = ActiveWorkoutSessionPreferences(context)
        val session = preferences.getSession() ?: return
        if (session.timerEndTimeMillis <= 0L) return

        val notifications = RestTimerNotificationHelper(context)
        val now = System.currentTimeMillis()

        if (session.timerEndTimeMillis <= now) {
            // Timer expired while the device was off: fire the completion immediately.
            notifications.cancelRunningTimer()
            notifications.showFinishedNotification(session.workoutId)
            preferences.clearTimerState()
        } else {
            // Timer is still running: re-show the countdown and re-arm the alarm.
            notifications.showRunningTimer(
                endTimeMillis = session.timerEndTimeMillis,
                exerciseName = null,
                setNumber = session.timerSetNumber,
                workoutId = session.workoutId
            )
            runCatching {
                notifications.scheduleCompletionAlarm(
                    endTimeMillis = session.timerEndTimeMillis,
                    timerVolumePercent = session.timerVolumePercent,
                    workoutId = session.workoutId
                )
            }
        }
    }
}
