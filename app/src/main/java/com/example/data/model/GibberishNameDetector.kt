package com.example.data.model

/**
 * Rejects text that is not a real exercise name.
 *
 * The "Ask AI" button accepts whatever the athlete typed, so keyboard mash ("asdkjh"), a typo of a
 * real word ("squaqt") and a nonsense phrase all reach the model. Letting Gemini invent an exercise
 * for those produces a confident, entirely fictional movement that then sits in the athlete's
 * library forever, so the text is checked before a single token is spent on it.
 *
 * The check is deliberately conservative in the direction of *accepting*: a real exercise with an
 * unusual name must never be blocked, so a word that is plausibly an exercise term always passes
 * and only text with no plausible reading at all is rejected.
 */
object GibberishNameDetector {

    /**
     * Word stems that appear in real exercise names. Any hit means the text is readable, whatever
     * else is in it ("z-bar bench curl" passes on "curl").
     */
    private val EXERCISE_TERMS = listOf(
        "squat", "press", "curl", "row", "raise", "fly", "extension", "flexion", "crunch", "dip",
        "deadlift", "bench", "plank", "lunge", "kickback", "shrug", "pullover", "pull-up", "pullup",
        "pull up", "pushup", "push-up", "push up", "chin", "dumbbell", "barbell", "kettlebell",
        "cable", "machine", "resistance", "band", "kettle", "calf", "leg", "arm", "chest", "back",
        "shoulder", "hip", "hamstring", "glute", "tricep", "bicep", "abs", "core", "lat", "trap",
        "deltoid", "pectoral", "quad", "adductor", "abduction", "adduction", "side", "lateral",
        "reverse", "single", "double", "assisted", "weighted", "tempo", "isometric", "hold",
        "sprint", "run", "rower", "bike", "skater", "jump", "hop", "burpee", "mountain", "climber",
        "thrust", "curl", "shrug", "superset", "face", "neck", "rotat", "twist", "reach", "swing",
        "carry", "drag", "sled", "band", "tube", "ring", "bar", "step", "march", "knee", "elbow",
        "wrist", "ankle", "calf", "good", "morning", "farmer", "wall", "floor", "bench", "incline",
        "decline", "flat", "bent", "straight", "alternat", "frog", "donkey", "fire", "hydrant",
        "plié", "plie", "sumo", "split", "lateral", "reverse", "hyperextension", "back", "bridge",
        "tuck", "hollow", "hollow", "bear", "bird", "dog", "dead", "bug", "toe", "touch", "reach"
    )

    /** Rows of a QWERTY keyboard: a run of these is a hand sliding across the keys. */
    private val KEYBOARD_ROWS = listOf(
        "qwertyuiop", "asdfghjkl", "zxcvbnm", "poiuytrewq", "lkjhgfdsa", "mnbvcxz"
    )

    /**
     * @return true when [text] cannot be read as an exercise name.
     */
    fun isGibberish(text: String): Boolean {
        val value = normalize(text)
        if (value.isEmpty()) return true

        // Any readable exercise term is enough to accept the text
        if (EXERCISE_TERMS.any { value.contains(it) }) return false

        // A hand sliding across the keyboard, or a letter held down, is never a word
        if (containsKeyboardRun(value)) return true
        if (Regex("(.)\\1{2,}").containsMatchIn(value.replace(" ", ""))) return true

        // A long run without a vowel cannot be pronounced
        if (Regex("[bcdfghjklmnpqrstvwxz]{5,}").containsMatchIn(value.replace(" ", ""))) return true

        val letters = value.replace(Regex("[^a-z]"), "")

        // "xyzzy" and "kjhgfdsa" have no real vowel at all: with no exercise term to vouch for the
        // text, a name without an a/e/i/o/u is a mash rather than a spelling mistake
        if (letters.length >= 4 && !letters.any { it in "aeiou" }) return true

        // No exercise is called "lol" or "xyz": three letters or fewer with nothing readable in them
        // is a keypress, and every real short name (dip, row, leg, abs) is an exercise term
        if (letters.length in 1..3) return true

        return false
    }

    /**
     * True when [value] walks along a QWERTY row, in either direction.
     *
     * A run of four consecutive keys from the same row is what "poiuy", "asdfghjkl" and
     * "zxcvbnm qwerty" look like once the athlete stops watching the screen. Four is the shortest
     * run that cannot happen by chance in a real word.
     */
    private fun containsKeyboardRun(value: String): Boolean {
        val letters = value.replace(Regex("[^a-z]"), "")
        if (letters.length < 4) return false

        // The whole thing is one row (or a fragment of one)
        if (KEYBOARD_ROWS.any { letters in it }) return true

        // Or a run inside it is
        for (row in KEYBOARD_ROWS) {
            for (start in 0..letters.length - 4) {
                if (letters.substring(start, start + 4) in row) return true
            }
        }
        return false
    }

    /**
     * The message shown when the AI refuses to build an exercise for [text]: it names the problem
     * instead of silently doing nothing.
     */
    fun rejectionMessage(text: String): String {
        val value = normalize(text)
        val shown = if (value.length > 40) value.take(40) + "..." else value
        return "\"$shown\" does not look like a real exercise, so Gemini could not create one. " +
                "Check the spelling, or add the exercise yourself with the + button."
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("[^a-z0-9\\-\\+\\s]"), " ").replace(Regex("\\s+"), " ").trim()
}