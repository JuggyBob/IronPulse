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
import com.example.data.model.RoutineDay
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.ScheduledWorkoutDay
import com.example.data.model.SessionWithSets
import com.example.data.model.StreakSummary
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutMetrics
import com.example.data.model.WorkoutStreak
import com.example.data.repository.FitnessRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the derived Home dashboard, streak and yearly activity state.
 *
 * The active routine, materialized schedule rows and their refresh side effect live together so
 * the ViewModel no longer has to know how the dashboard is assembled.
 */
class HomeDashboardController(
    private val repository: FitnessRepository,
    private val preferences: com.example.data.preferences.UserPreferencesRepository,
    private val scope: CoroutineScope,
    private val userProfile: StateFlow<UserProfile>,
    private val allExercises: StateFlow<List<com.example.data.model.Exercise>>,
    private val savedRoutines: StateFlow<List<SavedRoutine>>,
    private val weekStartFlow: StateFlow<Boolean>,
    private val refreshWidgets: () -> Unit
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

    val activeRoutineId: StateFlow<Long> = preferences.activeRoutineIdFlow
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0L)

    val activeRoutine: StateFlow<SavedRoutine?> = combine(
        savedRoutines,
        activeRoutineId
    ) { routines, id ->
        if (id <= 0L) null else routines.firstOrNull { it.id == id }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    val activeRoutineDays: StateFlow<List<RoutineDay>> = combine(
        activeRoutine,
        allExercises,
        weekStartFlow
    ) { routine, exercises, weekStartsOnMonday ->
        if (routine == null) {
            emptyList()
        } else {
            RoutineParser.parseWeek(routine.exercisesJson, exercises, weekStartsOnMonday)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleDays: StateFlow<List<ScheduledWorkoutDay>> = repository.getScheduleDays()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val currentDayTicker: Flow<Long> = flow {
        while (true) {
            emit(ActivityHeatmap.startOfDay(System.currentTimeMillis()))
            delay(60_000L)
        }
    }.distinctUntilChanged()

    val streakSummary: StateFlow<StreakSummary> = combine(
        scheduleDays,
        workoutHistory,
        weekStartFlow,
        currentDayTicker
    ) { schedule, sessions, weekStartsOnMonday, today ->
        WorkoutStreak.calculate(
            schedule = schedule,
            sessions = sessions,
            today = today,
            weekStartsOnMonday = weekStartsOnMonday
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), StreakSummary())

    init {
        scope.launch(Dispatchers.IO) {
            allExercises.first { it.isNotEmpty() }
            combine(activeRoutine, activeRoutineDays, weekStartFlow) { routine, days, _ ->
                routine to days
            }.collectLatest { (routine, days) ->
                val today = ActivityHeatmap.startOfDay(System.currentTimeMillis())
                val rows = if (routine == null) {
                    emptyList()
                } else {
                    WorkoutStreak.buildScheduleRows(
                        routineId = routine.id,
                        days = days,
                        fromDate = today
                    )
                }
                repository.replaceFutureSchedule(fromDate = today, rows = rows)
                refreshWidgets()
            }
        }
    }

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
