package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.BodyWeightLog
import com.example.data.model.Exercise
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The repair that runs when the database is opened.
 *
 * The reported bug was an upgraded install where every logged rep read `0KG`, including workouts
 * made after the update. Migration 10 -> 11 fills the snapshot for rows that predate the column,
 * but a build that already reached schema 11 keeps its zero rows forever because the migration
 * never runs again. These tests pin down the safety net that does run on every open.
 *
 * They also pin the retagging of custom bodyweight exercises. The custom form defaults to
 * `STANDARD_WEIGHT` + `Bodyweight`, which saved a bodyweight movement as a barbell movement that
 * could never show the athlete's weight.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BodyWeightRepairTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // === Helpers ===

    private suspend fun session(
        title: String = "Workout",
        startTime: Long = 1_000,
        endTime: Long = 2_000
    ): Long = db.workoutDao().insertSession(
        WorkoutSession(title = title, startTime = startTime, endTime = endTime)
    )

    private suspend fun set(
        sessionId: Long,
        exerciseName: String = "Push-Up",
        exerciseType: String = ExerciseType.PURE_BODYWEIGHT,
        weight: Double = 0.0,
        addedWeight: Double = 0.0,
        reps: Int = 8,
        bodyWeightKg: Double = 0.0,
        timestamp: Long = 1_500
    ): Long = db.workoutDao().insertSet(
        LoggedSet(
            sessionId = sessionId,
            exerciseId = 1,
            exerciseName = exerciseName,
            exerciseType = exerciseType,
            setNumber = 1,
            weight = weight,
            addedWeight = addedWeight,
            reps = reps,
            timestamp = timestamp,
            bodyWeightKg = bodyWeightKg
        )
    )

    private suspend fun weight(timestamp: Long, weightKg: Double) {
        db.bodyWeightDao().insertWeightLog(
            BodyWeightLog(weightKg = weightKg, timestamp = timestamp, note = "Weigh-in")
        )
    }

    private suspend fun profile(weightKg: Double) {
        db.userProfileDao().insertOrUpdate(
            UserProfile(id = 1, name = "Tester", currentWeightKg = weightKg)
        )
    }

    private suspend fun snapshot(sessionId: Long): Double =
        db.workoutDao().getAllSets().first { it.sessionId == sessionId }.bodyWeightKg

    private fun schemaVersion(): Int {
        val cursor = db.query("PRAGMA user_version", emptyArray())
        cursor.moveToFirst()
        val version = cursor.getInt(0)
        cursor.close()
        return version
    }

    // === The snapshot repair ===

    @Test
    fun `the repair uses the latest weigh-in at or before the workout end`() = runBlocking {
        val sessionId = session(endTime = 2_000)
        set(sessionId)
        weight(timestamp = 900, weightKg = 81.5)
        weight(timestamp = 2_500, weightKg = 84.0)

        assertEquals(1, db.workoutDao().repairMissingBodyWeights(79.0))
        assertEquals(81.5, snapshot(sessionId), 0.001)
    }

    @Test
    fun `a weigh-in between the set and the workout end still applies`() = runBlocking {
        // The set was written at t=1500, the athlete weighed in at t=1600 and finished at t=2000.
        // The workout is frozen at its end, so the 1600 weigh-in is the one that applies.
        val sessionId = session(endTime = 2_000)
        set(sessionId, timestamp = 1_500)
        weight(timestamp = 1_600, weightKg = 82.5)

        db.workoutDao().repairMissingBodyWeights(null)
        assertEquals(82.5, snapshot(sessionId), 0.001)
    }

    @Test
    fun `the earliest weigh-in is the fallback when none predates the workout`() = runBlocking {
        val sessionId = session(endTime = 2_000)
        set(sessionId)
        weight(timestamp = 2_500, weightKg = 84.0)
        weight(timestamp = 3_000, weightKg = 82.0)

        db.workoutDao().repairMissingBodyWeights(null)
        // The first weigh-in on record is the best guess for a workout that ended before any of
        // them: 84 kg at t=2500, not the lighter 82 kg logged later.
        assertEquals(84.0, snapshot(sessionId), 0.001)
    }

    @Test
    fun `the passed profile weight is used when there is no weigh-in`() = runBlocking {
        val sessionId = session()
        set(sessionId)

        db.workoutDao().repairMissingBodyWeights(76.5)
        assertEquals(76.5, snapshot(sessionId), 0.001)
    }

    @Test
    fun `the profile stored in Room is used when no live profile is passed`() = runBlocking {
        val sessionId = session()
        set(sessionId)
        profile(weightKg = 78.25)

        db.workoutDao().repairMissingBodyWeights(null)
        assertEquals(78.25, snapshot(sessionId), 0.001)
    }

    @Test
    fun `75 kg is the final fallback when nothing else exists`() = runBlocking {
        val sessionId = session()
        set(sessionId)

        db.workoutDao().repairMissingBodyWeights(null)
        assertEquals(75.0, snapshot(sessionId), 0.001)
    }

    @Test
    fun `a passed zero profile weight is ignored rather than stamped`() = runBlocking {
        val sessionId = session()
        set(sessionId)

        db.workoutDao().repairMissingBodyWeights(0.0)
        assertEquals(75.0, snapshot(sessionId), 0.001)
    }

    @Test
    fun `zero rows are repaired on a database that is already past migration 10 to 11`() = runBlocking {
        // This is the tester's state: the database is already at (or beyond) schema 11, so
        // MIGRATION_10_11 will never run again, and the zero snapshot is still there. The open
        // repair is the only thing that can fix it. An in-memory database is created at the current
        // version, so this is exactly it.
        assertTrue("the test database must be at least schema 11", schemaVersion() >= 11)
        val sessionId = session()
        set(sessionId)
        weight(timestamp = 900, weightKg = 80.0)

        assertEquals(1, db.workoutDao().repairMissingBodyWeights(null))
        assertEquals(80.0, snapshot(sessionId), 0.001)
    }

    @Test
    fun `the repair is idempotent and never rewrites an existing snapshot`() = runBlocking {
        val sessionId = session(endTime = 2_000)
        set(sessionId, exerciseName = "Push-Up", bodyWeightKg = 0.0)
        set(sessionId, exerciseName = "Pull-Up", bodyWeightKg = 80.0)
        weight(timestamp = 900, weightKg = 81.5)

        assertEquals(1, db.workoutDao().repairMissingBodyWeights(79.0))
        val afterFirst = db.workoutDao().getAllSets().associateBy { it.exerciseName }
        assertEquals(81.5, afterFirst.getValue("Push-Up").bodyWeightKg, 0.001)
        assertEquals(80.0, afterFirst.getValue("Pull-Up").bodyWeightKg, 0.001)

        // A later weigh-in must not rewrite either: the first has a snapshot now, and the second
        // already had one.
        weight(timestamp = 1_000, weightKg = 90.0)
        assertEquals(0, db.workoutDao().repairMissingBodyWeights(91.0))

        val afterSecond = db.workoutDao().getAllSets().associateBy { it.exerciseName }
        assertEquals(81.5, afterSecond.getValue("Push-Up").bodyWeightKg, 0.001)
        assertEquals(80.0, afterSecond.getValue("Pull-Up").bodyWeightKg, 0.001)
    }

    // === Retagging custom bodyweight exercises ===

    private suspend fun exercise(
        name: String,
        exerciseType: String = ExerciseType.STANDARD_WEIGHT,
        equipment: String = "Bodyweight",
        isCustom: Boolean = true
    ): Long = db.exerciseDao().insert(
        Exercise(
            name = name,
            category = "Calisthenics",
            exerciseType = exerciseType,
            equipment = equipment,
            isCustom = isCustom
        )
    )

    private suspend fun history(
        exerciseName: String,
        weight: Double = 0.0,
        addedWeight: Double = 0.0,
        reps: Int = 8,
        holdSeconds: Int = 0
    ) {
        val sessionId = session(title = "History")
        db.workoutDao().insertSet(
            LoggedSet(
                sessionId = sessionId,
                exerciseId = 1,
                exerciseName = exerciseName,
                exerciseType = ExerciseType.STANDARD_WEIGHT,
                setNumber = 1,
                weight = weight,
                addedWeight = addedWeight,
                reps = reps,
                holdDurationSeconds = holdSeconds,
                bodyWeightKg = 80.0
            )
        )
    }

    private suspend fun stored(name: String): Exercise =
        db.exerciseDao().getExerciseByName(name) ?: error("missing exercise $name")

    @Test
    fun `a custom bodyweight exercise saved as standard weight is retagged`() = runBlocking {
        exercise("Hindu Push-Up")
        history("Hindu Push-Up", reps = 10)

        assertEquals(1, db.exerciseDao().retagCustomBodyweightExercises())
        assertEquals(ExerciseType.ADDED_WEIGHT, stored("Hindu Push-Up").exerciseType)

        // The logged history follows the exercise, so old rows stop rendering 0 kg too.
        db.workoutDao().syncLoggedSetExerciseTypes()
        assertEquals(
            ExerciseType.ADDED_WEIGHT,
            db.workoutDao().getAllSets().single().exerciseType
        )
    }

    @Test
    fun `a custom exercise whose only history is a hold becomes pure bodyweight`() = runBlocking {
        exercise("Dead Hang")
        history("Dead Hang", reps = 0, holdSeconds = 45)

        assertEquals(1, db.exerciseDao().retagCustomBodyweightExercises())
        assertEquals(ExerciseType.PURE_BODYWEIGHT, stored("Dead Hang").exerciseType)
    }

    @Test
    fun `a custom exercise that has ever carried weight is left alone`() = runBlocking {
        exercise("Weighted Dip")
        history("Weighted Dip", reps = 8)
        history("Weighted Dip", weight = 10.0, reps = 6)

        assertEquals(0, db.exerciseDao().retagCustomBodyweightExercises())
        assertEquals(ExerciseType.STANDARD_WEIGHT, stored("Weighted Dip").exerciseType)
    }

    @Test
    fun `a custom exercise with non bodyweight equipment is left alone`() = runBlocking {
        exercise("Cable Fly", equipment = "Cable")
        history("Cable Fly", reps = 12)

        assertEquals(0, db.exerciseDao().retagCustomBodyweightExercises())
        assertEquals(ExerciseType.STANDARD_WEIGHT, stored("Cable Fly").exerciseType)
    }

    @Test
    fun `a catalog exercise is never retagged`() = runBlocking {
        exercise("Push-Up", equipment = "Bodyweight", isCustom = false)
        history("Push-Up", reps = 12)

        assertEquals(0, db.exerciseDao().retagCustomBodyweightExercises())
        assertEquals(ExerciseType.STANDARD_WEIGHT, stored("Push-Up").exerciseType)
    }

    @Test
    fun `a custom exercise with no history is left alone`() = runBlocking {
        exercise("Future Squat")

        assertEquals(0, db.exerciseDao().retagCustomBodyweightExercises())
        assertEquals(ExerciseType.STANDARD_WEIGHT, stored("Future Squat").exerciseType)
    }

    @Test
    fun `retagging is idempotent`() = runBlocking {
        exercise("Hindu Push-Up")
        history("Hindu Push-Up", reps = 10)

        assertEquals(1, db.exerciseDao().retagCustomBodyweightExercises())
        assertEquals(0, db.exerciseDao().retagCustomBodyweightExercises())
        assertNotNull(stored("Hindu Push-Up"))
        assertTrue(stored("Hindu Push-Up").exerciseType in ExerciseType.BODYWEIGHT)
    }
}
