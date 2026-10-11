package com.example

import com.example.data.model.MonthlyExerciseAnalyzer
import com.example.data.model.WeightConversion
import com.example.data.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Weights are stored in kilograms and only converted for display, so switching the unit can never
 * rewrite history. These tests pin the conversion and the "typed value -> stored kilograms" path.
 */
class WeightConversionTest {

    @Test
    fun `one hundred kilograms is two hundred and twenty pounds`() {
        assertEquals(220.462, WeightConversion.fromKg(100.0, WeightUnit.LB), 0.001)
        assertEquals(100.0, WeightConversion.fromKg(100.0, WeightUnit.KG), 0.0001)
    }

    @Test
    fun `round trip through pounds returns the stored kilograms`() {
        listOf(20.0, 60.5, 100.0, 142.857).forEach { kg ->
            val lb = WeightConversion.fromKg(kg, WeightUnit.LB)
            assertEquals(kg, WeightConversion.toKg(lb, WeightUnit.LB), 0.0001)
        }
    }

    @Test
    fun `a barbell plate converts the way a lifter expects`() {
        // The 45 lb plate is 20.4 kg, and a 20 kg plate is 44.1 lb: the conversion must be the
        // real one, not the rounded label a lifter would recognise
        assertEquals(44.09, WeightConversion.fromKg(20.0, WeightUnit.LB), 0.01)
        assertEquals(20.41, WeightConversion.toKg(45.0, WeightUnit.LB), 0.01)
        // And the plate an athlete actually loads comes back out of pounds
        assertEquals(45.0, WeightConversion.fromKg(WeightConversion.toKg(45.0, WeightUnit.LB), WeightUnit.LB), 0.001)
    }

    @Test
    fun `only the label is swapped for anything that is not pounds`() {
        listOf("lb", "LB", " lb ", null, "", "kg", "banana").forEach { label ->
            // "kg" and nonsense must never resolve to pounds, or a broken value would divide by 2.2
            val expected = if (label != null && label.trim().equals("lb", true)) WeightUnit.LB else WeightUnit.KG
            assertEquals(expected, WeightUnit.fromLabel(label))
        }
    }

    @Test
    fun `formatting converts and drops the noise on a round weight`() {
        assertEquals("100 kg", WeightConversion.format(100.0, WeightUnit.KG))
        assertEquals("220.5 lb", WeightConversion.format(100.0, WeightUnit.LB))
        assertEquals("220.5", WeightConversion.format(100.0, WeightUnit.LB, withUnit = false))
        // A pound weight that lands on a whole number reads better without the ".0"
        assertEquals("45 lb", WeightConversion.format(20.4119, WeightUnit.LB))
    }

    @Test
    fun `formatting always uses a dot as the decimal separator`() {
        val previous = Locale.getDefault()
        try {
            // A comma decimal separator would read as a thousands separator on a German device
            Locale.setDefault(Locale.GERMANY)
            assertEquals("80.5 kg", WeightConversion.format(80.5, WeightUnit.KG))
            assertEquals("220.5 lb", WeightConversion.format(100.0, WeightUnit.LB))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `typing pounds is stored as kilograms`() {
        assertEquals(100.0, WeightConversion.parseToKg("220.462", WeightUnit.LB)!!, 0.01)
        assertEquals(80.0, WeightConversion.parseToKg("80", WeightUnit.KG)!!, 0.0001)
    }

    @Test
    fun `an explicit unit in the typed value wins over the preference`() {
        // Pasting a pounds number while the preference is kilograms must not be read as kilograms
        assertEquals(45.36, WeightConversion.parseToKg("100 lb", WeightUnit.KG)!!, 0.01)
        assertEquals(100.0, WeightConversion.parseToKg("100 kg", WeightUnit.LB)!!, 0.0001)
        assertEquals(45.36, WeightConversion.parseToKg("100 lbs", WeightUnit.KG)!!, 0.01)
        assertEquals(100.0, WeightConversion.parseToKg("100 kilograms", WeightUnit.LB)!!, 0.0001)
    }

    @Test
    fun `a comma is read as a decimal separator`() {
        assertEquals(102.1, WeightConversion.parseToKg("102,1", WeightUnit.KG)!!, 0.01)
    }

    @Test
    fun `nonsense or non positive input is rejected instead of being stored as zero`() {
        listOf("", "   ", "abc", "-5", "0", "12abc", "kg").forEach { input ->
            assertNull("'$input' must not parse", WeightConversion.parseToKg(input, WeightUnit.LB))
        }
    }

    @Test
    fun `the ai payload speaks the athletes unit for a pounds athlete`() {
        val kgSets = listOf(
            loggedSet(1, weight = 100.0, reps = 5, daysAgo = 1)
        )
        val summary = MonthlyExerciseAnalyzer.build(
            exerciseName = "Barbell Bench Press",
            exerciseType = "STANDARD_WEIGHT",
            weightUnit = WeightUnit.LB,
            sets = kgSets,
            bodyWeightAt = { 80.0 },
            nowMillis = now()
        )

        val payload = summary.toPromptPayload(MonthlyExerciseAnalyzer.WINDOW_DAYS)

        // The stored kilograms must never leak into the prompt
        assertTrue(payload, payload.contains("UNIT: lb"))
        // 500 kg-reps becomes 1102.3 lb-reps; the stored kilograms must never leak
        assertTrue(payload, payload.contains("1102.3 lb-reps"))
        assertTrue(payload, !payload.contains(" kg"))
        // The per-day label is built in the same unit as the numbers
        assertTrue(payload, payload.contains("220.5lb"))
        // The summary object itself stays in kilograms: 100 kg x 5 is a 112.5 kg Epley 1RM
        assertEquals(112.5, summary.bestOneRm, 0.001)
    }

    @Test
    fun `the ai payload stays in kilograms for a kilograms athlete`() {
        val summary = MonthlyExerciseAnalyzer.build(
            exerciseName = "Barbell Bench Press",
            exerciseType = "STANDARD_WEIGHT",
            weightUnit = WeightUnit.KG,
            sets = listOf(loggedSet(1, weight = 100.0, reps = 5, daysAgo = 1)),
            bodyWeightAt = { 80.0 },
            nowMillis = now()
        )

        val payload = summary.toPromptPayload(MonthlyExerciseAnalyzer.WINDOW_DAYS)
        assertTrue(payload, payload.contains("UNIT: kg"))
        assertTrue(payload, payload.contains("500 kg-reps"))
        assertTrue(payload, payload.contains("100kg"))
    }

    @Test
    fun `a bodyweight exercise is expressed in the athletes unit too`() {
        val summary = MonthlyExerciseAnalyzer.build(
            exerciseName = "Pull-up",
            exerciseType = "PURE_BODYWEIGHT",
            weightUnit = WeightUnit.LB,
            sets = listOf(loggedSet(2, weight = 0.0, reps = 10, daysAgo = 1)),
            bodyWeightAt = { 80.0 },
            nowMillis = now()
        )

        val payload = summary.toPromptPayload(MonthlyExerciseAnalyzer.WINDOW_DAYS)
        assertTrue(payload, payload.contains("bodyweight 176.4lb"))
        assertTrue(payload, payload.contains("235.2 lb"))
    }

    private val dayMs = 24L * 60L * 60L * 1000L

    private fun now() = System.currentTimeMillis()

    private fun loggedSet(
        id: Long,
        weight: Double,
        reps: Int,
        daysAgo: Long
    ) = com.example.data.model.LoggedSet(
        id = id,
        sessionId = 1,
        exerciseId = 1,
        exerciseName = "Barbell Bench Press",
        exerciseType = if (weight > 0.0) "STANDARD_WEIGHT" else "PURE_BODYWEIGHT",
        setNumber = 1,
        reps = reps,
        weight = weight,
        timestamp = now() - daysAgo * dayMs
    )
}