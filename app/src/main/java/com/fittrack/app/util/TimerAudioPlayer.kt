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

    /** Single short tick played during the last-seconds countdown (3, 2, 1). */
    suspend fun playTickBeep(volumePercent: Int) {
        withAudioFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) {
            val ctx = setupToneContext(volumePercent) ?: return@withAudioFocus
            try {
                ctx.toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, TICK_TONE_DURATION_MS)
                delay(TICK_TONE_DURATION_MS.toLong())
            } finally {
                ctx.release()
            }
        }
    }

    /** Blocking variant of [playTickBeep] used from plain threads (e.g. BroadcastReceiver). */
    fun playTickBeepBlocking(volumePercent: Int) {
        withAudioFocusBlocking(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) {
            val ctx = setupToneContext(volumePercent) ?: return@withAudioFocusBlocking
            try {
                ctx.toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, TICK_TONE_DURATION_MS)
                Thread.sleep(TICK_TONE_DURATION_MS.toLong())
            } finally {
                ctx.release()
            }
        }
    }

    suspend fun playEndSequence(volumePercent: Int) {
        withAudioFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) {
            val ctx = setupToneContext(volumePercent) ?: return@withAudioFocus
            try {
                repeat(END_SEQUENCE_REPEAT_COUNT) { index ->
                    ctx.toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, END_SEQUENCE_TONE_DURATION_MS)
                    delay(
                        if (index == END_SEQUENCE_REPEAT_COUNT - 1) END_SEQUENCE_TONE_DURATION_MS.toLong()
                        else END_SEQUENCE_STEP_DURATION_MS
                    )
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
                repeat(END_SEQUENCE_REPEAT_COUNT) { index ->
                    ctx.toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, END_SEQUENCE_TONE_DURATION_MS)
                    Thread.sleep(
                        if (index == END_SEQUENCE_REPEAT_COUNT - 1) END_SEQUENCE_TONE_DURATION_MS.toLong()
                        else END_SEQUENCE_STEP_DURATION_MS
                    )
                }
            } finally {
                ctx.release()
            }
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

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

    private fun preferredStream(): Int = AudioManager.STREAM_MUSIC

    /** Builds an [AudioFocusRequest] for the requested [focusGain] mode. */
    private fun buildAudioFocusRequest(focusGain: Int): AudioFocusRequest =
        AudioFocusRequest.Builder(focusGain)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
        // 2 prominent beeps at the end of the rest timer ("2 mal").
        const val END_SEQUENCE_REPEAT_COUNT = 2
        const val END_SEQUENCE_TONE_DURATION_MS = 600
        const val END_SEQUENCE_STEP_DURATION_MS = 900L
        const val END_SEQUENCE_TOTAL_DURATION_MS =
            ((END_SEQUENCE_REPEAT_COUNT - 1L) * END_SEQUENCE_STEP_DURATION_MS) +
                END_SEQUENCE_TONE_DURATION_MS.toLong()

        // Short pip played at 3, 2, 1 seconds remaining.
        const val TICK_TONE_DURATION_MS = 120
    }
}
