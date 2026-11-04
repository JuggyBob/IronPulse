/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.WorkoutComparisonMatch
import com.example.data.ai.WorkoutComparisonSelector
import com.example.data.db.AppDatabase
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.SessionWithSets
import com.example.data.model.WorkoutAiSummary
import com.example.data.model.WorkoutSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** The comparison identity and the immutable storage of a generated summary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutSummaryTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun startOf(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun session(
        id: Long,
        date: LocalDate,
        title: String,
        routineId: Long = 0L,
        routineWeekday: Int = 0,
        exercise: String = "Push-up",
        reps: Int = 10
    ): SessionWithSets = SessionWithSets(
        session = WorkoutSession(
            id = id,
            title = title,
            startTime = startOf(date) + 12L * 60L * 60L * 1000L,
            endTime = startOf(date) + 13L * 60L * 60L * 1000L,
            routineId = routineId,
            routineWeekday = routineWeekday
        ),
        sets = listOf(
            LoggedSet(
                id = id * 10,
                sessionId = id,
                exerciseId = 1,
                exerciseName = exercise,
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                setNumber = 1,
                reps = reps,
                isCompleted = true
            )
        )
    )

    @Test
    fun `routine sessions compare with the previous same routine weekday`() {
        val monday = LocalDate.of(2026, 10, 26)
        val current = session(3, monday.plusWeeks(2), "Weekly Plan (Push Day)", routineId = 7, routineWeekday = 1)
        val previousMonday = session(
            1,
            monday,
            "Weekly Plan (Push Day)",
            routineId = 7,
            routineWeekday = 1,
            reps = 8
        )
        val closerTuesday = session(
            2,
            monday.plusWeeks(2).minusDays(1),
            "Weekly Plan (Push Day)",
            routineId = 7,
            routineWeekday = 2
        )

        val comparison = WorkoutComparisonSelector.select(
            current,
            listOf(previousMonday, closerTuesday, current)
        )

        assertEquals(WorkoutComparisonMatch.EXACT, comparison.match)
        assertEquals(1L, comparison.previous?.session?.id)
    }

    @Test
    fun `quick workouts with no stable id are disclosed as partial`() {
        val monday = LocalDate.of(2026, 10, 26)
        val current = session(2, monday.plusWeeks(1), "Morning Workout")
        val earlier = session(1, monday, "Morning Workout", reps = 5)

        val comparison = WorkoutComparisonSelector.select(current, listOf(earlier, current))

        assertEquals(WorkoutComparisonMatch.PARTIAL, comparison.match)
        assertEquals(1L, comparison.previous?.session?.id)
    }

    @Test
    fun `no previous session produces an explicit no-comparison result`() {
        val current = session(1, LocalDate.of(2026, 10, 26), "Quick Workout")
        val comparison = WorkoutComparisonSelector.select(current, listOf(current))
        assertEquals(WorkoutComparisonMatch.NONE, comparison.match)
        assertNull(comparison.previous)
    }

    @Test
    fun `a persisted summary cannot be overwritten by a second generation`() = runBlocking {
        db.workoutDao().insertSession(
            WorkoutSession(id = 100, title = "Push Day", startTime = 1_000, endTime = 2_000)
        )
        val first = WorkoutAiSummary(
            workoutId = 100,
            summaryMarkdown = "# Original\n\nFirst result.",
            provider = "google",
            model = "model-a",
            createdAt = 1_000
        )
        val firstId = db.workoutAiSummaryDao().insertIfAbsent(first)
        val duplicateId = db.workoutAiSummaryDao().insertIfAbsent(
            first.copy(
                summaryMarkdown = "# Replaced\n\nThis must never be stored.",
                model = "model-b"
            )
        )

        assertEquals(-1L, duplicateId)
        val stored = db.workoutAiSummaryDao().getSummaryForWorkout(100)
        assertNotNull(stored)
        assertEquals(firstId, stored!!.id)
        assertEquals("# Original\n\nFirst result.", stored.summaryMarkdown)
        assertEquals("model-a", stored.model)
    }

    @Test
    fun `a summary is associated with its exact workout id`() = runBlocking {
        db.workoutDao().insertSession(
            WorkoutSession(id = 201, title = "A", startTime = 1_000, endTime = 2_000)
        )
        db.workoutDao().insertSession(
            WorkoutSession(id = 202, title = "B", startTime = 3_000, endTime = 4_000)
        )
        db.workoutAiSummaryDao().insertIfAbsent(
            WorkoutAiSummary(workoutId = 202, summaryMarkdown = "# B only")
        )

        assertNull(db.workoutAiSummaryDao().getSummaryForWorkout(201))
        assertEquals("# B only", db.workoutAiSummaryDao().getSummaryForWorkout(202)?.summaryMarkdown)
    }
}
