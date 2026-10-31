package com.example

import com.example.data.model.ExerciseInstructions
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The exercise page renders the catalog's instruction paragraph as one bullet per step.
 *
 * Splitting prose is easy to get subtly wrong: a decimal point or an abbreviation looks exactly
 * like the end of a sentence, and breaking there would turn "12.5 kg" into two steps. These tests
 * pin both the useful splits and the cases that must not split.
 */
class ExerciseInstructionsTest {

    @Test
    fun `an empty instruction has no steps`() {
        assertEquals(emptyList<String>(), ExerciseInstructions.steps(""))
        assertEquals(emptyList<String>(), ExerciseInstructions.steps("   \n  "))
    }

    @Test
    fun `a run-on paragraph becomes one step per sentence`() {
        val text = "Lie on a flat bench. Grip the bar just outside shoulder width. " +
            "Lower the bar to your chest. Press it back up."
        assertEquals(
            listOf(
                "Lie on a flat bench.",
                "Grip the bar just outside shoulder width.",
                "Lower the bar to your chest.",
                "Press it back up."
            ),
            ExerciseInstructions.steps(text)
        )
    }

    @Test
    fun `decimals are not sentence boundaries`() {
        assertEquals(
            listOf("Set the bench to 30.5 degrees and use 12.5 kg."),
            ExerciseInstructions.steps("Set the bench to 30.5 degrees and use 12.5 kg.")
        )
    }

    @Test
    fun `abbreviations keep their period inside the sentence`() {
        assertEquals(
            listOf(
                "Use a light weight, e.g. 10 kg, on the first set.",
                "Then add weight as needed."
            ),
            ExerciseInstructions.steps(
                "Use a light weight, e.g. 10 kg, on the first set. Then add weight as needed."
            )
        )
    }

    @Test
    fun `line breaks the author wrote are respected`() {
        assertEquals(
            listOf("Stand tall.", "Brace your core.", "Curl the handle."),
            ExerciseInstructions.steps("Stand tall.\nBrace your core.\n\nCurl the handle.")
        )
    }

    @Test
    fun `existing bullets and numbers are removed because the UI adds its own`() {
        assertEquals(
            listOf("Grip the bar.", "Pull yourself up.", "Lower under control."),
            ExerciseInstructions.steps(
                "1. Grip the bar.\n- Pull yourself up.\n• Lower under control."
            )
        )
        assertEquals(
            listOf("Grip the bar.", "Pull yourself up."),
            ExerciseInstructions.steps("Step 1: Grip the bar.\nStep 2: Pull yourself up.")
        )
    }

    @Test
    fun `question and exclamation marks also end a step`() {
        assertEquals(
            listOf("Ready?", "Go!", "Keep breathing."),
            ExerciseInstructions.steps("Ready? Go! Keep breathing.")
        )
    }
}
