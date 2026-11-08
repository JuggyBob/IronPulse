/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.viewmodel

import android.app.Application
import com.example.data.model.ActiveRestTimerState
import com.example.data.model.CustomRestTimer
import com.example.data.preferences.UserPreferencesRepository
import com.example.util.RestTimerAlertCoordinator
import com.example.util.RestTimerEventManager
import com.example.util.RestTimerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the rest-timer countdown, its exact-alarm scheduling and the saved timer presets.
 *
 * Extracted from [MainViewModel] without changing the public state or callable surface used by
 * the timer components. The owning ViewModel's scope is supplied explicitly so the countdown keeps
 * exactly the same lifecycle as before.
 */
class RestTimerController(
    private val application: Application,
    private val preferences: UserPreferencesRepository,
    private val scope: CoroutineScope
) {
    val savedRestTimers: StateFlow<List<CustomRestTimer>> = preferences.savedRestTimersFlow
        .stateIn(scope, SharingStarted.Lazily, preferences.defaultRestTimers)

    private val _activeRestTimer = MutableStateFlow(ActiveRestTimerState())
    val activeRestTimer: StateFlow<ActiveRestTimerState> = _activeRestTimer.asStateFlow()

    private var restTimerJob: Job? = null

    init {
        scope.launch {
            RestTimerEventManager.timerFinishedFlow.collect {
                val current = _activeRestTimer.value
                if (current.isRunning) {
                    _activeRestTimer.value = current.copy(
                        remainingSeconds = 0,
                        isRunning = false,
                        isPaused = false,
                        hasFinished = true
                    )
                }
            }
        }
    }

    private fun playFinishedAlert(endTimeMillis: Long) {
        val key = if (endTimeMillis > 0L) {
            endTimeMillis.toString()
        } else {
            "inapp_${System.currentTimeMillis()}"
        }
        RestTimerAlertCoordinator.playFinishedAlert(application, key)
    }

    fun start(seconds: Int, name: String = "Rest Timer") {
        val total = seconds.coerceAtLeast(1)
        val endTime = System.currentTimeMillis() + (total * 1000L)
        restTimerJob?.cancel()
        _activeRestTimer.value = ActiveRestTimerState(
            isRunning = true,
            isPaused = false,
            totalSeconds = total,
            remainingSeconds = total,
            timerName = name,
            hasFinished = false,
            endTimeMillis = endTime
        )

        RestTimerScheduler.scheduleRestTimer(application, total, name, endTime)

        restTimerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                val current = _activeRestTimer.value
                if (!current.isRunning || current.isPaused) continue

                val nextRemaining =
                    ((current.endTimeMillis - System.currentTimeMillis()) / 1000L)
                        .toInt()
                        .coerceAtLeast(0)
                if (nextRemaining <= 0) {
                    _activeRestTimer.value = current.copy(
                        remainingSeconds = 0,
                        isRunning = false,
                        isPaused = false,
                        hasFinished = true
                    )
                    playFinishedAlert(current.endTimeMillis)
                    break
                } else {
                    _activeRestTimer.value = current.copy(remainingSeconds = nextRemaining)
                }
            }
        }
    }

    fun pause() {
        val current = _activeRestTimer.value
        if (current.isRunning && !current.isPaused) {
            RestTimerScheduler.cancelRestTimer(application)
            val remaining =
                ((current.endTimeMillis - System.currentTimeMillis()) / 1000L)
                    .toInt()
                    .coerceAtLeast(0)
            _activeRestTimer.value = current.copy(isPaused = true, remainingSeconds = remaining)
        }
    }

    fun resume() {
        val current = _activeRestTimer.value
        if (current.isRunning && current.isPaused && current.remainingSeconds > 0) {
            start(current.remainingSeconds, current.timerName)
        }
    }

    fun reset() {
        val current = _activeRestTimer.value
        if (current.totalSeconds > 0) {
            start(current.totalSeconds, current.timerName)
        }
    }

    fun stop() {
        restTimerJob?.cancel()
        RestTimerScheduler.cancelRestTimer(application)
        _activeRestTimer.value = ActiveRestTimerState()
    }

    fun addSeconds(secondsToAdd: Int) {
        val current = _activeRestTimer.value
        if (current.isRunning && current.remainingSeconds > 0) {
            val newTotal = (current.totalSeconds + secondsToAdd).coerceAtLeast(1)
            val newEndTime = current.endTimeMillis + (secondsToAdd * 1000L)
            val newRemaining =
                ((newEndTime - System.currentTimeMillis()) / 1000L)
                    .toInt()
                    .coerceAtLeast(1)
            _activeRestTimer.value = current.copy(
                totalSeconds = newTotal,
                remainingSeconds = newRemaining,
                endTimeMillis = newEndTime
            )
            RestTimerScheduler.cancelRestTimer(application)
            RestTimerScheduler.scheduleRestTimer(application, newRemaining, current.timerName, newEndTime)
        } else if (current.hasFinished || !current.isRunning) {
            start(
                secondsToAdd.coerceAtLeast(1),
                if (current.timerName.isNotBlank()) current.timerName else "Rest Timer"
            )
        }
    }

    fun saveCustomTimer(name: String, totalSeconds: Int) {
        val cleanName = name.trim().ifBlank { "Rest Timer" }
        val cleanSeconds = totalSeconds.coerceAtLeast(5)
        scope.launch {
            val currentList = savedRestTimers.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
            val newTimer = CustomRestTimer(name = cleanName, totalSeconds = cleanSeconds)
            if (existingIndex >= 0) {
                currentList[existingIndex] = newTimer
            } else {
                currentList.add(newTimer)
            }
            preferences.saveRestTimers(currentList)
        }
    }

    fun deleteCustomTimer(id: String) {
        scope.launch {
            val currentList = savedRestTimers.value.filter { it.id != id }
            preferences.saveRestTimers(currentList)
        }
    }
}
