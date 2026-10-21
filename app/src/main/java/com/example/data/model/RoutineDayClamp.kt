package com.example.data.model

/**
 * Keeps a routine's exercises inside the days of the week.
 *
 * A routine has seven slots, one per weekday, so a day can no longer be "stranded" by shrinking
 * the routine: there is always a Monday to hold an exercise. What can still go wrong is an
 * exercise that carries a weekday outside the week, which would make it invisible in the editor
 * and in the dashboard.
 *
 * That can only come from data written before the seven day model existed, or from a hand edited
 * file, so the clamp is the safety net that keeps such an exercise visible instead of lost.
 *
 * It is a pure function so it can be covered by a plain test, and so the editor and the save path
 * can call it and be certain they agree.
 */
object RoutineDayClamp {

    /** Clamps a weekday into 1..7. A weekday outside the week is pulled back onto it. */
    fun clampDay(weekday: Int): Int = weekday.coerceIn(1, Weekday.COUNT)

    /**
     * @return the exercises with any weekday outside the week moved onto the Monday.
     * The input order is preserved, and only the weekday changes.
     */
    fun clampExercises(exercises: List<RoutineExerciseItem>): List<RoutineExerciseItem> =
        exercises.map { item ->
            if (item.weekday in 1..Weekday.COUNT) {
                item
            } else {
                item.copy(weekday = Weekday.MONDAY)
            }
        }

    /**
     * Whether any exercise sits on a day that is not part of the week.
     *
     * Used by the tests and, in the app, to decide whether a correction pass is needed.
     */
    fun hasStrandedExercises(exercises: List<RoutineExerciseItem>): Boolean =
        exercises.any { it.weekday !in 1..Weekday.COUNT }
}
