/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.ai

import com.example.data.model.Exercise
import com.example.data.model.SavedRoutine
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** A new exercise that exists only inside an approval proposal until it is accepted. */
data class ProposedExercise(
    val name: String,
    val category: String,
    val bodyPart: String,
    val target: String,
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: String,
    val exerciseType: String,
    val instructions: String,
    val description: String = ""
) {
    fun toExercise(): Exercise = Exercise(
        name = name,
        category = category,
        bodyPart = bodyPart,
        target = target,
        secondaryMuscles = JSONArray().apply { secondaryMuscles.forEach { put(it) } }.toString(),
        exerciseType = exerciseType,
        instructions = instructions,
        description = description,
        equipment = equipment,
        isCustom = true,
        isAiGenerated = true
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("category", category)
        put("bodyPart", bodyPart)
        put("target", target)
        put("secondaryMuscles", JSONArray().apply { secondaryMuscles.forEach { put(it) } })
        put("equipment", equipment)
        put("exerciseType", exerciseType)
        put("instructions", instructions)
        put("description", description)
    }

    companion object {
        fun fromExercise(exercise: Exercise): ProposedExercise = ProposedExercise(
            name = exercise.name,
            category = exercise.category,
            bodyPart = exercise.bodyPart,
            target = exercise.target,
            secondaryMuscles = exercise.secondaryMuscleList,
            equipment = exercise.equipment,
            exerciseType = exercise.exerciseType,
            instructions = exercise.instructions,
            description = exercise.description
        )

        fun fromJson(obj: JSONObject): ProposedExercise? {
            val parsed = AiExerciseFactory.fromJsonObject(
                obj,
                fallbackName = obj.optString("name").trim()
            ) ?: return null
            // A generated exercise must be actionable, not a row with empty instructions.
            if (parsed.instructions.isBlank()) return null
            return ProposedExercise(
                name = parsed.name,
                category = parsed.category,
                bodyPart = parsed.bodyPart,
                target = parsed.target,
                secondaryMuscles = parsed.secondaryMuscleList,
                equipment = parsed.equipment,
                exerciseType = parsed.exerciseType,
                instructions = parsed.instructions,
                description = parsed.description
            )
        }
    }
}

/**
 * A one-time approval request for a routine change.
 *
 * The routine signature is the stale-check: if the routine JSON changes between proposal and
 * approval, the approval is rejected instead of applying a change to a different plan.
 */
data class RoutineModificationProposal(
    val proposalId: String = UUID.randomUUID().toString(),
    val routineId: Long,
    val routineName: String,
    val routineSignature: String,
    val weekday: Int,
    val dayTitle: String,
    val removeExerciseName: String,
    val removeExerciseId: Long,
    val replacementExerciseId: Long? = null,
    val replacementExerciseName: String,
    val generatedExercise: ProposedExercise? = null,
    val reason: String,
    val summary: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("proposalId", proposalId)
        put("routineId", routineId)
        put("routineName", routineName)
        put("routineSignature", routineSignature)
        put("weekday", weekday)
        put("dayTitle", dayTitle)
        put("removeExerciseName", removeExerciseName)
        put("removeExerciseId", removeExerciseId)
        replacementExerciseId?.let { put("replacementExerciseId", it) }
        put("replacementExerciseName", replacementExerciseName)
        generatedExercise?.let { put("generatedExercise", it.toJson()) }
        put("reason", reason)
        put("summary", summary)
    }

    companion object {
        fun fromJson(raw: String): RoutineModificationProposal? = runCatching {
            fromJson(JSONObject(raw))
        }.getOrNull()

        fun fromJson(obj: JSONObject): RoutineModificationProposal? {
            val routineId = obj.optLong("routineId", 0L)
            val removeName = obj.optString("removeExerciseName").trim()
            val replaceName = obj.optString("replacementExerciseName").trim()
            if (routineId <= 0L || removeName.isBlank() || replaceName.isBlank()) return null
            val weekday = obj.optInt("weekday", 0)
            if (weekday !in 1..7) return null
            return RoutineModificationProposal(
                proposalId = obj.optString("proposalId").ifBlank { UUID.randomUUID().toString() },
                routineId = routineId,
                routineName = obj.optString("routineName").trim(),
                routineSignature = obj.optString("routineSignature").trim(),
                weekday = weekday,
                dayTitle = obj.optString("dayTitle").trim(),
                removeExerciseName = removeName,
                removeExerciseId = obj.optLong("removeExerciseId", 0L),
                replacementExerciseId = obj.optLong("replacementExerciseId", 0L)
                    .takeIf { it > 0L },
                replacementExerciseName = replaceName,
                generatedExercise = obj.optJSONObject("generatedExercise")
                    ?.let(ProposedExercise::fromJson),
                reason = obj.optString("reason").trim(),
                summary = obj.optString("summary").trim()
            )
        }

        fun signatureOf(routine: SavedRoutine): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(routine.exercisesJson.toByteArray(Charsets.UTF_8))
            return routine.id.toString() + ":" + digest.joinToString("") { "%02x".format(it) }
        }
    }
}
