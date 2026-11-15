/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.model

/**
 * One input a set can record.
 *
 * Field identity is independent of [ExerciseType]. An exercise may support any combination of
 * these fields; its exercise type only supplies a default suggestion and the badge/fallback
 * metadata. New fields must be added here first, with a storage mapping, so an exercise cannot be
 * configured with an identifier that the UI or calculations do not understand.
 */
enum class ExerciseField(
    val id: String,
    /** Stable ordering within a generated default configuration. */
    val order: Int,
    /** Whether the value is a whole number. */
    val integral: Boolean
) {
    REPS("reps", 10, true),
    WEIGHT("weight", 20, false),
    ADDED_WEIGHT("addedWeight", 30, false),
    HOLD_SECONDS("holdSeconds", 40, true),
    TIME_SECONDS("timeSeconds", 50, true),
    DISTANCE_KM("distanceKm", 60, false);

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromId(id: String): ExerciseField? = byId[id.trim()]

        fun requireId(id: String): ExerciseField =
            fromId(id) ?: throw IllegalArgumentException("Unknown exercise field: $id")
    }
}

/** How a field is presented in the active workout and routine editor. */
enum class FieldPlacement { PRIMARY, DETAILS }

/**
 * The field catalogue and the legacy-type fallback.
 *
 * The first two fields of an exercise's configured list are the compact inline inputs. Any further
 * fields are rendered inside the existing per-set expansion. That rule is deliberately uniform: it
 * works for Plank (hold + added weight), a carry (weight + time + distance) and ordinary rep work
 * (weight + reps) without any exercise-name exceptions.
 */
object ExerciseFieldCatalog {

    /** Fields used when an old exercise row has no explicit configuration. */
    fun defaultsForType(exerciseType: String): List<ExerciseField> = when (exerciseType) {
        ExerciseType.ADDED_WEIGHT -> listOf(
            ExerciseField.ADDED_WEIGHT,
            ExerciseField.REPS
        )
        ExerciseType.PURE_BODYWEIGHT -> listOf(
            ExerciseField.REPS,
            ExerciseField.HOLD_SECONDS
        )
        ExerciseType.CARDIO -> listOf(
            ExerciseField.TIME_SECONDS,
            ExerciseField.DISTANCE_KM
        )
        else -> listOf(
            ExerciseField.WEIGHT,
            ExerciseField.REPS
        )
    }

    /** Parses the database/archive compact form, dropping unknown ids rather than crashing. */
    fun parseStored(raw: String): List<ExerciseField> {
        if (raw.isBlank()) return emptyList()
        val seen = LinkedHashSet<ExerciseField>()
        raw.split(',').forEach { token ->
            ExerciseField.fromId(token)?.let(seen::add)
        }
        return seen.toList()
    }

    /** Serializes a field list without duplicates, preserving its display order. */
    fun serialize(fields: Collection<ExerciseField>): String =
        fields.distinct().joinToString(",") { it.id }

    /**
     * The authoritative field list for an exercise.
     *
     * An empty explicit list means a legacy row: use the old type mapping so nothing disappears
     * before the migration fills it. Otherwise the explicit list wins, even when it is narrower
     * or wider than the type's suggestion.
     */
    fun effectiveFields(exercise: Exercise): List<ExerciseField> {
        val explicit = parseStored(exercise.supportedFields)
        return if (explicit.isEmpty()) defaultsForType(exercise.exerciseType) else explicit
    }

    fun primaryFields(fields: List<ExerciseField>): List<ExerciseField> = fields.take(2)

    fun detailFields(fields: List<ExerciseField>): List<ExerciseField> = fields.drop(2)

    /**
     * Validates a configuration and returns a stable, duplicate-free list.
     *
     * Hold and time share the existing duration column. An exercise cannot sensibly display both
     * controls for one stored value, so that combination is rejected rather than silently showing
     * the same number twice.
     */
    fun validate(fields: Collection<ExerciseField>): List<ExerciseField> {
        // Preserve the caller's display order: the first two are the inline fields, so
        // [weight, reps] and [reps, addedWeight] must not be re-sorted alphabetically/by enum.
        val ordered = fields.distinct()
        require(ordered.isNotEmpty()) { "An exercise must support at least one field." }
        require(
            !(ExerciseField.HOLD_SECONDS in ordered && ExerciseField.TIME_SECONDS in ordered)
        ) { "Hold and time cannot both use the same duration value." }
        require(
            !(ExerciseField.WEIGHT in ordered && ExerciseField.ADDED_WEIGHT in ordered)
        ) { "Weight and added weight are mutually exclusive load fields." }
        return ordered
    }

    /**
     * A safe explicit list for persistence. Invalid or empty input falls back to the
     * classification defaults rather than crashing the editor or storing an unrenderable row.
     */
    fun safeExplicit(fields: Collection<ExerciseField>, exerciseType: String): List<ExerciseField> =
        runCatching { validate(fields) }.getOrElse { defaultsForType(exerciseType) }

    /** Label for a field, with the athlete's weight/distance unit where relevant. */
    fun label(field: ExerciseField, unit: WeightUnit): String = when (field) {
        ExerciseField.REPS -> "Reps"
        ExerciseField.WEIGHT -> "Weight (${unit.suffix})"
        ExerciseField.ADDED_WEIGHT -> "Added (${unit.suffix})"
        ExerciseField.HOLD_SECONDS -> "Hold (s)"
        ExerciseField.TIME_SECONDS -> "Time (min)"
        ExerciseField.DISTANCE_KM -> "Distance (${DistanceFormat.suffixFor(unit)})"
    }

    /** Unit-free label for metadata panels, where the athlete has not chosen a unit yet. */
    fun plainLabel(field: ExerciseField): String = when (field) {
        ExerciseField.REPS -> "Reps"
        ExerciseField.WEIGHT -> "Weight"
        ExerciseField.ADDED_WEIGHT -> "Added weight"
        ExerciseField.HOLD_SECONDS -> "Hold (seconds)"
        ExerciseField.TIME_SECONDS -> "Time (minutes)"
        ExerciseField.DISTANCE_KM -> "Distance"
    }

    /** Short label for column headers and expanded controls. */
    fun shortLabel(field: ExerciseField, unit: WeightUnit): String = when (field) {
        ExerciseField.REPS -> "Reps"
        ExerciseField.WEIGHT -> "Weight (${unit.suffix})"
        ExerciseField.ADDED_WEIGHT -> "Added (${unit.suffix})"
        ExerciseField.HOLD_SECONDS -> "Hold (s)"
        ExerciseField.TIME_SECONDS -> "Time (min)"
        ExerciseField.DISTANCE_KM -> "Distance (${DistanceFormat.suffixFor(unit)})"
    }
}
