package com.fittrack.app.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fittrack.app.MainActivity
import com.fittrack.app.R
import com.fittrack.app.receiver.RestTimerAlarmReceiver

class RestTimerNotificationHelper(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun showRunningTimer(endTimeMillis: Long, exerciseName: String?, setNumber: Int) {
        ensureChannel()
        val content = buildString {
            append("Satz ")
            append(setNumber)
            exerciseName?.takeIf { it.isNotBlank() }?.let {
                append(" • ")
                append(it)
            }
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Pausentimer läuft")
            .setContentText(content)
            .setContentIntent(mainActivityPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(endTimeMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .build()
        notificationManager.notify(RUNNING_TIMER_NOTIFICATION_ID, notification)
    }

    fun showFinishedNotification() {
        ensureChannel()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Pausentimer beendet")
            .setContentText("Nächsten Satz starten")
            .setContentIntent(mainActivityPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(FINISHED_TIMER_NOTIFICATION_ID, notification)
    }

    fun cancelRunningTimer() {
        notificationManager.cancel(RUNNING_TIMER_NOTIFICATION_ID)
    }

    fun scheduleCompletionAlarm(endTimeMillis: Long, timerVolumePercent: Int) {
        val pendingIntent = alarmPendingIntent(timerVolumePercent = timerVolumePercent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTimeMillis, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTimeMillis, pendingIntent)
    }

    fun cancelCompletionAlarm() {
        alarmManager.cancel(alarmPendingIntent())
    }

    private fun alarmPendingIntent(timerVolumePercent: Int? = null): PendingIntent {
        val intent = Intent(appContext, RestTimerAlarmReceiver::class.java).apply {
            timerVolumePercent?.let {
                putExtra(EXTRA_TIMER_VOLUME_PERCENT, it.coerceIn(0, 100))
            }
        }
        return PendingIntent.getBroadcast(
            appContext,
            TIMER_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun mainActivityPendingIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            MAIN_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rest timer",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Benachrichtigungen für den Pausentimer"
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_TIMER_VOLUME_PERCENT = "extra_timer_volume_percent"
        private const val CHANNEL_ID = "rest_timer_channel"
        private const val RUNNING_TIMER_NOTIFICATION_ID = 3001
        private const val FINISHED_TIMER_NOTIFICATION_ID = 3002
        private const val TIMER_ALARM_REQUEST_CODE = 4101
        private const val MAIN_ACTIVITY_REQUEST_CODE = 4102
    }
}
