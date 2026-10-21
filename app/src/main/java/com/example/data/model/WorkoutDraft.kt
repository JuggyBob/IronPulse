package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * The workout in progress, saved so a closed app does not cost the athlete the whole session.
 *
 * A quick workout lives entirely in memory (the session, its exercises and its sets are three
 * separate StateFlows in the view model), so closing the app from the recents screen threw all of
 * it away. This is the persisted copy of that state, written every few seconds while a workout is
 * running and read back when the app starts again.
 *
 * It is stored as a single blob in one preference key rather than as rows, and each save replaces
 * the previous one. A log of every save would grow without bound for a workout that can run for
 * hours, and there is exactly one interesting state at any moment: the current one.
 */
data class WorkoutDraft(
    /** "quick" for a quick workout, "routine" for a routine (split) workout. */
    val kind: String = KIND_QUICK,
    /** The session title for a quick workout, the routine name for a routine workout. */
    val title: String = "",
    /** When the workout started. Kept so the elapsed time survives the app being closed. */
    val startTime: Long = 0L,
    /** The exercises in the workout, in the order they were added. */
    val exerciseIds: List<Long> = emptyList(),
    /** Every set, including the ones not ticked off yet. */
    val sets: List<LoggedSet> = emptyList(),
    /** The routine a routine workout came from. Null for a quick workout. */
    val routineId: Long = 0L,
    val routineGoal: String = "",
    /**
     * The weekday this workout belongs to: 1 = Monday ... 7 = Sunday.
     *
     * A draft written before the seven day model existed carries the old "dayNumber" key, which is
     * read as a position in the routine and resolved against the athlete's week start.
     */
    val weekday: Int = Weekday.MONDAY,
    val dayTitle: String = "",
    val dayNote: String = "",
    /** The prescribed targets of the day, so the routine workout can be rebuilt exactly. */
    val routineItems: List<RoutineExerciseItem> = emptyList()
) {
    val isRoutine: Boolean get() = kind == KIND_ROUTINE

    companion object {
        const val KIND_QUICK = "quick"
        const val KIND_ROUTINE = "routine"
    }
}

/**
 * Turns a [WorkoutDraft] into a string and back.
 *
 * Deliberately forgiving: a draft that cannot be read is a draft that is dropped, never a crash on
 * startup. The athlete is told their workout could not be restored, which is sad but recoverable,
 * whereas an app that refuses to open is not.
 */
object WorkoutDraftCodec {

    fun encode(draft: WorkoutDraft): String {
        val root = JSONObject().apply {
            put("kind", draft.kind)
            put("title", draft.title)
            put("startTime", draft.startTime)
            put("exerciseIds", JSONArray().apply { draft.exerciseIds.forEach { put(it) } })
            put("sets", JSONArray().apply { draft.sets.forEach { put(encodeSet(it)) } })
            if (draft.isRoutine) {
                put("routineId", draft.routineId)
                put("routineGoal", draft.routineGoal)
                put("weekday", draft.weekday)
                put("dayTitle", draft.dayTitle)
                put("dayNote", draft.dayNote)
                put("routineItems", RoutineParser.serializeExercises(draft.routineItems))
            }
        }
        return root.toString()
    }

    fun decode(raw: String): WorkoutDraft? {
        if (raw.isBlank()) return null
        return try {
            val root = JSONObject(raw)

            val exerciseIds = mutableListOf<Long>()
            val idsArray = root.optJSONArray("exerciseIds")
            if (idsArray != null) {
                for (i in 0 until idsArray.length()) {
                    exerciseIds.add(idsArray.optLong(i))
                }
            }

            val sets = mutableListOf<LoggedSet>()
            val setsArray = root.optJSONArray("sets")
            if (setsArray != null) {
                for (i in 0 until setsArray.length()) {
                    setsArray.optJSONObject(i)?.let { sets.add(decodeSet(it)) }
                }
            }

            val kind = root.optString("kind", WorkoutDraft.KIND_QUICK)
            val routineItems = if (kind == WorkoutDraft.KIND_ROUTINE) {
                // The routine items are stored with the routine's own serialiser, so the shape
                // cannot drift away from the one saved routines use.
                val itemsJson = root.optString("routineItems", "[]")
                RoutineParser.parseExercises(itemsJson, emptyList())
            } else {
                emptyList()
            }

            WorkoutDraft(
                kind = kind,
                title = root.optString("title", ""),
                startTime = root.optLong("startTime", 0L),
                exerciseIds = exerciseIds,
                sets = sets,
                routineId = root.optLong("routineId", 0L),
                routineGoal = root.optString("routineGoal", ""),
                // A draft written before the seven day model existed carries "dayNumber", which was
                // a position in the routine rather than a day of the week. It is resolved against
                // the athlete's week start so the workout comes back on a real day.
                weekday = root.optInt("weekday", 0).takeIf { it in 1..Weekday.COUNT }
                    ?: RoutineSchedule.weekdayForSlot(
                        root.optInt("dayNumber", 1).coerceAtLeast(1),
                        true
                    ),
                dayTitle = root.optString("dayTitle", ""),
                dayNote = root.optString("dayNote", ""),
                routineItems = routineItems
            )
        } catch (e: Exception) {
            // A truncated or hand edited draft is not worth crashing over.
            null
        }
    }

    private fun encodeSet(set: LoggedSet): JSONObject = JSONObject().apply {
        put("id", set.id)
        put("exerciseId", set.exerciseId)
        put("exerciseName", set.exerciseName)
        put("exerciseType", set.exerciseType)
        put("setNumber", set.setNumber)
        put("weight", set.weight)
        put("addedWeight", set.addedWeight)
        put("reps", set.reps)
        put("holdDurationSeconds", set.holdDurationSeconds)
        put("distanceKm", set.distanceKm)
        put("isCompleted", set.isCompleted)
        put("timestamp", set.timestamp)
        // The body weight the set was done at. It has to survive the round trip: a restored
        // workout whose sets lost their body weight would be measured against whatever the
        // athlete weighs today, which is not what they lifted.
        put("bodyWeightKg", set.bodyWeightKg)
    }

    private fun decodeSet(obj: JSONObject): LoggedSet = LoggedSet(
        id = obj.optLong("id", 0L),
        sessionId = 0L,
        exerciseId = obj.optLong("exerciseId", 0L),
        exerciseName = obj.optString("exerciseName", ""),
        exerciseType = obj.optString("exerciseType", ExerciseType.STANDARD_WEIGHT),
        setNumber = obj.optInt("setNumber", 1),
        weight = obj.optDouble("weight", 0.0),
        addedWeight = obj.optDouble("addedWeight", 0.0),
        reps = obj.optInt("reps", 0),
        holdDurationSeconds = obj.optInt("holdDurationSeconds", 0),
        distanceKm = obj.optDouble("distanceKm", 0.0),
        isCompleted = obj.optBoolean("isCompleted", true),
        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
        // A draft written before the snapshot existed has no body weight: 0 means "not recorded",
        // which is the same thing a set logged back then meant.
        bodyWeightKg = obj.optDouble("bodyWeightKg", 0.0)
    )
}
