package com.example

import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SupersetRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupersetSerializationTest {

    @Test
    fun `routine JSON preserves superset id and target effort`() {
        val linked = SupersetRules.link(
            listOf(
                RoutineExerciseItem(1, "Push-ups", ExerciseType.PURE_BODYWEIGHT, targetSets = 3),
                RoutineExerciseItem(2, "Rows", ExerciseType.STANDARD_WEIGHT, targetSets = 3)
            ),
            0,
            1
        ).map { it.copy(targetEffortRpe = 8.0) }

        val catalog = listOf(
            Exercise(
                id = 1,
                name = "Push-ups",
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                category = "Chest",
                equipment = "Bodyweight"
            ),
            Exercise(
                id = 2,
                name = "Rows",
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                category = "Back",
                equipment = "Barbell"
            )
        )
        val json = RoutineParser.serializeExercises(linked)
        val parsed = RoutineParser.parseSingleWorkout(json, catalog)
        assertEquals(2, parsed.size)
        assertEquals(linked[0].supersetGroupId, parsed[0].supersetGroupId)
        assertEquals(linked[1].supersetGroupId, parsed[1].supersetGroupId)
        assertEquals(8.0, parsed[0].targetEffortRpe!!, 0.0)
        assertEquals(8.0, parsed[1].targetEffortRpe!!, 0.0)
    }

    @Test
    fun `old routine JSON without the new keys remains ungrouped and has no effort`() {
        val old = """[{"id":1,"name":"Push-ups","type":"PURE_BODYWEIGHT","sets":3,"reps":10}]"""
        val catalog = listOf(
            Exercise(
                id = 1,
                name = "Push-ups",
                exerciseType = ExerciseType.PURE_BODYWEIGHT,
                category = "Chest",
                equipment = "Bodyweight"
            )
        )
        val parsed = RoutineParser.parseSingleWorkout(old, catalog)
        assertEquals(1, parsed.size)
        assertNull(parsed[0].supersetGroupId)
        assertNull(parsed[0].targetEffortRpe)
    }
}
