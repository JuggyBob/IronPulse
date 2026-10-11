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
}