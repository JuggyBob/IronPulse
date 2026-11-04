/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.model

/** What a single calendar day means for the workout streak. */
enum class StreakDayStatus {
    /** A planned workout was completed on this date. */
    COMPLETED,

    /** A planned workout was not completed before the date ended. */
    MISSED,

    /** The routine planned a rest day. */
    REST,

    /** Today's planned workout has not been completed yet. It is not a miss. */
    PENDING,

    /** A planned or rest day later than today. */
    FUTURE,

    /** No schedule was recorded for this date, usually before tracking started. */
    UNTRACKED
}

/** One cell of the streak heat map. */
data class StreakDay(
    /** Local midnight for display, derived from [dateKey] in the current timezone. */
    val dayStart: Long,
    /** Timezone-stable `yyyyMMdd` key used by all calculations. */
    val dateKey: Long,
    val status: StreakDayStatus,
    val dayTitle: String = "",
    val routineId: Long = 0L
)

/** The numbers and the heat-map cells the Home streak widget renders. */
data class StreakSummary(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val isAlive: Boolean = false,
    val isBroken: Boolean = false,
    val hasSchedule: Boolean = false,
    val todayStart: Long = 0L,
    val todayPlanned: Boolean = false,
    val todayCompleted: Boolean = false,
    val pendingToday: Boolean = false,
    val completedDays: Int = 0,
    val missedDays: Int = 0,
    val lastMissedDay: Long? = null,
    val nextPlannedDay: Long? = null,
    val weekStartsOnMonday: Boolean = true,
    val days: List<StreakDay> = emptyList()
)

/**
 * Schedule-adherence streak maths.
 *
 * A streak is not a count of consecutive workout sessions. It is the number of consecutive
 * *planned* workout days whose session was completed, with rest days ignored. A planned day that
 * passes without a linked finished session resets the current streak; an unscheduled workout on a
 * rest day never repairs it.
 *
 * The schedule is the per-date [ScheduledWorkoutDay] table, not the current routine. That matters:
 * editing next week's routine must not rewrite what was planned last month. A completed session is
 * linked to its planned date by [WorkoutSession.plannedDate] and must hold at least one completed
 * set. An abandoned workout is never saved, so it cannot count.
 */
object WorkoutStreak {

    /** The widget shows twelve calendar weeks, including the current one. */
    const val HEATMAP_WEEKS = 12

    /** How far ahead the active schedule is materialized. */
    const val TRACKING_HORIZON_DAYS = 366

    /** A guard against a corrupt date making the historical walk unbounded. */
    private const val MAX_HISTORY_DAYS = 365 * 10

    /**
     * Builds the streak summary.
     *
     * @param schedule materialized planned/rest days, including past and future dates.
     * @param sessions finished workout sessions with their sets.
     * @param today any timestamp on the current local date.
     * @param weekStartsOnMonday the athlete's display preference; it only changes the heat-map
     *        column alignment, never the streak itself.
     */
    fun calculate(
        schedule: List<ScheduledWorkoutDay>,
        sessions: List<SessionWithSets>,
        today: Long,
        weekStartsOnMonday: Boolean = true
    ): StreakSummary {
        val todayKey = LocalDateKey.keyOf(today)
        val todayStart = LocalDateKey.timestampOf(todayKey)
        // The key is authoritative. The local-midnight epoch is only a display/fallback value for
        // rows written before date keys existed.
        val byDate = schedule.associateBy { row ->
            row.dateKey.takeIf { it > 0L } ?: LocalDateKey.keyOf(row.dateStart)
        }
        val plannedDates = byDate.values
            .filter { it.exerciseCount > 0 }
            .map { it.dateKey.takeIf { key -> key > 0L } ?: LocalDateKey.keyOf(it.dateStart) }
            .sorted()

        // A session only counts when it is linked to a planned date and actually contains completed
        // work. Quick workouts, drafts and abandoned sessions have no planned date and are ignored.
        val completedDates = sessions.asSequence()
            .filter { wrapper ->
                wrapper.session.plannedDate > 0L &&
                    wrapper.sets.any { it.isCompleted }
            }
            .map { wrapper ->
                wrapper.session.plannedDateKey.takeIf { it > 0L }
                    ?: LocalDateKey.keyOf(wrapper.session.plannedDate)
            }
            .toSet()

        val firstKey = minOf(
            plannedDates.firstOrNull() ?: todayKey,
            completedDates.minOrNull() ?: todayKey
        )

        var current = 0
        var best = 0
        var completedDays = 0
        var missedDays = 0
        var lastMissedKey: Long? = null
        var lastPastMissedKey: Long? = null

        var dateKey = firstKey
        var guard = 0
        while (dateKey <= todayKey && guard < MAX_HISTORY_DAYS) {
            guard++
            val planned = (byDate[dateKey]?.exerciseCount ?: 0) > 0
            val completed = completedDates.contains(dateKey)

            if (planned) {
                if (completed) {
                    current++
                    completedDays++
                    if (current > best) best = current
                } else if (dateKey == todayKey) {
                    // Today's plan is pending, not missed. The streak stays as it was yesterday.
                } else {
                    current = 0
                    missedDays++
                    lastMissedKey = dateKey
                    lastPastMissedKey = dateKey
                }
            }
            // A rest day or an untracked day leaves the current streak untouched.
            dateKey = LocalDateKey.plusDays(dateKey, 1)
        }

        val todayPlan = byDate[todayKey]
        val todayPlanned = (todayPlan?.exerciseCount ?: 0) > 0
        // A linked session on a rest day is a real workout, but it is not completion of a planned
        // day. Keeping this false is what stops the broken-state copy saying "today is complete".
        val todayCompleted = todayPlanned && completedDates.contains(todayKey)
        val pendingToday = todayPlanned && !todayCompleted
        val hasSchedule = plannedDates.isNotEmpty()
        val nextPlannedKey = plannedDates.firstOrNull { plannedDate ->
            plannedDate > todayKey || (plannedDate == todayKey && !todayCompleted)
        }

        val heatmapStartKey = LocalDateKey.plusDays(
            LocalDateKey.startOfWeekKey(todayKey, weekStartsOnMonday),
            -(HEATMAP_WEEKS - 1) * 7
        )
        val days = (0 until HEATMAP_WEEKS * 7).map { offset ->
            val key = LocalDateKey.plusDays(heatmapStartKey, offset)
            val plan = byDate[key]
            val planned = (plan?.exerciseCount ?: 0) > 0
            val completed = completedDates.contains(key)
            val status = when {
                key > todayKey -> StreakDayStatus.FUTURE
                planned && completed -> StreakDayStatus.COMPLETED
                planned && key == todayKey -> StreakDayStatus.PENDING
                planned -> StreakDayStatus.MISSED
                // A rest day stays a rest day even if the athlete did an unrelated workout on it.
                plan != null -> StreakDayStatus.REST
                completed -> StreakDayStatus.COMPLETED
                else -> StreakDayStatus.UNTRACKED
            }
            StreakDay(
                dayStart = LocalDateKey.timestampOf(key),
                dateKey = key,
                status = status,
                dayTitle = plan?.dayTitle.orEmpty(),
                routineId = plan?.routineId ?: 0L
            )
        }

        return StreakSummary(
            currentStreak = current,
            bestStreak = best,
            isAlive = current > 0,
            // A pending first planned day is not a broken streak. A previous missed planned day is.
            isBroken = hasSchedule && current == 0 && lastPastMissedKey != null,
            hasSchedule = hasSchedule,
            todayStart = todayStart,
            todayPlanned = todayPlanned,
            todayCompleted = todayCompleted,
            pendingToday = pendingToday,
            completedDays = completedDays,
            missedDays = missedDays,
            lastMissedDay = lastMissedKey?.let(LocalDateKey::timestampOf),
            nextPlannedDay = nextPlannedKey?.let(LocalDateKey::timestampOf),
            weekStartsOnMonday = weekStartsOnMonday,
            days = days
        )
    }

    /**
     * Materializes the active routine for every date from [fromDate] through the horizon.
     *
     * Rest days are written as rows with [ScheduledWorkoutDay.exerciseCount] = 0 so the heat map can
     * tell "planned rest" from "before tracking started".
     */
    fun buildScheduleRows(
        routineId: Long,
        days: List<RoutineDay>,
        fromDate: Long,
        capturedAt: Long = System.currentTimeMillis(),
        horizonDays: Int = TRACKING_HORIZON_DAYS
    ): List<ScheduledWorkoutDay> {
        if (routineId <= 0L || days.isEmpty()) return emptyList()
        val startKey = LocalDateKey.keyOf(fromDate)
        val byWeekday = days.associateBy { it.weekday }
        return (0..horizonDays).map { offset ->
            val dateKey = LocalDateKey.plusDays(startKey, offset)
            val date = LocalDateKey.timestampOf(dateKey)
            val weekday = LocalDateKey.isoDayOfWeek(dateKey)
            val day = byWeekday[weekday]
            ScheduledWorkoutDay(
                dateStart = date,
                dateKey = dateKey,
                routineId = routineId,
                weekday = weekday,
                dayTitle = day?.dayTitle.orEmpty(),
                exerciseCount = day?.exercises?.size ?: 0,
                capturedAt = capturedAt
            )
        }
    }

    /**
     * The date of [weekday] in the calendar week containing [reference].
     *
     * A scheduled session done late is still credited to the day it was planned for. The result is
     * normalized to local midnight so it can be compared directly with schedule rows.
     */
    fun dateForWeekdayInWeek(
        weekday: Int,
        reference: Long,
        weekStartsOnMonday: Boolean
    ): Long {
        val todayKey = LocalDateKey.keyOf(reference)
        if (weekday !in 1..Weekday.COUNT) return LocalDateKey.timestampOf(todayKey)
        val weekStartKey = LocalDateKey.startOfWeekKey(todayKey, weekStartsOnMonday)
        val offset = if (weekStartsOnMonday) weekday - 1 else weekday % 7
        return LocalDateKey.timestampOf(LocalDateKey.plusDays(weekStartKey, offset))
    }

    /** Local midnight of the first day of the calendar week containing [timestamp]. */
    fun startOfWeek(timestamp: Long, weekStartsOnMonday: Boolean): Long {
        val dayKey = LocalDateKey.keyOf(timestamp)
        return LocalDateKey.timestampOf(LocalDateKey.startOfWeekKey(dayKey, weekStartsOnMonday))
    }
}
