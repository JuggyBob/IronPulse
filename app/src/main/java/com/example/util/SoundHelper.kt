package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object SoundHelper {

    private const val SAMPLE_RATE = 44100
    private const val SHORT_BEEP_MS = 140
    private const val GAP_MS = 140
    private const val LONG_PAUSE_MS = 1000
    private const val LONG_BEEP_MS = 900
    private const val LEAD_IN_SILENCE_MS = 120
    private const val SHORT_BEEP_FREQ = 987.77 // B5
    private const val LONG_BEEP_FREQ = 659.25  // E5, lower so the final beep reads as "beeeeeep"

    /** Total length of the alert, used to release the AudioTrack and to size the wake lock. */
    const val ALERT_DURATION_MS = LEAD_IN_SILENCE_MS + (SHORT_BEEP_MS + GAP_MS) * 3 + LONG_PAUSE_MS + LONG_BEEP_MS

    /**
     * Plays the rest timer alert (~3 seconds):
     * "beep - beep - beep" (three short beeps) -> 1 second pause -> "beeeeeep" (one long beep).
     *
     * Routes through the media stream so it stays audible on headphones and phone speakers.
     */
    fun playRestDing(context: Context) {
        try {
            val totalMs = ALERT_DURATION_MS
            val numSamples = (totalMs * SAMPLE_RATE) / 1000
            val buffer = ShortArray(numSamples)

            // beep, beep, beep
            var cursorMs = LEAD_IN_SILENCE_MS
            repeat(3) {
                writeTone(buffer, cursorMs, SHORT_BEEP_MS, SHORT_BEEP_FREQ, volume = 0.55)
                cursorMs += SHORT_BEEP_MS + GAP_MS
            }
            // 1 second silence, then the long closing beep
            cursorMs += LONG_PAUSE_MS
            writeTone(buffer, cursorMs, LONG_BEEP_MS, LONG_BEEP_FREQ, volume = 0.75)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.setVolume(1.0f)
            track.play()

            // Automatically release the AudioTrack once the full alert has been played
            CoroutineScope(Dispatchers.Default).launch {
                delay(totalMs + 300L)
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            playFallbackToneSequence()
        }
    }

    /**
     * Writes one beep into the buffer.
     * Uses a 6ms attack and an exponential decay so each beep starts and ends cleanly
     * (no clicks) and sounds like a real "beep" instead of a pure tone.
     */
    private fun writeTone(
        buffer: ShortArray,
        startMs: Int,
        durationMs: Int,
        frequency: Double,
        volume: Double
    ) {
        val startSample = (startMs * SAMPLE_RATE) / 1000
        val lengthSamples = (durationMs * SAMPLE_RATE) / 1000
        val twoPi = 2.0 * PI
        val attackSeconds = 0.006
        val decay = 4.0

        var i = 0
        while (i < lengthSamples) {
            val index = startSample + i
            if (index >= buffer.size) return
            val t = i.toDouble() / SAMPLE_RATE
            val attack = if (t < attackSeconds) t / attackSeconds else 1.0
            val envelope = attack * exp(-decay * t)
            val sample = sin(twoPi * frequency * t) * envelope * volume
            buffer[index] = (sample * 29000.0).toInt().coerceIn(-32768, 32767).toShort()
            i++
        }
    }

    /**
     * Robust fallback when the raw audio path is unavailable (e.g. no audio hardware).
     * Reproduces the same beep - beep - beep - pause - long beep rhythm.
     */
    private fun playFallbackToneSequence() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    repeat(3) {
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, SHORT_BEEP_MS)
                        delay((SHORT_BEEP_MS + GAP_MS).toLong())
                    }
                    delay(LONG_PAUSE_MS.toLong())
                    toneGen.startTone(ToneGenerator.TONE_PROP_ACK, LONG_BEEP_MS)
                    delay(LONG_BEEP_MS.toLong())
                } catch (_: Exception) {
                } finally {
                    try {
                        toneGen.release()
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }
}
