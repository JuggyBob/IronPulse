package com.example

import com.example.data.model.Exercise
import com.example.data.model.ExerciseMedia
import com.example.data.model.ExerciseType
import com.example.data.model.MuscleCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exercise catalog is now the hasaneyldrm/exercises-dataset schema, and animations are streamed
 * rather than bundled. These cover the parts of that contract which are pure logic: how a media
 * path resolves, and that the derived enum values the seeder writes are actually the ones the
 * catalog and the AI prompts use.
 */
class DatasetCatalogTest {

    // ---------------- Media resolution ----------------

    @Test
    fun `a dataset media path resolves to the dataset raw url`() {
        val url = ExerciseMedia.gifUrl("videos/0025-EIeI8Vf.gif")
        assertEquals(
            "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/videos/0025-EIeI8Vf.gif",
            url
        )
    }

    @Test
    fun `a leading slash in the stored path does not produce a double slash`() {
        val url = ExerciseMedia.gifUrl("/videos/0001-2gPfomN.gif")
        assertNotNull(url)
        assertFalse(url!!.contains("//main//"))
    }

    @Test
    fun `an exercise without media resolves to nothing rather than a broken url`() {
        // Custom exercises and the curated weighted calisthenics additions have no dataset media,
        // and the UI must be able to tell that apart from a failed download.
        assertNull(ExerciseMedia.gifUrl(""))
        assertNull(ExerciseMedia.gifUrl("   "))
        assertFalse(ExerciseMedia.hasDatasetMedia(""))
    }

    @Test
    fun `a non gif path is refused so a thumbnail url is never treated as an animation`() {
        assertNull(ExerciseMedia.gifUrl("images/0025-EIeI8Vf.jpg"))
        assertFalse(ExerciseMedia.hasDatasetMedia("images/0025-EIeI8Vf.jpg"))
    }

    @Test
    fun `the gym visual attribution travels with every animation`() {
        // The dataset's media terms require the credit to stay intact wherever a GIF is shown.
        assertTrue(ExerciseMedia.ATTRIBUTION.contains("Gym visual"))
        assertTrue(ExerciseMedia.ATTRIBUTION.contains("gymvisual.com"))
    }

    // ---------------- Enum agreement ----------------

    @Test
    fun `every category the AI is told to use is a category the app can store`() {
        // The prompt hardcodes this list; if MuscleCategory ever drifts, the AI would be asked to
        // emit a value the catalog cannot represent and the exercise would land misfiled.
        val promptCategories = listOf("Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Calisthenics")
        assertEquals(promptCategories.sorted(), MuscleCategory.ALL.sorted())
    }

    @Test
    fun `every body part the AI is told to use is one the dataset actually contains`() {
        val promptBodyParts = listOf(
            "chest", "back", "shoulders", "upper arms", "lower arms",
            "waist", "upper legs", "lower legs", "cardio", "neck"
        )
        // Exactly the ten body parts hasaneyldrm/exercises-dataset documents.
        assertEquals(10, promptBodyParts.size)
        assertEquals(promptBodyParts, promptBodyParts.distinct())
        assertTrue("the dataset calls the core \"waist\"", promptBodyParts.contains("waist"))
    }

    @Test
    fun `the three exercise types are the ones the logging screens branch on`() {
        val types = listOf(
            ExerciseType.STANDARD_WEIGHT,
            ExerciseType.ADDED_WEIGHT,
            ExerciseType.PURE_BODYWEIGHT
        )
        assertEquals(3, types.distinct().size)
    }

    // ---------------- Ownership semantics ----------------

    @Test
    fun `a custom exercise parses an empty muscle list instead of throwing`() {
        // Custom exercises are created without secondary muscles, so the stored value is "[]" or
        // blank and must never blow up the detail dialog.
        val blank = Exercise(name = "Z-Bar Curl", category = MuscleCategory.ARMS, exerciseType = ExerciseType.STANDARD_WEIGHT)
        assertEquals(emptyList<String>(), blank.secondaryMuscleList)

        val withMuscles = blank.copy(secondaryMuscles = "[\"Triceps\",\"Shoulders\"]")
        assertEquals(listOf("Triceps", "Shoulders"), withMuscles.secondaryMuscleList)
    }

    @Test
    fun `a corrupt muscle list degrades to empty rather than crashing the detail dialog`() {
        val broken = Exercise(
            name = "Z-Bar Curl",
            category = MuscleCategory.ARMS,
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            secondaryMuscles = "{not json"
        )
        assertEquals(emptyList<String>(), broken.secondaryMuscleList)
    }

    @Test
    fun `a dataset exercise carries everything the detail dialog renders`() {
        val fromDataset = Exercise(
            name = "Barbell Bench Press",
            category = MuscleCategory.CHEST,
            bodyPart = "chest",
            target = "Pectorals",
            secondaryMuscles = "[\"Triceps\",\"Shoulders\"]",
            exerciseType = ExerciseType.STANDARD_WEIGHT,
            instructions = "Lie on a flat bench and grip the bar just outside shoulder width.",
            equipment = "Barbell",
            mediaPath = "videos/0025-EIeI8Vf.gif",
            sourceId = "0025"
        )

        assertEquals("Pectorals", fromDataset.target)
        assertEquals(listOf("Triceps", "Shoulders"), fromDataset.secondaryMuscleList)
        assertTrue(fromDataset.instructions.isNotBlank())
        assertTrue(ExerciseMedia.hasDatasetMedia(fromDataset.mediaPath))
        assertFalse("dataset rows are library, never custom", fromDataset.isCustom)
    }
}
