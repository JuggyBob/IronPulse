/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.model

import kotlin.math.roundToInt

/**
 * The single translation between stable [ExerciseField]s and the stored set/routine columns.
 *
 * Hold and time deliberately share the existing duration column. They are separate field
 * identities because they mean different things, but an exercise cannot configure both as that
 * would display one stored value twice.
 */
object ExerciseFieldValues {

    fun read(set: LoggedSet, field: ExerciseField): Double = when (field) {
        ExerciseField.REPS -> set.reps.toDouble()
        ExerciseField.WEIGHT -> set.weight
        ExerciseField.ADDED_WEIGHT -> set.addedWeight
        ExerciseField.HOLD_SECONDS,
        ExerciseField.TIME_SECONDS -> set.holdDurationSeconds.toDouble()
        ExerciseField.DISTANCE_KM -> set.distanceKm
    }

    fun write(set: LoggedSet, field: ExerciseField, value: Double): LoggedSet {
        val safe = if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
        return when (field) {
            ExerciseField.REPS -> set.copy(reps = safe.roundToInt().coerceAtLeast(0))
            ExerciseField.WEIGHT -> set.copy(weight = safe)
            ExerciseField.ADDED_WEIGHT -> set.copy(addedWeight = safe)
            ExerciseField.HOLD_SECONDS,
            ExerciseField.TIME_SECONDS -> set.copy(
                holdDurationSeconds = safe.roundToInt().coerceAtLeast(0)
            )
            ExerciseField.DISTANCE_KM -> set.copy(distanceKm = safe)
        }
    }

    /** True when the set has a meaningful value for this field. */
    fun hasValue(set: LoggedSet, field: ExerciseField): Boolean = read(set, field) > 0.0

    fun read(target: RoutineExerciseItem, field: ExerciseField): Double = when (field) {
        ExerciseField.REPS -> target.targetReps.toDouble()
        ExerciseField.WEIGHT -> target.targetWeight
        ExerciseField.ADDED_WEIGHT -> target.targetAddedWeightKg
        ExerciseField.HOLD_SECONDS,
        ExerciseField.TIME_SECONDS -> target.targetHoldSeconds.toDouble()
        ExerciseField.DISTANCE_KM -> target.targetDistanceKm
    }

    fun write(target: RoutineExerciseItem, field: ExerciseField, value: Double): RoutineExerciseItem {
        val safe = if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
        return when (field) {
            ExerciseField.REPS -> target.copy(targetReps = safe.roundToInt().coerceAtLeast(0))
            ExerciseField.WEIGHT -> target.copy(targetWeight = safe)
            ExerciseField.ADDED_WEIGHT -> target.copy(targetAddedWeightKg = safe)
            ExerciseField.HOLD_SECONDS,
            ExerciseField.TIME_SECONDS -> target.copy(
                targetHoldSeconds = safe.roundToInt().coerceAtLeast(0)
            )
            ExerciseField.DISTANCE_KM -> target.copy(targetDistanceKm = safe)
        }
    }

    fun read(target: RoutineMainSetDefault, field: ExerciseField): Double = when (field) {
        ExerciseField.REPS -> target.targetReps.toDouble()
        ExerciseField.WEIGHT -> target.targetWeight
        ExerciseField.ADDED_WEIGHT -> target.targetAddedWeightKg
        ExerciseField.HOLD_SECONDS,
        ExerciseField.TIME_SECONDS -> target.targetHoldSeconds.toDouble()
        ExerciseField.DISTANCE_KM -> target.targetDistanceKm
    }

    fun write(
        target: RoutineMainSetDefault,
        field: ExerciseField,
        value: Double
    ): RoutineMainSetDefault {
        val safe = if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
        return when (field) {
            ExerciseField.REPS -> target.copy(targetReps = safe.roundToInt().coerceAtLeast(0))
            ExerciseField.WEIGHT -> target.copy(targetWeight = safe)
            ExerciseField.ADDED_WEIGHT -> target.copy(targetAddedWeightKg = safe)
            ExerciseField.HOLD_SECONDS,
            ExerciseField.TIME_SECONDS -> target.copy(
                targetHoldSeconds = safe.roundToInt().coerceAtLeast(0)
            )
            ExerciseField.DISTANCE_KM -> target.copy(targetDistanceKm = safe)
        }
    }

    fun read(target: RoutineDropSet, field: ExerciseField): Double = when (field) {
        ExerciseField.REPS -> target.targetReps.toDouble()
        ExerciseField.WEIGHT -> target.targetWeight
        ExerciseField.ADDED_WEIGHT -> target.targetAddedWeightKg
        ExerciseField.HOLD_SECONDS,
        ExerciseField.TIME_SECONDS -> target.targetHoldSeconds.toDouble()
        ExerciseField.DISTANCE_KM -> target.targetDistanceKm
    }

    fun write(target: RoutineDropSet, field: ExerciseField, value: Double): RoutineDropSet {
        val safe = if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
        return when (field) {
            ExerciseField.REPS -> target.copy(targetReps = safe.roundToInt().coerceAtLeast(0))
            ExerciseField.WEIGHT -> target.copy(targetWeight = safe)
            ExerciseField.ADDED_WEIGHT -> target.copy(targetAddedWeightKg = safe)
            ExerciseField.HOLD_SECONDS,
            ExerciseField.TIME_SECONDS -> target.copy(
                targetHoldSeconds = safe.roundToInt().coerceAtLeast(0)
            )
            ExerciseField.DISTANCE_KM -> target.copy(targetDistanceKm = safe)
        }
    }

    /**
     * Human-readable value for history, Progress and summaries.
     *
     * Only the value is returned; callers add separators and labels. TIME is stored in seconds but
     * shown in minutes. DISTANCE uses the athlete's distance unit.
     */
    fun format(set: LoggedSet, field: ExerciseField, unit: WeightUnit): String = when (field) {
        ExerciseField.REPS -> "${set.reps}"
        ExerciseField.WEIGHT -> WeightConversion.format(set.weight, unit, decimals = 1, withUnit = true)
        ExerciseField.ADDED_WEIGHT ->
            "+" + WeightConversion.format(set.addedWeight, unit, decimals = 1, withUnit = true)
        ExerciseField.HOLD_SECONDS -> "${set.holdDurationSeconds}s"
        ExerciseField.TIME_SECONDS -> formatMinutes(set.holdDurationSeconds)
        ExerciseField.DISTANCE_KM ->
            DistanceFormat.format(set.distanceKm, unit) + " " + DistanceFormat.suffixFor(unit)
    }

    fun format(target: RoutineDropSet, field: ExerciseField, unit: WeightUnit): String = when (field) {
        ExerciseField.REPS -> "${target.targetReps}"
        ExerciseField.WEIGHT -> WeightConversion.format(target.targetWeight, unit, decimals = 1, withUnit = true)
        ExerciseField.ADDED_WEIGHT ->
            "+" + WeightConversion.format(target.targetAddedWeightKg, unit, decimals = 1, withUnit = true)
        ExerciseField.HOLD_SECONDS -> "${target.targetHoldSeconds}s"
        ExerciseField.TIME_SECONDS -> formatMinutes(target.targetHoldSeconds)
        ExerciseField.DISTANCE_KM ->
            DistanceFormat.format(target.targetDistanceKm, unit) + " " + DistanceFormat.suffixFor(unit)
    }

    private fun formatMinutes(seconds: Int): String {
        if (seconds <= 0) return "0 min"
        val minutes = seconds / 60.0
        return if (minutes % 1.0 == 0.0) "${minutes.toInt()} min" else "%.1f min".format(minutes)
    }
}
