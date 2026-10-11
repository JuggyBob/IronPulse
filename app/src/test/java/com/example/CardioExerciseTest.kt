package com.example

import com.example.data.model.DistanceFormat
import com.example.data.model.Exercise
import com.example.data.model.ExerciseMedia
import com.example.data.model.ExerciseType
import com.example.data.model.LoggedSet
import com.example.data.model.MuscleCategory
import com.example.data.model.MusclePartResolver
import com.example.data.model.WeightUnit
import com.example.data.model.describeCardioSet
import com.example.data.model.describeLoggedSetEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cardio logging: the tag, the inputs it decides, and how a set reads back.
 *
 * The bug these cover: running and the machine based movements were logged as barbell work, so a
 * 5 km run was asked for a weight and a rep count it does not have. CARDIO exists so distance work
 * records a distance and a time instead.
 */
class CardioExerciseTest {

    private fun ex(
        name: String,
        bodyPart: String,
        equipment: String,
        target: String,
        type: String = ExerciseType.STANDARD_WEIGHT
    ) = Exercise(
        name = name,
        category = MuscleCategory.LEGS,
        bodyPart = bodyPart,
        target = target,
        equipment = equipment,
        exerciseType = type
    )

    private fun cardioSet(
        km: Double = 5.0,
        seconds: Int = 0,
        reps: Int = 0,
        weight: Double = 0.0
    ) = LoggedSet(
        sessionId = 1,
        exerciseId = 1,
        exerciseName = "Run",
        exerciseType = ExerciseType.CARDIO,
        setNumber = 1,
        weight = weight,
        reps = reps,
        holdDurationSeconds = seconds,
        distanceKm = km
    )

    // ---------------- The tag ----------------

    @Test
    fun `a cardio body part is cardio regardless of the equipment`() {
        assertTrue(ExerciseType.isCardio("cardio", "body weight", ""))
    }

    @Test
    fun `a treadmill is cardio even when the dataset files it under legs`() {
        // The dataset puts some treadmill work under "upper legs", so the body part alone is not
        // enough to catch it.
        assertTrue(ExerciseType.isCardio("upper legs", "Treadmill", "glutes"))
    }

    @Test
    fun `every machine the dataset uses for cardio is recognised`() {
        for (equipment in listOf(
            "Treadmill", "Stepmill Machine", "Stationary Bike", "Elliptical Machine",
            "Skierg Machine", "Upper Body Ergometer"
        )) {
            assertTrue(
                "\"$equipment\" must log as distance work",
                ExerciseType.isCardio("cardio", equipment, "")
            )
        }
    }

    @Test
    fun `the cardiovascular target is cardio even under an odd body part`() {
        assertTrue(ExerciseType.isCardio("chest", "Bodyweight", "cardiovascular system"))
    }

    @Test
    fun `an ordinary barbell exercise is never cardio`() {
        assertFalse(ExerciseType.isCardio("chest", "Barbell", "pectorals"))
        assertFalse(ExerciseType.isCardio("upper legs", "Barbell", "glutes"))
        assertFalse(ExerciseType.isCardio("back", "Pull-up Bar", "lats"))
    }

    @Test
    fun `a weighted calisthenics exercise is not cardio`() {
        // "Weighted Pull-up" and "Weighted Dips" are added weight, not distance: the weightable
        // patterns must never be captured by the cardio rule.
        assertFalse(ExerciseType.isCardio("back", "Dip Belt", "lats"))
        assertFalse(ExerciseType.isCardio("waist", "Weight Plate", "abs"))
    }

    @Test
    fun `cardio is one of the four types the app knows`() {
        assertEquals(4, ExerciseType.ALL.size)
        assertTrue(ExerciseType.CARDIO in ExerciseType.ALL)
        assertEquals(setOf(ExerciseType.CARDIO), ExerciseType.DISTANCE_BASED)
    }

    // ---------------- The fields it decides ----------------

    @Test
    fun `a cardio exercise offers time and distance and never a load`() {
        val fields = MusclePartResolver.trackedFields(ExerciseType.CARDIO)

        assertTrue(fields.contains("Time (minutes)"))
        assertTrue(fields.contains("Distance"))
        assertFalse("a run has no meaningful weight", fields.contains("Weight"))
        assertFalse("a run has no rep count", fields.contains("Reps"))
    }

    @Test
    fun `the other three types keep their own fields`() {
        assertEquals(
            listOf("Sets", "Reps", "Weight"),
            MusclePartResolver.trackedFields(ExerciseType.STANDARD_WEIGHT)
        )
        assertEquals(
            listOf("Sets", "Reps", "Added weight"),
            MusclePartResolver.trackedFields(ExerciseType.ADDED_WEIGHT)
        )
        assertEquals(
            listOf("Sets", "Reps", "Hold (seconds)"),
            MusclePartResolver.trackedFields(ExerciseType.PURE_BODYWEIGHT)
        )
    }

    @Test
    fun `a cardio set is recognised as distance based`() {
        assertTrue(cardioSet().isDistanceBased)
        assertFalse(
            cardioSet().copy(exerciseType = ExerciseType.STANDARD_WEIGHT).isDistanceBased
        )
    }

    // ---------------- How a set reads back ----------------

    @Test
    fun `a cardio set shows its distance and its time`() {
        val text = describeCardioSet(cardioSet(km = 5.0, seconds = 1500), WeightUnit.KG)

        assertTrue("expected the distance, got \"$text\"", text.contains("5.00 km"))
        assertTrue("expected the time in minutes, got \"$text\"", text.contains("25 min"))
    }

    @Test
    fun `a cardio set never claims it has no reps logged`() {
        // This is the bug in one line: the old description said "no reps logged" for a run.
        val text = describeCardioSet(cardioSet(km = 5.0, seconds = 1500), WeightUnit.KG)

        assertFalse(text.contains("no reps logged"))
        assertFalse(text.contains("0 kg"))
    }

    @Test
    fun `an empty cardio set says so rather than showing a zero`() {
        val text = describeCardioSet(cardioSet(km = 0.0, seconds = 0), WeightUnit.KG)

        assertEquals("no distance logged", text)
    }

    @Test
    fun `a partial run still reports what was recorded`() {
        // Distance without a time, and a time without a distance, are both real: the athlete
        // logged what they knew.
        val distanceOnly = describeCardioSet(cardioSet(km = 5.0, seconds = 0), WeightUnit.KG)
        assertEquals("5.00 km", distanceOnly)

        val timeOnly = describeCardioSet(cardioSet(km = 0.0, seconds = 1800), WeightUnit.KG)
        assertEquals("30 min", timeOnly)
    }

    @Test
    fun `a real remainder of seconds is not rounded away`() {
        // 4:30 is not "4 min", and reporting it as such would quietly under-report every run.
        val text = describeCardioSet(cardioSet(km = 0.0, seconds = 270), WeightUnit.KG)

        assertTrue("expected 4:30, got \"$text\"", text.contains("4:30"))
    }

    @Test
    fun `an ordinary weight set still describes its reps`() {
        val weighted = cardioSet(km = 0.0, reps = 8, weight = 80.0)
            .copy(exerciseType = ExerciseType.STANDARD_WEIGHT)

        assertEquals("8 reps", describeLoggedSetEffort(weighted))
    }

    // ---------------- Distance formatting ----------------

    @Test
    fun `distances are stored in km and converted only for display`() {
        // The same rule as every weight: one canonical unit in the database.
        assertEquals(5.0, DistanceFormat.fromKm(5.0, WeightUnit.KG), 0.0001)
        assertEquals(3.10686, DistanceFormat.fromKm(5.0, WeightUnit.LB), 0.001)
    }

    @Test
    fun `the mile conversion is exact enough to read`() {
        // 1.609344, not 1.6: at 5 km the difference is 0.11 mi, visible on every run.
        val miles = DistanceFormat.fromKm(5.0, WeightUnit.LB)
        assertEquals("3.11", DistanceFormat.format(5.0, WeightUnit.LB))
        assertTrue(miles in 3.10..3.12)
    }

    @Test
    fun `a short distance keeps two decimals and a long one does not`() {
        assertEquals("5.00 km", DistanceFormat.format(5.0, WeightUnit.KG) + " km")
        assertEquals("21.3", DistanceFormat.format(21.34, WeightUnit.KG))
    }

    @Test
    fun `a blank distance formats to nothing rather than to a zero`() {
        // A zero in the field would be read by the athlete as a real measurement of nothing.
        assertEquals("", DistanceFormat.format(0.0, WeightUnit.KG))
    }

    @Test
    fun `typed distances round trip through the unit`() {
        val typed = "5.00"
        val km = DistanceFormat.toKm(typed, WeightUnit.KG)
        assertEquals(5.0, km, 0.0001)
        assertEquals("5.00", DistanceFormat.format(km, WeightUnit.KG))

        val miles = DistanceFormat.toKm("3.1", WeightUnit.LB)
        assertEquals(3.1 * 1.609344, miles, 0.0001)
        assertEquals("3.10", DistanceFormat.format(miles, WeightUnit.LB))
    }

    @Test
    fun `a nonsense distance is rejected rather than stored`() {
        for (input in listOf("", "abc", "-5", "-0.1")) {
            assertEquals("\"$input\" must not become a distance", 0.0, DistanceFormat.toKm(input, WeightUnit.KG), 0.0)
        }
    }

    @Test
    fun `a comma decimal separator is accepted`() {
        // Several locales type a comma; rejecting it would make the field look broken.
        assertEquals(5.5, DistanceFormat.toKm("5,5", WeightUnit.KG), 0.0001)
    }

    @Test
    fun `the unit label follows the athlete's weight unit`() {
        assertEquals("km", DistanceFormat.suffixFor(WeightUnit.KG))
        assertEquals("mi", DistanceFormat.suffixFor(WeightUnit.LB))
    }

    // ---------------- Local media ownership ----------------

    @Test
    fun `only the athlete's own exercises can carry attached media`() {
        // The shipped library is locked, and would be overwritten by the next catalog reseed anyway.
        assertTrue(ExerciseMedia.isUserOwnedMedia(isCustom = true))
        assertFalse(ExerciseMedia.isUserOwnedMedia(isCustom = false))
    }

    @Test
    fun `a picked photo wins over the dataset animation`() {
        val source = ExerciseMedia.resolve(
            localUri = "content://media/external/images/1",
            localIsVideo = false,
            mediaPath = "videos/0025-EIeI8Vf.gif"
        )

        assertEquals(ExerciseMedia.Source.LOCAL_IMAGE, source)
    }

    @Test
    fun `an attached video needs a real player, not an image loader`() {
        assertEquals(
            ExerciseMedia.Source.LOCAL_VIDEO,
            ExerciseMedia.resolve("content://video/7", localIsVideo = true, mediaPath = "videos/0025.gif")
        )
    }

    @Test
    fun `with no local media the dataset animation is used`() {
        assertEquals(
            ExerciseMedia.Source.DATASET_GIF,
            ExerciseMedia.resolve("", localIsVideo = false, mediaPath = "videos/0025-EIeI8Vf.gif")
        )
    }

    @Test
    fun `the gym visual credit is shown for dataset media only`() {
        assertTrue(
            ExerciseMedia.needsAttribution("", false, "videos/0025-EIeI8Vf.gif")
        )
        // Putting a third party credit on the athlete's own photo would be wrong.
        assertFalse(
            ExerciseMedia.needsAttribution("content://media/1", false, "videos/0025-EIeI8Vf.gif")
        )
    }

    @Test
    fun `an unusable uri is refused rather than handed to the loader`() {
        // A pasted http url or a stray path would fail silently and leave a blank box forever.
        assertTrue(ExerciseMedia.isUsableLocalUri("content://media/external/images/1"))
        assertTrue(ExerciseMedia.isUsableLocalUri("file:///data/user/0/img.jpg"))
        assertFalse(ExerciseMedia.isUsableLocalUri("https://example.com/x.gif"))
        assertFalse(ExerciseMedia.isUsableLocalUri("/data/user/0/img.jpg"))
        assertFalse(ExerciseMedia.isUsableLocalUri(""))
    }

    @Test
    fun `an exercise with nothing attached resolves to no media`() {
        assertEquals(
            ExerciseMedia.Source.NONE,
            ExerciseMedia.resolve("", localIsVideo = false, mediaPath = "")
        )
        assertNull(ExerciseMedia.model("", false, ""))
    }
}
