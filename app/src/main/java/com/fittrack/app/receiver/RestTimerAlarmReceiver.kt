package com.fittrack.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fittrack.app.util.RestTimerNotificationHelper
import com.fittrack.app.util.TimerAudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val volume = intent.getIntExtra(RestTimerNotificationHelper.EXTRA_TIMER_VOLUME_PERCENT, 100)
            .coerceIn(0, 100)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching { TimerAudioPlayer(context).playEndSequence(volume) }
                RestTimerNotificationHelper(context).showFinishedNotification()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
