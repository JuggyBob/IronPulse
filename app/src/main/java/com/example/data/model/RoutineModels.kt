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
    val targetHoldSeconds: Int = 0,
    val prescription: String = "",
    val dayNumber: Int = 1,
    val dayTitle: String = "Day 1",
    /**
     * Coach note written for this training day. Stored on every item of the day so the day level
     * note survives the flat JSON array format, and never turned into a fake exercise.
     */
    val dayNote: String = ""
)

data class RoutineDay(
    val dayNumber: Int,
    val dayTitle: String,
    val exercises: List<RoutineExerciseItem>,
    val note: String = ""
)

data class ActiveRoutineWorkoutState(
    val routineId: Long,
    val routineName: String,
    val routineGoal: String,
    val startTime: Long = System.currentTimeMillis(),
    val exercises: List<Exercise>,
    val routineItems: List<RoutineExerciseItem> = emptyList(),
    val sets: List<LoggedSet> = emptyList(),
    val dayNumber: Int? = null,
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
                put("holdSeconds", item.targetHoldSeconds)
                put("prescription", item.prescription)
                put("day", item.dayNumber)
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

    fun parseDays(rawText: String, allExercises: List<Exercise>): List<RoutineDay> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val exByName = allExercises.associateBy { it.name.trim().lowercase() }

        // 1. Try JSON
        if ((trimmed.startsWith("[") && trimmed.endsWith("]")) || (trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            val jsonDays = parseJsonDays(trimmed, exByName)
            if (jsonDays.isNotEmpty()) return jsonDays
        }

        // 2. Try structured text / markdown with day headers
        return parseTextDays(trimmed, exByName)
    }

    fun parseExercises(rawText: String, allExercises: List<Exercise>): List<RoutineExerciseItem> {
        return parseDays(rawText, allExercises).flatMap { it.exercises }
    }

    private fun parseJsonDays(trimmed: String, exByName: Map<String, Exercise>): List<RoutineDay> {
        try {
            val items = mutableListOf<RoutineExerciseItem>()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val item = parseJsonExerciseItem(obj, exByName)
                    if (item != null) items.add(item)
                }
            } else {
                val rootObj = JSONObject(trimmed)
                // Check if it has "days" array
                val daysArray = rootObj.optJSONArray("days")
                if (daysArray != null && daysArray.length() > 0) {
                    val daysList = mutableListOf<RoutineDay>()
                    for (d in 0 until daysArray.length()) {
                        val dayObj = daysArray.optJSONObject(d) ?: continue
                        val dayNum = dayObj.optInt("day", d + 1)
                        val dayTitle = dayObj.optString("dayTitle", dayObj.optString("title", "Day $dayNum"))
                        val dayExArray = dayObj.optJSONArray("exercises") ?: JSONArray()
                        val dayItems = mutableListOf<RoutineExerciseItem>()
                        for (j in 0 until dayExArray.length()) {
                            val exObj = dayExArray.optJSONObject(j) ?: continue
                            val item = parseJsonExerciseItem(exObj, exByName, dayNum, dayTitle)
                            if (item != null) dayItems.add(item)
                        }
                        if (dayItems.isNotEmpty()) {
                            daysList.add(
                                RoutineDay(
                                    dayNum,
                                    dayTitle,
                                    dayItems,
                                    dayItems.firstOrNull()?.dayNote.orEmpty()
                                )
                            )
                        }
                    }
                    if (daysList.isNotEmpty()) return daysList
                }

                // Check if it has "exercises" array
                val exArray = rootObj.optJSONArray("exercises")
                if (exArray != null) {
                    for (i in 0 until exArray.length()) {
                        val obj = exArray.optJSONObject(i) ?: continue
                        val item = parseJsonExerciseItem(obj, exByName)
                        if (item != null) items.add(item)
                    }
                }
            }

            if (items.isEmpty()) return emptyList()

            // Group items by day number preserving insertion order
            val grouped = LinkedHashMap<Int, MutableList<RoutineExerciseItem>>()
            val dayTitles = mutableMapOf<Int, String>()
            for (item in items) {
                grouped.getOrPut(item.dayNumber) { mutableListOf() }.add(item)
                if (!dayTitles.containsKey(item.dayNumber) && item.dayTitle.isNotBlank()) {
                    dayTitles[item.dayNumber] = item.dayTitle
                }
            }

            return grouped.map { (dayNum, dayItems) ->
                val title = dayTitles[dayNum] ?: "Day $dayNum"
                // The note travels on every item of the day, so take it from any of them
                val note = dayItems.firstOrNull { it.dayNote.isNotBlank() }?.dayNote.orEmpty()
                RoutineDay(dayNum, title, dayItems, note)
            }
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

    private fun parseJsonExerciseItem(
        obj: JSONObject,
        exByName: Map<String, Exercise>,
        overrideDay: Int? = null,
        overrideDayTitle: String? = null
    ): RoutineExerciseItem? {
        val name = obj.optString("name").trim()
        if (name.isBlank()) return null

        val matched = exByName[name.lowercase()] ?: findMatchingExercise(name, exByName.values)
        val sets = obj.optInt("sets", 3)
        val reps = obj.optInt("reps", 8)
        val weight = obj.optDouble("weight", 0.0)
        val hold = obj.optInt("holdSeconds", 0)
        val rawPrescription = obj.optString("prescription", "")
        val matchedEquipment = matched?.equipment ?: obj.optString("equipment", "")
        val prescription = cleanPrescription(rawPrescription, matchedEquipment).ifBlank {
            if (hold > 0) "$sets sets x ${hold}s"
            else if (weight > 0.0) "$sets sets x $reps reps @ ${weight}kg"
            else "$sets sets x $reps reps"
        }
        val dayNum = overrideDay ?: obj.optInt("day", obj.optInt("dayNumber", 1))
        val dayTitle = overrideDayTitle ?: obj.optString("dayTitle", "Day $dayNum")
        val dayNote = obj.optString("dayNote", "")

        return if (matched != null) {
            RoutineExerciseItem(
                exerciseId = matched.id,
                exerciseName = matched.name,
                exerciseType = matched.exerciseType,
                category = matched.category,
                equipment = matched.equipment,
                targetSets = sets,
                targetReps = reps,
                targetWeight = weight,
                targetHoldSeconds = hold,
                prescription = prescription,
                dayNumber = dayNum,
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
                targetHoldSeconds = hold,
                prescription = prescription,
                dayNumber = dayNum,
                dayTitle = dayTitle,
                dayNote = dayNote
            )
        }
    }

    private fun isDayHeader(line: String): Pair<Int, String>? {
        val stripped = line.trim()
            .replace(Regex("""^#{1,6}\s*"""), "")
            .replace(Regex("""^\*+\s*"""), "")
            .replace(Regex("""\s*\*+$"""), "")
            .trim()

        val regex = Regex("""(?i)^(?:Day|Workout)\s*(\d+|[A-Za-z])(?:\s*[:\-–—]\s*(.*)|\s+(.*)|$)""")
        val match = regex.find(stripped)
        if (match != null) {
            val token = match.groupValues[1]
            val num = token.toIntOrNull() ?: (token.firstOrNull()?.let { (it.uppercaseChar() - 'A' + 1) } ?: 1)
            val title = stripped.ifBlank { "Day $num" }
            return Pair(num, title)
        }
        return null
    }

    private fun parseTextDays(rawText: String, exByName: Map<String, Exercise>): List<RoutineDay> {
        val lines = rawText.lines()
        val daysMap = LinkedHashMap<Int, MutableList<RoutineExerciseItem>>()
        val titlesMap = mutableMapOf<Int, String>()
        val notesMap = mutableMapOf<Int, MutableList<String>>()

        var currentDayNum = 1
        var currentDayTitle = "Day 1"
        var hasExplicitDayHeader = false

        for (line in lines) {
            val lineTrimmed = line.trim()
            if (lineTrimmed.isBlank()) continue

            // Check if this line is a day header
            val headerMatch = isDayHeader(lineTrimmed)
            if (headerMatch != null) {
                hasExplicitDayHeader = true
                currentDayNum = headerMatch.first
                currentDayTitle = headerMatch.second
                titlesMap[currentDayNum] = currentDayTitle
                if (!daysMap.containsKey(currentDayNum)) {
                    daysMap[currentDayNum] = mutableListOf()
                }
                continue
            }

            // Coach notes and day descriptions are kept as text and never become exercises
            if (isCoachNoteLine(lineTrimmed)) {
                val cleanedNote = cleanNoteText(lineTrimmed)
                // A bare label such as "**Coach's Note:**" carries no information on its own
                if (cleanedNote.isNotBlank() && !NOTE_LABEL_ONLY.matches(cleanedNote)) {
                    notesMap.getOrPut(currentDayNum) { mutableListOf() }.add(cleanedNote)
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
                    notesMap.getOrPut(currentDayNum) { mutableListOf() }.add(cleanedNote)
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

            val cleanedPrescription = cleanPrescription(prescriptionCandidate, matchedEquipment)
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
                    dayNumber = currentDayNum,
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
                    dayNumber = currentDayNum,
                    dayTitle = currentDayTitle
                )
            }
            daysMap.getOrPut(currentDayNum) { mutableListOf() }.add(item)
        }

        val nonNullDays = daysMap.filter { it.value.isNotEmpty() }
        if (nonNullDays.isEmpty()) return emptyList()

        return nonNullDays.map { (dayNum, items) ->
            val title = titlesMap[dayNum] ?: if (hasExplicitDayHeader) "Day $dayNum" else "Full Workout"
            val note = notesMap[dayNum]?.joinToString(" ").orEmpty()
            // The note is stored on every item so it survives the flat JSON round trip
            val itemsWithNote = if (note.isBlank()) items else items.map { it.copy(dayNote = note) }
            RoutineDay(dayNum, title, itemsWithNote, note)
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
                    val cleanPresc = cleanPrescription(ex.prescription, ex.equipment).ifBlank { ex.prescription }
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
