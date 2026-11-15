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
    val warmupSetCount: Int,
    val dropSetCount: Int = 0,
    val topSetLabel: String,
    val volume: Double,
    val estimatedOneRm: Double
)

/**
 * One individual set in the monthly window.
 *
 * The AI receives this list so it can see the warm-up classification of every set, not just an
 * aggregate. Warm-up sets carry volume but their [estimatedOneRm] is deliberately zero: they are
 * not working-set records.
 */
data class MonthlySetDetail(
    val dateLabel: String,
    val setNumber: Int,
    val loadLabel: String,
    val effortLabel: String,
    val isWarmup: Boolean,
    val isDropSet: Boolean = false,
    val dropIndex: Int = 0,
    val parentSetNumber: Int = 0,
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
    val dailyEntries: List<MonthlySetEntry>,
    val warmupSetCount: Int = 0,
    val workingSetCount: Int = 0,
    val setEntries: List<MonthlySetDetail> = emptyList()
) {
    /** True when the window contains at least one non-warm-up set. */
    val hasWorkingSets: Boolean get() = workingSetCount > 0
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
        sb.append("WORKING SETS: ").append(workingSetCount).append('\n')
        sb.append("WARM-UP SETS: ").append(warmupSetCount).append('\n')
        sb.append("TOTAL VOLUME: ").append(formatWeight(totalVolume)).append(' ').append(unit).append("-reps")
            .append(" (includes warm-up sets)\n")
        sb.append("BEST ESTIMATED 1RM: ").append(formatWeight(bestOneRm)).append(' ').append(unit)
            .append(" (").append(bestOneRmSource).append(") [working sets only]\n")
        sb.append("BEST LOAD LOGGED: ").append(bestLoadLabel).append(" [working sets only]\n")
        sb.append("HIGHEST REP COUNT IN A SINGLE WORKING SET: ").append(maxReps).append('\n')
        sb.append("FIRST LOGGED DAY ESTIMATED 1RM: ").append(formatWeight(firstDayOneRm)).append(' ').append(unit).append('\n')
        sb.append("MOST RECENT DAY ESTIMATED 1RM: ").append(formatWeight(lastDayOneRm)).append(' ').append(unit).append('\n')
        sb.append("CHANGE OVER THE PERIOD: ")
            .append(if (trendPercent >= 0) "+" else "")
            .append(formatNumber(trendPercent))
            .append("%\n")
        sb.append('\n')
        sb.append(
            "WARM-UP RULES: A warm-up set is preparation, not a working-set performance record. " +
                "Warm-up sets ARE included in TOTAL VOLUME and TOTAL SETS LOGGED. They are NOT " +
                "eligible for BEST ESTIMATED 1RM, HIGHEST REP COUNT IN A SINGLE WORKING SET, " +
                "working-set PRs or any peak working-set comparison.\n" +
                "DROP-SET RULES: A drop set is a direct child of one main set. Its reps and volume " +
                "count toward totals, but it is never a PR, best working set or 1RM source.\n"
        )
        sb.append('\n')
        sb.append("INDIVIDUAL SETS (oldest first; isWarmup and isDropSet are explicit):\n")
        setEntries.forEach { set ->
            sb.append("- ").append(set.dateLabel)
                .append(" | set ").append(set.setNumber)
                .append(" | isWarmup=").append(if (set.isWarmup) "true" else "false")
                .append(" | isDropSet=").append(if (set.isDropSet) "true" else "false")
                .append(if (set.isDropSet) " | mainSet=" + set.parentSetNumber + " | drop=" + set.dropIndex else "")
                .append(" | ").append(set.loadLabel).append(" x ").append(set.effortLabel)
                .append(" | volume ").append(formatWeight(set.volume)).append(' ').append(unit).append("-reps")
                .append(" | est 1RM ")
                .append(
                    if (set.isWarmup) {
                        "n/a (warm-up)"
                    } else if (set.isDropSet) {
                        "n/a (drop set)"
                    } else {
                        formatWeight(set.estimatedOneRm) + " " + unit
                    }
                )
                .append('\n')
        }
        sb.append('\n')
        sb.append("PER DAY BREAKDOWN (oldest first, one line per training day):\n")
        dailyEntries.forEach { entry ->
            sb.append("- ").append(entry.dateLabel)
                .append(" | sets: ").append(entry.setCount)
                .append(" | warm-ups: ").append(entry.warmupSetCount)
                .append(" | drops: ").append(entry.dropSetCount)
                .append(" | best working set: ").append(entry.topSetLabel)
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
    fun estimateOneRepMax(effectiveWeight: Double, reps: Int): Double =
        WorkoutMetrics.estimateOneRepMax(effectiveWeight, reps)

    /**
     * Load actually lifted: external weight, bodyweight + added weight, or bodyweight.
     *
     * The same rule every other screen measures with, so the report the coach writes and the
     * numbers on the Progress tab can never quote two different figures for the same set.
     *
     * @param bodyWeightForSet resolves the body weight that applied to a given set, or null when
     *        it is unknown. The body weight has to be the one the set carries, which is the weight
     *        the athlete had at the time of that set, not the weight they have today.
     * @return null when the set cannot be expressed in kg. Such a set is left out rather than
     *         given a made up number.
     */
    fun effectiveWeightOf(set: LoggedSet, bodyWeightForSet: (LoggedSet) -> Double?): Double? =
        WorkoutMetrics.effectiveWeightKg(set, bodyWeightForSet(set))

/**
     * @param sets every set ever logged for the exercise; only those inside the window are used.
     * @param bodyWeightForSet resolves the body weight that applied to a given set, or null when
     * it is unknown. The body weight is the one the set carries, so it is the weight the
     * athlete had at the time of that set.
     * @param weightUnit the unit the labels built for the AI are expressed in. The returned numbers
     * stay in kilograms and are converted by [MonthlyExerciseSummary.toPromptPayload].
     * @param nowMillis reference point for the rolling window.
     */
    fun build(
        exerciseName: String,
        exerciseType: String,
        weightUnit: WeightUnit,
        sets: List<LoggedSet>,
        bodyWeightForSet: (LoggedSet) -> Double?,
        nowMillis: Long = System.currentTimeMillis()
    ): MonthlyExerciseSummary {
        val windowStart = nowMillis - (WINDOW_DAYS * 24L * 60L * 60L * 1000L)
        val windowSets = sets
            .filter { it.timestamp >= windowStart && it.timestamp <= nowMillis }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))

        val formatter = DAY_LABEL.get() ?: SimpleDateFormat("MMM d", Locale.getDefault())

        val workingSets = WorkoutMetrics.workingSets(windowSets)
        val warmupSets = windowSets.filter { it.isWarmup }
        // Holds are real work but they have no rep count, so they can never win a 1RM comparison.
        val measurableWorking = workingSets.filter {
            it.reps > 0 && effectiveWeightOf(it, bodyWeightForSet) != null
        }

        val dailyEntries = windowSets
            .groupBy { formatter.format(Date(it.timestamp)) }
            .map { (dateLabel, daySets) ->
                val dayWorking = WorkoutMetrics.workingSets(daySets)
                val dayMeasurableWorking = dayWorking.filter {
                    it.reps > 0 && effectiveWeightOf(it, bodyWeightForSet) != null
                }
                // Warm-up sets never win the working-set comparison, even when their numbers are
                // larger. The day's top set is a working set by definition.
                val dayBest = dayMeasurableWorking.maxByOrNull {
                    estimateOneRepMax(effectiveWeightOf(it, bodyWeightForSet) ?: 0.0, it.reps)
                }
                val measurableAll = daySets.filter { effectiveWeightOf(it, bodyWeightForSet) != null }
                MonthlySetEntry(
                    dateLabel = dateLabel,
                    setCount = daySets.size,
                    warmupSetCount = daySets.count { it.isWarmup },
                    dropSetCount = daySets.count { it.isDropSet },
                    topSetLabel = describeSet(dayBest, weightUnit, bodyWeightForSet) { "${it.reps} reps" },
                    volume = measurableAll.sumOf {
                        (effectiveWeightOf(it, bodyWeightForSet) ?: 0.0) * it.reps
                    },
                    estimatedOneRm = dayBest?.let {
                        estimateOneRepMax(effectiveWeightOf(it, bodyWeightForSet) ?: 0.0, it.reps)
                    } ?: 0.0
                )
            }

        val bestOverall = measurableWorking.maxByOrNull {
            estimateOneRepMax(effectiveWeightOf(it, bodyWeightForSet) ?: 0.0, it.reps)
        }
        val bestOneRm = bestOverall?.let {
            estimateOneRepMax(effectiveWeightOf(it, bodyWeightForSet) ?: 0.0, it.reps)
        } ?: 0.0
        val bestLoadLabel = bestOverall?.let {
            describeSet(it, weightUnit, bodyWeightForSet) { set -> "${set.reps} reps" }
        } ?: "n/a"
        val bestSource = bestOverall?.let { set ->
            buildString {
                append(loadLabel(set, weightUnit, bodyWeightForSet))
                append(" x ")
                append(WorkoutMetrics.recordedEffortLabel(set, weightUnit))
            }
        } ?: if (workingSets.isEmpty()) {
            "no working sets logged"
        } else {
            "no rep-based working set logged"
        }

        val setEntries = windowSets.map { set ->
            val measurable = effectiveWeightOf(set, bodyWeightForSet) != null
            MonthlySetDetail(
                dateLabel = formatter.format(Date(set.timestamp)),
                setNumber = set.setNumber,
                loadLabel = loadLabel(set, weightUnit, bodyWeightForSet),
                effortLabel = WorkoutMetrics.recordedEffortLabel(set, weightUnit),
                isWarmup = set.isWarmup,
                isDropSet = set.isDropSet,
                dropIndex = set.dropIndex,
                parentSetNumber = if (set.isDropSet) set.setNumber else 0,
                volume = if (measurable) {
                    (effectiveWeightOf(set, bodyWeightForSet) ?: 0.0) * set.reps
                } else {
                    0.0
                },
                estimatedOneRm = if (set.isWarmup || set.isDropSet || !measurable) {
                    0.0
                } else {
                    estimateOneRepMax(
                        effectiveWeightOf(set, bodyWeightForSet) ?: 0.0,
                        set.reps
                    )
                }
            )
        }

        return MonthlyExerciseSummary(
            exerciseName = exerciseName,
            exerciseType = exerciseType,
            weightUnit = weightUnit,
            daysLogged = dailyEntries.size,
            setCount = windowSets.size,
            totalVolume = windowSets.sumOf {
                (effectiveWeightOf(it, bodyWeightForSet) ?: 0.0) * it.reps
            },
            bestOneRm = bestOneRm,
            bestOneRmSource = bestSource,
            bestLoadLabel = bestLoadLabel,
            maxReps = WorkoutMetrics.maxWorkingReps(windowSets),
            firstDayOneRm = dailyEntries.firstOrNull()?.estimatedOneRm ?: 0.0,
            lastDayOneRm = dailyEntries.lastOrNull()?.estimatedOneRm ?: 0.0,
            dailyEntries = dailyEntries,
            warmupSetCount = warmupSets.size,
            workingSetCount = workingSets.size,
            setEntries = setEntries
        )
    }

    /**
     * The load of a set, in the words the AI report uses.
     *
     * The same load the rest of the app measures, so a report never describes a set differently
     * from the screen the athlete logged it on. Never invents a body weight: a set with none
     * recorded says so plainly.
     */
    private fun loadLabel(set: LoggedSet, weightUnit: WeightUnit, bodyWeightForSet: (LoggedSet) -> Double?): String {
        val unit = weightUnit.suffix
        val bodyWeight = bodyWeightForSet(set)
        return when {
            set.exerciseType == ExerciseType.CARDIO -> describeCardioSet(set, weightUnit)
            // The added load, plus the body weight that applied back then: the one rep max this
            // set produces counts both, so the label has to show both.
            set.addedWeight > 0.0 -> "+${trim(set.addedWeight, weightUnit)}$unit" +
                (bodyWeight?.let { " (bodyweight ${trim(it, weightUnit)}$unit)" } ?: "")
            set.weight > 0.0 -> "${trim(set.weight, weightUnit)}$unit"
            // Only a bodyweight type can be described as bodyweight. An empty barbell is not a
            // bodyweight movement, and giving it the athlete's weight is the same ghost load the
            // Progress screen used to show.
            ExerciseType.isBodyweight(set.exerciseType) && bodyWeight != null ->
                "bodyweight ${trim(bodyWeight, weightUnit)}$unit"
            ExerciseType.isBodyweight(set.exerciseType) -> "bodyweight not recorded"
            else -> "0$unit"
        }
    }

    private fun describeSet(
        set: LoggedSet?,
        weightUnit: WeightUnit,
        bodyWeightForSet: (LoggedSet) -> Double?,
        detail: (LoggedSet) -> String
    ): String {
        if (set == null) return "n/a"
        return "${loadLabel(set, weightUnit, bodyWeightForSet)} x ${detail(set)}"
    }

    /** Formats a stored kilogram value in the athlete's unit, without a trailing ".0". */
    private fun trim(kgValue: Double, weightUnit: WeightUnit): String {
        val value = WeightConversion.fromKg(kgValue, weightUnit)
        return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    }
}
