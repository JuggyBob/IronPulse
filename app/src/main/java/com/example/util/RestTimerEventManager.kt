package com.example.util

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object RestTimerEventManager {
    private val _timerFinishedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val timerFinishedFlow = _timerFinishedFlow.asSharedFlow()

    fun notifyFinished() {
        _timerFinishedFlow.tryEmit(Unit)
    }
}

/**
 * A finished rest timer is announced by two independent paths:
 *  - the in-app countdown loop (only alive while the app process lives), and
 *  - the exact alarm broadcast (works when the app is closed, backgrounded or the phone rebooted).
 *
 * Both can fire within milliseconds of each other, which used to make one of them swallow the
 * other's vibration. This coordinator makes exactly one of them win, keyed by the timer it
 * belongs to, so the alert is never played twice and never lost.
 *
 * It also carries the two settings page switches: vibration and sound can be turned off
 * independently, and the exact alarm broadcast (which may start a fresh process) reads them from
 * SharedPreferences so it always matches what the athlete chose.
 */
object RestTimerAlertCoordinator {

    private const val PREFS_NAME = "rest_timer_alert_prefs"
    private const val PREF_VIBRATION = "vibration_enabled"
    private const val PREF_SOUND = "sound_enabled"

    private var lastAlertedTimerKey: String? = null
    private var lastAlertedAt = 0L

    @Volatile
    private var vibrationEnabled: Boolean = true

    @Volatile
    private var soundEnabled: Boolean = true

    /** Total playing time of the alert, used to keep the device awake long enough. */
    const val ALERT_DURATION_MS = 3_000L

    /**
     * @return true when the caller is the one allowed to play the alert for this timer.
     */
    @Synchronized
    fun tryClaim(timerKey: String, debounceMs: Long = 5_000L): Boolean {
        val now = System.currentTimeMillis()
        if (timerKey == lastAlertedTimerKey && now - lastAlertedAt < debounceMs) return false
        lastAlertedTimerKey = timerKey
        lastAlertedAt = now
        return true
    }

    /** Stores the rest timer alert switches coming from the settings page. */
    fun setAlertPreferences(context: Context, vibration: Boolean, sound: Boolean) {
        vibrationEnabled = vibration
        soundEnabled = sound
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_VIBRATION, vibration)
                .putBoolean(PREF_SOUND, sound)
                .apply()
        }
    }

    /**
     * Plays the finished-timer vibration and sound exactly once for the given timer.
     * Safe to call from the alarm broadcast receiver as well as from the in-app loop.
     *
     * When both switches are off the rest timer still ends, it just stays silent.
     */
    fun playFinishedAlert(context: Context, timerKey: String) {
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
        val vibrate = prefs?.getBoolean(PREF_VIBRATION, vibrationEnabled) ?: vibrationEnabled
        val sound = prefs?.getBoolean(PREF_SOUND, soundEnabled) ?: soundEnabled
        if (!vibrate && !sound) return
        if (!tryClaim(timerKey)) return
        if (vibrate) VibrationHelper.vibrateRestFinished(context)
        if (sound) SoundHelper.playRestDing(context)
    }
}
