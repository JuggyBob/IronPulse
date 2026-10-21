package com.example.data.model

/**
 * One workout of a routine, before it has been assigned to a weekday.
 *
 * @param item the exercise itself.
 * @param weekday the weekday the source named, or 0 when it did not name one.
 * @param slot the position of the workout within the routine. Workouts that share a slot belong to
 *        the same day, so a routine written as "Day 2: squat, bench" keeps them together.
 */
data class ScheduledWorkout(
    val item: RoutineExerciseItem,
    val weekday: Int = 0,
    val slot: Int = 1
)

/**
 * Builds the seven weekday slots of a routine.
 *
 * A routine is a week, not a list of numbered days. Every routine has seven slots, one per weekday,
 * and a slot with no exercises in it is a rest day. There is no separate rest flag: an empty slot IS
 * a rest day, so a day can never claim to be a training day while holding nothing.
 *
 * The rules the schedule is built by:
 *
 *  - A weekday the source named explicitly keeps it. "Monday and Wednesday" stays Monday and
 *    Wednesday, whatever else the week looks like, and a named weekday is never taken away from it
 *    to make room for another workout.
 *  - A workout with no weekday named is laid out on the weekdays that are left over, in order,
 *    starting at the first day of the athlete's week. That is what a list of four workouts with no
 *    days attached means: Monday to Thursday, with the rest of the week resting.
 *  - Every weekday nothing was assigned to is a rest day. A missing day is never filled with a
 *    repeat of the day before it.
 */
object RoutineSchedule {

    /**
     * The weekday the [slot]th day of a week falls on.
     *
     * Slot 1 is the first day of the athlete's week. A routine that was written as "Day 1, Day 2,
     * Day 3" before the seven day model existed lands on the first three days of the week, and the
     * athlete can move any of them afterwards.
     *
     * @param slot the one based position of the day within the routine.
     */
    fun weekdayForSlot(slot: Int, weekStartsOnMonday: Boolean): Int {
        val first = Weekday.first(weekStartsOnMonday)
        var weekday = first
        repeat((slot - 1).coerceAtLeast(0)) {
            weekday = Weekday.next(weekday)
        }
        return weekday
    }

    /**
     * The seven weekday slots of a routine, in the order they are displayed.
     *
     * @param workouts every workout of the routine, in the order they are performed.
     * @param weekStartsOnMonday whether the athlete's week starts on Monday.
     * @return exactly seven days, one per weekday, ordered for display.
     */
    fun buildDays(
        workouts: List<ScheduledWorkout>,
        weekStartsOnMonday: Boolean
    ): List<RoutineDay> {
        // The weekdays that were named are reserved before anything else is placed.
        val reserved = workouts.mapNotNull { it.weekday.takeIf { d -> d in 1..Weekday.COUNT } }.toSet()

        // The workouts that were not named are grouped by their position, so the exercises of one
        // day stay together even when the answer listed them out of order. The groups are ordered
        // by their position, not by the order they appeared in: the second day of a routine is the
        // second day, wherever the file happens to mention it.
        val unnamedGroups = workouts
            .filter { it.weekday !in 1..Weekday.COUNT }
            .groupBy { it.slot }
        val unnamedSlots = unnamedGroups.keys.sorted()

        // They fill the weekdays that are left over, in week order, starting at the first day of
        // the athlete's week.
        val free = Weekday.ordered(weekStartsOnMonday).filter { it !in reserved }
        val slotWeekday = mutableMapOf<Int, Int>()
        unnamedSlots.forEachIndexed { index, slot ->
            val weekday = if (index < free.size) {
                free[index]
            } else {
                // More workout days than the week has room for. The last one is joined by the
                // rest rather than dropped, so nothing the athlete wrote is lost.
                free.lastOrNull() ?: Weekday.first(weekStartsOnMonday)
            }
            slotWeekday[slot] = weekday
        }

        val byWeekday = LinkedHashMap<Int, MutableList<RoutineExerciseItem>>()
        val titles = mutableMapOf<Int, String>()
        val notes = mutableMapOf<Int, String>()

        for (workout in workouts) {
            val weekday = workout.weekday.takeIf { it in 1..Weekday.COUNT }
                ?: slotWeekday[workout.slot]
                ?: continue
            // The exercise carries the weekday it was assigned, so the day it belongs to survives
            // the flat format the routine is stored in.
            val item = workout.item.copy(weekday = weekday)
            byWeekday.getOrPut(weekday) { mutableListOf() }.add(item)
            if (titles[weekday].isNullOrBlank() && item.dayTitle.isNotBlank()) {
                titles[weekday] = item.dayTitle
            }
            if (notes[weekday].isNullOrBlank() && item.dayNote.isNotBlank()) {
                notes[weekday] = item.dayNote
            }
        }

        return Weekday.ordered(weekStartsOnMonday).map { weekday ->
            val dayItems = byWeekday[weekday].orEmpty()
            val note = notes[weekday].orEmpty()
            // The note travels on every item, so it survives the flat JSON array the routine is
            // stored in.
            val withNote = if (note.isBlank()) dayItems else dayItems.map { it.copy(dayNote = note) }
            RoutineDay(
                weekday = weekday,
                dayTitle = titles[weekday].orEmpty(),
                exercises = withNote,
                note = note
            )
        }
    }

    /**
     * The label of a day, e.g. "Monday" or "Monday: Upper Body Push".
     */
    fun dayLabel(day: RoutineDay): String {
        val name = Weekday.name(day.weekday)
        val title = day.dayTitle.trim()
        return if (title.isEmpty() || title.equals(name, ignoreCase = true)) name else "$name: $title"
    }

    /**
     * The day of a week a routine assigns to [weekday], or null when the routine has no such day.
     */
    fun dayFor(days: List<RoutineDay>, weekday: Int): RoutineDay? =
        days.firstOrNull { it.weekday == weekday }
}
