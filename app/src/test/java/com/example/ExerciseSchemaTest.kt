package com.example

import com.example.data.model.ExerciseSchema
import com.example.data.model.MuscleCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dataset vocabularies have to stay in agreement with each other. The prompts hardcode the body
 * part list, the body map resolves body parts, and the seeder writes them, so a drift in any one of
 * those would silently misfile exercises rather than fail loudly.
 */
class ExerciseSchemaTest {

    @Test
    fun `the body part list is the dataset's ten values`() {
        assertEquals(10, ExerciseSchema.DATASET_BODY_PARTS.size)
        for (part in listOf(
            "chest", "back", "shoulders", "upper arms", "lower arms",
            "waist", "upper legs", "lower legs", "cardio", "neck"
        )) {
            assertTrue("$part must be a known body part", part in ExerciseSchema.DATASET_BODY_PARTS)
        }
    }

    @Test
    fun `every muscle group chip maps to a body part the dataset actually uses`() {
        // This is the fallback for exercises the AI or the athlete creates, so it has to be total:
        // a chip with no mapping would leave a custom exercise with a blank body part.
        for (chip in MuscleCategory.ALL) {
            val mapped = ExerciseSchema.categoryToBodyPart(chip)
            assertTrue(
                "\"$chip\" mapped to \"$mapped\", which is not a dataset body part",
                mapped in ExerciseSchema.DATASET_BODY_PARTS
            )
        }
    }

    @Test
    fun `an unknown category falls back rather than producing a body part nothing matches`() {
        val mapped = ExerciseSchema.categoryToBodyPart("Something Invented")
        assertTrue(mapped in ExerciseSchema.DATASET_BODY_PARTS)
    }

    @Test
    fun `the chip list and the category list are the same set`() {
        assertEquals(
            MuscleCategory.ALL.sorted(),
            ExerciseSchema.MUSCLE_GROUP_CHIPS.sorted()
        )
    }

    @Test
    fun `core and calves map onto the dataset wording rather than the app wording`() {
        // The dataset calls the core "waist" and splits the legs into upper and lower. Getting these
        // wrong puts custom core work on the wrong muscle of the body map.
        assertEquals("waist", ExerciseSchema.categoryToBodyPart(MuscleCategory.CORE))
        assertEquals("upper legs", ExerciseSchema.categoryToBodyPart(MuscleCategory.LEGS))
        assertEquals("chest", ExerciseSchema.categoryToBodyPart(MuscleCategory.CHEST))
        assertEquals("upper arms", ExerciseSchema.categoryToBodyPart(MuscleCategory.ARMS))
    }
}
