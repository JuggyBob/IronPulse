package com.example

import com.example.data.model.GibberishNameDetector
import com.example.data.model.ReservedExerciseNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Ask AI" button and the custom exercise dialog both have to refuse nonsense. The detector has
 * to be strict enough to catch keyboard mash and forgiving enough never to block a real exercise.
 */
class GibberishAndReservedNameTest {

    @Test
    fun `real exercises are never rejected`() {
        listOf(
            "Barbell Bench Press",
            "Pull-up",
            "Weighted pull up",
            "Z-Bar Curl",
            "Bulgarian Split Squat",
            "Nordic Hamstring Curl",
            "Single-Arm Dumbbell Row on Bench",
            "Wall Handstand Push-up (Back to Wall)",
            "Copenhagen Plank",
            "Tibialis Raise (Heels on Floor)",
            "Seated Barbell Overhead Press",
            "Dumbbell Wrist Curl",
            "Cable Y-Raise",
            "Pistol Squat",
            "Sled Push",
            "Chest-Supported Row",
            "Freestanding Handstand Push-up",
            "Helms Row",
            "Single-Leg Hip Thrust",
            "Captain's Chair Knee Raise",
            "Rear Delt Fly",
            "Lateral Raise",
            "Hip Thrust",
            "Leg Press",
            "Incline Dumbbell Fly",
            "EZ Bar Curl",
            "Smith Machine Squat",
            "Good Morning",
            "Box Jump",
            "Kettlebell Swing",
            "Turkish Get-up",
            "Banded Pull-up",
            "Ring Dips",
            "Wide-Grip Bench Press"
        ).forEach { name ->
            assertFalse("'$name' is a real exercise and must be accepted", GibberishNameDetector.isGibberish(name))
        }
    }

    @Test
    fun `keyboard mash and nonsense are rejected`() {
        listOf(
            "asdkjhaskdjh",
            "qwertyuiop",
            "zxcvbnm",
            "kjhgfdsa",
            "poiuytrewq",
            "xyzzy",
            "aaaa",
            "asdfghjkl",
            "sdfghjkl;asdfgh",
            "poiuy",
            "lol",
            "aaa",
            "   ",
            ""
        ).forEach { name ->
            assertTrue("'$name' is not an exercise and must be rejected", GibberishNameDetector.isGibberish(name))
        }
    }

    @Test
    fun `an unpronounceable mash with no exercise term is rejected`() {
        // "bzxvkwlm" has no exercise word, no vowel and an unpronounceable consonant run
        assertTrue(GibberishNameDetector.isGibberish("bzxvkwlm"))
        assertTrue(GibberishNameDetector.isGibberish("tkrjvwbqz"))
    }

    @Test
    fun `a typo of a real exercise is still accepted so the AI can fix it`() {
        // Blocking a spelling mistake would stop the athlete from ever getting the real answer
        assertFalse(GibberishNameDetector.isGibberish("squaqt"))
        assertFalse(GibberishNameDetector.isGibberish("bench pres"))
        assertFalse(GibberishNameDetector.isGibberish("benc press"))
        assertFalse(GibberishNameDetector.isGibberish("bularian split"))
    }

    @Test
    fun `the rejection message names the problem instead of failing silently`() {
        val message = GibberishNameDetector.rejectionMessage("asdkjhaskdjh")
        assertTrue(message, message.contains("asdkjhaskdjh"))
        assertTrue(message, message.contains("does not look like a real exercise"))
        assertTrue(message, message.contains("Gemini could not create one"))
    }

    @Test
    fun `a very long mash is shortened in the message`() {
        val message = GibberishNameDetector.rejectionMessage("x".repeat(80))
        assertTrue(message.length < 200)
        assertTrue(message, message.contains("..."))
    }

    @Test
    fun `the two search keywords may never be exercise names`() {
        listOf("ai_generated", "user_generated", "AI_GENERATED", " user_generated ", "Ai_Generated").forEach { name ->
            assertTrue("'$name' is reserved", ReservedExerciseNames.isReserved(name))
        }
    }

    @Test
    fun `an ordinary name that merely contains a keyword is allowed`() {
        // Only an exact match is reserved, otherwise "user_generated squat" could never be created
        listOf("user_generated squat", "my ai_generated exercise", "ai_generated2").forEach { name ->
            assertFalse("'$name' must be allowed", ReservedExerciseNames.isReserved(name))
        }
    }

    @Test
    fun `the explanation tells the athlete what the keyword does`() {
        val message = ReservedExerciseNames.explanation("ai_generated")
        assertTrue(message, message.contains("ai_generated"))
        assertTrue(message, message.contains("search keyword"))
        assertEquals("", ReservedExerciseNames.explanation("Bench Press"))
    }
}