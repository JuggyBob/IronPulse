package com.example.data.model

/**
 * Turns the catalog's instruction text into readable steps.
 *
 * The dataset stores every instruction as one paragraph: five or six coaching sentences run
 * together, which is technically complete and practically unreadable on a phone held at arm's
 * length between sets. The exercise page shows them one step per line with a bullet.
 *
 * The split has to be conservative. A period is also the decimal separator and the end of an
 * abbreviation, so only a sentence ending followed by whitespace and a capital letter (or a digit)
 * starts a new step. Lines the author already broke are respected as they are. Leading bullets and
 * numbers are removed, because the UI adds its own.
 */
object ExerciseInstructions {

    /**
     * A sentence boundary the dataset's prose actually uses: `.` `!` or `?`, then whitespace, then
     * something that starts a sentence. A decimal point is never followed by a space, so "45
     * degrees" and "12.5 kg" stay in one piece. "e.g." and "i.e." are protected separately because
     * they do have a space after them.
     */
    private val SENTENCE_BOUNDARY = Regex("""(?<=[.!?])\s+(?=[A-Z0-9"“])""")

    private val ABBREVIATIONS = listOf("e.g.", "i.e.", "etc.", "vs.", "approx.", "No.")

    /** A bullet or a number the author already put at the start of a step. */
    private val LEADING_MARKER = Regex(
        """^\s*(?:[-•*–—]|\(?\d+[.)]|(?:step|set)\s*\d+\s*[:.)-])\s*""",
        RegexOption.IGNORE_CASE
    )

    /**
     * The instruction text as a list of steps, in the order it is written.
     *
     * Empty lines are dropped, existing line breaks are kept as step boundaries, and every step has
     * any leading bullet or number removed. The returned strings are plain text: the caller draws
     * the bullet, so the same steps can be rendered in any style.
     */
    fun steps(text: String): List<String> {
        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalized.isEmpty()) return emptyList()

        val steps = ArrayList<String>()
        for (rawLine in normalized.split('\n')) {
            val line = rawLine.stripLeadingMarker()
            if (line.isBlank()) continue
            for (sentence in splitSentences(line)) {
                val step = sentence.stripLeadingMarker()
                if (step.isNotBlank()) steps += step
            }
        }
        return steps
    }

    private fun splitSentences(line: String): List<String> {
        // Protect the periods of abbreviations with a character no sentence will contain, split,
        // then put the periods back.
        val protected = StringBuilder(line)
        for (abbreviation in ABBREVIATIONS) {
            var index = protected.indexOf(abbreviation, ignoreCase = true)
            while (index >= 0) {
                protected.setCharAt(index + abbreviation.length - 1, '\u0001')
                index = protected.indexOf(abbreviation, index + abbreviation.length, ignoreCase = true)
            }
        }
        return protected.toString()
            .split(SENTENCE_BOUNDARY)
            .map { it.replace('\u0001', '.') }
    }

    private fun String.stripLeadingMarker(): String = replace(LEADING_MARKER, "").trim()
}
