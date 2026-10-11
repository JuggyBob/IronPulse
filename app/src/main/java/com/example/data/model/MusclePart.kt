package com.example.data.model

/**
 * The body parts the muscle map can show.
 *
 * Each one owns a set of vector regions of the anatomical illustration, so the tap target and the
 * highlight are always the exact same shape: they can never drift apart.
 */
enum class MusclePart(val label: String, val searchTerms: List<String>) {
    CHEST("Chest", listOf("chest", "pec", "pectoral", "bench", "press", "flye", "fly", "dip")),
    BACK("Back", listOf("back", "lats", "lat", "row", "pull", "pulldown", "pullover", "deadlift")),
    SHOULDERS("Shoulders", listOf("shoulder", "deltoid", "delts", "lateral raise", "overhead press")),
    ARMS("Arms", listOf("arm", "biceps", "triceps", "curl", "extension", "press-up", "pushup")),
    CORE("Core", listOf("core", "abs", "abdominal", "plank", "hollow", "leg raise", "twist", "sit-up")),
    LEGS("Legs", listOf("leg", "squat", "lunge", "calf", "thigh", "hamstring", "split squat", "romanian")),
    GLUTES("Glutes", listOf("glute", "hip thrust", "hipthrust", "glute bridge")),
    TRAPS("Traps", listOf("trap", "shrug", "upper back")),
    FOREARMS("Forearms", listOf("forearm", "wrist", "grip", "dead hang", "hang")),
    FULL_BODY("Full Body", listOf("full body", "everything", "compound", "clean and jerk", "snatch", "thruster"));

    companion object {
        val selectable: List<MusclePart> = listOf(
            CHEST, BACK, SHOULDERS, ARMS, CORE, LEGS, GLUTES, TRAPS, FOREARMS, FULL_BODY
        )
    }
}

/**
 * Maps an exercise to the body part its muscle map should highlight.
 *
 * The muscle category always wins (it is the most reliable field), then the name, then the
 * equipment and finally the description, which lets an AI generated exercise land on the right
 * region even with an unusual name.
 */
object MusclePartResolver {

    fun resolve(exercise: Exercise): MusclePart? = resolve(
        category = exercise.category,
        equipment = exercise.equipment,
        name = exercise.name,
        description = exercise.description
    )

    fun resolve(category: String, equipment: String, name: String, description: String = ""): MusclePart? {
        fromTerms(category)?.let { return it }
        fromTerms(name)?.let { return it }
        fromTerms(equipment)?.let { return it }
        return fromTerms(description)
    }

    private fun fromTerms(value: String): MusclePart? {
        val lower = value.trim().lowercase()
        if (lower.isBlank()) return null
        // The longest matching term wins so "hip thrust" is never read as "hip" only
        val hits = MusclePart.selectable
            .flatMap { part -> part.searchTerms.map { term -> part to term } }
            .filter { (_, term) -> lower.contains(term) }
        return hits.maxByOrNull { it.second.length }?.first
    }

    /** The fields the athlete logs for this exercise type, shown in the information popup. */
    fun trackedFields(exerciseType: String): List<String> = when (exerciseType) {
        ExerciseType.ADDED_WEIGHT -> listOf("Sets", "Reps", "Added weight")
        ExerciseType.PURE_BODYWEIGHT -> listOf("Sets", "Reps", "Hold (seconds)")
        // Distance and time, never a load: a 5 km run has no meaningful weight or rep count, and
        // listing them here is what told the athlete to expect a barbell for their cardio.
        ExerciseType.CARDIO -> listOf("Sets", "Time (minutes)", "Distance")
        else -> listOf("Sets", "Reps", "Weight")
    }
}
