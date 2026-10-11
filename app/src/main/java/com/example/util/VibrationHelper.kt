package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object VibrationHelper {

    private const val TAG = "VibrationHelper"

    /** wait, vibrate, pause, vibrate, pause, vibrate */
    private val TIMINGS = longArrayOf(0, 400, 150, 400, 150, 600)
    private val AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255)

    /**
     * Vibrates the phone when a rest timer finishes.
     *
     * This runs from the exact alarm broadcast, i.e. also when the app was closed, backgrounded
     * or the device rebooted, so it must work without any UI and without a running process:
     *
     * - [Vibrator.hasAmplitudeControl] is checked first. Passing an amplitude array to a device
     *   that cannot control amplitude makes many OEM implementations silently drop the effect,
     *   which is exactly why the alert sound was audible while the phone never buzzed.
     * - The effect is played with [AudioAttributes] (alarm usage) so Do Not Disturb and silent
     *   profiles cannot suppress it.
     * - Every step has a fallback, because a thrown exception here would also silence the buzz.
     *
     * De-duplication between the in-app countdown loop and this broadcast is handled by
     * [RestTimerAlertCoordinator], never locally.
     */
    fun vibrateRestFinished(context: Context) {
        val vibrator = resolveVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val supportsAmplitude = try {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || vibrator.hasAmplitudeControl()
        } catch (_: Exception) {
            false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 1) Preferred: amplitude controlled pattern on devices that support it
            if (supportsAmplitude) {
                if (tryVibrate(vibrator, VibrationEffect.createWaveform(TIMINGS, AMPLITUDES, -1), attributes)) {
                    return
                }
            }
            // 2) Amplitude free pattern: works on every device, including the ones that silently
            //    drop amplitude based effects
            if (tryVibrate(vibrator, VibrationEffect.createWaveform(TIMINGS, -1), attributes)) {
                return
            }
            // 3) Last resort: a single heavy click
            tryVibrate(vibrator, VibrationEffect.createOneShot(700L, VibrationEffect.DEFAULT_AMPLITUDE), attributes)
        } else {
            @Suppress("DEPRECATION")
            try {
                // AudioAttributes overloads only exist from API 26, older devices get the plain call
                vibrator.vibrate(TIMINGS, -1)
            } catch (e: Exception) {
                Log.w(TAG, "Pattern vibration failed, falling back to a single buzz", e)
                try {
                    vibrator.vibrate(700L)
                } catch (_: Exception) {}
            }
        }
    }

    // Both annotations, for different reasons.
    //
    // DEPRECATION: the call was deprecated in API 33 but is still the only way to pass
    // AudioAttributes to the legacy Vibrator API, which is what lets the buzz survive Do Not
    // Disturb and silent mode.
    //
    // NewApi: vibrate(VibrationEffect, AudioAttributes) needs API 26, and the app supports 24.
    // It is nonetheless safe to call, because the only callers sit inside the
    // Build.VERSION.SDK_INT >= Build.VERSION_CODES.O branch of vibrateRestFinished, and lint
    // cannot see that through a separate function.
    @Suppress("DEPRECATION", "NewApi")
    private fun tryVibrate(vibrator: Vibrator, effect: VibrationEffect, attributes: AudioAttributes): Boolean =
        try {
            vibrator.vibrate(effect, attributes)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Vibration attempt failed, trying the next fallback", e)
            false
        }

    private fun resolveVibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not resolve the device vibrator", e)
        null
    }
}
