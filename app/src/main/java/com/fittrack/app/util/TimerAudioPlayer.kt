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

    /**
     * Plays [count] short countdown ticks (3-2-1) with a 1-second cadence, holding
     * `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` for the **entire** sequence so that
     * background music stays silent between ticks.  Works over Bluetooth because the
     * tone is rendered on [STREAM_MUSIC].
     *
     * A single [ToneGenerator] is reused for all ticks in the sequence. Opening and
     * closing an audio stream per tick causes an audible click/pop on many devices;
     * reuse eliminates this artifact. Because each tick lasts only [TICK_TONE_DURATION_MS]
     * and the inter-tick gap is [TICK_SEQUENCE_STEP_MS], the previous tone is always
     * finished before [ToneGenerator.startTone] is called again.
     */
    suspend fun playTickSequence(count: Int, volumePercent: Int) {
        withAudioFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) {
            val ctx = setupToneContext(volumePercent) ?: return@withAudioFocus
            try {
                repeat(count) { index ->
                    ctx.toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, TICK_TONE_DURATION_MS)
                    // After the last tick, keep focus a little longer so the tone fully
                    // rings out before music is allowed to resume.
                    delay(
                        if (index == count - 1) TICK_TONE_DURATION_MS.toLong() + TICK_POST_SEQUENCE_BUFFER_MS
                        else TICK_SEQUENCE_STEP_MS
                    )
                }
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
     *
     * The stream volume is raised **before** creating [ToneGenerator] so the track opens
     * at the correct level rather than starting silently and then jumping up mid-tone.
     *
     * Returns null if [ToneGenerator] creation fails (e.g. audio subsystem unavailable).
     */
    private fun setupToneContext(volumePercent: Int): ToneContext? {
        val stream = preferredStream()
        val toneVolume = toEffectiveAudioPercent(volumePercent)
        val originalVolume = audioManager.getStreamVolume(stream)
        val maxVolume = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        val targetVolume = ((maxVolume * (toneVolume / 100f)).roundToInt()).coerceIn(1, maxVolume)
        // Raise stream volume before opening ToneGenerator so the stream is at the
        // correct level from the very first sample.
        if (targetVolume > originalVolume) {
            audioManager.setStreamVolume(stream, targetVolume, 0)
        }
        val toneGenerator = runCatching { ToneGenerator(stream, toneVolume) }.getOrNull() ?: run {
            // Restore volume if ToneGenerator creation failed.
            if (targetVolume > originalVolume) {
                audioManager.setStreamVolume(stream, originalVolume, 0)
            }
            return null
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
        // Give other apps a moment to pause playback after receiving the focus-loss callback
        // before we raise the stream volume, so the user never hears the volume jump over
        // still-playing background audio.
        if (focusGranted) delay(AUDIO_FOCUS_SETTLE_DELAY_MS)
        try {
            block()
        } finally {
            if (focusGranted) audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun withAudioFocusBlocking(focusGain: Int, block: () -> Unit) {
        val focusRequest = buildAudioFocusRequest(focusGain)
        val focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        // Give other apps a moment to pause playback after receiving the focus-loss callback
        // before we raise the stream volume.
        if (focusGranted) Thread.sleep(AUDIO_FOCUS_SETTLE_DELAY_MS)
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
        // Gap between the start of each pip — matches the 1-second countdown cadence.
        const val TICK_SEQUENCE_STEP_MS = 1_000L
        // Extra time held after the last tick. Set to (TICK_SEQUENCE_STEP_MS - TICK_TONE_DURATION_MS)
        // so that the total delay after the final tick equals TICK_SEQUENCE_STEP_MS (1 000 ms) —
        // matching every other inter-tick gap. This means audio focus is held for exactly
        // count × TICK_SEQUENCE_STEP_MS (= 3 s), which covers the full countdown window and
        // prevents music from resuming in the ~630 ms gap before the end sequence acquires focus.
        const val TICK_POST_SEQUENCE_BUFFER_MS = 880L // = TICK_SEQUENCE_STEP_MS - TICK_TONE_DURATION_MS

        // Milliseconds to wait after requesting audio focus before raising the stream volume
        // and playing the tone. This gives other media apps time to actually pause playback
        // in response to the focus-loss callback so the user never hears the volume jump
        // while background audio is still playing.
        private const val AUDIO_FOCUS_SETTLE_DELAY_MS = 150L
    }
}
