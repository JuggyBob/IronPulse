package com.example.data.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class CustomRestTimer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val totalSeconds: Int
) {
    val minutes: Int get() = (totalSeconds.coerceAtLeast(0)) / 60
    val seconds: Int get() = (totalSeconds.coerceAtLeast(0)) % 60
    val formattedDuration: String
        get() = String.format("%02d:%02d", minutes, seconds)
}

@Immutable
data class ActiveRestTimerState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val timerName: String = "",
    val hasFinished: Boolean = false,
    val endTimeMillis: Long = 0L
) {
    val progressFraction: Float
        get() = if (totalSeconds > 0) {
            (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val formattedRemaining: String
        get() {
            val clamped = remainingSeconds.coerceAtLeast(0)
            val m = clamped / 60
            val s = clamped % 60
            return String.format("%02d:%02d", m, s)
        }
}
