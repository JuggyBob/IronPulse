package com.example

import com.example.data.model.BodyWeightTimeline
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.WorkoutDraft
import com.example.data.model.WorkoutDraftCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The behaviour added in round 6: the workout in progress survives the app being closed, the
 * prescription is always short, and a set keeps the body weight it was done at.
 *
 * Robolectric, because the draft and the routine are stored as JSON and org.json is part of the
 * Android runtime rather than the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Round6FeaturesTest {

    // ---------------------------------------------------------------- AI vs user ownership

    @Test
    fun `an AI generated exercise is not labelled as created by the user`() {
        val ai = Exercise(
            id = 1,
            name = "Wall Cuban Rotation",
            category = "Shoulders",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            isCustom = true,
            isAiGenerated = true
        )
        assertFalse("an AI exercise must not claim to be the user's", ai.isUserCreated)
        assertTrue(ai.isAiCreated)
    }

    @Test
    fun `an exercise the user created is labelled as theirs`() {
        val own = Exercise(
            id = 2,
            name = "My Own Movement",
            category = "Chest",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            isCustom = true,
            isAiGenerated = false
        )
        assertTrue(own.isUserCreated)
        assertFalse(own.isAiCreated)
    }

    @Test
    fun `a library exercise belongs to neither`() {
        val library = Exercise(
            id = 3,
            name = "Bench Press",
            category = "Chest",
            exerciseType = ExerciseType.STANDARD_WEIGHT
        )
        assertFalse(library.isUserCreated)
        assertFalse(library.isAiCreated)
    }

    // ---------------------------------------------------------------- short prescriptions

    @Test
    fun `a prescription is reduced to sets times reps and nothing else`() {
        assertEquals("4 sets x 8 reps", RoutineParser.shortPrescription("4 sets x 8 reps"))
        assertEquals("3 sets x 12-15 reps", RoutineParser.shortPrescription("3 sets x 12-15 reps"))
        assertEquals("4 sets x 6 reps", RoutineParser.shortPrescription("4 sets x 6 reps @ 80kg"))
        assertEquals("4 sets x 6 reps", RoutineParser.shortPrescription("4x6 @ +15kg (Dip Belt)", "Bar & Dip Belt"))
        assertEquals("2 sets x 20 reps", RoutineParser.shortPrescription("2 sets x 20 reps bodyweight"))
    }

    @Test
    fun `a hold prescription keeps its seconds`() {
        assertEquals("3 sets x 30s", RoutineParser.shortPrescription("3 sets x 30s"))
        assertEquals("3 sets x 45s", RoutineParser.shortPrescription("3 sets x 45s, rest 60s"))
    }

    @Test
    fun `equipment never survives into the prescription`() {
        assertEquals("4 sets x 8 reps", RoutineParser.shortPrescription("4 sets x 8 reps (Barbell)", "Barbell"))
        assertEquals("", RoutineParser.shortPrescription("Barbell", "Barbell"))
    }

    // ---------------------------------------------------------------- routine added weight

    @Test
    fun `a default added weight survives the routine json round trip`() {
        val item = RoutineExerciseItem(
            exerciseId = 42,
            exerciseName = "Dumbbell Single Leg Squat",
            exerciseType = ExerciseType.ADDED_WEIGHT,
            targetSets = 3,
            targetReps = 10,
            targetAddedWeightKg = 20.0,
            prescription = "3 sets x 10 reps"
        )
        val parsed = RoutineParser.parseExercises(RoutineParser.serializeExercises(listOf(item)), emptyList())
        assertEquals(1, parsed.size)
        assertEquals(20.0, parsed[0].targetAddedWeightKg, 0.0001)
    }

    @Test
    fun `a routine without an added weight has none`() {
        val item = RoutineExerciseItem(exerciseId = 1, exerciseName = "Bench Press")
        val parsed = RoutineParser.parseExercises(RoutineParser.serializeExercises(listOf(item)), emptyList())
        assertEquals(0.0, parsed[0].targetAddedWeightKg, 0.0001)
    }

    // ---------------------------------------------------------------- workout draft

    @Test
    fun `the auto saved workout comes back exactly as it was`() {
        val sets = listOf(
            LoggedSet(
                id = 1,
                sessionId = 0,
                exerciseId = 7,
                exerciseName = "Bench Press",
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                setNumber = 1,
                weight = 60.0,
                reps = 8,
                isCompleted = true,
                timestamp = 1_700_000_000_000L
            ),
            LoggedSet(
                id = 2,
                sessionId = 0,
                exerciseId = 7,
                exerciseName = "Bench Press",
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                setNumber = 2,
                weight = 65.0,
                reps = 6,
                isCompleted = false,
                timestamp = 1_700_000_060_000L
            )
        )
        val draft = WorkoutDraft(
            kind = WorkoutDraft.KIND_QUICK,
            title = "Push Day",
            startTime = 1_700_000_000_000L,
            exerciseIds = listOf(7L, 9L),
            sets = sets
        )

        val decoded = WorkoutDraftCodec.decode(WorkoutDraftCodec.encode(draft))
        assertEquals("Push Day", decoded?.title)
        assertEquals(1_700_000_000_000L, decoded?.startTime)
        assertEquals(listOf(7L, 9L), decoded?.exerciseIds)
        assertEquals(2, decoded?.sets?.size)
        assertEquals(60.0, decoded?.sets?.get(0)?.weight ?: 0.0, 0.0001)
        assertEquals(6, decoded?.sets?.get(1)?.reps)
        assertFalse("an unfinished set must come back unfinished", decoded?.sets?.get(1)?.isCompleted ?: true)
    }

    @Test
    fun `a routine workout draft comes back with its day and its targets`() {
        val draft = WorkoutDraft(
            kind = WorkoutDraft.KIND_ROUTINE,
            title = "Weekly Split",
            startTime = 1_700_000_000_000L,
            exerciseIds = listOf(3L),
            sets = emptyList(),
            routineId = 12L,
            routineGoal = "Strength",
            dayNumber = 2,
            dayTitle = "Day 2: Legs",
            dayNote = "Focus on depth",
            routineItems = listOf(
                RoutineExerciseItem(
                    exerciseId = 3,
                    exerciseName = "Squat",
                    targetSets = 4,
                    targetReps = 5,
                    targetAddedWeightKg = 20.0
                )
            )
        )
        val decoded = WorkoutDraftCodec.decode(WorkoutDraftCodec.encode(draft))
        assertTrue(decoded?.isRoutine ?: false)
        assertEquals(12L, decoded?.routineId)
        assertEquals(2, decoded?.dayNumber)
        assertEquals("Day 2: Legs", decoded?.dayTitle)
        assertEquals("Focus on depth", decoded?.dayNote)
        assertEquals(1, decoded?.routineItems?.size)
        assertEquals(20.0, decoded?.routineItems?.get(0)?.targetAddedWeightKg ?: 0.0, 0.0001)
    }

    @Test
    fun `an unreadable draft is dropped rather than crashing`() {
        assertNull(WorkoutDraftCodec.decode(""))
        assertNull(WorkoutDraftCodec.decode("not json at all"))
        assertNull(WorkoutDraftCodec.decode("{\"sets\": [ }"))
    }

    // ---------------------------------------------------------------- body weight snapshot

    @Test
    fun `a set keeps the body weight it was done at`() {
        val set = LoggedSet(
            sessionId = 0,
            exerciseId = 1,
            exerciseName = "Pull-up",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            setNumber = 1,
            reps = 10,
            bodyWeightKg = 80.0
        )
        // The athlete weighs in today and is now 82 kg. The set from last month is still 80.
        assertEquals(80.0, BodyWeightTimeline.bodyWeightForSet(set, 82.0) ?: 0.0, 0.0001)
    }

    @Test
    fun `a set logged before the snapshot existed falls back to the profile weight`() {
        val set = LoggedSet(
            sessionId = 0,
            exerciseId = 1,
            exerciseName = "Pull-up",
            exerciseType = ExerciseType.PURE_BODYWEIGHT,
            setNumber = 1,
            reps = 10,
            bodyWeightKg = 0.0
        )
        assertEquals(82.0, BodyWeightTimeline.bodyWeightForSet(set, 82.0) ?: 0.0, 0.0001)
    }

    @Test
    fun `two sets done at different body weights are two different tags`() {
        val older = LoggedSet(
            sessionId = 0, exerciseId = 1, exerciseName = "Pull-up",
            exerciseType = ExerciseType.PURE_BODYWEIGHT, setNumber = 1, reps = 10, bodyWeightKg = 80.0
        )
        val newer = LoggedSet(
            sessionId = 0, exerciseId = 1, exerciseName = "Pull-up",
            exerciseType = ExerciseType.PURE_BODYWEIGHT, setNumber = 1, reps = 12, bodyWeightKg = 82.0
        )
        val tags = listOf(older, newer)
            .mapNotNull { BodyWeightTimeline.bodyWeightForSet(it, 82.0) }
            .distinct()
            .sorted()
        assertEquals(listOf(80.0, 82.0), tags)
    }
}
