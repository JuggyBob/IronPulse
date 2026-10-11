package com.example.data.model

/**
 * Fuzzy, character aware search over the exercise catalog.
 *
 * A plain alphabetical listing is useless when many exercises share a name: typing "push ups"
 * would hide "Push Ups" between "Archer Push Ups" and "Weighted Push Ups". Results are therefore
 * ranked by how close the typed text is to the exercise **name**, character by character, and only
 * then alphabetically.
 *
 * Ranking ladder (best first):
 *  1. the name matches the query exactly
 *  2. the name and the query share the same beginning (either direction, e.g. "push up" / "push ups")
 *  3. the name contains the whole query at a word boundary (e.g. "archer push ups")
 *  4. the name contains the query anywhere
 *  5. the name is character similar to the query (typo / plural / reordered tolerant)
 *  6. the muscle group contains the query
 *  7. the equipment contains the query
 */
object ExerciseSearch {

    /**
     * Minimum character similarity for a fuzzy name match. Low enough to tolerate typos and
     * plurals, high enough to not return the whole catalog.
     */
    const val FUZZY_MIN_SCORE = 0.45

    /** Fuzzy matching short queries is pure noise, so it is only used from this length on. */
    private const val MIN_FUZZY_QUERY_LENGTH = 3

    /** Lower case, drop everything that is not a letter or a digit, single spaces. */
    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        var pendingSpace = false
        for (ch in raw.lowercase()) {
            if (ch.isLetterOrDigit()) {
                if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                pendingSpace = false
                sb.append(ch)
            } else {
                pendingSpace = true
            }
        }
        return sb.toString()
    }

    /**
     * Dice coefficient over character bigrams: 1.0 for identical strings, degrades smoothly with
     * typos, insertions and reordered words. Runs in O(n) per candidate, which matters because
     * this runs over the whole 800+ exercise catalog on every keystroke.
     */
    fun bigramSimilarity(candidate: String, query: String): Double {
        if (candidate == query) return 1.0
        if (candidate.length < 2 || query.length < 2) return 0.0
        if (candidate.isEmpty() || query.isEmpty()) return 0.0

        val candidateCounts = bigramCounts(candidate)
        val queryCounts = bigramCounts(query)

        var shared = 0
        for ((bigram, count) in candidateCounts) {
            val other = queryCounts[bigram] ?: 0
            shared += if (count < other) count else other
        }
        val total = candidateCounts.values.sum() + queryCounts.values.sum()
        return if (total == 0) 0.0 else (2.0 * shared) / total
    }

    private fun bigramCounts(value: String): Map<String, Int> {
        val counts = HashMap<String, Int>(value.length)
        for (i in 0 until value.length - 1) {
            val bigram = value.substring(i, i + 2)
            counts[bigram] = (counts[bigram] ?: 0) + 1
        }
        return counts
    }

    private fun containsAtWordBoundary(haystack: String, needle: String): Boolean {
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            val endsAtWord = index + needle.length >= haystack.length ||
                !haystack[index + needle.length].isLetterOrDigit()
            if (index == 0 || !haystack[index - 1].isLetterOrDigit()) {
                if (endsAtWord) return true
            }
            index = haystack.indexOf(needle, index + 1)
        }
        return false
    }

    /**
     * Ranks [exercises] against [rawQuery], best match first.
     *
     * A blank query returns the list untouched (the caller decides the default order).
     */
    fun rank(exercises: List<Exercise>, rawQuery: String): List<Exercise> {
        val query = normalize(rawQuery)
        if (query.isEmpty()) return exercises
        if (exercises.size < 2) return exercises

        val allowFuzzy = query.length >= MIN_FUZZY_QUERY_LENGTH
        // Reused for every candidate so it is only built once per keystroke
        val queryBigrams = bigramCounts(query)

        return exercises
            .mapNotNull { exercise ->
                val name = normalize(exercise.name)
                val similarity = if (allowFuzzy) {
                    diceFromBigrams(name, queryBigrams)
                } else {
                    0.0
                }

                val tier: Int
                val score: Double
                when {
                    name == query -> {
                        tier = 0
                        score = 1.0
                    }
                    name.startsWith(query) || query.startsWith(name) -> {
                        tier = 1
                        score = 0.9 + 0.1 * similarity
                    }
                    containsAtWordBoundary(name, query) -> {
                        tier = 2
                        score = 0.8 + 0.15 * similarity
                    }
                    name.contains(query) -> {
                        tier = 3
                        score = 0.65 + 0.2 * similarity
                    }
                    similarity >= FUZZY_MIN_SCORE -> {
                        tier = 4
                        score = similarity
                    }
                    normalize(exercise.category).contains(query) -> {
                        tier = 5
                        score = similarity
                    }
                    normalize(exercise.equipment).contains(query) -> {
                        tier = 6
                        score = similarity
                    }
                    else -> return@mapNotNull null
                }
                ScoredExercise(exercise, tier, score)
            }
            .sortedWith(
                compareBy<ScoredExercise> { it.tier }
                    .thenByDescending { it.score }
                    // A shorter name is the closer match ("push up" before "archer push ups")
                    .thenBy { it.exercise.name.length }
                    .thenBy { it.exercise.name.lowercase() }
            )
            .map { it.exercise }
    }

    /** Dice coefficient reusing pre-computed query bigrams (see [bigramSimilarity]). */
    private fun diceFromBigrams(candidate: String, queryBigrams: Map<String, Int>): Double {
        if (candidate.length < 2) return 0.0
        val candidateCounts = bigramCounts(candidate)
        var shared = 0
        for ((bigram, count) in candidateCounts) {
            val other = queryBigrams[bigram] ?: 0
            shared += if (count < other) count else other
        }
        val total = candidateCounts.values.sum() + queryBigrams.values.sum()
        return if (total == 0) 0.0 else (2.0 * shared) / total
    }

    private data class ScoredExercise(val exercise: Exercise, val tier: Int, val score: Double)
}