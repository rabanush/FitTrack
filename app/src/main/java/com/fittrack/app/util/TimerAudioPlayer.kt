package com.fittrack.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class TimerAudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ── Public API ──────────────────────────────────────────────────────────

    suspend fun playCountdownBeep(volumePercent: Int) {
        playTone(ToneGenerator.TONE_PROP_BEEP, 150, volumePercent)
    }

    suspend fun playEndSequence(volumePercent: Int) {
        withAudioFocus {
            val ctx = setupToneContext(volumePercent) ?: return@withAudioFocus
            try {
                ctx.toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
                delay(380L)
                ctx.toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 500)
                delay(560L)
            } finally {
                ctx.release()
            }
        }
    }

    /** Blocking variant used from a plain thread (e.g. inside a BroadcastReceiver). */
    fun playEndSequenceBlocking(volumePercent: Int) {
        withAudioFocusBlocking {
            val ctx = setupToneContext(volumePercent) ?: return@withAudioFocusBlocking
            try {
                ctx.toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
                Thread.sleep(380L)
                ctx.toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 500)
                Thread.sleep(560L)
            } finally {
                ctx.release()
            }
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private suspend fun playTone(toneType: Int, durationMs: Int, volumePercent: Int) {
        withAudioFocus {
            val ctx = setupToneContext(volumePercent) ?: return@withAudioFocus
            try {
                ctx.toneGenerator.startTone(toneType, durationMs)
                delay((durationMs + 80).toLong())
            } finally {
                ctx.release()
            }
        }
    }

    /**
     * Captures everything needed to play a tone at the desired volume and restore
     * the stream state afterwards.
     */
    private inner class ToneContext(
        val toneGenerator: ToneGenerator,
        private val stream: Int,
        private val originalVolume: Int,
        private val targetVolume: Int
    ) {
        fun release() {
            runCatching { toneGenerator.release() }
            if (targetVolume > originalVolume) {
                audioManager.setStreamVolume(stream, originalVolume, 0)
            }
        }
    }

    /** Returns null if ToneGenerator creation fails (audio subsystem unavailable). */
    private fun setupToneContext(volumePercent: Int): ToneContext? {
        val stream = preferredStream()
        val toneVolume = volumePercent.coerceIn(0, 100)
        val originalVolume = audioManager.getStreamVolume(stream)
        val maxVolume = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        val targetVolume = ((maxVolume * (toneVolume / 100f)).roundToInt()).coerceIn(1, maxVolume)
        val toneGenerator = runCatching { ToneGenerator(stream, toneVolume) }.getOrNull() ?: return null
        if (targetVolume > originalVolume) {
            audioManager.setStreamVolume(stream, targetVolume, 0)
        }
        return ToneContext(toneGenerator, stream, originalVolume, targetVolume)
    }

    private fun preferredStream(): Int =
        if (isBluetoothOutputConnected()) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM

    private fun isBluetoothOutputConnected(): Boolean {
        val bluetoothTypes = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER
        )
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in bluetoothTypes }
    }

    private fun buildAudioFocusRequest(): AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .build()

    private suspend fun withAudioFocus(block: suspend () -> Unit) {
        val focusRequest = buildAudioFocusRequest()
        val focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        try {
            block()
        } finally {
            if (focusGranted) audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun withAudioFocusBlocking(block: () -> Unit) {
        val focusRequest = buildAudioFocusRequest()
        val focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        try {
            block()
        } finally {
            if (focusGranted) audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }
}
