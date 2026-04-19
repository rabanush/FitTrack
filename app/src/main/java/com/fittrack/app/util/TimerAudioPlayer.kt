package com.fittrack.app.util

import android.content.Context
import android.media.AudioAttributes
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
        withAudioFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) {
            val ctx = setupToneContext(volumePercent) ?: return@withAudioFocus
            try {
                repeat(END_SEQUENCE_REPEAT_COUNT) {
                    ctx.toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, END_SEQUENCE_TONE_DURATION_MS)
                    delay(END_SEQUENCE_STEP_DURATION_MS)
                }
            } finally {
                ctx.release()
            }
        }
    }

    /** Blocking variant used from a plain thread (e.g. inside a BroadcastReceiver). */
    fun playEndSequenceBlocking(volumePercent: Int) {
        withAudioFocusBlocking(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) {
            val ctx = setupToneContext(volumePercent) ?: return@withAudioFocusBlocking
            try {
                repeat(END_SEQUENCE_REPEAT_COUNT) {
                    ctx.toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, END_SEQUENCE_TONE_DURATION_MS)
                    Thread.sleep(END_SEQUENCE_STEP_DURATION_MS)
                }
            } finally {
                ctx.release()
            }
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private suspend fun playTone(toneType: Int, durationMs: Int, volumePercent: Int) {
        withAudioFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) {
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

    /**
     * Creates a [ToneContext] for the current output stream at the requested volume.
     * If [volumePercent] is higher than the current stream level, the stream is temporarily
     * boosted so the alert is audible over background music; [ToneContext.release] restores
     * the original level afterwards.
     * Returns null if [ToneGenerator] creation fails (e.g. audio subsystem unavailable).
     */
    private fun setupToneContext(volumePercent: Int): ToneContext? {
        val stream = preferredStream()
        val toneVolume = toEffectiveAudioPercent(volumePercent)
        val originalVolume = audioManager.getStreamVolume(stream)
        val maxVolume = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        val targetVolume = ((maxVolume * (toneVolume / 100f)).roundToInt()).coerceIn(1, maxVolume)
        val toneGenerator = runCatching { ToneGenerator(stream, toneVolume) }.getOrNull() ?: return null
        if (targetVolume > originalVolume) {
            audioManager.setStreamVolume(stream, targetVolume, 0)
        }
        return ToneContext(toneGenerator, stream, originalVolume, targetVolume)
    }

    private fun preferredStream(): Int = AudioManager.STREAM_ALARM

    /** Builds an [AudioFocusRequest] for the requested [focusGain] mode. */
    private fun buildAudioFocusRequest(focusGain: Int): AudioFocusRequest =
        AudioFocusRequest.Builder(focusGain)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .build()

    private suspend fun withAudioFocus(focusGain: Int, block: suspend () -> Unit) {
        val focusRequest = buildAudioFocusRequest(focusGain)
        val focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        try {
            block()
        } finally {
            if (focusGranted) audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun withAudioFocusBlocking(focusGain: Int, block: () -> Unit) {
        val focusRequest = buildAudioFocusRequest(focusGain)
        val focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        try {
            block()
        } finally {
            if (focusGranted) audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun toEffectiveAudioPercent(displayPercent: Int): Int =
        // Keep the same perceived loudness when the default slider position moves from 100% to 50%.
        (displayPercent.coerceIn(0, 100) * 2).coerceIn(0, 100)

    companion object {
        const val END_SEQUENCE_REPEAT_COUNT = 4
        const val END_SEQUENCE_TONE_DURATION_MS = 450
        const val END_SEQUENCE_STEP_DURATION_MS = 850L
    }
}
