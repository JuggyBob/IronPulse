/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.ai

import com.example.data.model.ExerciseType
import com.example.data.model.RoutineDropSet
import com.example.data.model.RoutineExerciseItem
import com.example.data.model.RoutineParser
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One exercise inside an AI Coach routine proposal. Never written before approval. */
data class ProposedRoutineExercise(
    val name: String,
    val exerciseId: Long = 0L,
    val category: String = "",
    val equipment: String = "",
    val exerciseType: String = ExerciseType.PURE_BODYWEIGHT,
    /** Metadata for a movement that does not exist yet, created only after approval. */
    val generatedExercise: ProposedExercise? = null,
    val targetSets: Int = 3,
    val targetReps: Int = 8,
    val targetWeightKg: Double = 0.0,
    val targetAddedWeightKg: Double = 0.0,
    val targetHoldSeconds: Int = 0,
    val targetDistanceKm: Double = 0.0,
    val restSeconds: Int = 90,
    val prescription: String = "",
    /** Optional children, grouped under their 1-based main set index. */
    val dropSets: List<RoutineDropSet> = emptyList()
) {
    fun toRoutineItem(weekday: Int, dayTitle: String, dayNote: String): RoutineExerciseItem =
        RoutineExerciseItem(
            exerciseId = exerciseId,
            exerciseName = generatedExercise?.name ?: name,
            exerciseType = exerciseType,
            category = category,
            equipment = equipment,
            targetSets = targetSets,
            targetReps = targetReps,
            targetWeight = targetWeightKg,
            targetAddedWeightKg = targetAddedWeightKg,
            targetHoldSeconds = targetHoldSeconds,
            targetDistanceKm = targetDistanceKm,
            restSeconds = restSeconds,
            prescription = prescription,
            weekday = weekday,
            dayTitle = dayTitle,
            dayNote = dayNote,
            dropSets = dropSets
        )

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("exerciseId", exerciseId)
        put("category", category)
        put("equipment", equipment)
        put("exerciseType", exerciseType)
        generatedExercise?.let { put("generatedExercise", it.toJson()) }
        put("sets", targetSets)
        put("reps", targetReps)
        put("weightKg", targetWeightKg)
        put("addedWeightKg", targetAddedWeightKg)
        put("holdSeconds", targetHoldSeconds)
        put("distanceKm", targetDistanceKm)
        put("restSeconds", restSeconds)
        put("prescription", prescription)
        if (dropSets.isNotEmpty()) {
            put("dropSets", JSONArray().apply {
                dropSets.sortedWith(compareBy({ it.mainSetIndex }, { it.dropIndex })).forEach { drop ->
                    put(JSONObject().apply {
                        put("mainSet", drop.mainSetIndex)
                        put("drop", drop.dropIndex)
                        put("reps", drop.targetReps)
                        put("holdSeconds", drop.targetHoldSeconds)
                        put("distanceKm", drop.targetDistanceKm)
                        put("weightKg", drop.targetWeight)
                        put("addedWeightKg", drop.targetAddedWeightKg)
                        put("restSeconds", drop.restSeconds)
                    })
                }
            })
        }
    }

    companion object {
        /**
         * Reads optional drop-set children. A missing array is no drop sets, and a child is clamped
         * to a real main set so a malformed proposal can never create an orphan.
         */
        private fun parseDropSets(obj: JSONObject, mainSetCount: Int): List<RoutineDropSet> {
            val array = obj.optJSONArray("dropSets") ?: return emptyList()
            val result = mutableListOf<RoutineDropSet>()
            for (i in 0 until array.length()) {
                val drop = array.optJSONObject(i) ?: continue
                val mainSet = drop.optInt(
                    "mainSet",
                    drop.optInt("mainSetIndex", 1)
                ).coerceIn(1, mainSetCount.coerceAtLeast(1))
                result += RoutineDropSet(
                    mainSetIndex = mainSet,
                    dropIndex = drop.optInt("drop", drop.optInt("dropIndex", i + 1)).coerceAtLeast(1),
                    targetReps = drop.optInt("reps", 0).coerceIn(0, 100),
                    targetHoldSeconds = drop.optInt("holdSeconds", 0).coerceIn(0, 600),
                    targetDistanceKm = drop.optDouble("distanceKm", 0.0).coerceAtLeast(0.0),
                    targetWeight = drop.optDouble("weightKg", drop.optDouble("weight", 0.0))
                        .coerceAtLeast(0.0),
                    targetAddedWeightKg = drop.optDouble(
                        "addedWeightKg",
                        drop.optDouble("addedWeight", 0.0)
                    ).coerceAtLeast(0.0),
                    restSeconds = drop.optInt("restSeconds", 0).coerceIn(0, 600)
                )
            }
            return result
                .groupBy { it.mainSetIndex }
                .flatMap { (_, rows) ->
                    rows.sortedBy { it.dropIndex }.mapIndexed { index, drop ->
                        drop.copy(dropIndex = index + 1)
                    }
                }
        }

        fun fromJson(obj: JSONObject): ProposedRoutineExercise? {
            val name = obj.optString("name").trim()
            if (name.isBlank()) return null
            val exerciseId = obj.optLong("exerciseId", 0L).coerceAtLeast(0L)
            val generated = obj.optJSONObject("generatedExercise")
                ?.let(ProposedExercise::fromJson)
            // A proposal is actionable only when it points at a catalog row or carries validated
            // metadata for a new one. The tool handler resolves catalog names to ids first.
            if (exerciseId <= 0L && generated == null) return null

            val sets = obj.optInt("sets", 3).coerceIn(1, 10)
            var reps = obj.optInt("reps", 8).coerceIn(0, 100)
            val hold = obj.optInt("holdSeconds", 0).coerceIn(0, 600)
            if (reps <= 0 && hold <= 0) reps = 8
            val equipment = obj.optString("equipment", "")
                .ifBlank { generated?.equipment.orEmpty() }
            val rawPrescription = obj.optString("prescription", "")
            val prescription = RoutineParser.shortPrescription(rawPrescription, equipment)
                .ifBlank {
                    when {
                        hold > 0 && reps > 0 -> "$sets sets x $reps reps / ${hold}s"
                        hold > 0 -> "$sets sets x ${hold}s"
                        else -> "$sets sets x $reps reps"
                    }
                }
            return ProposedRoutineExercise(
                name = name,
                exerciseId = exerciseId,
                category = obj.optString("category", "")
                    .ifBlank { generated?.category.orEmpty() },
                equipment = equipment,
                exerciseType = obj.optString("exerciseType", "")
                    .ifBlank { generated?.exerciseType ?: ExerciseType.PURE_BODYWEIGHT },
                generatedExercise = generated,
                targetSets = sets,
                targetReps = reps,
                targetWeightKg = obj.optDouble("weightKg", 0.0).coerceAtLeast(0.0),
                targetAddedWeightKg = obj.optDouble("addedWeightKg", 0.0).coerceAtLeast(0.0),
                targetHoldSeconds = hold,
                targetDistanceKm = obj.optDouble("distanceKm", 0.0).coerceAtLeast(0.0),
                restSeconds = obj.optInt("restSeconds", 90).coerceIn(0, 600),
                prescription = prescription,
                dropSets = parseDropSets(obj, sets)
            )
        }
    }
}

/** One weekday of an AI Coach routine proposal. */
data class ProposedRoutineDay(
    val weekday: Int,
    val focus: String = "",
    val note: String = "",
    val exercises: List<ProposedRoutineExercise> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("weekday", weekday)
        put("focus", focus)
        put("note", note)
        put("exercises", JSONArray().apply { exercises.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(obj: JSONObject): ProposedRoutineDay? {
            val weekday = obj.optInt("weekday", 0)
            if (weekday !in 1..7) return null
            val array = obj.optJSONArray("exercises") ?: return null
            val exercises = (0 until array.length())
                .mapNotNull { array.optJSONObject(it)?.let(ProposedRoutineExercise::fromJson) }
            if (exercises.isEmpty()) return null
            return ProposedRoutineDay(
                weekday = weekday,
                focus = obj.optString("focus", "").trim(),
                note = obj.optString("note", "").trim(),
                exercises = exercises
            )
        }
    }
}

/**
 * A complete new routine the coach proposed in chat.
 *
 * It is inert until the athlete taps save. The approval service revalidates every exercise against
 * the live catalog so a catalog change between proposal and approval cannot produce a broken plan.
 */
data class NewRoutineProposal(
    val proposalId: String = UUID.randomUUID().toString(),
    val routineName: String,
    val goal: String = "",
    val summary: String = "",
    val days: List<ProposedRoutineDay>,
    val createdAt: Long = System.currentTimeMillis()
) {
    val trainingDays: Int get() = days.count { it.exercises.isNotEmpty() }
    val exerciseCount: Int get() = days.sumOf { it.exercises.size }

    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", KIND)
        put("proposalId", proposalId)
        put("routineName", routineName)
        put("goal", goal)
        put("summary", summary)
        put("createdAt", createdAt)
        put("days", JSONArray().apply { days.forEach { put(it.toJson()) } })
    }

    companion object {
        const val KIND = "new_routine"

        fun fromJson(raw: String): NewRoutineProposal? = runCatching {
            fromJson(JSONObject(raw))
        }.getOrNull()

        fun fromJson(obj: JSONObject): NewRoutineProposal? {
            val name = obj.optString("routineName", obj.optString("routine_name", "")).trim()
            if (name.isBlank()) return null
            val array = obj.optJSONArray("days") ?: return null
            if (array.length() == 0) return null

            // Merge duplicate weekdays instead of silently dropping one of them.
            val merged = linkedMapOf<Int, ProposedRoutineDay>()
            for (index in 0 until array.length()) {
                val day = array.optJSONObject(index)?.let(ProposedRoutineDay::fromJson) ?: continue
                val existing = merged[day.weekday]
                merged[day.weekday] = if (existing == null) {
                    day
                } else {
                    existing.copy(
                        focus = existing.focus.ifBlank { day.focus },
                        note = existing.note.ifBlank { day.note },
                        exercises = existing.exercises + day.exercises
                    )
                }
            }
            if (merged.isEmpty()) return null
            if (merged.values.sumOf { it.exercises.size } > 84) return null

            return NewRoutineProposal(
                proposalId = obj.optString("proposalId").ifBlank { UUID.randomUUID().toString() },
                routineName = name,
                goal = obj.optString("goal", "").trim(),
                summary = obj.optString("summary", "").trim(),
                days = merged.values.sortedBy { it.weekday },
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
