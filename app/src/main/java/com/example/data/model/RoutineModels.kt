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
    val dayNote: String = ""
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
    val note: String = ""
) {
    /** Whether this day is a planned rest day. */
    val isRestDay: Boolean get() = exercises.isEmpty()

    /** The name of the weekday, e.g. "Monday". */
    val dayName: String get() = Weekday.name(weekday)
}


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
    val dayNote: String = ""
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
        val separator = Regex("""\s+[-–—:=]\s+""").find(head)
        if (separator != null) head = head.substring(0, separator.range.first)
        head = head.substringBefore('(')
        return head.trim()
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
                put("prescription", item.prescription)
                // The weekday this exercise belongs to. A routine saved before the seven day model
                // existed has no "weekday" key, and the parser reads the old "day" slot instead.
                put("weekday", item.weekday)
                put("dayTitle", item.dayTitle)
                // Backward compatible: routines saved before this field simply have no note
                if (item.dayNote.isNotBlank()) put("dayNote", item.dayNote)
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

    private fun parseJsonExerciseItem(
        obj: JSONObject,
        exByName: Map<String, Exercise>,
        overrideWeekday: Int? = null,
        overrideDayTitle: String? = null,
        fallbackSlot: Int = 1
    ): ScheduledWorkout? {
        val name = obj.optString("name").trim()
        if (name.isBlank()) return null

        val matched = exByName[name.lowercase()] ?: findMatchingExercise(name, exByName.values)
        val sets = obj.optInt("sets", 3)
        val reps = obj.optInt("reps", 8)
        val weight = obj.optDouble("weight", 0.0)
        // The default added weight, read from the JSON in both branches below. It is a setting the
        // athlete made on the routine, not a property of the catalog exercise, so it must never be
        // taken from the matched exercise.
        val addedWeight = obj.optDouble("addedWeight", 0.0)
        val hold = obj.optInt("holdSeconds", 0)
        val rawPrescription = obj.optString("prescription", "")
        val matchedEquipment = matched?.equipment ?: obj.optString("equipment", "")
        val prescription = shortPrescription(rawPrescription, matchedEquipment).ifBlank {
            if (hold > 0) "$sets sets x ${hold}s"
            else "$sets sets x $reps reps"
        }
        val dayNote = obj.optString("dayNote", "")

        // Where this exercise sits in the week.
        //
        // A source may name the weekday ("Monday"), or it may only carry the old numbered slot
        // ("day": 2), which is a position in the routine rather than a day of the week. The schedule
        // resolves the slots afterwards, so a routine written before the seven day model existed
        // lands on real weekdays instead of nowhere.
        val hint = readScheduleHint(obj, fallbackSlot)
        val weekday = overrideWeekday ?: hint.weekday
        val slot = if (overrideWeekday != null) fallbackSlot else hint.slot
        val dayTitle = overrideDayTitle ?: cleanDayTitle(obj.optString("dayTitle", ""))

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
                prescription = prescription,
                weekday = weekday,
                dayTitle = dayTitle,
                dayNote = dayNote
            )
        } else {
            RoutineExerciseItem(
                exerciseId = obj.optLong("id", 0L),
                exerciseName = name,
                exerciseType = obj.optString("type", ExerciseType.STANDARD_WEIGHT),
                category = obj.optString("category", ""),
                equipment = obj.optString("equipment", ""),
                targetSets = sets,
                targetReps = reps,
                targetWeight = weight,
                targetAddedWeightKg = addedWeight,
                targetHoldSeconds = hold,
                prescription = prescription,
                weekday = weekday,
                dayTitle = dayTitle,
                dayNote = dayNote
            )
        }
        return ScheduledWorkout(item = item, weekday = weekday, slot = slot)
    }

    /**
     * Where a JSON entry sits in the week.
     *
     * @param weekday the weekday the entry named, or 0 when it named none.
     * @param slot the position of the workout within the routine, for an entry that named no weekday.
     */
    private data class ScheduleHint(val weekday: Int, val slot: Int)

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
                titlesMap[currentWeekday] = currentDayTitle
                if (!daysMap.containsKey(currentWeekday)) {
                    daysMap[currentWeekday] = mutableListOf()
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
            if (looksLikeProseNotExercise(proseCandidate)) {
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

            val item = if (matched != null) {
                RoutineExerciseItem(
                    exerciseId = matched.id,
                    exerciseName = matched.name,
                    exerciseType = matched.exerciseType,
                    category = matched.category,
                    equipment = matched.equipment,
                    targetSets = sets,
                    targetReps = reps,
                    targetWeight = 0.0,
                    targetHoldSeconds = hold,
                    prescription = finalPrescription,
                    weekday = currentWeekday,
                    dayTitle = currentDayTitle
                )
            } else {
                RoutineExerciseItem(
                    exerciseId = 0L,
                    exerciseName = cleanName,
                    exerciseType = ExerciseType.STANDARD_WEIGHT,
                    category = "Calisthenics",
                    equipment = "Bodyweight",
                    targetSets = sets,
                    targetReps = reps,
                    targetWeight = 0.0,
                    targetHoldSeconds = hold,
                    prescription = finalPrescription,
                    weekday = currentWeekday,
                    dayTitle = currentDayTitle
                )
            }
            daysMap.getOrPut(currentWeekday) { mutableListOf() }
                .add(ScheduledWorkout(item = item, weekday = currentWeekday, slot = currentSlot))
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
