package com.example.data.model

/**
 * The measurements of a finished workout.
 *
 * The app had two different ideas of "volume" before this existed: the Progress chart counted the
 * load actually lifted (so a bodyweight set counted the athlete's own weight), while the activity
 * heat map counted only the external load. Two screens showing two different numbers for the same
 * week is not a rounding difference, it is a lie, so there is one definition now and every screen
 * uses it.
 *
 * The same goes for the duration of a session. There is no duration column: it is worked out from
 * the start and end of the session, with the span between the first and the last logged set as a
 * second source, because a workout saved a few seconds after it started has an almost zero
 * end-start difference and would otherwise look like no training at all.
 */
object WorkoutMetrics {

    /** The minutes a session lasted. */
    fun minutes(session: WorkoutSession, sets: List<LoggedSet>): Int {
        val setTimestamps = sets.map { it.timestamp }
        val spanMinutes = if (setTimestamps.size >= 2) {
            ((setTimestamps.max() - setTimestamps.min()) / 60_000L).toInt().coerceAtLeast(0)
        } else {
            0
        }
        val sessionMinutes = if (session.endTime > session.startTime) {
            ((session.endTime - session.startTime) / 60_000L).toInt()
        } else {
            0
        }
        // The session duration wins when it is meaningful, the set span is the fallback.
        return maxOf(sessionMinutes, spanMinutes)
    }

    /**
     * The load a set actually moved, in kilograms.
     *
     * This is the one rule the whole app measures with:
     *
     *  - a barbell set moves the load the athlete put on the bar,
     *  - a weighted bodyweight set moves their own body weight plus the load they added to it,
     *  - a bodyweight set moves their own body weight,
     *  - and a cardio set moves nothing at all, because a run is not lifted. Giving a cardio set
     *    the body weight would invent volume out of a movement that has no load, and would let it
     *    win an estimated one rep max against a real set.
     *
     * @param bodyWeightKg the body weight that applied to the set, or null when it is unknown.
     * @return null when the set cannot be expressed in kilograms at all, which happens to a
     *         bodyweight set with no body weight on record. Such a set is left out rather than
     *         given a made up number.
     */
    fun effectiveWeightKg(set: LoggedSet, bodyWeightKg: Double?): Double? =
        when (set.exerciseType) {
            ExerciseType.STANDARD_WEIGHT -> set.weight
            ExerciseType.ADDED_WEIGHT -> bodyWeightKg?.let { it + set.addedWeight }
            ExerciseType.PURE_BODYWEIGHT -> bodyWeightKg
            ExerciseType.CARDIO -> null
            else -> bodyWeightKg
        }

    /**
     * The volume of a set, in kilogram-reps.
     *
     * A set whose load is unknown contributes nothing: a number invented here would show up as
     * progress that never happened.
     */
    fun setVolume(set: LoggedSet, bodyWeightKg: Double?): Double =
        (effectiveWeightKg(set, bodyWeightKg) ?: 0.0) * set.reps

    /**
     * The volume of a session, in kilogram-reps.
     *
     * @param bodyWeightForSet the body weight that applied to a given set.
     */
    fun sessionVolume(
        sets: List<LoggedSet>,
        bodyWeightForSet: (LoggedSet) -> Double?
    ): Double = sets.sumOf { setVolume(it, bodyWeightForSet(it)) }

    /**
     * The estimated one rep max of a set, in kilograms.
     *
     * The Epley formula, and the same one the Progress chart and the monthly summary use, so the
     * three can never quote a different number for the same set.
     */
    fun estimateOneRepMax(effectiveWeight: Double, reps: Int): Double = when {
        effectiveWeight <= 0.0 -> 0.0
        reps <= 1 -> effectiveWeight
        else -> effectiveWeight * (36.0 / (37.0 - reps.coerceAtMost(36).toDouble()))
    }

    /**
     * The load of a set, written the way the athlete reads it.
     *
     * The same text everywhere a set's load is shown, so the workout history and the Progress tab
     * can never describe the same set two different ways. A bodyweight set says the body weight it
     * was done at rather than a bare zero, which is what made an empty weight field look like the
     * athlete had lifted nothing.
     *
     * @param bodyWeightKg the body weight the set was done at, or null when there is none.
     */
    fun loadLabel(set: LoggedSet, bodyWeightKg: Double?, unit: WeightUnit): String = when {
        set.exerciseType == ExerciseType.CARDIO -> describeCardioSet(set, unit)
        set.addedWeight > 0.0 -> {
            // The added load, plus the body weight that applied back then.
            val bw = bodyWeightKg?.let { " (+${format(it, unit)} ${unit.suffix} BW)" } ?: ""
            "+${format(set.addedWeight, unit)} ${unit.suffix}$bw"
        }
        set.weight > 0.0 -> "${format(set.weight, unit)} ${unit.suffix}"
        // A bodyweight or added weight set with no load: the body weight it was done at.
        set.exerciseType != ExerciseType.STANDARD_WEIGHT && bodyWeightKg != null ->
            "${format(bodyWeightKg, unit)} ${unit.suffix} BW"
        set.exerciseType != ExerciseType.STANDARD_WEIGHT ->
            // No weigh-in existed on that day: never borrow today's weight.
            "Own body weight"
        // A barbell set with no load logged: the load really is zero.
        else -> "0 ${unit.suffix}"
    }

    /** A weight in the athlete's unit, without the trailing ",0" noise. */
    private fun format(kg: Double, unit: WeightUnit): String =
        WeightConversion.format(kg, unit, decimals = 1, withUnit = false)
}
