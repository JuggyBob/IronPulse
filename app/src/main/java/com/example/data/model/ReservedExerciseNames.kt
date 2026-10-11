package com.example.data.model

/**
 * Names that may never be used for an exercise.
 *
 * `user_generated` and `ai_generated` are not ordinary words: they are the two magic keywords the
 * Exercises search box understands, and typing either one lists a filtered set instead of searching
 * for a name. An exercise actually called that would be unreachable through its own name and would
 * shadow the filter, so it is rejected both in the dialog (with an explanation) and in the view
 * model (in case something else ever writes an exercise).
 */
object ReservedExerciseNames {

    /** The keyword that lists the exercises the user created themselves. */
    const val USER_GENERATED = "user_generated"

    /** The keyword that lists the exercises the AI registered on its own. */
    const val AI_GENERATED = "ai_generated"

    /** Every reserved keyword, in the order they are shown to the user. */
    val ALL: List<String> = listOf(USER_GENERATED, AI_GENERATED)

    /**
     * @return true when [name] is one of the reserved search keywords, whatever its casing or the
     * whitespace around it.
     */
    fun isReserved(name: String): Boolean {
        val trimmed = name.trim()
        return ALL.any { trimmed.equals(it, ignoreCase = true) }
    }

    /** The user facing explanation, shown under the name field. */
    fun explanation(name: String): String {
        val keyword = ALL.firstOrNull { name.trim().equals(it, ignoreCase = true) } ?: return ""
        return "\"$keyword\" is a search keyword, not an exercise name. Type it in the search box to " +
                "list the exercises you created or the ones the AI added."
    }
}