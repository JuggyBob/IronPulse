package com.example

import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.MonthlyExerciseAnalyzer
import com.example.data.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * The Gemini monthly summary is only as good as the statistics computed locally before the
 * request, so these lock down the window, the grouping and the 1RM / volume maths.
 */
class MonthlyExerciseAnalyzerTest {

    private val now = timestampOf(2026, Calendar.MARCH, 20, 12, 0)
    private val dayMs = 24 * 60 * 60 * 1000L

    private fun benchSet(
        id: Long,
        setNumber: Int,
        weight: Double,
        reps: Int,
        daysAgo: Int
    ) = LoggedSet(
        id = id,
        sessionId = 1L,
        exerciseId = 2L,
        exerciseName = "Barbell Bench Press",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        setNumber = setNumber,
        weight = weight,
        reps = reps,
        timestamp = now - daysAgo * dayMs
    )

    @Test
    fun `epley formula matches the progress chart formula`() {
        // 5 reps at 100kg -> 100 * 36 / (37 - 5) = 112.5
        assertEquals(112.5, MonthlyExerciseAnalyzer.estimateOneRepMax(100.0, 5), 0.001)
        // A single rep is already the max
        assertEquals(100.0, MonthlyExerciseAnalyzer.estimateOneRepMax(100.0, 1), 0.001)
        // No load means no estimate
        assertEquals(0.0, MonthlyExerciseAnalyzer.estimateOneRepMax(0.0, 10), 0.001)
    }

    @Test
    fun `effective weight follows the exercise type rules`() {
        val bodyWeight = { _: Long -> 80.0 }

        val standard = benchSet(1, 1, 100.0, 5, 1)
        assertEquals(100.0, MonthlyExerciseAnalyzer.effectiveWeightOf(standard, bodyWeight)!!, 0.001)

        val added = LoggedSet(
            id = 2, sessionId = 1, exerciseId = 3, exerciseName = "Weighted Pull-up",
            exerciseType = ExerciseType.ADDED_WEIGHT, setNumber = 1, addedWeight = 20.0, reps = 5, timestamp = now
        )
        assertEquals(100.0, MonthlyExerciseAnalyzer.effectiveWeightOf(added, bodyWeight)!!, 0.001)

        val pure = LoggedSet(
            id = 3, sessionId = 1, exerciseId = 4, exerciseName = "Pull-up",
            exerciseType = ExerciseType.PURE_BODYWEIGHT, setNumber = 1, reps = 8, timestamp = now
        )
        assertEquals(80.0, MonthlyExerciseAnalyzer.effectiveWeightOf(pure, bodyWeight)!!, 0.001)
    }

    @Test
    fun `a body weight set with no recorded body weight is unknown, never the current one`() {
        val unknownBodyWeight = { _: Long -> null }
        val bodyWeight = { _: Long -> 80.0 }

        val added = LoggedSet(
            id = 2, sessionId = 1, exerciseId = 3, exerciseName = "Weighted Pull-up",
            exerciseType = ExerciseType.ADDED_WEIGHT, setNumber = 1, addedWeight = 20.0, reps = 5, timestamp = now
        )
        val pure = LoggedSet(
            id = 3, sessionId = 1, exerciseId = 4, exerciseName = "Pull-up",
            exerciseType = ExerciseType.PURE_BODYWEIGHT, setNumber = 1, reps = 8, timestamp = now
        )

        // A standard barbell set needs no body weight at all
        assertEquals(100.0, MonthlyExerciseAnalyzer.effectiveWeightOf(benchSet(1, 1, 100.0, 5, 1), unknownBodyWeight)!!, 0.001)
        assertNull(MonthlyExerciseAnalyzer.effectiveWeightOf(added, unknownBodyWeight))
        assertNull(MonthlyExerciseAnalyzer.effectiveWeightOf(pure, unknownBodyWeight))
        // With a real weigh-in they resolve again
        assertEquals(100.0, MonthlyExerciseAnalyzer.effectiveWeightOf(added, bodyWeight)!!, 0.001)
    }

    @Test
    fun `summary aggregates a month of sets and detects an improving trend`() {
        val sets = listOf(
            benchSet(1, 1, 80.0, 8, 25),
            benchSet(2, 2, 80.0, 8, 25),
            benchSet(3, 1, 85.0, 6, 15),
            benchSet(4, 2, 85.0, 6, 15),
            benchSet(5, 1, 90.0, 5, 5),
            benchSet(6, 2, 90.0, 5, 5)
        )

        val summary = MonthlyExerciseAnalyzer.build(
            exerciseName = "Barbell Bench Press",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            weightUnit = WeightUnit.KG,
            sets = sets,
            bodyWeightAt = { 80.0 },
            nowMillis = now
        )

        assertEquals(3, summary.daysLogged)
        assertEquals(6, summary.setCount)
        // 2x(80x8) + 2x(85x6) + 2x(90x5) = 1280 + 1020 + 900
        assertEquals(3200.0, summary.totalVolume, 0.001)
        // 90kg x 5 -> 90 * 36 / 32 = 101.25
        assertEquals(101.25, summary.bestOneRm, 0.001)
        assertTrue(summary.bestOneRmSource.contains("90kg"))
        assertTrue(summary.bestOneRmSource.contains("5 reps"))
        assertEquals(8, summary.maxReps)
        // 80 * 36/29 = 99.31 -> 90 * 36/32 = 101.25 => improving
        assertTrue("trend should be positive", summary.trendPercent > 0.0)
        assertEquals(3, summary.dailyEntries.size)
    }

    @Test
    fun `sets older than the window are ignored`() {
        val sets = listOf(
            benchSet(1, 1, 100.0, 5, 45),   // 45 days ago -> outside
            benchSet(2, 1, 60.0, 10, 40),  // 40 days ago -> outside
            benchSet(3, 1, 90.0, 5, 3)     // inside
        )

        val summary = MonthlyExerciseAnalyzer.build(
            exerciseName = "Barbell Bench Press",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            weightUnit = WeightUnit.KG,
            sets = sets,
            bodyWeightAt = { 80.0 },
            nowMillis = now
        )

        assertEquals(1, summary.setCount)
        assertEquals(1, summary.daysLogged)
        assertEquals(450.0, summary.totalVolume, 0.001)
    }

    @Test
    fun `bodyweight exercises use the recorded body weight at the time of the set`() {
        val bodyLogs = listOf(
            now - 20 * dayMs to 78.0,
            now - 2 * dayMs to 80.0
        )
        fun bodyWeightAt(timestamp: Long): Double =
            bodyLogs.filter { it.first <= timestamp }.maxByOrNull { it.first }?.second ?: 75.0

        val sets = listOf(
            LoggedSet(
                id = 1, sessionId = 1, exerciseId = 1, exerciseName = "Pull-up",
                exerciseType = ExerciseType.PURE_BODYWEIGHT, setNumber = 1, reps = 10, timestamp = now - 18 * dayMs
            ),
            LoggedSet(
                id = 2, sessionId = 1, exerciseId = 1, exerciseName = "Pull-up",
                exerciseType = ExerciseType.PURE_BODYWEIGHT, setNumber = 2, reps = 12, timestamp = now - 1 * dayMs
            )
        )

        val summary = MonthlyExerciseAnalyzer.build(
            exerciseName = "Pull-up",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            weightUnit = WeightUnit.KG,
            sets = sets,
            bodyWeightAt = ::bodyWeightAt,
            nowMillis = now
        )

        // First day: 78kg x 10, most recent day: 80kg x 12
        assertEquals(78.0 * 10 + 80.0 * 12, summary.totalVolume, 0.001)
        assertTrue(summary.dailyEntries.first().topSetLabel.startsWith("bodyweight 78kg"))
        assertTrue(summary.dailyEntries.last().topSetLabel.startsWith("bodyweight 80kg"))
        // 80 x 12 -> 80 * 36 / 25 = 115.2
        assertEquals(115.2, summary.bestOneRm, 0.001)
    }

    @Test
    fun `no data in the window produces an empty summary`() {
        val summary = MonthlyExerciseAnalyzer.build(
            exerciseName = "Barbell Bench Press",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            weightUnit = WeightUnit.KG,
            sets = listOf(benchSet(1, 1, 100.0, 5, 90)),
            bodyWeightAt = { 80.0 },
            nowMillis = now
        )

        assertEquals(0, summary.setCount)
        assertEquals(0, summary.daysLogged)
        assertEquals(0.0, summary.totalVolume, 0.001)
        assertTrue(summary.dailyEntries.isEmpty())
        assertEquals(0.0, summary.trendPercent, 0.001)
    }

    @Test
    fun `prompt payload contains every number the ai is allowed to use`() {
        val summary = MonthlyExerciseAnalyzer.build(
            exerciseName = "Barbell Bench Press",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            weightUnit = WeightUnit.KG,
            sets = listOf(
                benchSet(1, 1, 80.0, 8, 10),
                benchSet(2, 1, 90.0, 5, 2)
            ),
            bodyWeightAt = { 80.0 },
            nowMillis = now
        )

        val payload = summary.toPromptPayload(MonthlyExerciseAnalyzer.WINDOW_DAYS)

        listOf(
            "EXERCISE: Barbell Bench Press",
            "EXERCISE TYPE: STANDARD_WEIGHT",
            "UNIT: kg",
            "WINDOW: last 30 days",
            "TRAINING DAYS LOGGED: 2",
            "TOTAL SETS LOGGED: 2",
            "TOTAL VOLUME: 1090 kg-reps",
            "BEST ESTIMATED 1RM: 101.3 kg",
            "HIGHEST REP COUNT IN A SINGLE SET: 8",
            "PER DAY BREAKDOWN"
        ).forEach { expected ->
            assertTrue("payload should contain \"$expected\"", payload.contains(expected))
        }
        // Numbers are always formatted for the model with a dot decimal separator
        assertTrue(payload.contains("101.3"))
        assertTrue(!payload.contains("101,3"))
        // One line per training day
        assertEquals(2, payload.lines().count { it.startsWith("- ") })
    }

    private fun timestampOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
