package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Human readable effort of a logged set.
 *
 * Reps and hold seconds are **independent** inputs (a body weight exercise such as an explosive
 * pull-up is measured in reps, a plank in seconds, and both may be logged at once). Showing only
 * the hold when it is set silently hid the reps the athlete had entered, so both are listed.
 */
fun describeLoggedSetEffort(set: LoggedSet): String {
    val parts = mutableListOf<String>()
    if (set.reps > 0) parts += "${set.reps} reps"
    if (set.holdDurationSeconds > 0) parts += "${set.holdDurationSeconds}s hold"
    return if (parts.isEmpty()) "no reps logged" else parts.joinToString(" • ")
}

/**
 * How a cardio set reads in the Progress page and the workout history.
 *
 * A run has neither reps nor load, so [describeLoggedSetEffort] would render "no reps logged" for
 * every set of a session the athlete actually ran. This shows the two things they did record: the
 * distance and how long it took. Duration is reported in minutes, because that is how a run is
 * logged, and "2700s" is not something anyone reads at a glance.
 */
fun describeCardioSet(set: LoggedSet, weightUnit: WeightUnit): String {
    val parts = mutableListOf<String>()

    val distance = DistanceFormat.format(set.distanceKm, weightUnit)
    if (distance.isNotEmpty()) {
        parts += "$distance ${DistanceFormat.suffixFor(weightUnit)}"
    }

    if (set.holdDurationSeconds > 0) {
        val minutes = set.holdDurationSeconds / 60
        val seconds = set.holdDurationSeconds % 60
        // A real remainder is kept, so a 4:30 run does not silently read as "4 min".
        parts += if (seconds == 0) {
            "${minutes} min"
        } else {
            "${minutes}:${seconds.toString().padStart(2, '0')} min"
        }
    }

    return if (parts.isEmpty()) "no distance logged" else parts.joinToString(" • ")
}

/**
 * One training day inside the monthly analysis window.
 */
data class MonthlySetEntry(
    val dateLabel: String,
    val setCount: Int,
    val topSetLabel: String,
    val volume: Double,
    val estimatedOneRm: Double
)

/**
 * Everything the Gemini monthly summary needs about a single exercise, pre-computed locally so
 * the AI only has to interpret the numbers instead of re-deriving them.
 *
 * Every numeric field stays in **kilograms**: the analyzer works on the stored data, so one summary
 * is correct whatever unit the athlete picked. [formatWeight] converts on the way out, and the
 * strings built for the model ([bestLoadLabel], [bestOneRmSource] and the per-day labels inside
 * [dailyEntries]) are already formatted in [weightUnit].
 */
data class MonthlyExerciseSummary(
    val exerciseName: String,
    val exerciseType: String,
    val weightUnit: WeightUnit,
    val daysLogged: Int,
    val setCount: Int,
    val totalVolume: Double,
    val bestOneRm: Double,
    val bestOneRmSource: String,
    val bestLoadLabel: String,
    val maxReps: Int,
    val firstDayOneRm: Double,
    val lastDayOneRm: Double,
    val dailyEntries: List<MonthlySetEntry>
) {
    /** Percentage change of the estimated 1RM between the first and the last logged day. */
    val trendPercent: Double
        get() = if (firstDayOneRm > 0.0) ((lastDayOneRm - firstDayOneRm) / firstDayOneRm) * 100.0 else 0.0

    /**
     * Formats a stored kilogram weight for the Gemini prompt, in the athlete's unit.
     *
     * Always [Locale.US] on purpose: a comma decimal separator ("101,3 kg") would be ambiguous
     * for the model, while the on screen formatting keeps using the device locale.
     */
    fun formatWeight(kgValue: Double): String =
        formatNumber(WeightConversion.fromKg(kgValue, weightUnit))

    /**
     * Formats a plain number (a percentage, a rep count) for the Gemini prompt.
     *
     * @see formatWeight
     */
    fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

    /**
     * Compact, unambiguous data block sent to Gemini.
     *
     * Every weight in it is expressed in [weightUnit], so the model never has to guess and never
     * quotes a kilograms number to an athlete who trains in pounds.
     */
    fun toPromptPayload(windowDays: Int): String {
        val unit = weightUnit.suffix
        val sb = StringBuilder()
        sb.append("EXERCISE: ").append(exerciseName).append('\n')
        sb.append("EXERCISE TYPE: ").append(exerciseType).append('\n')
        sb.append("UNIT: ").append(unit).append('\n')
        sb.append("WINDOW: last ").append(windowDays).append(" days\n")
        sb.append("TRAINING DAYS LOGGED: ").append(daysLogged).append('\n')
        sb.append("TOTAL SETS LOGGED: ").append(setCount).append('\n')
        sb.append("TOTAL VOLUME: ").append(formatWeight(totalVolume)).append(' ').append(unit).append("-reps\n")
        sb.append("BEST ESTIMATED 1RM: ").append(formatWeight(bestOneRm)).append(' ').append(unit)
            .append(" (").append(bestOneRmSource).append(")\n")
        sb.append("BEST LOAD LOGGED: ").append(bestLoadLabel).append('\n')
        sb.append("HIGHEST REP COUNT IN A SINGLE SET: ").append(maxReps).append('\n')
        sb.append("FIRST LOGGED DAY ESTIMATED 1RM: ").append(formatWeight(firstDayOneRm)).append(' ').append(unit).append('\n')
        sb.append("MOST RECENT DAY ESTIMATED 1RM: ").append(formatWeight(lastDayOneRm)).append(' ').append(unit).append('\n')
        sb.append("CHANGE OVER THE PERIOD: ")
            .append(if (trendPercent >= 0) "+" else "")
            .append(formatNumber(trendPercent))
            .append("%\n")
        sb.append('\n')
        sb.append("PER DAY BREAKDOWN (oldest first, one line per training day):\n")
        dailyEntries.forEach { entry ->
            sb.append("- ").append(entry.dateLabel)
                .append(" | sets: ").append(entry.setCount)
                .append(" | best set: ").append(entry.topSetLabel)
                .append(" | volume: ").append(formatWeight(entry.volume))
                .append(" | est 1RM: ").append(formatWeight(entry.estimatedOneRm))
                .append('\n')
        }
        return sb.toString()
    }
}

/**
 * Builds the "Gemini monthly summary" statistics for one exercise over a rolling window.
 *
 * The estimated 1RM uses the same Epley formula and the same body weight resolution rules as the
 * Progress charts, so the AI reasons about exactly the numbers the user sees on screen.
 */
object MonthlyExerciseAnalyzer {

    const val WINDOW_DAYS = 30

    private val DAY_LABEL = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("MMM d", Locale.getDefault())
    }

    /** Epley formula, matching ProgressScreen's MAX_REPS / ESTIMATED_1RM metrics. */
    fun estimateOneRepMax(effectiveWeight: Double, reps: Int): Double = when {
        effectiveWeight <= 0.0 -> 0.0
        reps <= 1 -> effectiveWeight
        else -> effectiveWeight * (36.0 / (37.0 - reps.coerceAtMost(36).toDouble()))
    }

    /**
     * Load actually lifted: external weight, bodyweight + added weight, or bodyweight.
     *
     * @return null when the set needs a body weight that was never recorded. Such a set cannot be
     * expressed in kg, and substituting today's weight would fabricate progress.
     */
    fun effectiveWeightOf(set: LoggedSet, bodyWeightAt: (Long) -> Double?): Double? {
        val bodyWeight = bodyWeightAt(set.timestamp)
        return when (set.exerciseType) {
            ExerciseType.STANDARD_WEIGHT -> set.weight
            ExerciseType.ADDED_WEIGHT -> bodyWeight?.let { it + set.addedWeight }
            else -> bodyWeight
        }
    }

/**
     * @param sets every set ever logged for the exercise; only those inside the window are used.
     * @param bodyWeightAt resolves the body weight that applied at a given timestamp, or null when
     * it is unknown (never the current weight).
     * @param weightUnit the unit the labels built for the AI are expressed in. The returned numbers
     * stay in kilograms and are converted by [MonthlyExerciseSummary.toPromptPayload].
     * @param nowMillis reference point for the rolling window.
     */
    fun build(
        exerciseName: String,
        exerciseType: String,
        weightUnit: WeightUnit,
        sets: List<LoggedSet>,
        bodyWeightAt: (Long) -> Double?,
        nowMillis: Long = System.currentTimeMillis()
    ): MonthlyExerciseSummary {
        val windowStart = nowMillis - (WINDOW_DAYS * 24L * 60L * 60L * 1000L)
        val windowSets = sets
            .filter { it.timestamp >= windowStart && it.timestamp <= nowMillis }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))

        val formatter = DAY_LABEL.get() ?: SimpleDateFormat("MMM d", Locale.getDefault())

        val dailyEntries = windowSets
            .groupBy { formatter.format(Date(it.timestamp)) }
            .map { (dateLabel, daySets) ->
                // Only the sets whose load is actually known can be expressed as a weight
                val measurable = daySets.filter { effectiveWeightOf(it, bodyWeightAt) != null }
                // The day's "best set" is the one with the highest estimated 1RM
                val dayBest = measurable.maxByOrNull { estimateOneRepMax(effectiveWeightOf(it, bodyWeightAt) ?: 0.0, it.reps) }
                MonthlySetEntry(
                    dateLabel = dateLabel,
                    setCount = daySets.size,
                    topSetLabel = describeSet(dayBest, weightUnit, bodyWeightAt) { "${it.reps} reps" },
                    volume = measurable.sumOf { (effectiveWeightOf(it, bodyWeightAt) ?: 0.0) * it.reps },
                    estimatedOneRm = dayBest?.let { estimateOneRepMax(effectiveWeightOf(it, bodyWeightAt) ?: 0.0, it.reps) } ?: 0.0
                )
            }

        val bestOverall = windowSets
            .filter { effectiveWeightOf(it, bodyWeightAt) != null }
            .maxByOrNull { estimateOneRepMax(effectiveWeightOf(it, bodyWeightAt) ?: 0.0, it.reps) }
        val bestOneRm = bestOverall?.let { estimateOneRepMax(effectiveWeightOf(it, bodyWeightAt) ?: 0.0, it.reps) } ?: 0.0
        val bestLoadLabel = bestOverall?.let { describeSet(it, weightUnit, bodyWeightAt) { "${it.reps} reps" } } ?: "n/a"
        val bestSource = bestOverall?.let { set ->
            buildString {
                append(loadLabel(set, weightUnit, bodyWeightAt))
                append(" x ")
                append(if (set.holdDurationSeconds > 0 && set.reps > 0) {
                    "${set.reps} reps • ${set.holdDurationSeconds}s hold"
                } else {
                    describeLoggedSetEffort(set)
                })
            }
        } ?: "no working sets logged"

        return MonthlyExerciseSummary(
            exerciseName = exerciseName,
            exerciseType = exerciseType,
            weightUnit = weightUnit,
            daysLogged = dailyEntries.size,
            setCount = windowSets.size,
            totalVolume = windowSets.sumOf { (effectiveWeightOf(it, bodyWeightAt) ?: 0.0) * it.reps },
            bestOneRm = bestOneRm,
            bestOneRmSource = bestSource,
            bestLoadLabel = bestLoadLabel,
            maxReps = windowSets.maxOfOrNull { it.reps } ?: 0,
            firstDayOneRm = dailyEntries.firstOrNull()?.estimatedOneRm ?: 0.0,
            lastDayOneRm = dailyEntries.lastOrNull()?.estimatedOneRm ?: 0.0,
            dailyEntries = dailyEntries
        )
    }

    private fun loadLabel(set: LoggedSet, weightUnit: WeightUnit, bodyWeightAt: (Long) -> Double?): String {
        val unit = weightUnit.suffix
        return when {
            set.exerciseType == ExerciseType.ADDED_WEIGHT && set.addedWeight > 0.0 ->
                "+${trim(set.addedWeight, weightUnit)}$unit"
            set.weight > 0.0 -> "${trim(set.weight, weightUnit)}$unit"
            // Never invent a body weight: say plainly that none was recorded for that day
            else -> bodyWeightAt(set.timestamp)?.let { "bodyweight ${trim(it, weightUnit)}$unit" }
                ?: "bodyweight not recorded"
        }
    }

    private fun describeSet(
        set: LoggedSet?,
        weightUnit: WeightUnit,
        bodyWeightAt: (Long) -> Double?,
        detail: (LoggedSet) -> String
    ): String {
        if (set == null) return "n/a"
        return "${loadLabel(set, weightUnit, bodyWeightAt)} x ${detail(set)}"
    }

    /** Formats a stored kilogram value in the athlete's unit, without a trailing ".0". */
    private fun trim(kgValue: Double, weightUnit: WeightUnit): String {
        val value = WeightConversion.fromKg(kgValue, weightUnit)
        return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    }
}
