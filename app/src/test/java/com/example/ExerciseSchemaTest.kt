package com.example

import com.example.data.model.ExerciseSchema
import com.example.data.model.MuscleCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dataset vocabularies have to stay in agreement with each other.
 *
 * The prompts speak the dataset's lower case keys, the body map resolves body parts, the seeder
 * writes rows, and the exercise editor offers a dropdown. A drift in any one of those used to show
 * up as a muscle tag in lower case that the editor could not select. These tests pin the display
 * spelling and the canonicalisation that keeps stored data inside the dropdown vocabulary.
 */
class ExerciseSchemaTest {

    @Test
    fun `the body part keys are the dataset's ten values`() {
        assertEquals(10, ExerciseSchema.DATASET_BODY_PARTS.size)
        for (part in listOf(
            "chest", "back", "shoulders", "upper arms", "lower arms",
            "waist", "upper legs", "lower legs", "cardio", "neck"
        )) {
            assertTrue("$part must be a known body part", part in ExerciseSchema.DATASET_BODY_PARTS)
        }
    }

    @Test
    fun `the editor options spell every dataset body part in Title Case`() {
        assertEquals(10, ExerciseSchema.BODY_PART_OPTIONS.size)
        assertEquals(
            listOf(
                "Chest", "Back", "Shoulders", "Upper Arms", "Lower Arms",
                "Waist", "Upper Legs", "Lower Legs", "Cardio", "Neck"
            ),
            ExerciseSchema.BODY_PART_OPTIONS
        )
        for (part in ExerciseSchema.DATASET_BODY_PARTS) {
            assertEquals(
                "the stored form of \"$part\" must be an option the editor can show",
                ExerciseSchema.canonicalBodyPart(part),
                ExerciseSchema.BODY_PART_OPTIONS.first { it.equals(part, ignoreCase = true) }
            )
        }
    }

    @Test
    fun `every muscle group chip maps to a body part the editor offers`() {
        // This is the fallback for exercises the AI or the athlete creates, so it has to be total:
        // a chip with no mapping would leave a custom exercise with a blank body part.
        for (chip in MuscleCategory.ALL) {
            val mapped = ExerciseSchema.categoryToBodyPart(chip)
            assertTrue(
                "\"$chip\" mapped to \"$mapped\", which is not an editor body part",
                mapped in ExerciseSchema.BODY_PART_OPTIONS
            )
        }
    }

    @Test
    fun `an unknown category falls back rather than producing a body part nothing matches`() {
        val mapped = ExerciseSchema.categoryToBodyPart("Something Invented")
        assertTrue(mapped in ExerciseSchema.BODY_PART_OPTIONS)
    }

    @Test
    fun `the chip list and the category list are the same set`() {
        assertEquals(
            MuscleCategory.ALL.sorted(),
            ExerciseSchema.MUSCLE_GROUP_CHIPS.sorted()
        )
    }

    @Test
    fun `core and legs map onto the dataset wording rather than the app wording`() {
        // The dataset calls the core "waist" and splits the legs into upper and lower. Getting these
        // wrong puts custom core work on the wrong muscle of the body map.
        assertEquals("Waist", ExerciseSchema.categoryToBodyPart(MuscleCategory.CORE))
        assertEquals("Upper Legs", ExerciseSchema.categoryToBodyPart(MuscleCategory.LEGS))
        assertEquals("Chest", ExerciseSchema.categoryToBodyPart(MuscleCategory.CHEST))
        assertEquals("Upper Arms", ExerciseSchema.categoryToBodyPart(MuscleCategory.ARMS))
    }

    @Test
    fun `a body part is canonicalised no matter how the dataset spells it`() {
        assertEquals("Chest", ExerciseSchema.canonicalBodyPart("chest"))
        assertEquals("Upper Arms", ExerciseSchema.canonicalBodyPart("  UPPER   ARMS "))
        assertEquals("Lower Legs", ExerciseSchema.canonicalBodyPart("lower legs"))
        assertTrue(ExerciseSchema.isKnownBodyPart("Neck"))
        assertFalse(ExerciseSchema.isKnownBodyPart("Something Invented"))
    }

    @Test
    fun `the short gym words in the dataset resolve into the editor targets`() {
        // These are the values that used to appear as lower case tags: the asset says "abs",
        // "delts", "quads", "traps" and "spine", while the editor's dropdown uses the anatomy
        // names. Every one has to land on a value the dropdown can show.
        val aliases = mapOf(
            "abs" to "Abdominals",
            "core" to "Abdominals",
            "delts" to "Deltoids",
            "quads" to "Quadriceps",
            "traps" to "Trapezius",
            "spine" to "Lower Back",
            "levator scapulae" to "Neck",
            "Back" to "Lats",
            "Chest" to "Pectorals",
            "Legs" to "Quadriceps",
            "Shoulders" to "Deltoids"
        )
        for ((raw, expected) in aliases) {
            assertEquals(expected, ExerciseSchema.canonicalTarget(raw))
            assertTrue(
                "$raw must resolve to a target the editor offers",
                ExerciseSchema.isKnownTarget(raw)
            )
        }
    }

    @Test
    fun `a target already in the editor vocabulary keeps its spelling`() {
        for (target in ExerciseSchema.TARGET_MUSCLES) {
            assertEquals(target, ExerciseSchema.canonicalTarget(target))
            assertEquals(target, ExerciseSchema.canonicalTarget(target.lowercase()))
        }
        assertFalse(ExerciseSchema.isKnownTarget("Something Invented"))
    }
}
