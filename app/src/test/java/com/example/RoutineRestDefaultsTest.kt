package com.example

import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The per-routine-entry rest default.
 *
 * It is stored inside the routine's flat JSON, not on the exercise: the same movement can want 60
 * seconds in one block and 180 in another. These tests pin down the backward-compatible part, which
 * matters most: a routine saved before the field existed must parse as "no default" and keep the
 * manual timer behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoutineRestDefaultsTest {

    private val bench = Exercise(
        id = 1,
        name = "Barbell Bench Press",
        category = "Chest",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Barbell"
    )

    private fun item(
        name: String = "Barbell Bench Press",
        restSeconds: Int = 0,
        weekday: Int = 1
    ) = RoutineExerciseItem(
        exerciseId = 1,
        exerciseName = name,
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        category = "Chest",
        equipment = "Barbell",
        targetSets = 4,
        targetReps = 8,
        restSeconds = restSeconds,
        weekday = weekday
    )

    @Test
    fun `rest seconds survive a serialize and parse round trip`() {
        val serialized = RoutineParser.serializeExercises(
            listOf(item(restSeconds = 90), item(restSeconds = 120, weekday = 3))
        )

        val parsed = RoutineParser.parseExercises(serialized, listOf(bench))
        assertEquals(2, parsed.size)
        assertEquals(90, parsed[0].restSeconds)
        assertEquals(120, parsed[1].restSeconds)
    }

    @Test
    fun `zero rest is omitted from the stored JSON`() {
        val serialized = RoutineParser.serializeExercises(listOf(item(restSeconds = 0)))
        val obj = JSONArray(serialized).getJSONObject(0)
        assertFalse("0 means no routine default and must not be stored", obj.has("restSeconds"))
    }

    @Test
    fun `a routine saved before the field existed parses as no default`() {
        val legacy = JSONArray().put(
            JSONObject().apply {
                put("id", 1)
                put("name", "Barbell Bench Press")
                put("type", ExerciseType.STANDARD_WEIGHT)
                put("category", "Chest")
                put("equipment", "Barbell")
                put("sets", 4)
                put("reps", 8)
                put("weight", 0.0)
                put("addedWeight", 0.0)
                put("holdSeconds", 0)
                put("prescription", "4 sets x 8 reps")
                put("weekday", 1)
            }
        ).toString()

        val parsed = RoutineParser.parseExercises(legacy, listOf(bench))
        assertEquals(1, parsed.size)
        assertEquals(0, parsed[0].restSeconds)
    }

    @Test
    fun `an out of range rest is clamped instead of starting an absurd timer`() {
        fun parseWith(rest: Any): Int {
            val raw = JSONArray().put(
                JSONObject().apply {
                    put("name", "Barbell Bench Press")
                    put("sets", 4)
                    put("reps", 8)
                    put("restSeconds", rest)
                    put("weekday", 1)
                }
            ).toString()
            return RoutineParser.parseExercises(raw, listOf(bench)).single().restSeconds
        }

        assertEquals(600, parseWith(9_999))
        assertEquals(600, parseWith(601))
        assertEquals(0, parseWith(-30))
        assertEquals(0, parseWith("not a number"))
        assertEquals(0, parseWith(JSONObject.NULL))
        assertEquals(600, parseWith(600))
    }

    @Test
    fun `the same exercise can carry different rests in different routine entries`() {
        val serialized = RoutineParser.serializeExercises(
            listOf(
                item(restSeconds = 60, weekday = 1),
                item(restSeconds = 180, weekday = 4)
            )
        )
        val parsed = RoutineParser.parseExercises(serialized, listOf(bench))
        assertEquals(listOf(60, 180), parsed.map { it.restSeconds })
        // The catalog record itself is untouched: the default belongs to the routine entry.
        assertEquals(ExerciseType.STANDARD_WEIGHT, bench.exerciseType)
        assertEquals("Barbell", bench.equipment)
    }

    @Test
    fun `rest survives even when the exercise is no longer in the catalog`() {
        // The routine must not lose its default just because the library entry was renamed or
        // deleted; the parser falls back to the stored name and keeps the field.
        val serialized = RoutineParser.serializeExercises(
            listOf(item(name = "Retired Movement", restSeconds = 75))
        )
        val parsed = RoutineParser.parseExercises(serialized, emptyList())
        assertEquals(1, parsed.size)
        assertEquals("Retired Movement", parsed[0].exerciseName)
        assertEquals(75, parsed[0].restSeconds)
    }

    @Test
    fun `a stored rest is visible to the routine tool output`() {
        // The read-only tool layer returns the same field, so the coach can answer "how long do I
        // rest on bench?" from the real routine instead of guessing.
        val serialized = RoutineParser.serializeExercises(listOf(item(restSeconds = 45)))
        val json = JSONArray(serialized).getJSONObject(0)
        assertTrue(json.has("restSeconds"))
        assertEquals(45, json.getInt("restSeconds"))
    }
}
