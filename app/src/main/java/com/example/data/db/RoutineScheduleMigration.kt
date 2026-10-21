package com.example.data.db

import com.example.data.model.RoutineParser
import com.example.data.model.RoutineSchedule
import com.example.data.model.SavedRoutine

/**
 * Binds the routines that were saved before the seven day model existed to real weekdays.
 *
 * A routine used to be a list of abstract days: "Day 1", "Day 2", "Day 3". The days said nothing
 * about the days of the week, so there was no way to tell an athlete what to train today. The
 * schedule is now a week of seven weekday slots, and every routine has to end up with one.
 *
 * The old days are bound to the weekdays in order, starting at the first day of the week: the
 * first training day of a three day split becomes Monday, the second Tuesday, the third Wednesday,
 * and the rest of the week rests. That mapping cannot be anything else: the old format never said
 * which weekday a day was, so any other reading would be a guess that changes the athlete's plan.
 *
 * What is preserved is everything that says what to train: the exercise names and their ids, the
 * order they are performed in, the sets and reps and loads, the coach notes, the routine name and
 * its description. What changes is only which weekday each group of exercises sits on, and the
 * athlete can move any of them in the editor afterwards.
 *
 * The migration is repeat safe. Each routine carries a flag saying whether it has been through it
 * yet, so a run that is interrupted halfway leaves the untouched routines to be migrated the next
 * time the app opens, and the ones that are done are never touched again.
 */
object RoutineScheduleMigration {

    /** The flag value of a routine whose days are real weekdays. */
    const val SCHEDULE_VERSION = 1

    /**
     * The weekday a routine's days are bound to.
     *
     * Monday, the default first day of the week. The athlete's own preference did not exist when
     * these routines were written, and the setting must never move a day that is already bound, so
     * the binding is fixed rather than following a setting that can change.
     */
    const val MIGRATION_WEEK_STARTS_ON_MONDAY = true

    /**
     * The routine as it looks after the migration.
     *
     * @return the routine to write back, or null when it needs nothing: it is already in the seven
     *         day format, or it holds no exercise at all.
     */
    fun migrate(routine: SavedRoutine): SavedRoutine? {
        if (routine.scheduleVersion >= SCHEDULE_VERSION) return null
        val raw = routine.exercisesJson.trim()
        if (raw.isEmpty()) {
            // Nothing to move. The routine is still marked as done, so it is not looked at again.
            return routine.copy(scheduleVersion = SCHEDULE_VERSION)
        }

        // The exercises are read without the catalog, so a movement that is not in the catalog is
        // still read as the exercise the athlete wrote. Nothing is dropped in the migration.
        //
        // Reading them already binds the old numbered days to the weekdays: the parser lays the
        // days out on the first days of the week, in the order they were written.
        val workouts = RoutineParser.parseExercises(
            rawText = raw,
            allExercises = emptyList(),
            weekStartsOnMonday = MIGRATION_WEEK_STARTS_ON_MONDAY
        )
        if (workouts.isEmpty()) {
            return routine.copy(scheduleVersion = SCHEDULE_VERSION)
        }

        val trainingDays = workouts.map { it.weekday }.distinct().size.coerceAtLeast(1)

        return routine.copy(
            exercisesJson = RoutineParser.serializeExercises(workouts),
            daysPerWeek = trainingDays,
            scheduleVersion = SCHEDULE_VERSION
        )
    }
}
