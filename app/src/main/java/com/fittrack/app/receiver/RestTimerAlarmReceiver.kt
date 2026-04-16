package com.fittrack.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fittrack.app.util.RestTimerNotificationHelper
import com.fittrack.app.util.TimerAudioPlayer

class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val volume = intent.getIntExtra(RestTimerNotificationHelper.EXTRA_TIMER_VOLUME_PERCENT, 100)
            .coerceIn(0, 100)
        Thread {
            runCatching {
                TimerAudioPlayer(context).playEndSequence(volume)
            }
        }.start()
        RestTimerNotificationHelper(context).showFinishedNotification()
    }
}
