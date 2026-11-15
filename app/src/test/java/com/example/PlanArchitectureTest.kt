/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.RoutineModelMigration
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import com.example.data.model.SavedRoutine
import com.example.data.model.Weekday
import com.example.data.model.WorkoutSession
import com.example.data.repository.PlanRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlanArchitectureTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: PlanRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlanRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun item(
        weekday: Int,
        name: String,
        sets: Int = 4,
        reps: Int = 8,
        dayTitle: String = "",
        restSeconds: Int = 90
    ) = RoutineExerciseItem(
        exerciseId = name.hashCode().toLong().let { if (it < 0) -it else it },
        exerciseName = name,
        exerciseType = ExerciseType.STANDARD_WEIGHT,
        category = "Chest",
        equipment = "Barbell",
        targetSets = sets,
        targetReps = reps,
        restSeconds = restSeconds,
        prescription = "$sets sets x $reps reps",
        weekday = weekday,
        dayTitle = dayTitle
    )

    @Test
    fun `legacy multi day routine becomes one Schedule and deduplicated Routines`() = runBlocking {
        val legacy = SavedRoutine(
            id = 100L,
            name = "Push Pull Legs",
            description = "Legacy plan",
            daysPerWeek = 3,
            goal = "Strength & Muscle Growth",
            exercisesJson = RoutineParser.serializeExercises(
                listOf(
                    item(1, "Barbell Bench Press", dayTitle = "Push"),
                    item(3, "Barbell Bent Over Row", dayTitle = "Pull"),
                    item(4, "Barbell Bench Press", dayTitle = "Push")
                )
            )
        )
        db.savedRoutineDao().insertRoutine(legacy)

        RoutineModelMigration.migrate(db)

        val userRoutines = db.savedRoutineDao().getUserRoutinesOnce()
        assertEquals(2, userRoutines.size)
        val schedules = db.schedulePlanDao().getAllSchedulesOnce()
        assertEquals(1, schedules.size)
        assertEquals(100L, schedules.single().legacyRoutineId)
        val slots = db.schedulePlanDao().getDaysForScheduleOnce(schedules.single().id)
        assertEquals(7, slots.size)
        val monday = slots.first { it.weekday == Weekday.MONDAY }.routineId
        val wednesday = slots.first { it.weekday == Weekday.WEDNESDAY }.routineId
        assertNotNull(monday)
        assertNotNull(wednesday)
        assertEquals(monday, slots.first { it.weekday == Weekday.MONDAY }.routineId)
        assertNull(slots.first { it.weekday == Weekday.TUESDAY }.routineId)
        assertNull(slots.first { it.weekday == Weekday.SUNDAY }.routineId)

        val mondayRoutine = userRoutines.first { it.id == monday }
        val mondayItems = RoutineParser.parseSingleWorkout(mondayRoutine.exercisesJson, emptyList())
        assertEquals(listOf("Barbell Bench Press"), mondayItems.map { it.exerciseName })
        assertEquals(0, mondayItems.single().weekday)

        val before = db.savedRoutineDao().getAllRoutinesOnce().size
        RoutineModelMigration.migrate(db)
        assertEquals(before, db.savedRoutineDao().getAllRoutinesOnce().size)
        assertEquals(1, db.schedulePlanDao().getAllSchedulesOnce().size)
    }

    @Test
    fun `legacy backup field aliases still produce a real Schedule`() = runBlocking {
        val legacyJson = """
            [{"exerciseId":7,"exerciseName":"Barbell Bench Press","weekday":1,
              "targetSets":4,"targetReps":6,"restSeconds":120,"dayTitle":"Push"}]
        """.trimIndent()
        db.savedRoutineDao().insertRoutine(
            SavedRoutine(
                id = 101L,
                name = "Old Push",
                description = "v1 backup shape",
                daysPerWeek = 1,
                goal = "Strength",
                exercisesJson = legacyJson
            )
        )
        RoutineModelMigration.migrate(db)
        val schedule = db.schedulePlanDao().getAllSchedulesOnce().single()
        val monday = db.schedulePlanDao().getDaysForScheduleOnce(schedule.id)
            .first { it.weekday == Weekday.MONDAY }
            .routineId
        assertNotNull(monday)
        val routine = db.savedRoutineDao().getRoutineById(monday!!)
        assertNotNull(routine)
        val items = RoutineParser.parseSingleWorkout(routine!!.exercisesJson, emptyList())
        assertEquals(listOf("Barbell Bench Press"), items.map { it.exerciseName })
        assertEquals(4, items.single().targetSets)
        assertEquals(6, items.single().targetReps)
    }

    @Test
    fun `deleting a Routine turns every assigned day into rest and preserves history tables`() = runBlocking {
        val routineId = repository.createRoutine(
            name = "Push Day",
            description = "",
            goal = "Hypertrophy",
            items = listOf(item(0, "Barbell Bench Press"))
        )
        val scheduleId = repository.createSchedule("My Week", "", "Strength")
        repository.setScheduleDay(scheduleId, Weekday.MONDAY, routineId)
        repository.setScheduleDay(scheduleId, Weekday.THURSDAY, routineId)

        repository.deleteRoutine(routineId)

        val slots = repository.getDaysOnce(scheduleId)
        assertNull(slots.first { it.weekday == Weekday.MONDAY }.routineId)
        assertNull(slots.first { it.weekday == Weekday.THURSDAY }.routineId)
        assertNull(repository.getRoutine(routineId))
        assertNotNull(repository.getSchedule(scheduleId))
    }

    @Test
    fun `copying a Schedule duplicates each unique Routine once and preserves repeats`() = runBlocking {
        val push = repository.createRoutine(
            "Push Day", "", "Hypertrophy", listOf(item(0, "Barbell Bench Press"))
        )
        val pull = repository.createRoutine(
            "Pull Day", "", "Hypertrophy", listOf(item(0, "Barbell Bent Over Row"))
        )
        val scheduleId = repository.createSchedule("PPL", "", "Hypertrophy")
        repository.setScheduleDay(scheduleId, Weekday.MONDAY, push)
        repository.setScheduleDay(scheduleId, Weekday.WEDNESDAY, pull)
        repository.setScheduleDay(scheduleId, Weekday.FRIDAY, push)

        val copyId = repository.duplicateSchedule(scheduleId)!!
        val originalSlots = repository.getDaysOnce(scheduleId)
        val copySlots = repository.getDaysOnce(copyId)
        val copyRoutines = repository.getAllRoutinesOnce()
        assertEquals(4, copyRoutines.size)

        val copyMonday = copySlots.first { it.weekday == Weekday.MONDAY }.routineId
        val copyWednesday = copySlots.first { it.weekday == Weekday.WEDNESDAY }.routineId
        val copyFriday = copySlots.first { it.weekday == Weekday.FRIDAY }.routineId
        assertEquals(copyMonday, copyFriday)
        assertNotNull(copyMonday)
        assertNotNull(copyWednesday)
        assert(copyMonday != copyWednesday)
        assert(copyMonday != originalSlots.first { it.weekday == Weekday.MONDAY }.routineId)
        assertEquals(4, repository.getAllRoutinesOnce().size)
    }

    @Test
    fun `copying a Routine creates an independent Routine with a new id`() = runBlocking {
        val originalId = repository.createRoutine(
            "Push Day",
            "Original",
            "Hypertrophy",
            listOf(item(0, "Barbell Bench Press", sets = 4, reps = 6))
        )
        val copyId = repository.duplicateRoutine(originalId)!!
        assertNotNull(copyId)
        assertTrue(copyId != originalId)

        val original = repository.getRoutine(originalId)!!
        val copy = repository.getRoutine(copyId)!!
        assertEquals("Push Day Copy", copy.name)
        assertEquals(
            RoutineParser.parseSingleWorkout(original.exercisesJson, emptyList())
                .map { it.exerciseName to it.targetSets },
            RoutineParser.parseSingleWorkout(copy.exercisesJson, emptyList())
                .map { it.exerciseName to it.targetSets }
        )

        repository.saveRoutine(copy.copy(name = "Edited Copy", description = "changed"))
        assertEquals("Push Day", repository.getRoutine(originalId)!!.name)
        assertEquals("Original", repository.getRoutine(originalId)!!.description)
        assertEquals("Edited Copy", repository.getRoutine(copyId)!!.name)

        repository.deleteRoutine(copyId)
        assertNotNull(repository.getRoutine(originalId))
    }

    @Test
    fun `deleting a Routine leaves completed sessions and logged sets untouched`() = runBlocking {
        val routineId = repository.createRoutine(
            "Push Day", "", "Hypertrophy", listOf(item(0, "Barbell Bench Press"))
        )
        val sessionId = db.workoutDao().insertSession(
            WorkoutSession(
                title = "Push Day",
                routineId = routineId,
                routineWeekday = Weekday.MONDAY,
                plannedDate = 1_760_000_000_000L,
                plannedDateKey = 20261005L
            )
        )
        db.workoutDao().insertSets(
            listOf(
                LoggedSet(
                    sessionId = sessionId,
                    exerciseId = 1L,
                    exerciseName = "Barbell Bench Press",
                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                    setNumber = 1,
                    weight = 80.0,
                    reps = 6,
                    isCompleted = true,
                    timestamp = 1_760_000_100_000L,
                    bodyWeightKg = 80.0
                )
            )
        )

        repository.deleteRoutine(routineId)

        assertNull(repository.getRoutine(routineId))
        val sessions = db.workoutDao().getAllSessionsOnce()
        assertEquals(1, sessions.size)
        assertEquals(routineId, sessions.single().routineId)
        assertEquals("Push Day", sessions.single().title)
        assertEquals(1, db.workoutDao().getAllSets().size)
    }

    @Test
    fun `goal strategy belongs to the Schedule and two Schedules can share one Routine`() = runBlocking {
        val shared = repository.createRoutine(
            "Push Day", "", "Hypertrophy", listOf(item(0, "Barbell Bench Press"))
        )
        val strength = repository.createSchedule("Strength Week", "", "Strength & Power")
        val volume = repository.createSchedule("Volume Week", "", "Hypertrophy & Muscle Growth")
        repository.setScheduleDay(strength, Weekday.MONDAY, shared)
        repository.setScheduleDay(volume, Weekday.MONDAY, shared)

        repository.updateSchedule(
            repository.getSchedule(strength)!!.copy(goal = "Calisthenics Mastery")
        )

        assertEquals("Calisthenics Mastery", repository.getSchedule(strength)!!.goal)
        assertEquals("Hypertrophy & Muscle Growth", repository.getSchedule(volume)!!.goal)
        assertEquals("Hypertrophy", repository.getRoutine(shared)!!.goal)
        assertEquals(
            shared,
            repository.getDaysOnce(volume).first { it.weekday == Weekday.MONDAY }.routineId
        )
    }

    @Test
    fun `a single workout request creates one Routine and no Schedule`() = runBlocking {
        val result = repository.createPlanFromItems(
            name = "Push Day",
            description = "One standalone workout",
            goal = "Hypertrophy",
            items = listOf(
                item(0, "Barbell Bench Press"),
                item(0, "Dumbbell Incline Bench Press")
            )
        )
        assertEquals(1, result.routineIds.size)
        assertNull(result.scheduleId)
        assertEquals(1, repository.getAllRoutinesOnce().size)
        val routine = repository.getRoutine(result.routineIds.single())
        assertNotNull(routine)
        assertEquals(2, RoutineParser.parseSingleWorkout(routine!!.exercisesJson, emptyList()).size)
    }

    @Test
    fun `publishing a multi-day plan creates routines then a seven-slot Schedule`() = runBlocking {
        val result = repository.createPlanFromItems(
            name = "Generated PPL",
            description = "",
            goal = "Hypertrophy",
            items = listOf(
                item(1, "Barbell Bench Press", dayTitle = "Push"),
                item(3, "Barbell Bent Over Row", dayTitle = "Pull"),
                item(4, "Barbell Bench Press", dayTitle = "Push")
            )
        )
        assertEquals(2, result.routineIds.size)
        assertNotNull(result.scheduleId)
        val slots = repository.getDaysOnce(result.scheduleId!!)
        assertEquals(7, slots.size)
        assertEquals(
            slots.first { it.weekday == Weekday.MONDAY }.routineId,
            slots.first { it.weekday == Weekday.THURSDAY }.routineId
        )
    }

    @Test
    fun `batch deleting Routines clears every reference and preserves history`() = runBlocking {
        val push = repository.createRoutine(
            "Push Day", "", "Hypertrophy", listOf(item(0, "Barbell Bench Press"))
        )
        val pull = repository.createRoutine(
            "Pull Day", "", "Hypertrophy", listOf(item(0, "Barbell Bent Over Row"))
        )
        val week = repository.createSchedule("Week", "", "Hypertrophy")
        repository.setScheduleDay(week, Weekday.MONDAY, push)
        repository.setScheduleDay(week, Weekday.THURSDAY, push)
        repository.setScheduleDay(week, Weekday.TUESDAY, pull)
        val sessionId = db.workoutDao().insertSession(
            WorkoutSession(title = "Push Day", routineId = push, routineWeekday = Weekday.MONDAY)
        )

        repository.deleteRoutines(listOf(push, pull))

        assertNull(repository.getRoutine(push))
        assertNull(repository.getRoutine(pull))
        assertTrue(repository.getDaysOnce(week).all { it.routineId == null })
        assertEquals(1, db.workoutDao().getAllSessionsOnce().size)
        assertEquals(sessionId, db.workoutDao().getAllSessionsOnce().single().id)
    }

    @Test
    fun `batch deleting Schedules keeps their referenced Routines`() = runBlocking {
        val push = repository.createRoutine(
            "Push Day", "", "Hypertrophy", listOf(item(0, "Barbell Bench Press"))
        )
        val first = repository.createSchedule("First", "", "Hypertrophy")
        val second = repository.createSchedule("Second", "", "Hypertrophy")
        repository.setScheduleDay(first, Weekday.MONDAY, push)
        repository.setScheduleDay(second, Weekday.FRIDAY, push)

        repository.deleteSchedules(listOf(first, second))

        assertNull(repository.getSchedule(first))
        assertNull(repository.getSchedule(second))
        assertNotNull(repository.getRoutine(push))
        assertTrue(repository.getDaysOnce(first).isEmpty())
    }

    @Test
    fun `clearing all plans removes Schedules and slots without deleting Routines`() = runBlocking {
        repository.createRoutine(
            "Push Day", "", "Hypertrophy", listOf(item(0, "Barbell Bench Press"))
        )
        repository.createSchedule("Week", "", "Hypertrophy")

        repository.clearAllPlans()

        assertTrue(repository.getAllSchedulesOnce().isEmpty())
        assertEquals(1, repository.getAllRoutinesOnce().size)
    }
}
