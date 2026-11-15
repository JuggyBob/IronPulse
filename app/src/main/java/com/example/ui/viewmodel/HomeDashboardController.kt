/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.viewmodel

import com.example.data.model.ActivityDay
import com.example.data.model.ActivityHeatmap
import com.example.data.model.BodyWeightTimeline
import com.example.data.model.HomeDashboard
import com.example.data.model.LoggedSet
import com.example.data.model.SessionWithSets
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutMetrics
import com.example.data.repository.FitnessRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Owns the history-derived Home dashboard and yearly activity state.
 *
 * Schedule adherence lives in PlanController now; keeping these flows separate prevents the
 * history and weekly-plan producers from rewriting each other.
 */
class HomeDashboardController(
    private val repository: FitnessRepository,
    private val scope: CoroutineScope,
    private val userProfile: StateFlow<UserProfile>,
    private val weekStartFlow: StateFlow<Boolean>
) {
    val activityDays: StateFlow<List<ActivityDay>> = combine(
        repository.getAllSessionsWithSets(),
        userProfile
    ) { sessions, profile -> buildActivityDays(sessions, profile.currentWeightKg) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workoutHistory: StateFlow<List<SessionWithSets>> = repository.getAllSessionsWithSets()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val homeHeatmap: StateFlow<List<HomeDashboard.DayCell>> = combine(
        workoutHistory,
        userProfile
    ) { sessions, profile ->
        HomeDashboard.rollingHeatmap(
            sessions = sessions,
            today = System.currentTimeMillis(),
            bodyWeightForSet = bodyWeightForSet(profile.currentWeightKg)
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lifetimeTrainingDays: StateFlow<Int> = workoutHistory
        .map { sessions -> HomeDashboard.qualifyingDayCount(sessions) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    val weekTotals: StateFlow<HomeDashboard.WeekTotals> = combine(
        workoutHistory,
        userProfile,
        weekStartFlow
    ) { sessions, profile, weekStartsOnMonday ->
        HomeDashboard.weekTotals(
            sessions = sessions,
            today = System.currentTimeMillis(),
            weekStartsOnMonday = weekStartsOnMonday,
            bodyWeightForSet = bodyWeightForSet(profile.currentWeightKg)
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), HomeDashboard.WeekTotals(0, 0, 0.0))

    val recentWorkout: StateFlow<SessionWithSets?> = workoutHistory
        .map { sessions -> HomeDashboard.mostRecentWorkout(sessions) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    private fun bodyWeightForSet(profileWeightKg: Double): (LoggedSet) -> Double? = { set ->
        BodyWeightTimeline.bodyWeightForSet(set, profileWeightKg)
    }

    private fun buildActivityDays(
        sessions: List<SessionWithSets>,
        profileWeightKg: Double
    ): List<ActivityDay> {
        val oneYearAgo = ActivityHeatmap.startOfDay(
            java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.MONTH, -12)
            }.timeInMillis
        )

        val minutesByDay = HashMap<Long, Int>()
        val volumeByDay = HashMap<Long, Double>()
        val setsByDay = HashMap<Long, Int>()

        sessions.forEach { wrapper ->
            val day = ActivityHeatmap.startOfDay(wrapper.session.startTime)
            if (day < oneYearAgo) return@forEach

            val minutes = WorkoutMetrics.minutes(wrapper.session, wrapper.sets)
            val volume = WorkoutMetrics.sessionVolume(wrapper.sets) { set ->
                BodyWeightTimeline.bodyWeightForSet(set, profileWeightKg)
            }

            minutesByDay[day] = (minutesByDay[day] ?: 0) + minutes
            volumeByDay[day] = (volumeByDay[day] ?: 0.0) + volume
            setsByDay[day] = (setsByDay[day] ?: 0) + wrapper.sets.size
        }

        return setsByDay.keys.sorted().map { day ->
            ActivityDay(
                epochDayStart = day,
                minutes = minutesByDay[day] ?: 0,
                volume = volumeByDay[day] ?: 0.0,
                setCount = setsByDay[day] ?: 0
            )
        }
    }
}
