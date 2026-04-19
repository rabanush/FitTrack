package com.fittrack.app.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.fittrack.app.MainActivity
import com.fittrack.app.R
import com.fittrack.app.receiver.RestTimerAlarmReceiver

class RestTimerNotificationHelper(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun showRunningTimer(endTimeMillis: Long, exerciseName: String?, setNumber: Int, workoutId: Long) {
        ensureChannel()
        notificationManager.cancel(FINISHED_TIMER_NOTIFICATION_ID)
        val content = buildString {
            append(appContext.getString(R.string.timer_set_label, setNumber))
            exerciseName?.takeIf { it.isNotBlank() }?.let {
                append(" • ")
                append(it)
            }
        }
        val remoteViews = timerRemoteViews(endTimeMillis = endTimeMillis, content = content)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(appContext.getString(R.string.timer_running_title))
            .setContentText(content)
            .setContentIntent(mainActivityPendingIntent(workoutId))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .build()
        notificationManager.notify(RUNNING_TIMER_NOTIFICATION_ID, notification)
    }

    fun showFinishedNotification(workoutId: Long? = null) {
        ensureChannel()
        notificationManager.cancel(RUNNING_TIMER_NOTIFICATION_ID)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(appContext.getString(R.string.timer_finished_title))
            .setContentText(appContext.getString(R.string.timer_finished_message))
            .setContentIntent(mainActivityPendingIntent(workoutId))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(FINISHED_TIMER_NOTIFICATION_ID, notification)
    }

    fun cancelRunningTimer() {
        notificationManager.cancel(RUNNING_TIMER_NOTIFICATION_ID)
    }

    fun scheduleCompletionAlarm(endTimeMillis: Long, timerVolumePercent: Int, workoutId: Long) {
        val pendingIntent = alarmPendingIntent(
            timerVolumePercent = timerVolumePercent,
            workoutId = workoutId,
            timerEndTimeMillis = endTimeMillis
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTimeMillis, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTimeMillis, pendingIntent)
    }

    fun cancelCompletionAlarm() {
        alarmManager.cancel(alarmPendingIntent())
    }

    private fun alarmPendingIntent(
        timerVolumePercent: Int? = null,
        workoutId: Long? = null,
        timerEndTimeMillis: Long? = null
    ): PendingIntent {
        val intent = Intent(appContext, RestTimerAlarmReceiver::class.java).apply {
            timerVolumePercent?.let {
                putExtra(EXTRA_TIMER_VOLUME_PERCENT, it.coerceIn(0, 100))
            }
            workoutId?.let {
                putExtra(EXTRA_WORKOUT_ID, it)
            }
            timerEndTimeMillis?.let {
                putExtra(EXTRA_TIMER_END_TIME_MILLIS, it)
            }
        }
        return PendingIntent.getBroadcast(
            appContext,
            TIMER_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun mainActivityPendingIntent(workoutId: Long? = null): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            workoutId?.let {
                putExtra(EXTRA_WORKOUT_ID, it)
            }
        }
        return PendingIntent.getActivity(
            appContext,
            MAIN_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun timerRemoteViews(endTimeMillis: Long, content: String): RemoteViews {
        val baseElapsedRealtime = SystemClock.elapsedRealtime() +
            (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        return RemoteViews(appContext.packageName, R.layout.notification_rest_timer).apply {
            setTextViewText(R.id.timerTitle, appContext.getString(R.string.timer_running_title))
            setTextViewText(R.id.timerSubtitle, content)
            setTextColor(R.id.timerTitle, appContext.getColor(android.R.color.holo_red_light))
            setTextColor(R.id.timerSubtitle, appContext.getColor(android.R.color.white))
            setChronometer(R.id.timerChronometer, baseElapsedRealtime, null, true)
            setTextColor(R.id.timerChronometer, appContext.getColor(android.R.color.white))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Countdown mode is only supported from API 24; older versions show an increasing chronometer.
                setBoolean(R.id.timerChronometer, "setCountDown", true)
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.timer_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = appContext.getString(R.string.timer_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_TIMER_VOLUME_PERCENT = "extra_timer_volume_percent"
        const val EXTRA_WORKOUT_ID = "extra_workout_id"
        const val EXTRA_TIMER_END_TIME_MILLIS = "extra_timer_end_time_millis"
        private const val CHANNEL_ID = "rest_timer_channel"
        private const val RUNNING_TIMER_NOTIFICATION_ID = 3001
        private const val FINISHED_TIMER_NOTIFICATION_ID = 3002
        private const val TIMER_ALARM_REQUEST_CODE = 4101
        private const val MAIN_ACTIVITY_REQUEST_CODE = 4102
    }
}
