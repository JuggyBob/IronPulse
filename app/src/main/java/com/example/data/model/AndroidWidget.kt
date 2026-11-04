/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.model

import com.example.ui.theme.ThemeMode

/** The three actual launcher widgets IronPulse registers. */
enum class AndroidWidgetKind(
    val storageKey: String,
    val displayName: String,
    val description: String
) {
    STREAK(
        storageKey = "streak",
        displayName = "Workout Streak",
        description = "Current streak, best streak and the alive or burnt-out flame."
    ),
    WEEKLY(
        storageKey = "weekly",
        displayName = "Weekly Workout Overview",
        description = "Seven binary training days, today's workout and a start button."
    ),
    PROFILE(
        storageKey = "profile",
        displayName = "Workout Profile",
        description = "Name, weight, height and BMI from your IronPulse profile."
    );

    companion object {
        fun fromStorage(value: String?): AndroidWidgetKind? =
            entries.firstOrNull { it.storageKey.equals(value?.trim(), ignoreCase = true) }
    }
}

/** One independently selected appearance for each launcher widget. */
data class AndroidWidgetThemes(
    val streak: ThemeMode = ThemeMode.IRONPULSE,
    val weekly: ThemeMode = ThemeMode.IRONPULSE,
    val profile: ThemeMode = ThemeMode.IRONPULSE
) {
    fun forKind(kind: AndroidWidgetKind): ThemeMode = when (kind) {
        AndroidWidgetKind.STREAK -> streak
        AndroidWidgetKind.WEEKLY -> weekly
        AndroidWidgetKind.PROFILE -> profile
    }

    fun with(kind: AndroidWidgetKind, mode: ThemeMode): AndroidWidgetThemes = when (kind) {
        AndroidWidgetKind.STREAK -> copy(streak = mode)
        AndroidWidgetKind.WEEKLY -> copy(weekly = mode)
        AndroidWidgetKind.PROFILE -> copy(profile = mode)
    }
}
