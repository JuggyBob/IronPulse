package com.example

import com.example.data.ai.NewRoutineProposal
import com.example.data.model.AiRoutineSchedule
import com.example.data.backup.BackupFormat
import com.example.data.backup.BackupMapper
import com.example.data.backup.BackupSchemaMigrations
import com.example.data.backup.JsonNode
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.DropSetRules
import com.example.data.model.LoggedSet
import com.example.data.model.RoutineDropSet
import com.example.data.model.RoutineParser
import com.example.data.model.WorkoutDraft
import com.example.data.model.WorkoutDraftCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Routine text/JSON, draft and backup compatibility for drop sets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DropSetSerializationTest {

    private val bench = Exercise(
        id = 11L,
        name = "Barbell Bench Press",
        category = "Chest",
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        equipment = "Barbell"
    )

    @Test
    fun `routine text parses two reductions as children of the first main set`() {
        val items = RoutineParser.parseSingleWorkout(
            "Barbell Bench Press - 3x8 @ 40kg, drop 30kg x 8, drop 20kg x 6",
            listOf(bench)
        )

        val item = items.single()
        assertEquals(3, item.targetSets)
        assertEquals(2, item.dropSets.size)
        assertTrue(item.dropSets.all { it.mainSetIndex == 1 })
        assertEquals(listOf(1, 2), item.dropSets.map { it.dropIndex })
        assertEquals(listOf(30.0, 20.0), item.dropSets.map { it.targetWeight })
        assertEquals(listOf(8, 6), item.dropSets.map { it.targetReps })
    }

    @Test
    fun `routine json round trips parent index and independent child values`() {
        val item = com.example.data.model.RoutineExerciseItem(
            exerciseId = bench.id,
            exerciseName = bench.name,
            exerciseType = bench.exerciseType,
            category = bench.category,
            equipment = bench.equipment,
            targetSets = 2,
            targetReps = 10,
            dropSets = listOf(
                RoutineDropSet(
                    mainSetIndex = 1,
                    dropIndex = 1,
                    targetReps = 12,
                    targetWeight = 30.0,
                    restSeconds = 45
                ),
                RoutineDropSet(
                    mainSetIndex = 2,
                    dropIndex = 1,
                    targetReps = 6,
                    targetWeight = 20.0
                )
            )
        )

        val parsed = RoutineParser.parseSingleWorkout(
            RoutineParser.serializeExercises(listOf(item)),
            listOf(bench)
        ).single()

        assertEquals(2, parsed.dropSets.size)
        assertEquals(1, parsed.dropSets.first { it.mainSetIndex == 1 }.dropIndex)
        assertEquals(12, parsed.dropSets.first { it.mainSetIndex == 1 }.targetReps)
        assertEquals(45, parsed.dropSets.first { it.mainSetIndex == 1 }.restSeconds)
        assertEquals(2, parsed.dropSets.first { it.mainSetIndex == 2 }.mainSetIndex)
        assertEquals(0, parsed.dropSets.first { it.mainSetIndex == 2 }.restSeconds)
    }

    @Test
    fun `legacy routine json without drop sets parses as no drops`() {
        val legacy = """[{"id":11,"name":"Barbell Bench Press","type":"STANDARD_WEIGHT",
            "sets":3,"reps":8,"weight":40.0,"prescription":"3 sets x 8 reps"}]"""
        val item = RoutineParser.parseSingleWorkout(legacy, listOf(bench)).single()
        assertTrue(item.dropSets.isEmpty())
    }

    @Test
    fun `active draft keeps uids parent links and session-only ids`() {
        val main = LoggedSet(
            sessionId = 4L,
            exerciseId = bench.id,
            exerciseName = bench.name,
            exerciseType = bench.exerciseType,
            setNumber = 1,
            weight = 40.0,
            reps = 8,
            isCompleted = true,
            timestamp = 10L,
            setUid = "main-uid"
        )
        val child = main.copy(
            setNumber = 1,
            weight = 30.0,
            reps = 6,
            timestamp = 11L,
            setUid = "child-uid",
            parentSetUid = "main-uid",
            dropIndex = 1,
            isCompleted = true
        )
        val draft = WorkoutDraft(
            kind = WorkoutDraft.KIND_ROUTINE,
            title = "Push",
            startTime = 5L,
            exerciseIds = listOf(bench.id),
            sets = listOf(main, child),
            routineId = 9L,
            sessionOnlyExerciseIds = setOf(99L)
        )

        val decoded = WorkoutDraftCodec.decode(WorkoutDraftCodec.encode(draft))!!

        assertEquals(listOf("main-uid", "child-uid"), decoded.sets.map { it.setUid })
        assertEquals("main-uid", decoded.sets[1].parentSetUid)
        assertEquals(1, decoded.sets[1].dropIndex)
        assertTrue(decoded.sets[1].isDropSet)
        assertEquals(setOf(99L), decoded.sessionOnlyExerciseIds)

        // JSON null must stay null, not become the string "null": a restored main set that looks
        // like an orphan drop makes normalize detach the whole branch.
        assertNull(decoded.sets[0].parentSetUid)
        assertTrue(!decoded.sets[0].isDropSet)
        val normalized = DropSetRules.normalize(decoded.sets)
        assertTrue(normalized.first { it.setUid == "child-uid" }.isDropSet)
        assertEquals("main-uid", normalized.first { it.setUid == "child-uid" }.parentSetUid)
    }

    @Test
    fun `backup v4 migrates to non-drop sets with deterministic legacy uids`() {
        val sets = JsonNode.Arr(
            mutableListOf(
                JsonNode.Obj().apply {
                    set("id", JsonNode.Num("41"))
                    set("setNumber", JsonNode.Num("1"))
                    set("isWarmup", JsonNode.Bool(true))
                },
                JsonNode.Obj().apply {
                    set("id", JsonNode.Num("42"))
                    set("setNumber", JsonNode.Num("2"))
                }
            )
        )
        val sections = mutableMapOf(
            BackupFormat.SECTION_WORKOUTS to JsonNode.Obj().apply {
                set("sets", sets)
            }
        )

        BackupSchemaMigrations.migrate(sections, fromVersion = 4, toVersion = 5)

        val migrated = sections.getValue(BackupFormat.SECTION_WORKOUTS)["sets"] as JsonNode.Arr
        val first = migrated[0] as JsonNode.Obj
        assertEquals("legacy-41", (first["setUid"] as JsonNode.Str).value)
        assertTrue(first["parentSetUid"] is JsonNode.Null)
        assertEquals(0, (first["dropIndex"] as JsonNode.Num).toInt())
        assertTrue((first["isWarmup"] as JsonNode.Bool).value)
        assertEquals("legacy-42", ((migrated[1] as JsonNode.Obj)["setUid"] as JsonNode.Str).value)
    }

    @Test
    fun `backup mapper preserves a child and derives old uids deterministically`() {
        val parent = LoggedSet(
            id = 1L,
            sessionId = 2L,
            exerciseId = bench.id,
            exerciseName = bench.name,
            exerciseType = bench.exerciseType,
            setNumber = 1,
            weight = 40.0,
            reps = 8,
            isCompleted = true,
            timestamp = 1L,
            setUid = "p"
        )
        val child = parent.copy(
            id = 2L,
            weight = 30.0,
            reps = 6,
            timestamp = 2L,
            setUid = "c",
            parentSetUid = "p",
            dropIndex = 1
        )

        val restoredParent = BackupMapper.setFromDto(BackupMapper.setToDto(parent))
        val restoredChild = BackupMapper.setFromDto(BackupMapper.setToDto(child))
        assertEquals("p", restoredParent.setUid)
        assertEquals("p", restoredChild.parentSetUid)
        assertEquals(1, restoredChild.dropIndex)

        val legacyDto = BackupMapper.setToDto(parent).copy(setUid = "", parentSetUid = null)
        assertEquals("legacy-1", BackupMapper.setFromDto(legacyDto).setUid)
        assertNull(BackupMapper.setFromDto(legacyDto).parentSetUid)
    }

    @Test
    fun `AI proposal accepts drop children and rejects an orphan parent index`() {
        val json = JSONObject(
            """
            {
              "routineName": "Push",
              "days": [{
                "weekday": 1,
                "exercises": [{
                  "name": "Barbell Bench Press",
                  "exerciseId": 11,
                  "sets": 2,
                  "reps": 8,
                  "dropSets": [
                    {"mainSet": 1, "drop": 1, "reps": 10, "weightKg": 30},
                    {"mainSet": 99, "drop": 1, "reps": 6, "weightKg": 20}
                  ]
                }]
              }]
            }
            """.trimIndent()
        )

        val proposal = NewRoutineProposal.fromJson(json)!!
        val exercise = proposal.days.single().exercises.single()
        assertEquals(2, exercise.dropSets.size)
        assertTrue(exercise.dropSets.all { it.mainSetIndex in 1..2 })
        assertEquals(listOf(1, 1), exercise.dropSets.map { it.dropIndex })
        assertEquals(30.0, exercise.toRoutineItem(1, "Push", "").dropSets.first().targetWeight, 0.001)
    }
    @Test
    fun `AI routine schedule reads drop children and keeps the parent main set`() = runBlocking {
        val raw = """
            {
              "routine_name": "Push",
              "days": [{
                "weekday": "Monday",
                "focus": "Push",
                "exercises": [{
                  "name": "Barbell Bench Press",
                  "sets": 3,
                  "reps": 8,
                  "dropSets": [
                    {"mainSet": 1, "drop": 1, "reps": 10, "weight": 30.0},
                    {"mainSet": 1, "drop": 2, "reps": 6, "weight": 20.0}
                  ]
                }]
              }]
            }
        """.trimIndent()

        val parsed = AiRoutineSchedule.parse(
            rawResponse = raw,
            resolveExercise = { name -> if (name == bench.name) bench else null },
            weekStartsOnMonday = true
        )

        val item = parsed.items.single()
        assertEquals(2, item.dropSets.size)
        assertEquals(listOf(1, 2), item.dropSets.map { it.dropIndex })
        assertTrue(item.dropSets.all { it.mainSetIndex == 1 })
        assertEquals(listOf(10, 6), item.dropSets.map { it.targetReps })
    }

    @Test
    fun `AI routine schedule without dropSets still parses`() = runBlocking {
        val raw = """
            {"days":[{"weekday":"Monday","exercises":[
              {"name":"Barbell Bench Press","sets":3,"reps":8}
            ]}]}
        """.trimIndent()
        val parsed = AiRoutineSchedule.parse(
            rawResponse = raw,
            resolveExercise = { name -> if (name == bench.name) bench else null },
            weekStartsOnMonday = true
        )
        assertTrue(parsed.items.single().dropSets.isEmpty())
    }

    @Test
    fun `standalone drop lines attach to the preceding exercise`() {
        val items = RoutineParser.parseSingleWorkout(
            """
            Barbell Bench Press - 3x8 @ 40kg
            Drop set 1: 30kg x 8
            Drop set 2: 20kg x 6
            """.trimIndent(),
            listOf(bench)
        )

        val item = items.single()
        assertEquals(2, item.dropSets.size)
        assertEquals(listOf(1, 2), item.dropSets.map { it.dropIndex })
        assertEquals(listOf(30.0, 20.0), item.dropSets.map { it.targetWeight })
    }

}
