package com.example.data.model

/**
 * The numbers behind the Home dashboard.
 *
 * Everything here is a pure function of the workout history, so the dashboard can be verified
 * without a phone and the numbers on the screen can be traced back to the records they came from.
 *
 * A calendar day is the app's own day boundary, [ActivityHeatmap.startOfDay]. The same function is
 * used by the activity heat map, the weekly summaries and the lifetime counter, so a workout can
 * never land on two different days depending on which screen is asking.
 */
object HomeDashboard {

    /** A workout only counts as a training day when it lasted longer than this. */
    const val QUALIFYING_MINUTES = 5

    /** A workout only counts as a training day when it holds at least this many completed sets. */
    const val QUALIFYING_SETS = 2

    /**
     * Whether a workout counts towards the lifetime training day count.
     *
     * Both conditions have to hold: the session lasted more than five minutes and it holds at
     * least two completed sets. A session that was opened and abandoned is not a training day,
     * and neither is a two minute warm up.
     */
    fun qualifies(session: WorkoutSession, sets: List<LoggedSet>): Boolean {
        if (minutes(session, sets) <= QUALIFYING_MINUTES) return false
        return sets.count { it.isCompleted } >= QUALIFYING_SETS
    }

    /** The minutes a session lasted. */
    fun minutes(session: WorkoutSession, sets: List<LoggedSet>): Int =
        WorkoutMetrics.minutes(session, sets)

    /** The volume of a session, in kilogram-reps. */
    fun volume(sets: List<LoggedSet>, bodyWeightForSet: (LoggedSet) -> Double?): Double =
        WorkoutMetrics.sessionVolume(sets, bodyWeightForSet)

    /**
     * The number of days on which the athlete completed at least one qualifying workout.
     *
     * The count never resets: it is the number of distinct calendar days, not a streak of
     * consecutive days, so a week off costs nothing and a month off costs nothing.
     */
    fun qualifyingDayCount(sessions: List<SessionWithSets>): Int {
        val days = mutableSetOf<Long>()
        for (wrapper in sessions) {
            if (!qualifies(wrapper.session, wrapper.sets)) continue
            days.add(ActivityHeatmap.startOfDay(wrapper.session.startTime))
        }
        return days.size
    }

    /**
     * The seven calendar dates the rolling heat map covers, oldest first, today last.
     *
     * The rolling window is a different thing from the calendar week of the schedule: it always
     * ends today and always covers seven dates, whatever the athlete's week starts on.
     */
    fun rollingWindow(today: Long): List<Long> {
        val todayStart = ActivityHeatmap.startOfDay(today)
        return (6 downTo 0).map { todayStart - it * DAY_MS }
    }

    /**
     * One cell of the rolling heat map.
     *
     * @param dayStart the start of the calendar date the cell stands for.
     * @param volume the volume trained that day, in kilogram-reps.
     * @param minutes the minutes trained that day.
     * @param setCount the sets logged that day.
     * @param hasSession whether a workout was recorded at all on that date. A day with no workout
     *        and a day whose history is missing are told apart by this flag.
     */
    data class DayCell(
        val dayStart: Long,
        val volume: Double,
        val minutes: Int,
        val setCount: Int,
        val hasSession: Boolean
    ) {
        /** Whether anything was trained on that day. */
        val hasActivity: Boolean get() = setCount > 0
    }

    /**
     * The rolling heat map: exactly seven cells, one per calendar date, today included.
     *
     * Several workouts on the same date are added together, so a day with two sessions shows the
     * sum of both rather than only the last one.
     */
    fun rollingHeatmap(
        sessions: List<SessionWithSets>,
        today: Long,
        bodyWeightForSet: (LoggedSet) -> Double?
    ): List<DayCell> {
        val window = rollingWindow(today)
        val first = window.first()
        val last = window.last()
        val byDay = LinkedHashMap<Long, DayCell>()
        window.forEach { byDay[it] = DayCell(it, 0.0, 0, 0, false) }

        for (wrapper in sessions) {
            val day = ActivityHeatmap.startOfDay(wrapper.session.startTime)
            if (day < first || day > last) continue
            val cell = byDay[day] ?: continue
            byDay[day] = cell.copy(
                volume = cell.volume + volume(wrapper.sets, bodyWeightForSet),
                minutes = cell.minutes + minutes(wrapper.session, wrapper.sets),
                setCount = cell.setCount + wrapper.sets.size,
                hasSession = true
            )
        }
        return window.map { byDay.getValue(it) }
    }

    /**
     * The totals of the calendar week the athlete is in.
     *
     * @param weekStartsOnMonday whether the week begins on Monday.
     */
    data class WeekTotals(
        val workouts: Int,
        val minutes: Int,
        val volume: Double
    )

    /**
     * The totals of the calendar week that contains [today].
     *
     * Unfinished sessions are not counted: a session is finished when it has an end time after its
     * start time and holds at least one set.
     */
    fun weekTotals(
        sessions: List<SessionWithSets>,
        today: Long,
        weekStartsOnMonday: Boolean,
        bodyWeightForSet: (LoggedSet) -> Double?
    ): WeekTotals {
        val firstDay = startOfWeek(today, weekStartsOnMonday)
        val lastDay = firstDay + 7 * DAY_MS
        var workouts = 0
        var minutes = 0
        var volume = 0.0
        for (wrapper in sessions) {
            if (wrapper.sets.isEmpty()) continue
            val start = wrapper.session.startTime
            if (start < firstDay || start >= lastDay) continue
            workouts++
            minutes += minutes(wrapper.session, wrapper.sets)
            volume += volume(wrapper.sets, bodyWeightForSet)
        }
        return WeekTotals(workouts, minutes, volume)
    }

    /**
     * The start of the calendar week that contains [timestamp].
     *
     * @param weekStartsOnMonday whether the week begins on Monday.
     */
    fun startOfWeek(timestamp: Long, weekStartsOnMonday: Boolean): Long {
        val dayStart = ActivityHeatmap.startOfDay(timestamp)
        val weekday = ActivityHeatmap.isoDayOfWeek(timestamp)
        val firstWeekday = if (weekStartsOnMonday) Weekday.MONDAY else Weekday.SUNDAY
        val offset = (weekday - firstWeekday + Weekday.COUNT) % Weekday.COUNT
        return dayStart - offset * DAY_MS
    }

    /**
     * The start of the calendar day that contains [timestamp].
     *
     * The same boundary the activity heat map uses, so a workout can never land on two different
     * days depending on which screen is asking.
     */
    fun startOfDay(timestamp: Long): Long = ActivityHeatmap.startOfDay(timestamp)

    /**
     * The seven calendar dates of the calendar week that contains [timestamp].
     *
     * @param weekStartsOnMonday whether the week begins on Monday.
     */
    fun weekDates(timestamp: Long, weekStartsOnMonday: Boolean): List<Long> {
        val first = startOfWeek(timestamp, weekStartsOnMonday)
        return (0 until Weekday.COUNT).map { first + it * DAY_MS }
    }

    /**
     * The last workout the athlete finished, newest first.
     *
     * @return the session and its sets, or null when nothing has been logged yet.
     */
    fun mostRecentWorkout(sessions: List<SessionWithSets>): SessionWithSets? =
        sessions
            .filter { it.sets.isNotEmpty() }
            .maxByOrNull { it.session.startTime }
}
