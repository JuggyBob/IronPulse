package com.example.data.model

import kotlin.math.abs
import kotlin.math.roundToInt

/** How the athlete prefers effort values to be shown. The stored value is always canonical RPE. */
enum class EffortDisplay {
    RIR,
    RPE;

    companion object {
        fun fromStorage(value: String?): EffortDisplay =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: RIR
    }
}

/**
 * The single effort scale used by the whole app.
 *
 * RPE is the canonical stored value because it is continuous and unambiguous in history and AI
 * payloads. RIR is a presentation of the same number:
 *
 *   RIR 0 = RPE 10, RIR 1 = RPE 9, RIR 2 = RPE 8, RIR 3 = RPE 7, RIR 4+ = RPE 6
 *
 * The 4+ bucket is intentionally lossy: RPE 6.0 and anything below it display as "4+". An RIR
 * value is never stored separately, so switching the preference cannot create two contradictory
 * records.
 */
object EffortScale {
    const val MIN_RPE = 6.0
    const val MAX_RPE = 10.0

    fun rirToRpe(rir: Int): Double =
        if (rir >= 4) MIN_RPE else (10 - rir.coerceIn(0, 4)).toDouble()

    /** Display RIR from canonical RPE. Values below the scale collapse into the 4+ bucket. */
    fun rpeToRir(rpe: Double): Int? {
        val safe = normalize(rpe) ?: return null
        return (10 - safe).roundToInt().coerceIn(0, 4)
    }

    fun normalize(rpe: Double?): Double? {
        if (rpe == null || !rpe.isFinite()) return null
        // Half-step values are meaningful for RPE; keep them but never leave the supported range.
        val clamped = rpe.coerceIn(MIN_RPE, MAX_RPE)
        return (clamped * 2.0).roundToInt() / 2.0
    }

    /** True when RIR is a meaningful presentation for this exercise. */
    fun supportsRir(exercise: Exercise): Boolean =
        exercise.exerciseType != ExerciseType.CARDIO &&
            ExerciseField.REPS in exercise.exerciseFields

    fun supportsRir(set: LoggedSet): Boolean =
        set.exerciseType != ExerciseType.CARDIO && set.reps > 0

    /**
     * Whether the RIR preference should be used for this set. Cardio, distance, timed and pure
     * hold exercises always use RPE because "reps in reserve" has no meaning there.
     */
    fun usesRir(set: LoggedSet, exercise: Exercise? = null): Boolean =
        if (exercise != null) {
            supportsRir(exercise)
        } else {
            set.exerciseType != ExerciseType.CARDIO && set.reps > 0
        }

    fun display(
        rpe: Double?,
        preference: EffortDisplay,
        repetitionBased: Boolean
    ): String {
        val safe = normalize(rpe) ?: return "Not logged"
        return if (preference == EffortDisplay.RIR && repetitionBased) {
            val rir = rpeToRir(safe) ?: return "Not logged"
            if (rir >= 4) "RIR 4+" else "RIR $rir"
        } else {
            val text = if (abs(safe - safe.roundToInt()) < 0.01) {
                safe.roundToInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.1f", safe)
            }
            "RPE $text"
        }
    }

    /** Returns the next canonical RPE in the requested display direction, or null at the boundary. */
    fun step(
        current: Double?,
        preference: EffortDisplay,
        repetitionBased: Boolean,
        direction: Int
    ): Double? {
        val normalized = normalize(current)
        return if (preference == EffortDisplay.RIR && repetitionBased) {
            val currentRir = normalized?.let { rpeToRir(it) } ?: 4
            // "+" means more effort, which is fewer reps in reserve.
            val nextRir = (currentRir - direction).coerceIn(0, 4)
            rirToRpe(nextRir)
        } else {
            val base = normalized ?: MIN_RPE
            (base + direction * 0.5).coerceIn(MIN_RPE, MAX_RPE)
        }
    }

    fun labelForTarget(
        rpe: Double?,
        preference: EffortDisplay,
        repetitionBased: Boolean
    ): String = when {
        rpe == null -> "None"
        preference == EffortDisplay.RIR && repetitionBased -> display(rpe, preference, true)
        else -> display(rpe, EffortDisplay.RPE, repetitionBased)
    }
}
