package com.fittrack.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fittrack.app.util.RestTimerNotificationHelper
import com.fittrack.app.util.TimerAudioPlayer
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val volume = intent.getIntExtra(RestTimerNotificationHelper.EXTRA_TIMER_VOLUME_PERCENT, 100)
            .coerceIn(0, 100)
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                runBlocking {
                    runCatching { TimerAudioPlayer(context).playEndSequence(volume) }
                }
                RestTimerNotificationHelper(context).showFinishedNotification()
            } finally {
                pendingResult.finish()
                executor.shutdown()
            }
        }
    }
}
