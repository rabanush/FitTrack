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

    suspend fun playCountdownBeep(volumePercent: Int) {
        playTone(ToneGenerator.TONE_PROP_BEEP, 150, volumePercent)
    }

    suspend fun playEndSequence(volumePercent: Int) {
        withAudioFocus {
            val stream = preferredStream()
            val toneVolume = volumePercent.coerceIn(0, 100)
            val originalVolume = audioManager.getStreamVolume(stream)
            val maxVolume = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
            val targetVolume = ((maxVolume * (toneVolume / 100f)).roundToInt()).coerceIn(1, maxVolume)
            val toneGenerator = runCatching { ToneGenerator(stream, toneVolume) }.getOrNull() ?: return@withAudioFocus

            try {
                if (targetVolume > originalVolume) {
                    audioManager.setStreamVolume(stream, targetVolume, 0)
                }
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
                delay(380L)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 500)
                delay(560L)
            } finally {
                runCatching { toneGenerator.release() }
                if (targetVolume > originalVolume) {
                    audioManager.setStreamVolume(stream, originalVolume, 0)
                }
            }
        }
    }

    fun playEndSequenceBlocking(volumePercent: Int) {
        withAudioFocusBlocking {
            val stream = preferredStream()
            val toneVolume = volumePercent.coerceIn(0, 100)
            val originalVolume = audioManager.getStreamVolume(stream)
            val maxVolume = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
            val targetVolume = ((maxVolume * (toneVolume / 100f)).roundToInt()).coerceIn(1, maxVolume)
            val toneGenerator = runCatching { ToneGenerator(stream, toneVolume) }.getOrNull() ?: return@withAudioFocusBlocking

            try {
                if (targetVolume > originalVolume) {
                    audioManager.setStreamVolume(stream, targetVolume, 0)
                }
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
                Thread.sleep(380L)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 500)
                Thread.sleep(560L)
            } finally {
                runCatching { toneGenerator.release() }
                if (targetVolume > originalVolume) {
                    audioManager.setStreamVolume(stream, originalVolume, 0)
                }
            }
        }
    }

    private suspend fun playTone(toneType: Int, durationMs: Int, volumePercent: Int) {
        withAudioFocus {
            val stream = preferredStream()
            val toneVolume = volumePercent.coerceIn(0, 100)
            val originalVolume = audioManager.getStreamVolume(stream)
            val maxVolume = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
            val targetVolume = ((maxVolume * (toneVolume / 100f)).roundToInt()).coerceIn(1, maxVolume)
            val toneGenerator = runCatching { ToneGenerator(stream, toneVolume) }.getOrNull() ?: return@withAudioFocus

            try {
                if (targetVolume > originalVolume) {
                    audioManager.setStreamVolume(stream, targetVolume, 0)
                }
                toneGenerator.startTone(toneType, durationMs)
                delay((durationMs + 80).toLong())
            } finally {
                runCatching { toneGenerator.release() }
                if (targetVolume > originalVolume) {
                    audioManager.setStreamVolume(stream, originalVolume, 0)
                }
            }
        }
    }

    private fun preferredStream(): Int {
        return if (isBluetoothOutputConnected()) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM
    }

    private fun isBluetoothOutputConnected(): Boolean {
        val bluetoothTypes = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER
        )
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in bluetoothTypes }
    }

    private suspend fun withAudioFocus(block: suspend () -> Unit) {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .build()

        val focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        try {
            block()
        } finally {
            if (focusGranted) {
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
        }
    }

    private fun withAudioFocusBlocking(block: () -> Unit) {
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .build()

        val focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        try {
            block()
        } finally {
            if (focusGranted) {
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
        }
    }
}
