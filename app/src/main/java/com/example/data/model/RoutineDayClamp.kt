package com.example.data.model

/**
 * Keeps a routine's exercises inside the days the routine actually has.
 *
 * The bug this exists for: an athlete puts an exercise on day 5, then shortens the routine to 4 days
 * and saves. The exercise stayed in the routine. It was invisible on every day, so it looked like it
 * had been deleted, yet it was still being saved and logged, and it reappeared the moment the routine
 * was widened back to 5. Both readings are wrong.
 *
 * Clamping is the fix: an exercise never sits on a day that does not exist. It is a pure function so
 * it can be covered by a plain test, and so both the live screen and the save path can call it and be
 * certain they agree.
 */
object RoutineDayClamp {

    /** Clamps [dayNumber] into 1..[daysPerWeek]. A non positive week collapses to day 1. */
    fun clampDay(dayNumber: Int, daysPerWeek: Int): Int {
        val lastDay = daysPerWeek.coerceAtLeast(1)
        return dayNumber.coerceIn(1, lastDay)
    }

    /** The day number the athlete should be looking at after [daysPerWeek] changed. */
    fun clampSelectedDay(selectedDay: Int, daysPerWeek: Int): Int =
        clampDay(selectedDay, daysPerWeek)

    /**
     * @return the exercises with any out of range day number moved onto the last surviving day.
     * The input order is preserved, and only the day number and title change.
     */
    fun clampExercises(
        exercises: List<RoutineExerciseItem>,
        daysPerWeek: Int
    ): List<RoutineExerciseItem> {
        val lastDay = daysPerWeek.coerceAtLeast(1)
        return exercises.map { item ->
            if (item.dayNumber > lastDay) {
                item.copy(dayNumber = lastDay, dayTitle = "Day $lastDay")
            } else if (item.dayNumber < 1) {
                item.copy(dayNumber = 1, dayTitle = "Day 1")
            } else {
                item
            }
        }
    }

    /**
     * Whether any exercise is stranded on a day the routine does not have.
     *
     * Used by the tests and, in the app, to decide whether a shrink actually needs a correction pass.
     */
    fun hasStrandedExercises(
        exercises: List<RoutineExerciseItem>,
        daysPerWeek: Int
    ): Boolean {
        val lastDay = daysPerWeek.coerceAtLeast(1)
        return exercises.any { it.dayNumber > lastDay || it.dayNumber < 1 }
    }
}
