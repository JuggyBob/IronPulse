package com.example.data.model

import java.util.Calendar
import java.util.Locale

/**
 * The seven weekdays a routine is scheduled on.
 *
 * A weekday is identified by its real day of the week, never by its position in a list. The
 * athlete's week may start on Monday or on Sunday, the routine editor may show the days in either
 * order, and the AI may hand the days back in any order at all, but "Wednesday" is Wednesday
 * whatever is displayed first.
 *
 * The numbering is ISO: 1 = Monday ... 7 = Sunday. It is the same numbering
 * [ActivityHeatmap.isoDayOfWeek] already uses for the activity grid, so there is one convention in
 * the app rather than two.
 */
object Weekday {

    const val MONDAY = 1
    const val TUESDAY = 2
    const val WEDNESDAY = 3
    const val THURSDAY = 4
    const val FRIDAY = 5
    const val SATURDAY = 6
    const val SUNDAY = 7

    /** How many days a week has. */
    const val COUNT = 7

    /** The full name of each weekday, Monday first. */
    val names: List<String> = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

    /** The three letter name of each weekday, Monday first. */
    val shortNames: List<String> = names.map { it.take(3) }

    /** The single letter of each weekday, Monday first. */
    val initials: List<String> = names.map { it.take(1) }

    /** The full name of a weekday. */
    fun name(weekday: Int): String = names.getOrElse(weekday - 1) { "Day ${weekday.coerceIn(1, COUNT)}" }

    /** The three letter name of a weekday. */
    fun shortName(weekday: Int): String = shortNames.getOrElse(weekday - 1) { "?" }

    /** The single letter of a weekday. */
    fun initial(weekday: Int): String = initials.getOrElse(weekday - 1) { "?" }

    /**
     * The weekday a timestamp falls on.
     *
     * Local time, because the athlete's week is their local week: a workout at 00:30 on Wednesday
     * is a Wednesday workout.
     */
    fun of(timestamp: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val raw = cal.get(Calendar.DAY_OF_WEEK)
        return if (raw == Calendar.SUNDAY) SUNDAY else raw - 1
    }

    /** The weekday today is, in local time. */
    fun today(): Int = of(System.currentTimeMillis())

    /**
     * The weekday after [weekday], wrapping around the week.
     */
    fun next(weekday: Int): Int = if (weekday >= COUNT) MONDAY else weekday + 1

    /**
     * The weekday before [weekday], wrapping around the week.
     */
    fun previous(weekday: Int): Int = if (weekday <= 1) COUNT else weekday - 1

    /**
     * The seven weekdays in the order they are displayed.
     *
     * @param weekStartsOnMonday true for Monday first, false for Sunday first.
     */
    fun ordered(weekStartsOnMonday: Boolean): List<Int> =
        if (weekStartsOnMonday) {
            (MONDAY..SUNDAY).toList()
        } else {
            // The Sunday comes first and the rest of the week follows it, so it cannot be written
            // as a range: a range from Sunday to Monday would be empty.
            listOf(SUNDAY) + (MONDAY..SATURDAY).toList()
        }

    /**
     * The first weekday of a week, which is where an unspecified sequence of workouts begins.
     */
    fun first(weekStartsOnMonday: Boolean): Int = if (weekStartsOnMonday) MONDAY else SUNDAY

    /**
     * The weekday a name refers to.
     *
     * Accepts the full name, the short name, the single letter and the number, in any case. The
     * number is the ISO weekday, so a routine that says "3" means Wednesday.
     *
     * @return the weekday, or null when the name is not a weekday.
     */
    fun fromName(raw: String?): Int? {
        val text = raw?.trim()?.lowercase()?.removeSuffix(".").orEmpty()
        if (text.isEmpty()) return null

        // A number is the ISO weekday. Only 1..7 is a weekday, so "0" and "8" are not.
        text.toIntOrNull()?.let { number ->
            return if (number in 1..COUNT) number else null
        }

        names.forEachIndexed { index, name ->
            val lower = name.lowercase()
            if (text == lower || text == lower.take(3) || text == lower.take(1)) return index + 1
        }
        return null
    }

    /**
     * The weekday a name refers to, with a fallback for a name that is not one.
     */
    fun fromNameOr(raw: String?, fallback: Int): Int = fromName(raw) ?: fallback.coerceIn(1, COUNT)

    /**
     * A localised weekday name, for a screen that shows the athlete their own language.
     */
    fun localisedName(weekday: Int, locale: Locale = Locale.getDefault()): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, if (weekday == SUNDAY) Calendar.SUNDAY else weekday + 1)
        return cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, locale)
            ?: name(weekday)
    }
}
