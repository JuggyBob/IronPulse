package com.example.data.model

/**
 * How the exercise list is sorted.
 *
 * There are two independent ways to narrow the catalog: the muscle group chips under the search bar
 * and the body part map. They intersect, so leaving both set hides most of the catalog with no
 * explanation at all. Picking one therefore always clears the other, which is what this type owns:
 * the UI and the view model share the same rule instead of each re-implementing it.
 */
data class ExerciseFilters(
    /** The muscle group chip that is selected, or null for "All". */
    val category: String? = "All",
    /** The body part chosen on the map, if any. */
    val musclePart: MusclePart? = null
) {
    val hasCategory: Boolean get() = !category.isNullOrBlank() && !category.equals("All", ignoreCase = true)
    val hasMusclePart: Boolean get() = musclePart != null

    /** Selects a muscle group chip and drops any body part selection. */
    fun selectCategory(newCategory: String?): ExerciseFilters =
        copy(category = newCategory, musclePart = null)

    /**
     * Selects a body part on the map and drops any muscle group chip. Clearing the body part
     * deliberately leaves the chip alone: that is the "Clear" button, not a new selection.
     */
    fun selectMusclePart(part: MusclePart?): ExerciseFilters =
        if (part == null) copy(musclePart = null) else copy(category = "All", musclePart = part)
}