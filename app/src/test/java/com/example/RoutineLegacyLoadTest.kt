package com.example

import com.example.data.db.RoutineScheduleMigration
import com.example.data.model.Exercise
import com.example.data.model.ExerciseField
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineDropSet
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineMainSetDefault
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.sanitizedFor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Routines written before the explicit field architecture stored a prescribed dumbbell load in
 * `addedWeight`. After the 4.8.0 field audit, a STANDARD_WEIGHT exercise only records `weight`, so
 * the value disappeared from the editor and sanitizing erased it on save.
 *
 * The JSON below is the real shape found in the athlete's backup: `"weight":0,"addedWeight":9` on
 * "Bentover Rear Delt Fly" (a custom STANDARD_WEIGHT exercise with no explicit field list).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoutineLegacyLoadTest {

    private val rearDelt = Exercise(
        id = 1345,
        name = "Bentover Rear Delt Fly",
        category = "Shoulders",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Dumbbells",
        isCustom = true
    )

    private val bandCurl = Exercise(
        id = 1346,
        name = "Single Arm Band Curl",
        category = "Arms",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Resistance Band",
        isCustom = true
    )

    private val weightedPullUp = Exercise(
        id = 2,
        name = "Weighted Pull-Up",
        category = "Back",
        exerciseType = ExerciseType.ADDED_WEIGHT,
        equipment = "Bodyweight",
        supportedFields = "addedWeight,reps"
    )

    private fun jsonFor(
        id: Long,
        name: String,
        type: String,
        weight: Double,
        addedWeight: Double
    ): String = "[{\"id\":$id,\"name\":\"$name\",\"type\":\"$type\"," +
        "\"category\":\"Shoulders\",\"equipment\":\"Dumbbells\",\"sets\":3,\"reps\":8," +
        "\"weight\":$weight,\"addedWeight\":$addedWeight,\"holdSeconds\":0," +
        "\"restSeconds\":0,\"prescription\":\"3 sets x 8 reps\",\"weekday\":0,\"dayTitle\":\"\"}]"

    @Test
    fun `a dumbbell load stored as added weight becomes the working weight`() {
        val json = jsonFor(1345, "Bentover Rear Delt Fly", ExerciseType.STANDARD_WEIGHT, 0.0, 9.0)
        val items = RoutineParser.parseSingleWorkout(json, listOf(rearDelt))
        assertEquals("json=$json parsed=$items", 1, items.size)
        val parsed = items.single()

        assertEquals(9.0, parsed.targetWeight, 0.001)
        assertEquals(0.0, parsed.targetAddedWeightKg, 0.001)
        // The field list still is the one the exercise actually records.
        assertEquals(
            listOf(ExerciseField.WEIGHT, ExerciseField.REPS),
            rearDelt.exerciseFields
        )
    }

    @Test
    fun `a genuine added-weight exercise keeps its added weight`() {
        val parsed = RoutineParser.parseSingleWorkout(
            jsonFor(2, "Weighted Pull-Up", ExerciseType.ADDED_WEIGHT, 0.0, 9.0),
            listOf(weightedPullUp)
        ).single()

        assertEquals(0.0, parsed.targetWeight, 0.001)
        assertEquals(9.0, parsed.targetAddedWeightKg, 0.001)
    }

    @Test
    fun `sanitizing keeps the migrated load and still clears unsupported targets`() {
        val item = RoutineExerciseItem(
            exerciseId = rearDelt.id,
            exerciseName = rearDelt.name,
            exerciseType = rearDelt.exerciseType,
            targetSets = 3,
            targetReps = 8,
            targetAddedWeightKg = 9.0,
            targetHoldSeconds = 45
        )

        val sanitized = item.sanitizedFor(rearDelt.exerciseFields)

        assertEquals(9.0, sanitized.targetWeight, 0.001)
        assertEquals(0.0, sanitized.targetAddedWeightKg, 0.001)
        // A reps/weight exercise does not record a hold, so that value is still cleared.
        assertEquals(0, sanitized.targetHoldSeconds)
    }

    @Test
    fun `main-set and drop-set defaults migrate the same way`() {
        val item = RoutineExerciseItem(
            exerciseId = rearDelt.id,
            exerciseName = rearDelt.name,
            exerciseType = rearDelt.exerciseType,
            targetSets = 2,
            targetReps = 8,
            targetAddedWeightKg = 9.0,
            mainSetDefaults = listOf(
                RoutineMainSetDefault(
                    mainSetIndex = 2,
                    targetReps = 10,
                    targetAddedWeightKg = 9.0
                )
            ),
            dropSets = listOf(
                RoutineDropSet(
                    mainSetIndex = 1,
                    dropIndex = 1,
                    targetReps = 6,
                    targetAddedWeightKg = 7.0
                )
            )
        )

        val sanitized = item.sanitizedFor(rearDelt.exerciseFields)

        assertEquals(9.0, sanitized.targetWeight, 0.001)
        assertEquals(9.0, sanitized.mainSetDefaults.single().targetWeight, 0.001)
        assertEquals(0.0, sanitized.mainSetDefaults.single().targetAddedWeightKg, 0.001)
        assertEquals(7.0, sanitized.dropSets.single().targetWeight, 0.001)
        assertEquals(0.0, sanitized.dropSets.single().targetAddedWeightKg, 0.001)
    }

    @Test
    fun `a load written only in the text prescription survives the schedule migration`() {
        // This is the shape the 4.4 AI importer produced: the load lives in the line, not in a
        // weight field. Before the 4.5 rebuild was repaired, the text parser hard-coded 0 and the
        // migrated routine lost every prescribed weight.
        val legacyText = """
            Day 1: Upper Body
            - Single Arm Band Curl: 3 x 8 @ 34 kg
        """.trimIndent()
        val legacy = SavedRoutine(
            id = 1,
            name = "Legacy Upper Body",
            description = "AI Parsed Program",
            exercisesJson = legacyText,
            scheduleVersion = 0,
            modelVersion = 1
        )

        val migrated = RoutineScheduleMigration.migrate(legacy)
            ?: error("the schedule migration returned no routine")
        val item = RoutineParser.parseSingleWorkout(migrated.exercisesJson, listOf(bandCurl)).single()

        assertEquals(34.0, item.targetWeight, 0.001)
        assertEquals(0.0, item.targetAddedWeightKg, 0.001)
    }

    @Test
    fun `a load in a JSON prescription is read when the weight field is empty`() {
        val json = "[{\"id\":1346,\"name\":\"Single Arm Band Curl\"," +
            "\"type\":\"STANDARD_WEIGHT\",\"sets\":3,\"reps\":8,\"weight\":0," +
            "\"addedWeight\":0,\"prescription\":\"3 sets x 8 reps @ 34 kg\",\"weekday\":0}]"

        val item = RoutineParser.parseSingleWorkout(json, listOf(bandCurl)).single()

        assertEquals(34.0, item.targetWeight, 0.001)
    }

    @Test
    fun `a plus load in text is an added weight`() {
        val item = RoutineParser.parseSingleWorkout(
            "- Weighted Pull-Up: 3 x 8 @ +9 kg",
            listOf(weightedPullUp)
        ).single()

        assertEquals(0.0, item.targetWeight, 0.001)
        assertEquals(9.0, item.targetAddedWeightKg, 0.001)
    }

    @Test
    fun `a plain load on an added-weight exercise moves to added weight`() {
        val item = RoutineParser.parseSingleWorkout(
            "- Weighted Pull-Up: 3 x 8 @ 9 kg",
            listOf(weightedPullUp)
        ).single()

        assertEquals(0.0, item.targetWeight, 0.001)
        assertEquals(9.0, item.targetAddedWeightKg, 0.001)
    }

    @Test
    fun `a text line without a unit keeps its weight at zero`() {
        val item = RoutineParser.parseSingleWorkout(
            "- Single Arm Band Curl: 3 x 8",
            listOf(bandCurl)
        ).single()

        assertEquals(0.0, item.targetWeight, 0.001)
        assertEquals(0.0, item.targetAddedWeightKg, 0.001)
    }
}
