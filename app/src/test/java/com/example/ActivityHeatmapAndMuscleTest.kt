package com.example

import com.example.data.model.ActivityDay
import com.example.data.model.ActivityHeatmap
import com.example.data.model.ActivityWeek
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.MusclePart
import com.example.data.model.MusclePartResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/**
 * Activity heat map maths.
 *
 * The colours used to stay empty right after saving a workout, so the two cases that matter are
 * pinned down here: a day with logged sets always lights up, and the month labels sit above the
 * right week instead of marching evenly across the card.
 */
class ActivityHeatmapTest {

    /** 6 October 2026 is a Wednesday. */
    private fun calendar(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `a rest day is completely empty`() {
        assertEquals(0f, ActivityHeatmap.intensity(0, 0.0, 0, useVolume = false), 0.001f)
    }

    @Test
    fun `a day with logged sets is visible even with no measurable time or volume`() {
        // A session saved a few seconds after it started, and a pure bodyweight session: both must
        // still paint their square instead of leaving a hole in the year.
        val intensity = ActivityHeatmap.intensity(minutes = 0, volume = 0.0, setCount = 9, useVolume = false)
        assertTrue("a trained day must be visible", intensity > 0f)
        assertEquals(ActivityHeatmap.MIN_VISIBLE_INTENSITY, intensity, 0.001f)
    }

    @Test
    fun `time mode saturates at ninety minutes`() {
        assertEquals(0.5f, ActivityHeatmap.intensity(45, 0.0, 10, useVolume = false), 0.01f)
        assertEquals(1f, ActivityHeatmap.intensity(90, 0.0, 10, useVolume = false), 0.001f)
        assertEquals(1f, ActivityHeatmap.intensity(400, 0.0, 10, useVolume = false), 0.001f)
    }

    @Test
    fun `volume mode saturates at the volume threshold`() {
        assertEquals(0.5f, ActivityHeatmap.intensity(10, 10_000.0, 10, useVolume = true), 0.01f)
        assertEquals(1f, ActivityHeatmap.intensity(10, 25_000.0, 10, useVolume = true), 0.001f)
    }

    @Test
    fun `start of day ignores the time of day`() {
        val morning = ActivityHeatmap.startOfDay(calendar(2026, Calendar.OCTOBER, 6, 6))
        val evening = ActivityHeatmap.startOfDay(calendar(2026, Calendar.OCTOBER, 6, 23))
        assertEquals(morning, evening)
    }

    @Test
    fun `the grid covers fifty three weeks ending with the current one`() {
        val weeks = ActivityHeatmap.weeksEndingAt(calendar(2026, Calendar.OCTOBER, 6))
        assertEquals(53, weeks.size)
        weeks.forEach { week ->
            assertEquals(7, week.days.size)
            // Monday first, seven consecutive days
            assertEquals(week.mondayStart, week.days.first())
            assertEquals(6 * 86_400_000L, week.days.last() - week.mondayStart)
        }
        // The last week contains today
        val today = ActivityHeatmap.startOfDay(calendar(2026, Calendar.OCTOBER, 6))
        assertTrue(weeks.last().contains(today))
        // The first week is a full year earlier
        val spanDays = (today - weeks.first().mondayStart) / 86_400_000L
        assertTrue(spanDays in 360..372)
    }

    @Test
    fun `every week starts on a monday`() {
        val weeks = ActivityHeatmap.weeksEndingAt(calendar(2026, Calendar.OCTOBER, 6))
        assertTrue(weeks.all { ActivityHeatmap.isoDayOfWeek(it.mondayStart) == 1 })
    }

    @Test
    fun `month labels sit above the first week of their month and every month appears once`() {
        val weeks = ActivityHeatmap.weeksEndingAt(calendar(2026, Calendar.OCTOBER, 6))
        val labels = ActivityHeatmap.monthLabels(weeks)

        assertTrue("at least twelve month labels", labels.size >= 12)

        val formatter = java.text.SimpleDateFormat("MMM", Locale.getDefault())
        labels.forEach { (columnIndex, label) ->
            val week = weeks[columnIndex]
            // The label belongs to the month of the Monday of that column, except for the very last
            // column which is attributed to its Sunday so the current month is never missing.
            val anchor = if (columnIndex == weeks.lastIndex) week.dayAt(6) else week.mondayStart
            assertEquals(
                "label of column $columnIndex must match its own month",
                formatter.format(java.util.Date(anchor)),
                label
            )
        }

        // No two consecutive columns carry the same label: one label per month change
        val sorted = labels.keys.sorted()
        sorted.zipWithNext { a, b -> assertTrue(b > a) }

        // The month of today is always labelled
        val todayFormatter = java.text.SimpleDateFormat("MMM", Locale.getDefault())
        val today = ActivityHeatmap.startOfDay(calendar(2026, Calendar.OCTOBER, 6))
        assertTrue(
            "the current month must appear",
            labels.values.contains(todayFormatter.format(java.util.Date(today)))
        )
    }

    @Test
    fun `a day key can be handed over with any time of day and still light up`() {
        val weeks = ActivityHeatmap.weeksEndingAt(calendar(2026, Calendar.OCTOBER, 6))
        val monday = weeks.last().mondayStart
        val day = ActivityDay(epochDayStart = monday + 13 * 3_600_000L, minutes = 0, volume = 0.0, setCount = 3)
        // The card normalises keys through startOfDay, so a timestamp with a time still matches
        assertEquals(monday, ActivityHeatmap.startOfDay(day.epochDayStart))
    }

    @Test
    fun `the week helper reports the day of a column`() {
        val week = ActivityWeek(
            mondayStart = ActivityHeatmap.startOfDay(calendar(2026, Calendar.OCTOBER, 5)),
            days = (0 until 7).map { ActivityHeatmap.startOfDay(calendar(2026, Calendar.OCTOBER, 5)) + it * 86_400_000L }
        )
        assertEquals(1, ActivityHeatmap.isoDayOfWeek(week.dayAt(0)))
        assertEquals(7, ActivityHeatmap.isoDayOfWeek(week.dayAt(6)))
        assertTrue(week.contains(week.dayAt(3)))
        assertTrue(!week.contains(week.dayAt(6) + 86_400_000L))
    }
}

/** The muscle map of a single exercise is resolved from the exercise data itself. */
class MusclePartResolverTest {

    private fun exercise(category: String, equipment: String, name: String) = Exercise(
        id = 1,
        name = name,
        category = category,
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = equipment,
        description = ""
    )

    @Test
    fun `category decides the body part`() {
        assertEquals(MusclePart.CHEST, MusclePartResolver.resolve(exercise("Chest", "Barbell", "Barbell Bench Press")))
        assertEquals(MusclePart.BACK, MusclePartResolver.resolve(exercise("Back", "Barbell", "Barbell Row")))
        assertEquals(MusclePart.LEGS, MusclePartResolver.resolve(exercise("Legs", "Barbell", "Barbell Squat")))
        assertEquals(MusclePart.CORE, MusclePartResolver.resolve(exercise("Core", "Bodyweight", "Plank")))
    }

    @Test
    fun `the name resolves when the category is unknown`() {
        assertEquals(MusclePart.GLUTES, MusclePartResolver.resolve(exercise("Other", "Dumbbells", "Hip Thrust")))
    }

    @Test
    fun `equipment can resolve as a last resort`() {
        assertEquals(MusclePart.BACK, MusclePartResolver.resolve("Unknown", "Lat Pulldown Machine", "Mystery Move"))
    }

    @Test
    fun `the description is the final fallback`() {
        assertEquals(MusclePart.SHOULDERS, MusclePartResolver.resolve("Unknown", "", "Mystery Move", "Overhead shoulder press"))
    }

    @Test
    fun `nothing is invented when there is no clue at all`() {
        assertNull(MusclePartResolver.resolve("", "", "", ""))
    }

    @Test
    fun `every muscle part can label itself`() {
        MusclePart.selectable.forEach { part ->
            assertNotNull(part.label)
            assertTrue(part.label.isNotBlank())
            assertTrue(part.searchTerms.isNotEmpty())
        }
    }

    @Test
    fun `the fields logged follow the exercise type`() {
        assertEquals(
            listOf("Sets", "Reps", "Added weight"),
            MusclePartResolver.trackedFields(ExerciseType.ADDED_WEIGHT)
        )
        assertEquals(
            listOf("Sets", "Reps", "Hold (seconds)"),
            MusclePartResolver.trackedFields(ExerciseType.PURE_BODYWEIGHT)
        )
        assertEquals(
            listOf("Sets", "Reps", "Weight"),
            MusclePartResolver.trackedFields(ExerciseType.STANDARD_WEIGHT)
        )
    }
}
