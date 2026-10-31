package com.example.data.model

/**
 * Result of asking the AI to invent one missing exercise.
 *
 * [Inserted] carries the generated exercise when it is not already in the database. When the
 * exercise exists already it is null and [Existing] holds the stored one.
 */
data class AiGeneratedExerciseResult(
    val Inserted: Exercise?,
    val Existing: Exercise?,
    val ErrorMessage: String?
)

/**
 * Single source of truth for "what is the athlete's body weight right now".
 *
 * Weigh-ins are an optional tracking feature: the athlete may never record one, may delete them
 * all, or loses them with "Delete App Data". The **athlete profile weight is required**, so it is
 * the only value that may be used wherever a body weight is needed (body weight exercise tags, the
 * 1RM / volume maths, the AI context and the monthly summary).
 *
 * Using a weigh-in instead had two bad side effects: exercises silently fell back to an unrelated
 * morning weigh-in after an athlete profile edit, and every body weight exercise lost its weight
 * entirely once the weigh-in log was emptied.
 */
object BodyWeightTimeline {

    /** Fallback only when the profile itself has no usable weight (should never happen). */
    const val FALLBACK_KG = 75.0

    /**
     * The body weight to use for body weight exercises, in kg.
     *
     * @param profileWeightKg the athlete profile "current weight" stat.
     */
    fun currentWeightKg(profileWeightKg: Double): Double =
        if (profileWeightKg > 0.0) profileWeightKg else FALLBACK_KG

    /**
     * Same value, but null when it is not usable at all. Used by the reporting paths that must
     * never invent a number.
     */
    fun currentWeightOrNull(profileWeightKg: Double): Double? =
        profileWeightKg.takeIf { it > 0.0 }

    /**
     * Weigh-in timeline helper: the recorded weigh-in at or before [timestamp], or null.
     *
     * Only the body weight **timeline chart** needs this. Exercise tags must not use it.
     */
    fun weighInAt(weightLogs: List<BodyWeightLog>, timestamp: Long): Double? {
        var best: BodyWeightLog? = null
        for (log in weightLogs) {
            if (log.timestamp > timestamp) continue
            if (best == null || log.timestamp > best.timestamp) best = log
        }
        return best?.weightKg
    }

    /**
     * The body weight that applied to a logged set.
     *
     * The snapshot the set carries wins: it is the weight the athlete had when they did the work,
     * and reading today's number instead would rewrite the history every time they weigh in. That
     * was the bug this fixed: the isolated weight tag of a set logged last month changed as soon
     * as a new weight was logged today.
     *
     * A stored set should never need the fallback: migration 10 -> 11 fills the rows that predate
     * the snapshot, and finishing a workout stamps the final body weight on every set it saves.
     * The fallback remains for a set that is still in progress and has not been stamped yet.
     */
    fun bodyWeightForSet(set: LoggedSet, profileWeightKg: Double): Double? =
        set.bodyWeightKg.takeIf { it > 0.0 } ?: currentWeightOrNull(profileWeightKg)

    /**
     * Whether a set can appear under a bodyweight isolated-weight tag at all.
     *
     * Only the bodyweight types qualify. A [ExerciseType.STANDARD_WEIGHT] set with no load is an
     * empty bar, not a bodyweight set: tagging it with the athlete's weight is what produced the
     * ghost "BW: 80 kg" chip on a zero-load barbell exercise.
     */
    fun isBodyweightTagCandidate(set: LoggedSet): Boolean =
        set.weight == 0.0 && set.addedWeight == 0.0 && ExerciseType.isBodyweight(set.exerciseType)

    /**
     * Whether [set] belongs to the isolated weight [tag].
     *
     * A set matches by its external load, its added load, or — for bodyweight work only — the
     * body weight it was done at. The body weight passed in is already the set's own snapshot, not
     * today's profile weight.
     */
    fun matchesIsolatedWeight(set: LoggedSet, tag: Double, bodyWeightKg: Double?): Boolean =
        set.weight == tag ||
            set.addedWeight == tag ||
            (isBodyweightTagCandidate(set) && bodyWeightKg == tag)

    /**
     * The same value, but with the fallback applied so a number is always returned.
     *
     * For the paths that cannot report "no body weight" and would otherwise have to invent one.
     */
    fun bodyWeightForSetOrFallback(set: LoggedSet, profileWeightKg: Double): Double =
        bodyWeightForSet(set, profileWeightKg) ?: FALLBACK_KG
}