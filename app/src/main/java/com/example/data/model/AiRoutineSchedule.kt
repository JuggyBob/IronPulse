package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns the JSON an AI answer carries into the seven weekday slots of a routine.
 *
 * The AI is asked for a schedule, but the app does not trust it to produce a valid one. The model
 * extracts the training meaning; this class applies the scheduling rules, deterministically:
 *
 *  - A weekday the answer named is kept, whatever else the week looks like. "Monday and Wednesday"
 *    stays Monday and Wednesday.
 *  - A workout with no weekday named is laid out on the free weekdays, in order, starting at the
 *    first day of the athlete's week. Four unnamed workouts on a Monday-first week are Monday to
 *    Thursday.
 *  - Two entries that claim the same weekday are merged into it, so nothing is silently lost.
 *  - Every weekday nothing was assigned to is a rest day. A missing day is never filled with a
 *    repeat of the day before it.
 *
 * The result always has seven days. A day with no exercises in it is a rest day.
 */
object AiRoutineSchedule {

    /**
     * The outcome of reading an AI answer.
     *
     * @param days the seven weekday slots, empty when the answer was not usable.
     * @param isValid false when the answer asked for something the app cannot turn into a routine.
     * @param errorReason why the answer was rejected, shown to the athlete.
     */
    data class Parsed(
        val routineName: String,
        val routineGoal: String,
        val days: List<RoutineDay>,
        val isValid: Boolean,
        val errorReason: String
    ) {
        /** The exercises of every day, in week order. */
        val items: List<RoutineExerciseItem> get() = days.flatMap { it.exercises }

        /** How many days of the week hold a workout. */
        val trainingDays: Int get() = days.count { !it.isRestDay }
    }

    /**
     * Reads an AI answer.
     *
     * @param rawResponse the text the model returned, which may be wrapped in prose or a code fence.
     * @param resolveExercise maps an exercise name from the answer to a catalog exercise, or null
     *        when the catalog has no such movement. The caller decides what to do with an unknown
     *        movement, because only it knows whether it may create one.
     * @param weekStartsOnMonday where the athlete's week begins. It decides where an unnamed
     *        sequence of workouts starts.
     */
    suspend fun parse(
        rawResponse: String,
        resolveExercise: suspend (String) -> Exercise?,
        weekStartsOnMonday: Boolean
    ): Parsed {
        val cleaned = RoutineParser.extractJson(rawResponse)
        val root = try {
            if (cleaned.trimStart().startsWith("[")) JSONObject("{\"exercises\": $cleaned}")
            else JSONObject(cleaned)
        } catch (_: Exception) {
            return Parsed("", "", emptyList(), false, "The AI answer could not be read as a routine.")
        }

        // The two ways an answer can refuse the request.
        if (root.optBoolean("is_gibberish", false)) {
            return Parsed(
                routineName = root.optString("routine_name", ""),
                routineGoal = root.optString("routine_goal", ""),
                days = emptyList(),
                isValid = false,
                errorReason = root.optString("error_reason", "").ifBlank {
                    "The input appears to be unreadable or does not specify recognizable exercises."
                }
            )
        }
        if (root.has("is_valid_request") && !root.optBoolean("is_valid_request", true)) {
            return Parsed(
                routineName = root.optString("routine_name", ""),
                routineGoal = root.optString("routine_goal", ""),
                days = emptyList(),
                isValid = false,
                errorReason = root.optString("error_reason", "").ifBlank {
                    "The request does not describe a workout routine the app can build."
                }
            )
        }

        val items = mutableListOf<ScheduledWorkout>()

        // The seven day form: a "days" array with one entry per weekday. A day with no exercises in
        // it is a rest day and is kept as such.
        val daysArray = root.optJSONArray("days")
        if (daysArray != null && daysArray.length() > 0) {
            for (d in 0 until daysArray.length()) {
                val dayObj = daysArray.optJSONObject(d) ?: continue
                val weekday = weekdayNamedIn(dayObj)
                val focus = dayObj.optString(
                    "focus",
                    dayObj.optString("dayTitle", dayObj.optString("title", ""))
                )
                val note = RoutineParser.cleanNoteText(dayObj.optString("note", ""))
                val exercises = dayObj.optJSONArray("exercises") ?: JSONArray()
                for (j in 0 until exercises.length()) {
                    val exObj = exercises.optJSONObject(j) ?: continue
                    val item = readExercise(exObj, resolveExercise, weekday, focus, note)
                    if (item != null) {
                        items.add(
                            ScheduledWorkout(
                                item = item,
                                weekday = weekday,
                                // A day that names its weekday keeps it; a day that does not is
                                // laid out from the start of the athlete's week.
                                slot = d + 1
                            )
                        )
                    }
                }
            }
        }

        // The flat form: one list of exercises, each saying which day it belongs to.
        if (items.isEmpty()) {
            val notesByWeekday = readDayNotes(root.optJSONArray("day_notes"))
            val exercises = root.optJSONArray("exercises")
            if (exercises != null) {
                var slot = 1
                for (i in 0 until exercises.length()) {
                    val exObj = exercises.optJSONObject(i) ?: continue
                    val weekday = weekdayNamedIn(exObj)
                    val focus = exObj.optString("dayTitle", "")
                    val item = readExercise(exObj, resolveExercise, weekday, focus, "")
                    if (item != null) {
                        val itemSlot = slotOf(exObj, slot)
                        items.add(
                            ScheduledWorkout(
                                item = item.copy(dayNote = notesByWeekday[weekday].orEmpty()),
                                weekday = weekday,
                                slot = itemSlot
                            )
                        )
                        // A workout with no day of its own is a day of its own.
                        if (weekday == 0 && itemSlot == slot) slot++
                    }
                }
            }
        }

        if (items.isEmpty()) {
            return Parsed(
                routineName = root.optString("routine_name", ""),
                routineGoal = root.optString("routine_goal", ""),
                days = emptyList(),
                isValid = false,
                errorReason = root.optString("error_reason", "").ifBlank {
                    "The AI did not return any usable exercise for this routine."
                }
            )
        }

        return Parsed(
            routineName = root.optString("routine_name", ""),
            routineGoal = root.optString("routine_goal", ""),
            days = RoutineSchedule.buildDays(items, weekStartsOnMonday),
            isValid = true,
            errorReason = ""
        )
    }

    /**
     * The weekday an entry names, or 0 when it did not name one.
     *
     * A name is a weekday, whatever the athlete's week starts on. A number in the "weekday" field
     * is the ISO weekday, and a number in the old "day" field is a position in the routine, so it
     * is left unassigned and laid out from the start of the athlete's week.
     */
    fun weekdayNamedIn(obj: JSONObject): Int {
        if (obj.has("weekday")) {
            val text = obj.opt("weekday").toStringText()
            if (text.isNotEmpty()) {
                Weekday.fromName(text)?.let { return it }
            }
        }
        if (obj.has("day")) {
            val raw = obj.opt("day")
            if (raw is String) {
                val text = raw.trim()
                Weekday.fromName(text)?.let { return it }
                // A number written as a string is still a position, not a weekday.
                return 0
            }
            // A number: the old position in the routine, which the schedule fills in from the
            // start of the athlete's week.
            return 0
        }
        return 0
    }

    /** The notes the answer attached to its days, keyed by weekday. */
    private fun readDayNotes(notes: JSONArray?): Map<Int, String> {
        if (notes == null) return emptyMap()
        val result = mutableMapOf<Int, String>()
        for (i in 0 until notes.length()) {
            val obj = notes.optJSONObject(i) ?: continue
            val weekday = weekdayNamedIn(obj)
            if (weekday <= 0) continue
            val note = RoutineParser.cleanNoteText(obj.optString("note", ""))
            if (note.isNotBlank()) result[weekday] = note
        }
        return result
    }

    /**
     * The position of a workout within the routine.
     *
     * A number in the old "day" field is a position, and the workouts that share it belong to the
     * same day. Anything else keeps the position it was given.
     */
    private fun slotOf(obj: JSONObject, fallback: Int): Int {
        val dayRaw = obj.opt("day")
        if (dayRaw != null && dayRaw != JSONObject.NULL && dayRaw !is String) {
            val slot = obj.optInt("day", 0)
            if (slot > 0) return slot
        }
        if (dayRaw is String) {
            val asNumber = dayRaw.trim().toIntOrNull()
            if (asNumber != null && asNumber > 0) return asNumber
        }
        return fallback
    }

    /**
     * Reads one exercise of the answer.
     *
     * @param weekday the weekday the entry sits on, 0 when the answer did not name one.
     * @param focus the focus of the day, e.g. "Upper Body Push".
     * @param note the note of the day, if there is one.
     * @return null when the entry is not an exercise the app can log.
     */
    private suspend fun readExercise(
        obj: JSONObject,
        resolveExercise: suspend (String) -> Exercise?,
        weekday: Int,
        focus: String,
        note: String
    ): RoutineExerciseItem? {
        val name = obj.optString("name").trim()
        // A greeting or a sentence that leaked into the exercises list is never logged.
        if (name.isBlank()) return null
        if (RoutineParser.looksLikeProseNotExercise(name)) return null

        val matched = resolveExercise(name)
        val sets = obj.optInt("sets", 3).coerceIn(1, 10)
        val hold = obj.optInt("holdSeconds", 0).coerceAtLeast(0)
        var reps = obj.optInt("reps", 8).coerceAtLeast(0)
        // A rep based movement must never end up as a hold only set.
        if (reps <= 0 && hold <= 0) reps = 8
        val weight = obj.optDouble("weight", 0.0).coerceAtLeast(0.0)
        val addedWeight = obj.optDouble("addedWeight", 0.0).coerceAtLeast(0.0)
        val equipment = matched?.equipment ?: obj.optString("equipment", "")

        val prescription = RoutineParser.shortPrescription(
            obj.optString("prescription", ""),
            equipment
        ).ifBlank {
            when {
                hold > 0 && reps > 0 -> "$sets sets x $reps reps / ${hold}s"
                hold > 0 -> "$sets sets x ${hold}s"
                else -> "$sets sets x $reps reps"
            }
        }

        return RoutineExerciseItem(
            exerciseId = matched?.id ?: obj.optLong("id", 0L),
            exerciseName = matched?.name ?: name,
            exerciseType = matched?.exerciseType
                ?: obj.optString("type", ExerciseType.STANDARD_WEIGHT),
            category = matched?.category ?: obj.optString("category", ""),
            equipment = equipment,
            targetSets = sets,
            targetReps = reps,
            targetWeight = weight,
            targetAddedWeightKg = addedWeight,
            targetHoldSeconds = hold,
            prescription = prescription,
            weekday = weekday,
            dayTitle = focus,
            dayNote = note
        )
    }

    /** Reads a JSON value as text, without turning a missing value into the word "null". */
    private fun Any?.toStringText(): String {
        if (this == null || this == JSONObject.NULL) return ""
        val text = toString().trim()
        return if (text == "null") "" else text
    }
}
