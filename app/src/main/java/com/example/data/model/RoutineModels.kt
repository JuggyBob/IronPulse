package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class RoutineExerciseItem(
    val exerciseId: Long = 0L,
    val exerciseName: String,
    val exerciseType: String = ExerciseType.STANDARD_WEIGHT,
    val category: String = "",
    val equipment: String = "",
    val targetSets: Int = 3,
    val targetReps: Int = 8,
    val targetWeight: Double = 0.0,
    /**
     * The default *added* weight for this exercise in the routine, in kilograms.
     *
     * Separate from [targetWeight] because the two mean different things: a target weight is the
     * barbell the athlete lifts, an added weight is what they hang on top of their own body weight.
     * A routine that says "single leg squat, 3 sets x 10 reps, +20 kg" wants the second one.
     *
     * 0 means no added weight, which is the normal case.
     */
    val targetAddedWeightKg: Double = 0.0,
    val targetHoldSeconds: Int = 0,
    /** Default distance for distance-based work, in kilometres. */
    val targetDistanceKm: Double = 0.0,
    /**
     * The default rest between sets of this exercise *in this routine*, in seconds.
     *
     * It belongs to the routine entry, not to the exercise: the same movement can want 60 seconds
     * in a hypertrophy block and 180 in a strength block. 0 means "no routine default", which keeps
     * the old behaviour (the athlete opens the timer manually). The value never overwrites the
     * global rest timer presets.
     */
    val restSeconds: Int = 0,
    val prescription: String = "",
    /**
     * The weekday this exercise belongs to: 1 = Monday ... 7 = Sunday.
     *
     * The identity of a day is its real weekday, never its position in a list. The athlete's week
     * may start on Monday or on Sunday and the editor may show the days in either order, but
     * "Wednesday" is Wednesday whatever is displayed first.
     *
     * 0 means the exercise has not been assigned to a weekday yet, which is what a routine that was
     * written before the seven day model existed looks like.
     */
    val weekday: Int = 0,
    /**
     * A short label for the day, e.g. "Upper Body Push".
     *
     * The weekday name itself is derived, never stored: storing it would be a second source of
     * truth that could disagree with [weekday].
     */
    val dayTitle: String = "",
    /**
     * Coach note written for this training day. Stored on every item of the day so the day level
     * note survives the flat JSON array format, and never turned into a fake exercise.
     */
    val dayNote: String = "",
    /** Drop sets grouped under their 1-based main set index. Empty means no drop sets. */
    val dropSets: List<RoutineDropSet> = emptyList(),
    /** Optional per-main-set defaults; missing indexes inherit the values above. */
    val mainSetDefaults: List<RoutineMainSetDefault> = emptyList(),
    /**
     * Stable pair identity shared by exactly two adjacent exercises in this routine.
     *
     * Null means no superset. The pair is an exercise-level relationship; each exercise keeps its
     * own sets, targets, rest and completion state.
     */
    val supersetGroupId: String? = null,
    /**
     * Optional planned effort target, stored canonically as RPE 6.0-10.0. RIR is display-only.
     */
    val targetEffortRpe: Double? = null
) {
    fun defaultForMainSet(mainSetIndex: Int): RoutineMainSetDefault {
        val explicit = mainSetDefaults.firstOrNull { it.mainSetIndex == mainSetIndex }
        return explicit ?: RoutineMainSetDefault(
            mainSetIndex = mainSetIndex,
            targetReps = targetReps,
            targetHoldSeconds = targetHoldSeconds,
            targetWeight = targetWeight,
            targetAddedWeightKg = targetAddedWeightKg,
            targetDistanceKm = targetDistanceKm,
            restSeconds = restSeconds
        )
    }

    fun withMainSetDefault(updated: RoutineMainSetDefault): RoutineExerciseItem {
        val next = mainSetDefaults.filterNot { it.mainSetIndex == updated.mainSetIndex } + updated
        return copy(mainSetDefaults = next.sortedBy { it.mainSetIndex })
    }
}

/**
 * Optional explicit defaults for one main set. Missing indexes fall back to the exercise-level
 * target values, so old routines keep working without materialising every row.
 */
data class RoutineMainSetDefault(
    val mainSetIndex: Int = 1,
    val targetReps: Int = 0,
    val targetHoldSeconds: Int = 0,
    val targetWeight: Double = 0.0,
    val targetAddedWeightKg: Double = 0.0,
    val targetDistanceKm: Double = 0.0,
    val restSeconds: Int = 0
)

/**
 * Re-homes a prescribed load that was stored in the field this exercise no longer records.
 *
 * Before the explicit field architecture, routine JSON (and the AI routine importer) wrote a
 * prescribed load as `addedWeight` even for dumbbell exercises, which are standard-weight work.
 * The 4.8.0 field audit then gave those exercises `weight` only, so the stored 9 kg became
 * invisible in the editor and was erased on the next save. Moving the value into the field the
 * exercise actually records keeps the athlete's prescription instead of silently zeroing it.
 *
 * If the exercise records both kinds of load, or neither, the values are left exactly as stored:
 * there is no single correct field to choose.
 */
fun RoutineExerciseItem.withLegacyLoadFallback(
    fields: List<ExerciseField>
): RoutineExerciseItem {
    if (fields.isEmpty()) return this
    val (weight, added) = migrateStoredLoad(targetWeight, targetAddedWeightKg, fields)
    val normalizedDrops = dropSets.map { it.withLegacyLoadFallback(fields) }
    val normalizedMains = mainSetDefaults.map { it.withLegacyLoadFallback(fields) }
    val normalized = copy(
        targetWeight = weight,
        targetAddedWeightKg = added,
        dropSets = normalizedDrops,
        mainSetDefaults = normalizedMains
    )
    return if (normalized == this) this else normalized
}

fun RoutineDropSet.withLegacyLoadFallback(fields: List<ExerciseField>): RoutineDropSet {
    if (fields.isEmpty()) return this
    val (weight, added) = migrateStoredLoad(targetWeight, targetAddedWeightKg, fields)
    return if (weight == targetWeight && added == targetAddedWeightKg) {
        this
    } else {
        copy(targetWeight = weight, targetAddedWeightKg = added)
    }
}

fun RoutineMainSetDefault.withLegacyLoadFallback(
    fields: List<ExerciseField>
): RoutineMainSetDefault {
    if (fields.isEmpty()) return this
    val (weight, added) = migrateStoredLoad(targetWeight, targetAddedWeightKg, fields)
    return if (weight == targetWeight && added == targetAddedWeightKg) {
        this
    } else {
        copy(targetWeight = weight, targetAddedWeightKg = added)
    }
}

/**
 * @return the pair (weight, addedWeight) to store for this exercise's field configuration.
 */
private fun migrateStoredLoad(
    weight: Double,
    addedWeight: Double,
    fields: List<ExerciseField>
): Pair<Double, Double> {
    val supportsWeight = ExerciseField.WEIGHT in fields
    val supportsAdded = ExerciseField.ADDED_WEIGHT in fields
    // Both fields are meaningful, or neither is: keep exactly what the routine stored.
    if (supportsWeight == supportsAdded) return weight to addedWeight
    return when {
        supportsWeight && weight == 0.0 && addedWeight > 0.0 -> addedWeight to 0.0
        supportsAdded && addedWeight == 0.0 && weight > 0.0 -> 0.0 to weight
        else -> weight to addedWeight
    }
}

/**
 * Clears targets the exercise no longer supports and rebuilds the prescription.
 *
 * This is for editable routine defaults, not historical sets. The stored workout history keeps
 * every value it was recorded with; a routine that the athlete is editing should not claim a
 * "8 reps" target for a Plank that only records holds.
 */
fun RoutineExerciseItem.sanitizedFor(fields: List<ExerciseField>): RoutineExerciseItem {
    if (fields.isEmpty()) return this
    val normalized = withLegacyLoadFallback(fields)
    val sanitized = normalized.copy(
        targetReps = if (ExerciseField.REPS in fields) normalized.targetReps else 0,
        targetWeight = if (ExerciseField.WEIGHT in fields) normalized.targetWeight else 0.0,
        targetAddedWeightKg = if (ExerciseField.ADDED_WEIGHT in fields) {
            normalized.targetAddedWeightKg
        } else {
            0.0
        },
        targetHoldSeconds = if (
            ExerciseField.HOLD_SECONDS in fields || ExerciseField.TIME_SECONDS in fields
        ) {
            normalized.targetHoldSeconds
        } else {
            0
        },
        targetDistanceKm = if (ExerciseField.DISTANCE_KM in fields) {
            normalized.targetDistanceKm
        } else {
            0.0
        },
        dropSets = normalized.dropSets.map { it.sanitizedFor(fields) },
        mainSetDefaults = normalized.mainSetDefaults.map { it.sanitizedFor(fields) }
    )
    return sanitized.copy(prescription = RoutineParser.buildPrescription(sanitized))
}

fun RoutineDropSet.sanitizedFor(fields: List<ExerciseField>): RoutineDropSet {
    if (fields.isEmpty()) return this
    val normalized = withLegacyLoadFallback(fields)
    return normalized.copy(
        targetReps = if (ExerciseField.REPS in fields) normalized.targetReps else 0,
        targetWeight = if (ExerciseField.WEIGHT in fields) normalized.targetWeight else 0.0,
        targetAddedWeightKg = if (ExerciseField.ADDED_WEIGHT in fields) {
            normalized.targetAddedWeightKg
        } else {
            0.0
        },
        targetHoldSeconds = if (
            ExerciseField.HOLD_SECONDS in fields || ExerciseField.TIME_SECONDS in fields
        ) {
            normalized.targetHoldSeconds
        } else {
            0
        },
        targetDistanceKm = if (ExerciseField.DISTANCE_KM in fields) {
            normalized.targetDistanceKm
        } else {
            0.0
        }
    )
}

fun RoutineMainSetDefault.sanitizedFor(fields: List<ExerciseField>): RoutineMainSetDefault {
    if (fields.isEmpty()) return this
    val normalized = withLegacyLoadFallback(fields)
    return normalized.copy(
        targetReps = if (ExerciseField.REPS in fields) normalized.targetReps else 0,
        targetWeight = if (ExerciseField.WEIGHT in fields) normalized.targetWeight else 0.0,
        targetAddedWeightKg = if (ExerciseField.ADDED_WEIGHT in fields) {
            normalized.targetAddedWeightKg
        } else {
            0.0
        },
        targetHoldSeconds = if (
            ExerciseField.HOLD_SECONDS in fields || ExerciseField.TIME_SECONDS in fields
        ) {
            normalized.targetHoldSeconds
        } else {
            0
        },
        targetDistanceKm = if (ExerciseField.DISTANCE_KM in fields) {
            normalized.targetDistanceKm
        } else {
            0.0
        }
    )
}

/**
 * One drop-set prescription attached to a main set of a saved routine.
 *
 * [mainSetIndex] is the 1-based main set within the exercise, and [dropIndex] is the 1-based
 * position within that main set. Numbers are local, never global identifiers.
 */
data class RoutineDropSet(
    val mainSetIndex: Int = 1,
    val dropIndex: Int = 1,
    val targetReps: Int = 0,
    val targetHoldSeconds: Int = 0,
    val targetWeight: Double = 0.0,
    val targetAddedWeightKg: Double = 0.0,
    val targetDistanceKm: Double = 0.0,
    val restSeconds: Int = 0
)

data class RoutineDay(
    /**
     * The weekday this slot stands for: 1 = Monday ... 7 = Sunday.
     */
    val weekday: Int,
    /**
     * A short label for the day, e.g. "Upper Body Push". Empty for a plain day.
     */
    val dayTitle: String = "",
    /**
     * The exercises of this day.
     *
     * A day with no exercises is a rest day. There is no separate rest flag: an empty list IS the
     * rest day, so a day can never claim to be a workout day while holding nothing.
     */
    val exercises: List<RoutineExerciseItem> = emptyList(),
    val note: String = "",
    /** The standalone Routine referenced by this Schedule slot, or 0 for a rest day. */
    val routineId: Long = 0L
) {
    /** Whether this day is a planned rest day. */
    val isRestDay: Boolean get() = exercises.isEmpty()

    /** The name of the weekday, e.g. "Monday". */
    val dayName: String get() = Weekday.name(weekday)
}


/**
 * Snapshot of an exercise removed from a running workout, used by the Undo action.
 *
 * The saved routine is deliberately not part of this snapshot: active-workout deletion never
 * changes the routine. The snapshot only restores the session's own exercise, sets and position.
 */
data class RemovedActiveExercise(
    val exercise: Exercise,
    val routineItem: RoutineExerciseItem?,
    val sets: List<LoggedSet>,
    val index: Int,
    val wasSessionOnly: Boolean
)

data class ActiveRoutineWorkoutState(
    val routineId: Long,
    val routineName: String,
    val routineGoal: String,
    val startTime: Long = System.currentTimeMillis(),
    val exercises: List<Exercise>,
    val routineItems: List<RoutineExerciseItem> = emptyList(),
    val sets: List<LoggedSet> = emptyList(),
    /**
     * The weekday this workout belongs to: 1 = Monday ... 7 = Sunday.
     */
    val weekday: Int? = null,
    val dayTitle: String? = null,
    val dayNote: String = "",
    /**
     * The local start-of-day of the planned routine day this session fulfils.
     *
     * 0 means the day could not be resolved (for example a legacy routine with no weekday); such a
     * session is still saved, but it cannot count towards schedule adherence.
     */
    val plannedDate: Long = 0L,
    /** Timezone-stable `yyyyMMdd` key of [plannedDate]. */
    val plannedDateKey: Long = 0L,
    /** Schedule that supplied the workout, or 0 for a standalone Routine. */
    val scheduleId: Long = 0L,
    /** Schedule goal strategy captured at start. */
    val scheduleGoal: String = "",
    /** Exercises added during this active workout that did not come from the saved routine. */
    val sessionOnlyExerciseIds: Set<Long> = emptySet()
)

object RoutineParser {
    /**
     * Words that change which movement an exercise actually is.
     *
     * Two exercises sharing a modifier are not interchangeable, so a query carrying a modifier
     * never falls back onto a candidate that lacks it. This list grew with the dataset catalog:
     * it now also carries the load qualifiers the dataset uses ("assisted", "single", "one"),
     * the equipment families it distinguishes ("smith", "leverage", "ez"), and the bodyweight
     * variants ("pike", "hindu", "archer"). Without them "smith machine squat" would happily match
     * a plain "squat", which is a different movement with a different bar path.
     */
    val MODIFIER_WORDS = setOf(
        "band", "banded", "ring", "rings", "cable", "dumbbell", "barbell",
        "kettlebell", "machine", "weighted", "incline", "decline", "close",
        "wide", "reverse", "seated", "standing", "hanging", "nordic", "pelican",
        "smith", "leverage", "ez", "assisted", "single", "one", "pike",
        "hindu", "archer", "twin", "isometric", "plyometric", "explosive"
    )

    /**
     * Strips every separator and lowercases the result, so "push-up", "Push Up" and "pushup"
     * all collapse to the same key.
     */
    private fun alphanumericKey(value: String): String = value.filter { it.isLetterOrDigit() }.lowercase()

    /**
     * Splits a string into lowercase alphanumeric words. Implemented without a Regex because the
     * exercise matcher runs against the whole 800+ library on every keystroke of the custom
     * exercise name field.
     */
    private fun tokenize(value: String): Set<String> {
        val tokens = HashSet<String>()
        val current = StringBuilder()
        for (char in value.lowercase()) {
            if (char.isLetterOrDigit()) {
                current.append(char)
            } else if (current.isNotEmpty()) {
                tokens.add(current.toString())
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens.filter { it.length > 1 }.map { it.removeSuffix("es").removeSuffix("s") }.toSet()
    }

    /**
     * Same as [alphanumericKey] but also removes a trailing plural "s"/"es", so "pushups"
     * collapses onto "pushup" and therefore matches the stored "Push-up".
     */
    private fun singularAlphanumericKey(value: String): String {
        val key = alphanumericKey(value)
        return when {
            key.length > 3 && key.endsWith("es") -> key.dropLast(2)
            key.length > 2 && key.endsWith("s") -> key.dropLast(1)
            else -> key
        }
    }

    fun findMatchingExercise(queryName: String, candidates: Collection<Exercise>): Exercise? {
        val cleanQuery = queryName.trim().lowercase()
        if (cleanQuery.isBlank()) return null

        // 1. Exact case-insensitive match
        candidates.firstOrNull { it.name.trim().equals(cleanQuery, ignoreCase = true) }?.let { return it }

        // 2. Normalized alphanumeric (e.g. "push-up" vs "push up" vs "pushup" / "pushups")
        val queryAlnum = alphanumericKey(cleanQuery)
        candidates.firstOrNull { alphanumericKey(it.name) == queryAlnum }?.let { return it }
        val querySingular = singularAlphanumericKey(cleanQuery)
        if (querySingular.isNotEmpty()) {
            candidates.firstOrNull { singularAlphanumericKey(it.name) == querySingular }?.let { return it }
        }

        // 3. Stem match: singular/plural (e.g. "knee raises" -> "knee raise", "squats" -> "squat")
        val stemQuery = cleanQuery.removeSuffix("s").removeSuffix("es")
        candidates.firstOrNull {
            val candClean = it.name.trim().lowercase()
            val candStem = candClean.removeSuffix("s").removeSuffix("es")
            candStem == stemQuery || candStem == cleanQuery || candClean == stemQuery
        }?.let { return it }

        // 4. Base name without parenthetical details in DB title
        candidates.firstOrNull {
            val baseCand = it.name.substringBefore("(").trim().lowercase()
            baseCand == cleanQuery || baseCand.removeSuffix("s").removeSuffix("es") == stemQuery
        }?.let { return it }

        // 5. Query without parenthetical details
        val queryBase = cleanQuery.substringBefore("(").trim()
        if (queryBase.length >= 3 && queryBase != cleanQuery) {
            candidates.firstOrNull {
                it.name.trim().equals(queryBase, ignoreCase = true) ||
                it.name.trim().lowercase().removeSuffix("s") == queryBase.removeSuffix("s")
            }?.let { return it }
        }

        // 6. Token-based overlap matching for multi-word queries
        val queryTokens = tokenize(cleanQuery)

        if (queryTokens.isNotEmpty()) {
            val scored = candidates.mapNotNull { cand ->
                val candTokens = tokenize(cand.name)

                // Check for modifier conflict: if query has a modifier that candidate lacks
                val queryModifiers = queryTokens.intersect(MODIFIER_WORDS)
                val candModifiers = candTokens.intersect(MODIFIER_WORDS)
                if (queryModifiers.isNotEmpty() && !candModifiers.containsAll(queryModifiers)) {
                    return@mapNotNull null
                }

                val intersection = queryTokens.intersect(candTokens)
                if (intersection.size == queryTokens.size) {
                    val unionSize = (queryTokens.size + candTokens.size - intersection.size)
                    val jaccard = intersection.size.toDouble() / unionSize
                    Pair(cand, jaccard)
                } else if (intersection.size >= 2 && intersection.size == candTokens.size) {
                    val unionSize = (queryTokens.size + candTokens.size - intersection.size)
                    val jaccard = intersection.size.toDouble() / unionSize
                    Pair(cand, jaccard)
                } else {
                    null
                }
            }
            val best = scored.maxByOrNull { it.second }
            if (best != null && best.second >= 0.5) {
                return best.first
            }
        }

        return null
    }

    /**
     * The exercise an AI name actually refers to, using the athlete's own words as the authority.
     *
     * This exists because of a real failure: a pasted workout containing "Decline Push Ups" came back
     * from the AI as "Push-Up", and the exact-name lookup then succeeded, so the decline was lost
     * silently. The two movements are not interchangeable — a decline push-up loads the upper chest
     * and front deltoid much harder — so mislabelling one as the other corrupts the athlete's history
     * rather than merely their routine.
     *
     * The AI normalises hard, and "Push-Up" is a plausible answer to a busy model. It cannot be
     * trusted with modifiers, so the source text is consulted instead: the line the athlete typed is
     * matched against the catalog, and that match wins. The AI's own name is only used to find *which
     * line* we are talking about.
     *
     * @param aiName the name the AI returned for this item
     * @param sourceText the raw text the athlete pasted, unmodified
     * @param candidates the catalog to match against
     * @return the exercise the athlete actually wrote, or null when the source cannot be recovered
     */
    fun resolveAgainstSource(
        aiName: String,
        sourceText: String,
        candidates: Collection<Exercise>
    ): Exercise? {
        val aiKey = alphanumericKey(aiName)
        if (aiKey.isBlank() || sourceText.isBlank()) return null

        // Lines that could correspond to this item. Compared on the alphanumeric key so a line the
        // AI reformatted still counts as the source for this name.
        val lines = sourceText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val relevant = lines.filter { line ->
            val lineKey = alphanumericKey(nameHeadOfLine(line))
            lineKey.isNotEmpty() && (lineKey.contains(aiKey) || aiKey.contains(lineKey))
        }
        if (relevant.isEmpty()) return null

        // The first line that resolves to a catalog exercise wins. Restricted to lines whose
        // modifiers are all carried by the match, so a source line mentioning a decline never
        // satisfies itself with a plain push-up.
        for (line in relevant) {
            val head = nameHeadOfLine(line)
            if (head.isBlank()) continue
            val headTokens = tokenize(head)
            val requiredModifiers = headTokens.intersect(MODIFIER_WORDS)

            val match = findMatchingExercise(head, candidates) ?: continue
            if (requiredModifiers.isEmpty()) continue
            if (tokenize(match.name).containsAll(requiredModifiers)) return match
        }

        return null
    }

    /**
     * The exercise-name part of a pasted line, without the prescription.
     *
     * Splits on separators that cannot occur inside a movement name: a spaced dash or colon, a pipe,
     * and an opening parenthesis. A hyphen without spaces is deliberately not a separator, or
     * "Push-Up" would be truncated to "Push".
     */
    private fun nameHeadOfLine(line: String): String {
        var head = line.removePrefix("-").removePrefix("*").removePrefix("•").trim()
        head = head.replaceFirst(Regex("""^\d+[\.\)]\s*"""), "")
        head = head.substringBefore('|')
        // A spaced separator only. A hyphen without spaces is deliberately left alone, or "Push-Up"
        // would be truncated to "Push".
        // A spaced dash, or a colon/equals followed by a space. A hyphen without spaces is
        // deliberately left alone, or "Push-Up" would be truncated to "Push". The colon form has
        // to match without a space before it: "Single Arm Band Curl: 3 x 8 @ 34kg" is a normal
        // pasted line, and treating it as prose dropped the whole exercise.
        val separator = Regex("""(?:\s+[-–—]\s+|\s*[:=]\s+)""").find(head)
        if (separator != null) head = head.substring(0, separator.range.first)
        head = head.substringBefore('(')
        return head.trim()
    }

    /**
     * A compact prescription that keeps every independently configured target.
     *
     * It deliberately excludes load: the existing design keeps weight in its own fields and out of
     * the prescription text. Unlike the old implementation, reps and hold/distance are not
     * mutually exclusive.
     */
    /**
     * The prescription as it should be displayed for a routine target.
     *
     * Standard rep work keeps a hand-written rep range; a hold or distance target is rebuilt from
     * the values so the header cannot show "3 x 8 reps" for a Plank with no rep field.
     */
    fun displayPrescription(item: RoutineExerciseItem): String =
        if (item.targetHoldSeconds > 0 || item.targetDistanceKm > 0.0) {
            buildPrescription(item)
        } else {
            shortPrescription(item.prescription, item.equipment)
                .ifBlank { buildPrescription(item) }
        }

    fun buildPrescription(item: RoutineExerciseItem): String {
        val parts = mutableListOf<String>()
        if (item.targetReps > 0) parts += "${item.targetReps} reps"
        if (item.targetHoldSeconds > 0) parts += "${item.targetHoldSeconds}s"
        if (item.targetDistanceKm > 0.0) parts += "${formatKm(item.targetDistanceKm)} km"
        if (parts.isEmpty()) parts += "0 reps"
        return "${item.targetSets} sets x " + parts.joinToString(" / ")
    }

    private fun formatKm(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            "%.2f".format(value).trimEnd('0').trimEnd('.')
        }

    fun serializeExercises(items: List<RoutineExerciseItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.exerciseId)
                put("name", item.exerciseName)
                put("type", item.exerciseType)
                put("category", item.category)
                put("equipment", item.equipment)
                put("sets", item.targetSets)
                put("reps", item.targetReps)
                put("weight", item.targetWeight)
                // Backward compatible: routines saved before this field simply have no added weight
                put("addedWeight", item.targetAddedWeightKg)
                put("holdSeconds", item.targetHoldSeconds)
                if (item.targetDistanceKm > 0.0) put("distanceKm", item.targetDistanceKm)
                // Backward compatible: routines saved before this field simply have no rest default.
                if (item.restSeconds > 0) put("restSeconds", item.restSeconds)
                put("prescription", item.prescription)
                // The weekday this exercise belongs to. A routine saved before the seven day model
                // existed has no "weekday" key, and the parser reads the old "day" slot instead.
                put("weekday", item.weekday)
                put("dayTitle", item.dayTitle)
                // Backward compatible: routines saved before this field simply have no note
                if (item.dayNote.isNotBlank()) put("dayNote", item.dayNote)
                item.supersetGroupId?.takeIf { it.isNotBlank() }?.let { put("supersetGroupId", it) }
                EffortScale.normalize(item.targetEffortRpe)?.let { put("targetEffortRpe", it) }
                if (item.mainSetDefaults.isNotEmpty()) {
                    put("mainSets", JSONArray().apply {
                        item.mainSetDefaults.sortedBy { it.mainSetIndex }.forEach { mainSet ->
                            put(JSONObject().apply {
                                put("main", mainSet.mainSetIndex)
                                put("reps", mainSet.targetReps)
                                put("holdSeconds", mainSet.targetHoldSeconds)
                                put("weight", mainSet.targetWeight)
                                put("addedWeight", mainSet.targetAddedWeightKg)
                                if (mainSet.targetDistanceKm > 0.0) {
                                    put("distanceKm", mainSet.targetDistanceKm)
                                }
                                if (mainSet.restSeconds > 0) put("restSeconds", mainSet.restSeconds)
                            })
                        }
                    })
                }
                if (item.dropSets.isNotEmpty()) {
                    put("dropSets", JSONArray().apply {
                        for (drop in item.dropSets.sortedWith(compareBy({ it.mainSetIndex }, { it.dropIndex }))) {
                            put(JSONObject().apply {
                                put("mainSet", drop.mainSetIndex)
                                put("drop", drop.dropIndex)
                                put("reps", drop.targetReps)
                                put("holdSeconds", drop.targetHoldSeconds)
                                put("weight", drop.targetWeight)
                                put("addedWeight", drop.targetAddedWeightKg)
                                if (drop.targetDistanceKm > 0.0) put("distanceKm", drop.targetDistanceKm)
                                if (drop.restSeconds > 0) put("restSeconds", drop.restSeconds)
                            })
                        }
                    })
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Words that mark a line as a coaching note instead of an exercise.
     *
     * Only strings that no exercise name can start with belong here, otherwise a real exercise
     * would silently disappear from the generated routine.
     */
    private val NOTE_KEYWORDS = listOf(
        // Label style
        "coach", "note", "tip", "reminder", "remember", "focus", "aim", "goal", "principle",
        "important", "intensity", "progression", "volume", "recovery", "nutrition", "hydration",
        "sleep", "mobility", "key point", "progressive overload", "rep range", "rest period",
        "warm up", "warm-up", "warmup", "cool down", "cooldown", "form cue",
        // Sentence style
        "ensure", "prioritize", "prioritise", "keep", "avoid", "tempo", "breathing", "cue",
        "exhale", "inhale", "brace", "use ", "start with", "rest ", "increase", "decrease",
        "recommended", "recommend", "suggest", "consider", "perform", "you should", "take a",
        "drop set", "rpe"
    )

    /** Regex matching a line that is nothing but a note label, e.g. "**Coach's Note:**". */
    private val NOTE_LABEL_ONLY = Regex(
        "^(coach'?s?\\s*)?(note|tip|tips|reminder|focus|goal|aim|principle|intensity|" +
                "progression|volume|recovery|nutrition|hydration|sleep|mobility|key point|" +
                "progressive overload|rep range|rest period|warm ?-?up|cool ?-?down)" +
                "(s)?\\s*[:\\-–—]?\\s*$",
        RegexOption.IGNORE_CASE
    )

    /**
     * Detects a coach note / day description line.
     *
     * The AI writes these under the exercises (e.g. "**Coach's Note:** leave 2 reps in reserve"
     * or a plain prose sentence). They must become the day description and never an exercise,
     * otherwise they show up as a bogus exercise in the routine and in the workout screen.
     */
    fun isCoachNoteLine(rawLine: String): Boolean {
        val line = rawLine.trim()
        if (line.isBlank()) return false
        // Markdown blockquote
        if (line.startsWith(">")) return true

        val stripped = line.trimStart('#', '*', '-', '\u2022', ' ').trim()
        if (stripped.isEmpty()) return false
        val lower = stripped.lowercase()

        // "Coach's Note: ...", "Note: ...", "Tip - ...", "Focus on ...", ...
        if (NOTE_KEYWORDS.any { lower.startsWith(it) }) return true

        val hasDigits = stripped.any { it.isDigit() }
        val wordCount = stripped.split(Regex("\\s+")).count { it.isNotBlank() }
        val boldWrapped = stripped.length > 4 && stripped.startsWith("**") && stripped.endsWith("**")

        // Pure prose sentence with no prescription numbers: a note
        if (!hasDigits && wordCount >= 5 && (stripped.endsWith(".") || stripped.endsWith("!") || boldWrapped)) {
            return true
        }
        // Bold/italic wrapped phrase with no numbers
        if (!hasDigits && boldWrapped && wordCount >= 4) return true

        return false
    }

    /** Strips the markdown decorations of a note so it renders as clean text. */
    fun cleanNoteText(rawNote: String): String = rawNote
        .lines()
        .map { line ->
            line.trim()
                .removePrefix(">")
                .trim()
                .replace(Regex("^[*_#>\\-\u2022\\s]+"), "")
                // "Coach's Note:** text" -> "Coach's Note: text"
                .replace(Regex("[*_`]{2,}\\s*:\\s*"), ": ")
                .replace(Regex("[*_`]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
        .joinToString(" ")
        .replace(Regex("\\s{2,}"), " ")
        .trim()

    /**
     * The days of a routine that hold a workout.
     *
     * A rest day holds no exercise, so it is not one of the training days. Use [parseWeek] to see
     * the seven slots of the week, rest days included.
     *
     * @param weekStartsOnMonday where the athlete's week begins. It decides where a day that was
     *        written as a position rather than a name lands.
     */
    fun parseDays(
        rawText: String,
        allExercises: List<Exercise>,
        weekStartsOnMonday: Boolean = true
    ): List<RoutineDay> =
        parseWeek(rawText, allExercises, weekStartsOnMonday).filter { !it.isRestDay }

    /**
     * The seven slots of the week a routine describes, one per weekday.
     *
     * The result always has seven days, whether the source named them or not. A day with no
     * exercises in it is a rest day.
     *
     * @param weekStartsOnMonday where the athlete's week begins. It decides where a day that was
     *        written as a position rather than a name lands.
     */
    fun parseWeek(
        rawText: String,
        allExercises: List<Exercise>,
        weekStartsOnMonday: Boolean = true
    ): List<RoutineDay> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val exByName = allExercises.associateBy { it.name.trim().lowercase() }

        // 1. Try JSON
        if ((trimmed.startsWith("[") && trimmed.endsWith("]")) || (trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            val jsonDays = parseJsonDays(trimmed, exByName, weekStartsOnMonday)
            if (jsonDays.isNotEmpty()) return jsonDays
            // A well formed JSON answer that holds no routine in it is an answer with no routine in
            // it. Reading it as prose would turn the brackets themselves into an exercise.
            if (isJsonStructure(trimmed)) return emptyList()
        }

        // 2. Try structured text / markdown with day headers
        return parseTextDays(trimmed, exByName, weekStartsOnMonday)
    }

    /**
     * Whether the text is a well formed JSON array or object.
     *
     * Used to tell an answer that really is JSON apart from text that merely starts with a
     * bracket.
     */
    fun isJsonStructure(text: String): Boolean = try {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed)
        }
        true
    } catch (_: Exception) {
        false
    }

    fun parseExercises(
        rawText: String,
        allExercises: List<Exercise>,
        weekStartsOnMonday: Boolean = true
    ): List<RoutineExerciseItem> {
        return parseWeek(rawText, allExercises, weekStartsOnMonday).flatMap { it.exercises }
    }

    /**
     * The flat exercise list of one standalone workout Routine.
     *
     * Any weekday metadata is intentionally discarded: a Routine is one workout, not a day of a
     * week. Legacy week JSON is accepted so an existing routine can still be opened before the
     * one-time model migration has run.
     */
    fun parseSingleWorkout(
        rawText: String,
        allExercises: List<Exercise>
    ): List<RoutineExerciseItem> = parseExercises(
        rawText = rawText,
        allExercises = allExercises,
        weekStartsOnMonday = true
    ).map { it.copy(weekday = 0, dayTitle = "", dayNote = "") }
        .let(SupersetRules::normalize)

    private fun parseJsonDays(
        trimmed: String,
        exByName: Map<String, Exercise>,
        weekStartsOnMonday: Boolean
    ): List<RoutineDay> {
        try {
            val items = mutableListOf<ScheduledWorkout>()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                var slot = 1
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val workout = parseJsonExerciseItem(obj, exByName, fallbackSlot = slot)
                    if (workout != null) {
                        items.add(workout)
                        // A workout with no day of its own is a day of its own.
                        if (workout.weekday == 0 && obj.opt("day") == null) slot++
                    }
                }
            } else {
                val rootObj = JSONObject(trimmed)
                // The seven day form: a "days" array with one entry per weekday. A day with no
                // exercises in it is a rest day, and it is kept rather than dropped, so the
                // schedule the source described survives.
                val daysArray = rootObj.optJSONArray("days")
                if (daysArray != null && daysArray.length() > 0) {
                    val dayWorkouts = mutableListOf<ScheduledWorkout>()
                    for (d in 0 until daysArray.length()) {
                        val dayObj = daysArray.optJSONObject(d) ?: continue
                        val dayTitle = cleanDayTitle(
                            dayObj.optString("dayTitle", dayObj.optString("title", dayObj.optString("focus", "")))
                        )
                        val note = dayObj.optString("note", "")
                        // A day may name its weekday, or be a rest day with nothing in it.
                        val weekday = readScheduleHint(dayObj, d + 1).weekday
                        val dayExArray = dayObj.optJSONArray("exercises") ?: JSONArray()
                        for (j in 0 until dayExArray.length()) {
                            val exObj = dayExArray.optJSONObject(j) ?: continue
                            val workout = parseJsonExerciseItem(
                                exObj,
                                exByName,
                                overrideWeekday = weekday,
                                overrideDayTitle = dayTitle,
                                fallbackSlot = d + 1
                            )
                            if (workout != null) {
                                dayWorkouts.add(
                                    workout.copy(
                                        item = workout.item.copy(dayNote = workout.item.dayNote.ifBlank { note })
                                    )
                                )
                            }
                        }
                    }
                    if (dayWorkouts.isNotEmpty()) {
                        return RoutineSchedule.buildDays(dayWorkouts, weekStartsOnMonday)
                    }
                }

                // Check if it has "exercises" array
                val exArray = rootObj.optJSONArray("exercises")
                if (exArray != null) {
                    var slot = 1
                    for (i in 0 until exArray.length()) {
                        val obj = exArray.optJSONObject(i) ?: continue
                        val workout = parseJsonExerciseItem(obj, exByName, fallbackSlot = slot)
                        if (workout != null) {
                            items.add(workout)
                            if (workout.weekday == 0 && obj.opt("day") == null) slot++
                        }
                    }
                }
            }

            if (items.isEmpty()) return emptyList()

            // The seven slots, built from what the source gave.
            return RoutineSchedule.buildDays(items, weekStartsOnMonday)
        } catch (_: Exception) {
            return emptyList()
        }
    }

    fun cleanPrescription(rawPrescription: String, equipment: String = ""): String {
        if (rawPrescription.isBlank()) return ""
        var s = rawPrescription.trim()

        // 1. Remove parenthesized or bracketed equipment (e.g. "(Barbell)", "(with Dumbbells)", "[Cable]")
        s = s.replace(Regex("""(?i)\((?:(?:with|using|on)\s+)?(?:barbell|dumbbell|dumbbells|cable|cables|machine|machines|bodyweight|rings|gymnastic rings|resistance bands?|bands?|kettlebells?|smith machine|ez[- ]bar|ez[- ]curl bar|trap bar|hex bar|pull[- ]up bar|parallel bars|parallettes|plates?|weight plates?|bench|dip belt|weight vest)[^)]*\)"""), "")
        s = s.replace(Regex("""(?i)\[(?:(?:with|using|on)\s+)?(?:barbell|dumbbell|dumbbells|cable|cables|machine|machines|bodyweight|rings|gymnastic rings|resistance bands?|bands?|kettlebells?|smith machine|ez[- ]bar|ez[- ]curl bar|trap bar|hex bar|pull[- ]up bar|parallel bars|parallettes|plates?|weight plates?|bench|dip belt|weight vest)[^\]]*\]"""), "")

        // 2. Remove phrases like "with barbell", "using dumbbells", "on cable", "on machine"
        s = s.replace(Regex("""(?i)\b(?:with|using|on)\s+(?:a\s+)?(?:barbell|dumbbell|dumbbells|cable|cables|machine|machines|bodyweight|rings|gymnastic rings|resistance bands?|bands?|kettlebells?|smith machine|ez[- ]bar|ez[- ]curl bar|trap bar|hex bar|pull[- ]up bar|parallel bars|parallettes|plates?|weight plates?|bench|dip belt|weight vest)\b"""), "")
        s = s.replace(Regex("""(?i)\b(?:barbell|dumbbell|dumbbells|cable|cables|machine|machines|bodyweight|rings|gymnastic rings|resistance bands?|bands?|kettlebells?|smith machine|ez[- ]bar|ez[- ]curl bar|trap bar|hex bar|pull[- ]up bar|parallel bars|parallettes|plates?|weight plates?|bench|dip belt|weight vest)\s+(?:version|variation|only)\b"""), "")

        // 3. Remove specific equipment string if passed
        if (equipment.isNotBlank()) {
            val escaped = Regex.escape(equipment.trim())
            s = s.replace(Regex("""(?i)\($escaped\)"""), "")
            s = s.replace(Regex("""(?i)\[$escaped\]"""), "")
            s = s.replace(Regex("""(?i)\b(?:with|using|on)\s+$escaped\b"""), "")
            s = s.replace(Regex("""(?i)\b$escaped\b"""), "")
        }

        // 4. Remove empty parens/brackets that might have been left over
        s = s.replace(Regex("""\(\s*\)"""), "")
        s = s.replace(Regex("""\[\s*\]"""), "")

        // 5. Remove leading/trailing punctuation and whitespace
        s = s.replace(Regex("""^[\s\-–—:•|,\\]+|[\s\-–—:•|,\\]+$"""), "")
        s = s.replace(Regex("""\s+"""), " ").trim()

        // 6. If the entire prescription had ONLY equipment and no numbers/sets/reps left, return "" so default sets x reps is generated
        val hasDigits = s.any { it.isDigit() }
        if (!hasDigits) {
            return ""
        }

        return s
    }

    /**
     * The short form of a prescription: "<sets> sets x <reps> reps", or "<sets> sets x <seconds>s"
     * for an isometric hold. Nothing else.
     *
     * The AI is asked for exactly this shape, but a routine saved before that rule existed still
     * carries the longer form it used to write ("4 sets x 6 reps @ 80kg", "3 sets x 30s, rest 60s")
     * and the tag that shows it is far too narrow for that. Reducing it here keeps every saved
     * routine readable without rewriting the athlete's data.
     *
     * Rep ranges are kept as written ("2 sets x 12-15 reps"): the range is the prescription.
     */
    fun shortPrescription(rawPrescription: String, equipment: String = ""): String {
        val cleaned = cleanPrescription(rawPrescription, equipment)
        if (cleaned.isBlank()) return ""

        val core = SHORT_PRESCRIPTION.find(cleaned)
        if (core != null) {
            val sets = core.groupValues[1]
            val count = core.groupValues[2].replace(" ", "")
            val unit = core.groupValues[3].lowercase()
            // "s" means seconds. Everything else (including a missing unit) is reps.
            return if (unit.startsWith("s")) "$sets sets x ${count}s" else "$sets sets x $count reps"
        }

        // No sets x reps core: fall back to dropping the load and any trailing commentary.
        return stripLoadAndCommentary(cleaned)
    }

    /** "4 sets x 8 reps", "3 sets x 12-15 reps", "3 sets x 30s", "4x8". */
    private val SHORT_PRESCRIPTION = Regex(
        """(\d+)\s*(?:sets|set)?\s*(?:x|×|\*)\s*(\d+(?:\s*[-–]\s*\d+)?)\s*(reps?|seconds?|secs?|s)?""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Removes a load and any trailing commentary from a prescription that has no sets x reps core.
     * A last resort for shapes like "80kg for 10 reps", which the core regex cannot read.
     */
    private fun stripLoadAndCommentary(text: String): String {
        var s = text
        s = s.replace(Regex("""(?i)(@\s*)?\b\d+(?:[.,]\d+)?\s*(?:kg|kgs|kilograms?|lb|lbs|pounds?)\b"""), "")
        s = s.replace(Regex("""(?i)\b(?:body\s*weight|bodyweight|bw)\b"""), "")
        s = s.substringBefore(",")
        s = s.replace(Regex("""\s+"""), " ").trim()
        s = s.replace(Regex("""^[\s\-–—:•|,\.]+|[\s\-–—:•|,\.]+$"""), "")
        return s
    }

    /**
     * Reads inline drop-set clauses from a text prescription.
     *
     * Examples understood:
     *  - "3x8 @ 40kg, drop 30kg x 8, drop 20kg x 6"
     *  - "4 sets x 10 reps (drop set: 25kg x 12)"
     *  - "3x8; after set 2 drop to 30kg for 8"
     *
     * The parent is explicit: "after set N" selects that main set, otherwise the first one. Each
     * reduction becomes a direct child with its own index, never another top-level exercise.
     */
    /**
     * A line that is only a drop-set instruction for the previous exercise.
     *
     * Returns an empty list when the line is not a drop instruction or carries no numbers, so
     * ordinary coach notes still follow the note path.
     */
    private fun parseStandaloneDropLine(line: String, mainSetCount: Int): List<RoutineDropSet> {
        val cleaned = line
            .trim()
            .replaceFirst(Regex("^[*_#>\\-\\u2022\\s]+"), "")
            .trim()
        if (!cleaned.startsWith("drop", ignoreCase = true)) return emptyList()
        return parseInlineDropSets(cleaned, mainSetCount)
    }

    private fun parseInlineDropSets(rawPrescription: String, mainSetCount: Int): List<RoutineDropSet> {
        if (rawPrescription.isBlank() || !rawPrescription.contains("drop", ignoreCase = true)) {
            return emptyList()
        }
        val mainFromText = Regex(
            "(?i)(?:after|on|following)\\s*(?:main\\s*)?set\\s*(\\d+)"
        ).find(rawPrescription)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val defaultMain = (mainFromText ?: 1).coerceIn(1, mainSetCount.coerceAtLeast(1))

        val matches = Regex("(?i)\\bdrop(?:\\s*set)?\\b").findAll(rawPrescription).toList()
        if (matches.isEmpty()) return emptyList()
        val result = mutableListOf<RoutineDropSet>()
        matches.forEachIndexed { index, match ->
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: rawPrescription.length
            var segment = rawPrescription.substring(start, end)
                .trim()
                .trimStart(':', '=', '-', '\u2013', ',')
                .trim()
            // "drop set 2: ..." names the child, not a repetition count.
            segment = segment.replace(Regex("^\\d+\\s*[:\\-=]\\s*"), "").trim()
            if (segment.isBlank()) return@forEachIndexed
            val weight = Regex(
                "(\\d+(?:[.,]\\d+)?)\\s*(?:kg|kgs|kilograms?|lb|lbs|pounds?)\\b",
                RegexOption.IGNORE_CASE
            ).find(segment)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
            val reps = Regex("(?:x|\\u00d7|\\*)\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(segment)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("(\\d+)\\s*reps?\\b", RegexOption.IGNORE_CASE)
                    .find(segment)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val hold = Regex("(\\d+)\\s*(?:s|sec|secs|seconds?)\\b", RegexOption.IGNORE_CASE)
                .find(segment)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (reps == null && weight == null && hold == null) return@forEachIndexed
            val mainSet = if (Regex("(?i)\\bafter\\b").containsMatchIn(segment)) {
                Regex("(?i)after\\s*(?:set\\s*)?(\\d+)").find(segment)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?.coerceIn(1, mainSetCount.coerceAtLeast(1)) ?: defaultMain
            } else {
                defaultMain
            }
            result += RoutineDropSet(
                mainSetIndex = mainSet,
                dropIndex = result.count { it.mainSetIndex == mainSet } + 1,
                targetReps = (reps ?: 0).coerceIn(0, 100),
                targetHoldSeconds = (hold ?: 0).coerceIn(0, 600),
                targetWeight = (weight ?: 0.0).coerceAtLeast(0.0)
            )
        }
        return result
    }

    /**
     * Reads a load written in free text, e.g. "3x8 @ 34kg" or "3 sets x 8 reps @ +9kg".
     *
     * The legacy AI importer put the load in the prescription and the text parser used to ignore
     * it and hard-code `targetWeight = 0`. That silently erased every prescribed load from a
     * routine when the 4.5 schedule/routine migration converted its text to the JSON format.
     *
     * A plain "34kg" is a working weight; a leading "+", "added" or "weighted" means the load was
     * prescribed on top of body weight. The caller normalizes the pair against the exercise's real
     * field list afterwards, so a weight stored here is not lost on an added-weight movement.
     *
     * @return (weightKg, addedWeightKg), both 0 when no load is written.
     */
    private fun parsePrescribedLoad(text: String): Pair<Double, Double> {
        if (text.isBlank()) return 0.0 to 0.0
        val match = LOAD_IN_TEXT.find(text) ?: return 0.0 to 0.0
        val prefix = match.groupValues[1].lowercase()
        val value = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return 0.0 to 0.0
        if (value <= 0.0) return 0.0 to 0.0
        val unit = match.groupValues[3].lowercase()
        val kilograms = if (unit.startsWith("lb") || unit.startsWith("pound")) {
            WeightConversion.toKg(value, WeightUnit.LB)
        } else {
            value
        }
        val added = prefix.contains('+') || prefix.contains("added") || prefix.contains("weighted")
        return if (added) 0.0 to kilograms else kilograms to 0.0
    }

    /** See [parsePrescribedLoad]. The unit is required so reps and sets can never match. */
    private val LOAD_IN_TEXT = Regex(
        """(?i)([+]|added|weighted|bw\s*\+)?\s*(\d+(?:[.,]\d+)?)\s*(kg|kgs|kilograms?|lb|lbs|pounds?)\b"""
    )

    private fun parseJsonExerciseItem(
        obj: JSONObject,
        exByName: Map<String, Exercise>,
        overrideWeekday: Int? = null,
        overrideDayTitle: String? = null,
        fallbackSlot: Int = 1
    ): ScheduledWorkout? {
        // The canonical writer uses "name". Older backups and test fixtures written against the
        // entity field names use "exerciseName"; both are real user data and must migrate.
        val name = obj.optString("name").ifBlank {
            obj.optString("exerciseName")
        }.trim()
        if (name.isBlank()) return null

        val matched = exByName[name.lowercase()] ?: findMatchingExercise(name, exByName.values)
        val sets = obj.optInt("sets", obj.optInt("targetSets", 3))
        val reps = obj.optInt("reps", obj.optInt("targetReps", 8))
        var weight = obj.optDouble("weight", obj.optDouble("targetWeight", 0.0))
        // The default added weight, read from the JSON in both branches below. It is a setting the
        // athlete made on the routine, not a property of the catalog exercise, so it must never be
        // taken from the matched exercise.
        var addedWeight = obj.optDouble("addedWeight", obj.optDouble("targetAddedWeightKg", 0.0))
        val hold = obj.optInt("holdSeconds", obj.optInt("targetHoldSeconds", 0))
        // Missing, null, negative or absurd values all fall back to "no routine default" instead of
        // letting a corrupt JSON blob start a ten hour timer.
        val restSeconds = obj.optInt("restSeconds", 0).coerceIn(0, 600)
        val rawPrescription = obj.optString("prescription", "")
        // Older AI writers stored the load only inside the prescription text. When the dedicated
        // fields say nothing, read the text instead of silently keeping 0.
        if (weight <= 0.0 && addedWeight <= 0.0) {
            val (textWeight, textAddedWeight) = parsePrescribedLoad(rawPrescription)
            weight = textWeight
            addedWeight = textAddedWeight
        }
        val matchedEquipment = matched?.equipment ?: obj.optString("equipment", "")
        val prescription = shortPrescription(rawPrescription, matchedEquipment).ifBlank {
            if (hold > 0) "$sets sets x ${hold}s"
            else "$sets sets x $reps reps"
        }
        val dayNote = obj.optString("dayNote", obj.optString("note", ""))
        val supersetGroupId = obj.optString("supersetGroupId").takeIf { it.isNotBlank() }
        val targetEffortRpe = if (obj.has("targetEffortRpe") && !obj.isNull("targetEffortRpe")) {
            EffortScale.normalize(obj.optDouble("targetEffortRpe", Double.NaN))
        } else {
            null
        }

        // Where this exercise sits in the week.
        //
        // A source may name the weekday ("Monday"), or it may only carry the old numbered slot
        // ("day": 2), which is a position in the routine rather than a day of the week. The schedule
        // resolves the slots afterwards, so a routine written before the seven day model existed
        // lands on real weekdays instead of nowhere.
        val hint = readScheduleHint(obj, fallbackSlot)
        val weekday = overrideWeekday ?: hint.weekday
        val slot = if (overrideWeekday != null) fallbackSlot else hint.slot
        val dayTitle = overrideDayTitle ?: cleanDayTitle(
            obj.optString("dayTitle", obj.optString("focus", obj.optString("title", "")))
        )

        val item = if (matched != null) {
            RoutineExerciseItem(
                exerciseId = matched.id,
                exerciseName = matched.name,
                exerciseType = matched.exerciseType,
                category = matched.category,
                equipment = matched.equipment,
                targetSets = sets,
                targetReps = reps,
                targetWeight = weight,
                targetAddedWeightKg = addedWeight,
                targetHoldSeconds = hold,
                targetDistanceKm = obj.optDouble("distanceKm", obj.optDouble("distance", 0.0))
                    .coerceAtLeast(0.0),
                restSeconds = restSeconds,
                prescription = prescription,
                weekday = weekday,
                dayTitle = dayTitle,
                dayNote = dayNote,
                dropSets = parseDropSets(obj),
                mainSetDefaults = parseMainSetDefaults(obj),
                supersetGroupId = supersetGroupId,
                targetEffortRpe = targetEffortRpe
            )
        } else {
            RoutineExerciseItem(
                exerciseId = obj.optLong("id", obj.optLong("exerciseId", 0L)),
                exerciseName = name,
                exerciseType = obj.optString(
                    "type",
                    obj.optString("exerciseType", ExerciseType.STANDARD_WEIGHT)
                ),
                category = obj.optString("category", ""),
                equipment = obj.optString("equipment", ""),
                targetSets = sets,
                targetReps = reps,
                targetWeight = weight,
                targetAddedWeightKg = addedWeight,
                targetHoldSeconds = hold,
                targetDistanceKm = obj.optDouble("distanceKm", obj.optDouble("distance", 0.0))
                    .coerceAtLeast(0.0),
                restSeconds = restSeconds,
                prescription = prescription,
                weekday = weekday,
                dayTitle = dayTitle,
                dayNote = dayNote,
                dropSets = parseDropSets(obj),
                mainSetDefaults = parseMainSetDefaults(obj),
                supersetGroupId = supersetGroupId,
                targetEffortRpe = targetEffortRpe
            )
        }
        // Old routine JSON stored dumbbell loads under addedWeight. Migrate only when the catalog
        // really knows this exercise: a caller with no catalog (the workout draft codec) has no
        // field list to judge against, and guessing from the type could move a value the exercise
        // does record. Those callers normalize later, against the resolved exercise.
        val normalized = if (matched != null) {
            item.withLegacyLoadFallback(matched.exerciseFields)
        } else {
            item
        }
        return ScheduledWorkout(item = normalized, weekday = weekday, slot = slot)
    }

    /**
     * Where a JSON entry sits in the week.
     *
     * @param weekday the weekday the entry named, or 0 when it named none.
     * @param slot the position of the workout within the routine, for an entry that named no weekday.
     */
    private data class ScheduleHint(val weekday: Int, val slot: Int)

    /** Reads optional per-main-set defaults; missing rows are fine and fall back to the item. */
    private fun parseMainSetDefaults(obj: JSONObject): List<RoutineMainSetDefault> {
        val array = obj.optJSONArray("mainSets") ?: return emptyList()
        val result = mutableListOf<RoutineMainSetDefault>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val main = row.optInt("main", row.optInt("mainSet", i + 1)).coerceAtLeast(1)
            result += RoutineMainSetDefault(
                mainSetIndex = main,
                targetReps = row.optInt("reps", 0).coerceIn(0, 100),
                targetHoldSeconds = row.optInt("holdSeconds", 0).coerceIn(0, 86400),
                targetWeight = row.optDouble("weight", 0.0).coerceAtLeast(0.0),
                targetAddedWeightKg = row.optDouble("addedWeight", 0.0).coerceAtLeast(0.0),
                targetDistanceKm = row.optDouble("distanceKm", 0.0).coerceAtLeast(0.0),
                restSeconds = row.optInt("restSeconds", 0).coerceIn(0, 600)
            )
        }
        return result.distinctBy { it.mainSetIndex }.sortedBy { it.mainSetIndex }
    }

    /**
     * Reads optional drop-set prescriptions. A missing or malformed array is no drop sets, never
     * a reason to reject the whole routine.
     */
    private fun parseDropSets(obj: JSONObject): List<RoutineDropSet> {
        val array = obj.optJSONArray("dropSets") ?: return emptyList()
        val result = mutableListOf<RoutineDropSet>()
        for (i in 0 until array.length()) {
            val drop = array.optJSONObject(i) ?: continue
            val mainSet = drop.optInt("mainSet", drop.optInt("mainSetIndex", 1)).coerceAtLeast(1)
            val dropIndex = drop.optInt("drop", drop.optInt("dropIndex", i + 1)).coerceAtLeast(1)
            result += RoutineDropSet(
                mainSetIndex = mainSet,
                dropIndex = dropIndex,
                targetReps = drop.optInt("reps", drop.optInt("targetReps", 0)).coerceIn(0, 1000),
                targetHoldSeconds = drop.optInt("holdSeconds", drop.optInt("targetHoldSeconds", 0)).coerceIn(0, 86400),
                targetWeight = drop.optDouble("weight", drop.optDouble("targetWeight", 0.0)).coerceAtLeast(0.0),
                targetAddedWeightKg = drop.optDouble("addedWeight", drop.optDouble("targetAddedWeightKg", 0.0)).coerceAtLeast(0.0),
                targetDistanceKm = drop.optDouble("distanceKm", drop.optDouble("distance", 0.0)).coerceAtLeast(0.0),
                restSeconds = drop.optInt("restSeconds", 0).coerceIn(0, 600)
            )
        }
        return result.sortedWith(compareBy({ it.mainSetIndex }, { it.dropIndex }))
    }

    private fun readScheduleHint(obj: JSONObject, fallbackSlot: Int): ScheduleHint {
        // The weekday key: a name, or the ISO weekday number.
        val weekdayRaw = obj.opt("weekday")
        if (weekdayRaw != null && weekdayRaw != JSONObject.NULL) {
            val text = weekdayRaw.toString().trim()
            if (text.isNotEmpty() && text != "null") {
                Weekday.fromName(text)?.let { return ScheduleHint(it, fallbackSlot) }
            }
        }

        // The day key: a name is a weekday, a number is a position in the routine.
        val dayRaw = obj.opt("day")
        if (dayRaw != null && dayRaw != JSONObject.NULL) {
            if (dayRaw is String) {
                val text = dayRaw.trim()
                if (text.isNotEmpty()) {
                    val asNumber = text.toIntOrNull()
                    if (asNumber != null) {
                        return ScheduleHint(0, asNumber.coerceAtLeast(1))
                    }
                    Weekday.fromName(text)?.let { return ScheduleHint(it, fallbackSlot) }
                }
            } else {
                val slot = obj.optInt("day", 0)
                if (slot > 0) return ScheduleHint(0, slot)
            }
        }
        return ScheduleHint(0, fallbackSlot)
    }

    /**
     * The focus of a day, without the day number or the weekday name in it.
     *
     * A routine written before the seven day model existed called its days "Day 1: Upper Body
     * Push". The weekday is derived now, so only the focus is kept.
     */
    fun cleanDayTitle(raw: String): String {
        var title = raw.trim()
        val legacy = Regex("""(?i)^day\s*\d+\s*[:\-–—]?\s*""").find(title)
        if (legacy != null) title = title.removeRange(legacy.range).trim()
        val named = Regex(
            """(?i)^(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\s*[:\-–—]\s*"""
        ).find(title)
        if (named != null) title = title.removeRange(named.range).trim()
        return title
    }

    /**
     * A day header found in a routine that was written as text.
     *
     * @param weekday the weekday the header named, or 0 when it was a numbered slot instead.
     * @param slot the one based position of the day, for a routine written as "Day 1, Day 2".
     * @param title the focus of the day, e.g. "Upper Body Push".
     */
    private data class DayHeader(
        val weekday: Int,
        val slot: Int,
        val title: String
    )

    private fun isDayHeader(line: String): DayHeader? {
        val stripped = line.trim()
            .replace(Regex("""^#{1,6}\s*"""), "")
            .replace(Regex("""^\*+\s*"""), "")
            .replace(Regex("""\s*\*+$"""), "")
            .trim()

        // A header that names the weekday itself: "Monday: Upper Body Push" or just "Monday".
        // The title is what comes after it, never the weekday name, so the name is derived from
        // the weekday rather than stored twice.
        val namedWeekday = Weekday.fromName(stripped.substringBefore(":").substringBefore("-").trim())
        if (namedWeekday != null) {
            val title = stripped
                .substringBefore(":").substringBefore("-").trim()
                .let { head ->
                    // Drop the weekday name from the head, keeping what followed it.
                    if (head.length >= stripped.length) "" else stripped.substring(head.length).trim()
                }
                .removePrefix(":").removePrefix("-").trim()
            return DayHeader(weekday = namedWeekday, slot = 0, title = title)
        }

        val regex = Regex("""(?i)^(?:Day|Workout)\s*(\d+|[A-Za-z])(?:\s*[:\-–—]\s*(.*)|\s+(.*)|$)""")
        val match = regex.find(stripped)
        if (match != null) {
            val token = match.groupValues[1]
            val num = token.toIntOrNull() ?: (token.firstOrNull()?.let { (it.uppercaseChar() - 'A' + 1) } ?: 1)
            // The focus of the day, with the "Day N" prefix taken off.
            val title = match.groupValues[2].ifBlank { match.groupValues[3] }.trim()
            return DayHeader(weekday = 0, slot = num, title = title)
        }
        return null
    }

    private fun parseTextDays(
        rawText: String,
        exByName: Map<String, Exercise>,
        weekStartsOnMonday: Boolean
    ): List<RoutineDay> {
        val lines = rawText.lines()
        val daysMap = LinkedHashMap<Int, MutableList<ScheduledWorkout>>()
        val titlesMap = mutableMapOf<Int, String>()
        val notesMap = mutableMapOf<Int, MutableList<String>>()

        // The day the exercises below this point belong to.
        //
        // A header that names a weekday is taken literally. A header that is a position ("Day 1")
        // is the first day of the athlete's week, so a routine written before the seven day model
        // existed lands on real weekdays rather than on nothing.
        var currentWeekday = RoutineSchedule.weekdayForSlot(1, weekStartsOnMonday)
        var currentSlot = 1
        var currentDayTitle = ""
        // The exercise a standalone "Drop set: ..." line belongs to.
        var lastWorkout: ScheduledWorkout? = null

        for (line in lines) {
            val lineTrimmed = line.trim()
            if (lineTrimmed.isBlank()) continue

            // Check if this line is a day header
            val header = isDayHeader(lineTrimmed)
            if (header != null) {
                currentSlot = header.slot.coerceAtLeast(1)
                currentWeekday = if (header.weekday > 0) {
                    header.weekday
                } else {
                    RoutineSchedule.weekdayForSlot(currentSlot, weekStartsOnMonday)
                }
                currentDayTitle = header.title
                lastWorkout = null
                titlesMap[currentWeekday] = currentDayTitle
                if (!daysMap.containsKey(currentWeekday)) {
                    daysMap[currentWeekday] = mutableListOf()
                }
                continue
            }

            // A standalone reduction line belongs to the exercise above it. This is the shape
            // people paste from a coach ("Drop set: 30 kg x 8") and must not become a note.
            val standaloneDrops = parseStandaloneDropLine(
                lineTrimmed,
                lastWorkout?.item?.targetSets ?: 1
            )
            if (standaloneDrops.isNotEmpty() && lastWorkout != null) {
                val weekday = lastWorkout!!.weekday
                val list = daysMap[weekday]
                val index = list?.indexOfLast { it === lastWorkout } ?: -1
                if (list != null && index >= 0) {
                    val currentItem = list[index].item
                    val combined = (currentItem.dropSets + standaloneDrops)
                        .groupBy { it.mainSetIndex }
                        .flatMap { (_, rows) ->
                            rows.sortedBy { it.dropIndex }.mapIndexed { childIndex, drop ->
                                drop.copy(dropIndex = childIndex + 1)
                            }
                        }
                        .sortedWith(compareBy({ it.mainSetIndex }, { it.dropIndex }))
                    val updated = list[index].copy(item = currentItem.copy(dropSets = combined))
                    list[index] = updated
                    lastWorkout = updated
                }
                continue
            }

            // Coach notes and day descriptions are kept as text and never become exercises
            if (isCoachNoteLine(lineTrimmed)) {
                val cleanedNote = cleanNoteText(lineTrimmed)
                // A bare label such as "**Coach's Note:**" carries no information on its own
                if (cleanedNote.isNotBlank() && !NOTE_LABEL_ONLY.matches(cleanedNote)) {
                    notesMap.getOrPut(currentWeekday) { mutableListOf() }.add(cleanedNote)
                }
                continue
            }

            // Anything that reads as a sentence ("Welcome to your program, designed to...") is an
            // intro or a note, never an exercise. Without this it became exercise #1 of the day.
            // A leading list number ("1. ", "2) ") is stripped first so numbered exercise lists
            // are never mistaken for sentences.
            val proseCandidate = lineTrimmed
                .replaceFirst(Regex("""^[*_\-•\s>]+"""), "")
                .removePrefix("EXERCISE:")
                .removePrefix("Exercise:")
                .replaceFirst(Regex("""^\d+[\.\)]\s*"""), "")
                .substringBefore('|')
                .trim()
            // Test the exercise-name head, not the prescription. A comma in "3x8, drop 30kg x8"
            // used to make the whole line look like prose and silently drop the exercise.
            if (looksLikeProseNotExercise(nameHeadOfLine(proseCandidate))) {
                val cleanedNote = cleanNoteText(lineTrimmed)
                if (cleanedNote.isNotBlank() && !NOTE_LABEL_ONLY.matches(cleanedNote)) {
                    notesMap.getOrPut(currentWeekday) { mutableListOf() }.add(cleanedNote)
                }
                continue
            }

            // Parse exercise on this line
            var nameCandidate = ""
            var prescriptionCandidate = ""

            if (lineTrimmed.contains("EXERCISE:", ignoreCase = true)) {
                val afterPrefix = lineTrimmed.substring(lineTrimmed.indexOf("EXERCISE:", ignoreCase = true) + 9).trim()
                if (afterPrefix.contains("|")) {
                    nameCandidate = afterPrefix.substringBefore("|").trim()
                    prescriptionCandidate = afterPrefix.substringAfter("|").trim()
                } else if (afterPrefix.contains("(") && afterPrefix.contains(")")) {
                    nameCandidate = afterPrefix.substringBefore("(").trim()
                    prescriptionCandidate = afterPrefix.substringAfter("(").substringBefore(")").trim()
                } else {
                    nameCandidate = afterPrefix
                    prescriptionCandidate = ""
                }
            } else {
                // Check format like: "Exercise Name = 8 reps x 4 sets" or "Exercise: 4x8"
                val eqMatch = Regex("""(?i)^(.+?)\s*[:=]\s*(.+)""").find(lineTrimmed)
                if (eqMatch != null && !eqMatch.groupValues[1].contains("day", ignoreCase = true) && !eqMatch.groupValues[1].contains("workout", ignoreCase = true)) {
                    nameCandidate = eqMatch.groupValues[1].trim()
                    prescriptionCandidate = eqMatch.groupValues[2].trim()
                } else {
                    // Check dash separator e.g. "Bench Press - 4 sets x 8 reps"
                    val dashMatch = Regex("""(?i)^(.+?)\s+[-–—]\s+(.+)""").find(lineTrimmed)
                    if (dashMatch != null && !dashMatch.groupValues[1].contains("day", ignoreCase = true) && !dashMatch.groupValues[1].contains("workout", ignoreCase = true)) {
                        nameCandidate = dashMatch.groupValues[1].trim()
                        prescriptionCandidate = dashMatch.groupValues[2].trim()
                    } else {
                        // Check trailing parentheses containing prescription (e.g. "1. Bench Press (Barbell) (4x8)" or "1. Bench Press (4x8)")
                        val parens = Regex("""\(([^)]+)\)""").findAll(lineTrimmed).toList()
                        var foundParenPrescription = false
                        if (parens.isNotEmpty()) {
                            val lastParen = parens.last()
                            val content = lastParen.groupValues[1].trim()
                            val hasPrescriptionNumbers = Regex("""\d+\s*(?:sets|reps|x|\*|s|sec|kg|lbs)""", RegexOption.IGNORE_CASE).containsMatchIn(content)
                                || Regex("""^\d+\s*x\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(content)
                            if (hasPrescriptionNumbers) {
                                nameCandidate = lineTrimmed.substring(0, lastParen.range.first).trim()
                                prescriptionCandidate = content
                                foundParenPrescription = true
                            }
                        }

                        if (!foundParenPrescription) {
                            val cleaned = lineTrimmed.removePrefix("-").removePrefix("*").trim()
                            val withoutNumber = cleaned.replaceFirst(Regex("""^\d+[\.\)]\s*"""), "").trim()
                            if (withoutNumber.contains("|")) {
                                nameCandidate = withoutNumber.substringBefore("|").trim()
                                prescriptionCandidate = withoutNumber.substringAfter("|").trim()
                            } else if (withoutNumber.contains("(") && withoutNumber.contains(")")) {
                                val insideParen = withoutNumber.substringAfter("(").substringBefore(")").trim()
                                val hasNumbers = insideParen.any { it.isDigit() }
                                if (hasNumbers) {
                                    nameCandidate = withoutNumber.substringBefore("(").trim()
                                    prescriptionCandidate = insideParen
                                } else {
                                    nameCandidate = withoutNumber
                                    prescriptionCandidate = ""
                                }
                            } else {
                                nameCandidate = withoutNumber
                                prescriptionCandidate = ""
                            }
                        }
                    }
                }
            }

            val cleanName = nameCandidate
                .removePrefix("-").removePrefix("*").trim()
                .replaceFirst(Regex("""^\d+[\.\)]\s*"""), "").trim()
                .replace("*", "").replace("•", "").trim()
            if (cleanName.length < 2) continue

            // Look up matching exercise using exact or smart matcher
            val matched = exByName[cleanName.lowercase()] ?: findMatchingExercise(cleanName, exByName.values)
            val matchedEquipment = matched?.equipment ?: ""

            var sets = 3
            var reps = 8
            var hold = 0
            val setsMatch = Regex("""(\d+)\s*(?:sets|x|\*)\s*(\d+)""", RegexOption.IGNORE_CASE).find(prescriptionCandidate)
            if (setsMatch != null) {
                sets = setsMatch.groupValues[1].toIntOrNull() ?: 3
                reps = setsMatch.groupValues[2].toIntOrNull() ?: 8
            } else {
                val singleSetMatch = Regex("""(\d+)\s*sets?""", RegexOption.IGNORE_CASE).find(prescriptionCandidate)
                if (singleSetMatch != null) sets = singleSetMatch.groupValues[1].toIntOrNull() ?: 3
                val repsMatch = Regex("""(\d+)\s*reps?""", RegexOption.IGNORE_CASE).find(prescriptionCandidate)
                if (repsMatch != null) reps = repsMatch.groupValues[1].toIntOrNull() ?: 8
                val secMatch = Regex("""(\d+)\s*(?:s|sec|seconds)""", RegexOption.IGNORE_CASE).find(prescriptionCandidate)
                if (secMatch != null) hold = secMatch.groupValues[1].toIntOrNull() ?: 0
            }

            val cleanedPrescription = shortPrescription(prescriptionCandidate, matchedEquipment)
            val finalPrescription = cleanedPrescription.ifBlank {
                if (hold > 0) "$sets sets x ${hold}s"
                else "$sets sets x $reps reps"
            }

            // The prescription is the only place a pasted program carries its load, e.g.
            // "3x8 @ 34kg". Without this the text-to-JSON migration wrote 0 for every exercise.
            val (textWeight, textAddedWeight) = parsePrescribedLoad(prescriptionCandidate)
            val item = if (matched != null) {
                RoutineExerciseItem(
                    exerciseId = matched.id,
                    exerciseName = matched.name,
                    exerciseType = matched.exerciseType,
                    category = matched.category,
                    equipment = matched.equipment,
                    targetSets = sets,
                    targetReps = reps,
                    targetWeight = textWeight,
                    targetAddedWeightKg = textAddedWeight,
                    targetHoldSeconds = hold,
                    prescription = finalPrescription,
                    weekday = currentWeekday,
                    dayTitle = currentDayTitle,
                    dropSets = parseInlineDropSets(prescriptionCandidate, sets)
                ).withLegacyLoadFallback(matched.exerciseFields)
            } else {
                RoutineExerciseItem(
                    exerciseId = 0L,
                    exerciseName = cleanName,
                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                    category = "Calisthenics",
                    equipment = "Bodyweight",
                    targetSets = sets,
                    targetReps = reps,
                    targetWeight = textWeight,
                    targetAddedWeightKg = textAddedWeight,
                    targetHoldSeconds = hold,
                    prescription = finalPrescription,
                    weekday = currentWeekday,
                    dayTitle = currentDayTitle,
                    dropSets = parseInlineDropSets(prescriptionCandidate, sets)
                )
            }
            val scheduled = ScheduledWorkout(item = item, weekday = currentWeekday, slot = currentSlot)
            daysMap.getOrPut(currentWeekday) { mutableListOf() }.add(scheduled)
            lastWorkout = scheduled
        }

        val nonNullDays = daysMap.filter { it.value.isNotEmpty() }
        if (nonNullDays.isEmpty()) return emptyList()

        // The seven slots, so a day nothing was written for shows as a rest day.
        val allWorkouts = nonNullDays.values.flatten()
        val days = RoutineSchedule.buildDays(allWorkouts, weekStartsOnMonday)
        return days.map { day ->
            val note = notesMap[day.weekday]?.joinToString(" ").orEmpty()
            // The note is stored on every item so it survives the flat JSON round trip
            val itemsWithNote = if (note.isBlank()) day.exercises else day.exercises.map { it.copy(dayNote = note) }
            day.copy(exercises = itemsWithNote, note = note)
        }
    }

    fun formatRoutineForDisplay(rawText: String, allExercises: List<Exercise>): String {
        val days = parseDays(rawText, allExercises)
        if (days.isNotEmpty()) {
            val sb = StringBuilder()
            for (day in days) {
                if (days.size > 1) {
                    sb.append("### ${day.dayTitle}\n")
                }
                if (day.note.isNotBlank()) {
                    sb.append("**Coach's Note:** ${day.note}\n")
                }
                for (ex in day.exercises) {
                    val detail = if (ex.category.isNotBlank()) " (${ex.category})" else ""
                    val cleanPresc = shortPrescription(ex.prescription, ex.equipment).ifBlank { ex.prescription }
                    sb.append("• **${ex.exerciseName}** — $cleanPresc$detail\n")
                }
                sb.append("\n")
            }
            return sb.toString().trim()
        }
        val trimmed = rawText.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return "Custom Workout Routine"
        }
        return rawText
    }

    /**
     * Pulls the JSON out of an AI answer.
     *
     * Models often wrap the object in prose ("Here is your program: ...") or in a ```json fence,
     * sometimes followed by a closing remark that itself contains braces. This scans for the first
     * balanced `{...}` block, ignoring braces inside strings, so the extracted text always parses.
     */
    fun extractJson(text: String): String {
        val trimmed = text.trim()

        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```").find(trimmed)
        if (fence != null) {
            val inner = fence.groupValues[1].trim()
            val balanced = firstBalancedObject(inner)
            if (balanced != null) return balanced
            if (inner.startsWith("{")) return inner
        }

        val balanced = firstBalancedObject(trimmed)
        if (balanced != null) return balanced

        val arrayStart = firstBalancedArray(trimmed)
        if (arrayStart != null) return arrayStart

        return trimmed
    }

    /** First balanced `{...}` block, string aware. */
    private fun firstBalancedObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        return balancedSlice(text, start, '{', '}')
    }

    /** First balanced `[...]` block, string aware. */
    private fun firstBalancedArray(text: String): String? {
        val start = text.indexOf('[')
        if (start < 0) return null
        return balancedSlice(text, start, '[', ']')
    }

    private fun balancedSlice(text: String, startIndex: Int, open: Char, close: Char): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in startIndex until text.length) {
            val ch = text[i]
            when {
                escaped -> escaped = false
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                inString -> Unit
                ch == open -> depth++
                ch == close -> {
                    depth--
                    if (depth == 0) return text.substring(startIndex, i + 1)
                }
            }
        }
        return null
    }

    /**
     * Rejects AI prose that ended up where an exercise name belongs.
     *
     * An AI answer regularly starts with "Welcome to your ... program, designed ..." which used to
     * become exercise #1 of day 1. Only sentence-level signals are used here, so a legitimate line
     * such as "Pull-up (Weighted) = 4 sets x 6 reps @ +10kg" is never mistaken for prose.
     */
    fun looksLikeProseNotExercise(name: String): Boolean {
        val value = name.trim()
        if (value.isEmpty()) return true
        // A whole sentence always carries sentence punctuation
        if (value.contains('.') || value.contains(',') || value.contains(';') || value.contains(':')) return true
        if (value.endsWith('!') || value.endsWith('?')) return true
        // Very long titles are never exercise names
        if (value.length > 70) return true
        // Intro / instruction filler the models like to add
        val lower = value.lowercase()
        val proseOpeners = listOf(
            "welcome", "here is", "here's", "this is", "your routine", "the program",
            "note", "remember", "focus on", "important", "disclaimer", "created by", "generated by",
            "in this", "for this", "you will", "you'll", "let's", "warm up", "warm-up",
            "cool down", "tip", "progression", "intensity"
        )
        return proseOpeners.any { lower.startsWith(it) }
    }
}

sealed interface UnstructuredParseState {
    object Idle : UnstructuredParseState
    object Loading : UnstructuredParseState
    data class Error(val reason: String) : UnstructuredParseState
    data class Success(
        val routine: SavedRoutine,
        val newExercisesCreated: List<Exercise>,
        val exerciseCount: Int
    ) : UnstructuredParseState
}

sealed interface CustomRoutinePromptState {
    object Idle : CustomRoutinePromptState
    object Loading : CustomRoutinePromptState
    data class Error(val reason: String) : CustomRoutinePromptState
    data class Success(
        val routine: SavedRoutine,
        val newExercisesCreated: List<Exercise>,
        val exerciseCount: Int,
        val isSaved: Boolean = false
    ) : CustomRoutinePromptState
}
